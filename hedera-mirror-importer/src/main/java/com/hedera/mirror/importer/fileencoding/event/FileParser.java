package com.hedera.mirror.importer.fileencoding.event;

/*-
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

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.function.Consumer;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Hex;

import com.hedera.mirror.importer.domain.EventFile;
import com.hedera.mirror.importer.domain.EventItem;
import com.hedera.mirror.importer.exception.HashMismatchException;
import com.hedera.mirror.importer.util.Utility;

@Log4j2
public class FileParser {
    public static final String HASH_ALGORITHM = "SHA-384";
    public static final byte EVENT_TYPE_PREV_HASH = 1;       // next 48 bytes are hash384 or previous files
    public static final byte EVENT_STREAM_FILE_VERSION_LEGACY = 2;
    public static final byte EVENT_STREAM_VERSION = 2;
    public static final byte EVENT_STREAM_FILE_VERSION_CURRENT = 3;
    public static final byte EVENT_STREAM_START_NO_TRANS_WITH_VERSION = 0x5b;
    public static final byte EVENT_STREAM_START_WITH_VERSION = 0x5a;
    public static final byte EVENT_COMM_EVENT_LAST = 0x46;

    /**
     * Parses event stream file.

     * @param filePath path to event file
     * @param expectedPrevFileHash expected previous file's hash in current file. Throws {@link HashMismatchException}
     *                             on mismatch
     * @param verifyHashAfter previous file's hash mismatch is ignored if file is from before this time
     * @param eventItemConsumer if not null, consumer is invoked for each event in the file
     * @return parsed event file
     */
    public static EventFile parse(String filePath, String expectedPrevFileHash, Instant verifyHashAfter,
                                  Consumer<EventItem> eventItemConsumer) {
        EventFile eventFile = new EventFile();
        eventFile.setName(filePath);
        String fileName = Utility.getFileName(filePath);

        try (DataInputStream dis = new DataInputStream(new FileInputStream(filePath))) {
            int fileVersion = dis.readInt();
            if (fileVersion < EVENT_STREAM_FILE_VERSION_LEGACY) {
                throw new IllegalArgumentException("Invalid event stream file version " + fileVersion);
            }
            eventFile.setFileVersion(fileVersion);

            // MessageDigest for getting the file Hash
            // suppose file[i] = p[i] || h[i] || c[i];
            // p[i] denotes the bytes before previousFileHash;
            // h[i] denotes the hash of file i - 1, i.e., previousFileHash;
            // c[i] denotes the bytes after previousFileHash;
            // '||' means concatenation
            // for Version2, h[i + 1] = hash(p[i] || h[i] || c[i]);
            // for Version3, h[i + 1] = hash(p[i] || h[i] || hash(c[i]))
            MessageDigest md = MessageDigest.getInstance(HASH_ALGORITHM);
            md.update(Utility.integerToBytes(fileVersion));

            MessageDigest mdForContent = md;
            if (fileVersion >= EVENT_STREAM_FILE_VERSION_CURRENT) {
                // For calculating hash(c[i]) separately if version >= 3
                mdForContent = MessageDigest.getInstance(HASH_ALGORITHM);
            }
            while (dis.available() != 0) {
                byte typeDelimiter = dis.readByte();
                EventItem eventItem;
                switch (typeDelimiter) {
                    case EVENT_TYPE_PREV_HASH:
                        md.update(typeDelimiter);
                        byte[] readPrevFileHash = new byte[48];
                        dis.readFully(readPrevFileHash);
                        eventFile.setPreviousHash(Hex.encodeHexString(readPrevFileHash));
                        md.update(readPrevFileHash);
                        Instant fileInstant = Instant.parse(fileName.replaceAll(".evts", "").replaceAll("_", ":"));

                        if (!Utility.verifyHashChain(eventFile.getPreviousHash(), expectedPrevFileHash,
                                verifyHashAfter, fileInstant)) {
                            throw new HashMismatchException("Hash mismatch for file " + fileName);
                        }
                        break;

                    case EVENT_STREAM_START_NO_TRANS_WITH_VERSION:
                        mdForContent.update(typeDelimiter);
                        eventItem = loadEvent(dis, mdForContent, false);
                        if (eventItemConsumer != null) {
                            eventItemConsumer.accept(eventItem);
                        }
                        break;

                    case EVENT_STREAM_START_WITH_VERSION:
                        mdForContent.update(typeDelimiter);
                        eventItem = loadEvent(dis, mdForContent, true);
                        if (eventItemConsumer != null) {
                            eventItemConsumer.accept(eventItem);
                        }
                        break;

                    default:
                        throw new IllegalArgumentException(String.format(
                                "Unknown event file delimiter %s for file %s", typeDelimiter, fileName));
                }
            }
            if (fileVersion >= EVENT_STREAM_FILE_VERSION_CURRENT) {
                md.update(mdForContent.digest());
            }
            if (eventFile.getPreviousHash() == null) {
                throw new IllegalArgumentException("previous hash is null in file " + fileName);
            }
            eventFile.setFileHash(Hex.encodeHexString(md.digest()));
            log.trace("Calculated file hash for the event file {}", eventFile.getFileHash());
            return eventFile;
        } catch (Exception e) {
            throw new IllegalArgumentException("Error parsing bad event file " + fileName, e);
        }
    }

    private static EventItem loadEvent(DataInputStream dis, MessageDigest md, boolean hasTransactions)
            throws IOException {
        EventItem eventItem = new EventItem();
        int version = dis.readInt();
        if (version != EVENT_STREAM_VERSION) {
            throw new IllegalArgumentException("Invalid EventStream format version : " + version);
        }
        md.update(Utility.integerToBytes(EVENT_STREAM_VERSION));

        long creatorId = dis.readLong();
        md.update(longToBytes(creatorId));
        eventItem.setCreatorId(creatorId);

        long creatorSeq = dis.readLong();
        md.update(longToBytes(creatorSeq));
        eventItem.setCreatorSeq(creatorSeq);

        long otherId = dis.readLong();
        md.update(longToBytes(otherId));
        eventItem.setOtherId(otherId);

        long otherSeq = dis.readLong();
        md.update(longToBytes(otherSeq));
        eventItem.setOtherSeq(otherSeq);

        long selfParentGen = dis.readLong();
        md.update(longToBytes(selfParentGen));
        eventItem.setSelfParentGen(selfParentGen);

        long otherParentGen = dis.readLong();
        md.update(longToBytes(otherParentGen));
        eventItem.setOtherParentGen(otherParentGen);

        eventItem.setSelfParentHash(readNullableByteArray(dis, md));
        eventItem.setOtherParentHash(readNullableByteArray(dis, md));

        if (hasTransactions) {
            eventItem.setTransactions(Transaction.readArray(dis, md));
        }

        Instant timeCreated = readInstant(dis);
        md.update(instantToBytes(timeCreated));
        eventItem.setTimeCreated(timeCreated);

        eventItem.setSignature(readByteArray(dis, md));
        byte eventEndMarker = dis.readByte();
        if (eventEndMarker != EVENT_COMM_EVENT_LAST) {
            throw new IllegalArgumentException("Invalid event end marker : " + eventEndMarker);
        }
        md.update(EVENT_COMM_EVENT_LAST);

        eventItem.setHash(readByteArray(dis, md));  // event's hash

        Instant consensusTimeStamp = readInstant(dis);
        md.update(instantToBytes(consensusTimeStamp));
        eventItem.setConsensusTimeStamp(consensusTimeStamp);

        long consensusOrder = dis.readLong();
        md.update(longToBytes(consensusOrder));
        eventItem.setConsensusOrder(consensusOrder);

        if (log.isTraceEnabled()) {
            log.trace("Event: {}", eventItem);
        }
        return eventItem;
    }

    private static Instant readInstant(DataInput dis) throws IOException {
        return Instant.ofEpochSecond(
                dis.readLong(), // from getEpochSecond()
                dis.readLong()); // from getNano()
    }

    private static byte[] instantToBytes(Instant instant) {
        ByteBuffer b = ByteBuffer.allocate(16);
        b.putLong(instant.getEpochSecond()).putLong(instant.getNano());
        return b.array();
    }

    private static byte[] readByteArray(DataInputStream dis, MessageDigest md) throws IOException {
        int len = dis.readInt();
        if (len < 0) {
            throw new IllegalArgumentException("Invalid length: " + len);
        }
        md.update(Utility.integerToBytes(len));
        return readByteArrayOfLength(dis, len, md);
    }

    private static byte[] readNullableByteArray(DataInputStream dis, MessageDigest md) throws IOException {
        int len = dis.readInt();
        md.update(Utility.integerToBytes(len));
        if (len < 0) {
            return null;
        }
        return readByteArrayOfLength(dis, len, md);
    }

    private static byte[] readByteArrayOfLength(DataInputStream dis, int len, MessageDigest md) throws IOException {
        int checksum = dis.readInt();
        md.update(Utility.integerToBytes(checksum));

        if (checksum != (101 - len)) { // must be at wrong place in the stream
            throw new IllegalArgumentException("Invalid checksum : " + checksum + ". length is " + len);
        }
        byte[] data = new byte[len];
        dis.readFully(data);
        md.update(data);
        return data;
    }

    private static byte[] longToBytes(long number) {
        ByteBuffer b = ByteBuffer.allocate(8);
        b.putLong(number);
        return b.array();
    }
}
