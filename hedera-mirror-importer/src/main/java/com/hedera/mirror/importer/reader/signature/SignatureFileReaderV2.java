package com.hedera.mirror.importer.reader.signature;/*
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;

import com.hedera.mirror.importer.util.FileDelimiter;

@Log4j2
@Named
public class SignatureFileReaderV2 implements SignatureFileReader {
    @Override
    public Pair<byte[], byte[]> read(InputStream inputStream) {
        byte[] sig = null;

        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(inputStream))) {
            byte[] fileHash = new byte[48];

            while (dis.available() != 0) {
                byte typeDelimiter = dis.readByte();

                switch (typeDelimiter) {
                    case FileDelimiter.SIGNATURE_TYPE_FILE_HASH:
                        int length = dis.read(fileHash);
                        if (length != fileHash.length) {
                            throw new IllegalArgumentException("Unable to read signature file hash");
                        }
                        break;

                    case FileDelimiter.SIGNATURE_TYPE_SIGNATURE:
                        int sigLength = dis.readInt();
                        byte[] sigBytes = new byte[sigLength];
                        dis.readFully(sigBytes);
                        sig = sigBytes;
                        break;
                    default:
                        //TODO
//                        log.error("Unknown file delimiter {} in signature file {}", typeDelimiter, file);
                        return null;
                }
            }

            return Pair.of(fileHash, sig);
        } catch (Exception e) {
            //TODO Log without filename;
//            log.error("Unable to extract hash and signature from file {}", file, e);
        }

        return null;
    }
}
