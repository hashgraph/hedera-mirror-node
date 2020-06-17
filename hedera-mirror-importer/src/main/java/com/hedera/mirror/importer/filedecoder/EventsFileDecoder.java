package com.hedera.mirror.importer.filedecoder;

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

import static com.hedera.mirror.importer.util.Utility.readBytes;
import static com.hedera.mirror.importer.util.Utility.readInt;

import com.swirlds.common.Transaction;
import com.swirlds.common.crypto.Signature;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Hex;

import com.hedera.mirror.importer.domain.EventFile;
import com.hedera.mirror.importer.domain.EventItem;
import com.hedera.mirror.importer.exception.HashMismatchException;
import com.hedera.mirror.importer.util.Utility;

@Log4j2
public class EventsFileDecoder {
    public static final String HASH_ALGORITHM = "SHA-384";
    public static final byte EVENT_TYPE_PREV_HASH = 1;       // next 48 bytes are hash384 or previous files
    public static final byte EVENT_STREAM_FILE_VERSION_LEGACY = 2;
    public static final byte EVENT_STREAM_VERSION = 2;
    public static final byte EVENT_STREAM_FILE_VERSION_CURRENT = 3;
    public static final byte EVENT_STREAM_START_NO_TRANS_WITH_VERSION = 0x5b;
    public static final byte EVENT_STREAM_START_WITH_VERSION = 0x5a;
    public static final byte EVENT_COMM_EVENT_LAST = 0x46;

    /**
     * Decodes event stream file.

     * @param filePath path to event file
     * @param expectedPrevFileHash expected previous file's hash in current file. Throws {@link HashMismatchException}
     *                             on mismatch
     * @param verifyHashAfter previous file's hash mismatch is ignored if file is from before this time
     * @param eventItemConsumer if not null, consumer is invoked for each event in the file
     */
    public static EventFile decode(String filePath, String expectedPrevFileHash, Instant verifyHashAfter,
                                   Consumer<EventItem> eventItemConsumer) {
        EventFile eventFile = new EventFile();
        eventFile.setName(filePath);
        String fileName = Utility.getFileName(filePath);

        try (DataInputStream dis = new DataInputStream(new FileInputStream(filePath))) {
            // MessageDigest for getting the file Hash
            // suppose file[i] = p[i] || h[i] || c[i];
            // p[i] denotes the bytes before previousFileHash;
            // h[i] denotes the hash of file i - 1, i.e., previousFileHash;
            // c[i] denotes the bytes after previousFileHash;
            // '||' means concatenation
            // for Version2, h[i + 1] = hash(p[i] || h[i] || c[i]);
            // for Version3, h[i + 1] = hash(p[i] || h[i] || hash(c[i]))
            MessageDigest md = MessageDigest.getInstance(HASH_ALGORITHM);

            int fileVersion = readInt(dis, md);
            if (fileVersion < EVENT_STREAM_FILE_VERSION_LEGACY) {
                throw new IllegalArgumentException("Invalid event stream file version " + fileVersion);
            }
            eventFile.setFileVersion(fileVersion);

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
                        byte[] readPrevFileHash = readBytes(dis, 48, md);
                        eventFile.setPreviousHash(Hex.encodeHexString(readPrevFileHash));
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
        int version = readInt(dis, md);
        if (version != EVENT_STREAM_VERSION) {
            throw new IllegalArgumentException("Invalid EventStream format version : " + version);
        }

        eventItem.setCreatorId(readLong(dis, md));
        eventItem.setCreatorSeq(readLong(dis, md));
        eventItem.setOtherId(readLong(dis, md));
        eventItem.setOtherSeq(readLong(dis, md));
        eventItem.setSelfParentGen(readLong(dis, md));
        eventItem.setOtherParentGen(readLong(dis, md));
        eventItem.setSelfParentHash(readBytesWithChecksum(dis, md, true));
        eventItem.setOtherParentHash(readBytesWithChecksum(dis, md, true));
        if (hasTransactions) {
            eventItem.setTransactions(readTransactions(dis, md));
        }
        eventItem.setTimeCreated(readInstant(dis, md));
        eventItem.setSignature(readBytesWithChecksum(dis, md, false));

        byte eventEndMarker = dis.readByte();
        if (eventEndMarker != EVENT_COMM_EVENT_LAST) {
            throw new IllegalArgumentException("Invalid event end marker : " + eventEndMarker);
        }
        md.update(EVENT_COMM_EVENT_LAST);

        eventItem.setHash(readBytesWithChecksum(dis, md, false));  // event's hash
        eventItem.setConsensusTimeStamp(readInstant(dis, md));
        eventItem.setConsensusOrder(readLong(dis, md));
        if (log.isTraceEnabled()) {
            log.trace("Event: {}", eventItem);
        }
        return eventItem;
    }

    /**
     * Read all {@link Transaction}s from the {@link DataInputStream}.
     *
     * @throws IOException  if the internal checksum cannot be validated, or if any error occurs when reading the file
     */
    public static List<Transaction> readTransactions(DataInputStream dis, MessageDigest md) throws IOException {
        int numTransactions = readInt(dis, md);
        if (numTransactions < 0) {
            throw new IllegalArgumentException("Invalid number of transactions: " + numTransactions);
        }
        readAndValidateChecksum(dis, 1873 - numTransactions, md);
        List<Transaction> transactions = new ArrayList<>(numTransactions);
        for (int i = 0; i < numTransactions; i++) {
            transactions.add(deserialize(dis, md));
        }
        return transactions;
    }

    /**
     * Read single {@link Transaction} from the {@link DataInputStream}.
     *
     * @throws IOException  if the internal checksum cannot be validated, or if any error occurs when reading the file
     */
    private static Transaction deserialize(DataInputStream dis, MessageDigest md) throws IOException {
        int txLen = readInt(dis, md);
        if (txLen < 0) {
            throw new IllegalArgumentException("Invalid number of transactions: " + txLen);
        }
        readAndValidateChecksum(dis, 277 - txLen, md);
        readBoolean(dis, md); // system field of transaction
        byte[] contents = readBytes(dis, txLen, md);
        Signature[] signatures = readSignatures(dis, md);
        return new Transaction(contents, signatures);
    }

    /**
     * Read all {@link Signature}s from the {@link DataInputStream}.
     *
     * @throws IOException  if the internal checksum cannot be validated, or if any error occurs when reading the file
     */
    private static Signature[] readSignatures(DataInputStream dis, MessageDigest md) throws IOException {
        int numSigs = readInt(dis, md);
        if (numSigs < 0) {
            throw new IllegalArgumentException("Invalid number of signatures: " + numSigs);
        }
        readAndValidateChecksum(dis, 353 - numSigs, md);
        Signature[] signatures = null;
        if (numSigs > 0) {
            signatures = new Signature[numSigs];
            for (int i = 0; i < numSigs; i++) {
                signatures[i] = Signature.deserialize(dis, null);
            }
        }
        return signatures;
    }

    private static Instant readInstant(DataInputStream dis, MessageDigest md) throws IOException {
         return Instant.ofEpochSecond(readLong(dis, md), readLong(dis, md));
    }

    private static byte[] readBytesWithChecksum(DataInputStream dis, MessageDigest md, boolean canBeEmpty)
            throws IOException {
        int len = readInt(dis, md);
        if (len < 0) {
            if (canBeEmpty) {
                return null;
            }
            throw new IllegalArgumentException("Invalid length: " + len);
        }
        readAndValidateChecksum(dis, 101 - len, md);
        return readBytes(dis, len, md);
    }

    private static long readLong(DataInputStream dis, MessageDigest md) throws IOException {
        long value = dis.readLong();
        ByteBuffer b = ByteBuffer.allocate(8);
        b.putLong(value);
        md.update(b.array());
        return value;
    }

    private static boolean readBoolean(DataInputStream dis, MessageDigest md) throws IOException {
        boolean value = dis.readBoolean();
        md.update(value ? (byte) 1 : (byte) 0);
        return value;
    }

    private static void readAndValidateChecksum(DataInputStream dis, int expectedChecksum, MessageDigest md)
            throws IOException {
        int checksum = readInt(dis, md);
        if (checksum != expectedChecksum) {
            throw new IllegalArgumentException("Invalid checksum : " + checksum + ". Expected: " + expectedChecksum);
        }
    }
}
