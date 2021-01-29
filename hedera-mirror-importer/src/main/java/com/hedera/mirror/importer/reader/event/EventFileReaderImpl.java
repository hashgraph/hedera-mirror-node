package com.hedera.mirror.importer.reader.event;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.google.common.primitives.Ints;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import javax.inject.Named;
import org.apache.commons.codec.binary.Hex;

import com.hedera.mirror.importer.domain.EventFile;
import com.hedera.mirror.importer.exception.InvalidEventFileException;

@Named
public class EventFileReaderImpl implements EventFileReader {

    public static final String HASH_ALGORITHM = "SHA-384";
    public static final byte EVENT_TYPE_PREV_HASH = 1; // next 48 bytes are SHA-384 hash of previous files
    public static final int  EVENT_PREV_HASH_LENGTH = 48; // SHA-384 - 48 bytes
    public static final byte EVENT_STREAM_FILE_VERSION_2 = 2;
    public static final byte EVENT_STREAM_FILE_VERSION_3 = 3;

    @Override
    public EventFile read(File file) {
        EventFile eventFile = new EventFile();
        String fileName = file.getName();

        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            // MessageDigest for getting the file Hash
            // suppose file[i] = p[i] || h[i] || c[i];
            // p[i] denotes the bytes before previousFileHash;
            // h[i] denotes the hash of file i - 1, i.e., previousFileHash;
            // c[i] denotes the bytes after previousFileHash;
            // '||' means concatenation
            // for Version2, h[i + 1] = hash(p[i] || h[i] || c[i]);
            // for Version3, h[i + 1] = hash(p[i] || h[i] || hash(c[i]))
            MessageDigest md = MessageDigest.getInstance(HASH_ALGORITHM);

            int fileVersion = dis.readInt();
            md.update(Ints.toByteArray(fileVersion));
            if (fileVersion < EVENT_STREAM_FILE_VERSION_2 ||
                    fileVersion > EVENT_STREAM_FILE_VERSION_3) {
                throw new InvalidEventFileException("Invalid event stream file version " + fileVersion);
            }

            byte typePrevHash = dis.readByte();
            md.update(typePrevHash);
            if (typePrevHash != EVENT_TYPE_PREV_HASH) {
                throw new InvalidEventFileException("Expect EVENT_TYPE_PREV_HASH marker, got " + typePrevHash);
            }

            byte[] prevFileHash = new byte[EVENT_PREV_HASH_LENGTH];
            dis.readFully(prevFileHash);
            md.update(prevFileHash);

            byte[] remaining = dis.readAllBytes();
            if (remaining.length != 0) {
                if (fileVersion == EVENT_STREAM_FILE_VERSION_2) {
                    md.update(remaining);
                } else {
                    MessageDigest mdForEventData = MessageDigest.getInstance(HASH_ALGORITHM);
                    md.update(mdForEventData.digest(remaining));
                }
            }

            eventFile.setName(fileName);
            eventFile.setFileVersion(fileVersion);
            eventFile.setPreviousHash(Hex.encodeHexString(prevFileHash));
            eventFile.setHash(Hex.encodeHexString(md.digest()));
            eventFile.setCount(0L);
        } catch (InvalidEventFileException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidEventFileException("Error reading bad event file " + fileName, e);
        }

        return eventFile;
    }
}
