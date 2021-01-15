package com.hedera.mirror.importer.reader.record;

/*-
 *
 * Hedera Mirror Node
 *  ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
 *  ​
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
 *
 */

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Consumer;
import javax.inject.Named;
import lombok.Value;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FilenameUtils;

import com.hedera.mirror.importer.domain.DigestAlgorithm;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;
import com.hedera.mirror.importer.exception.StreamFileReaderException;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.reader.Utility;

@Named
public class RecordFileReaderImplV5 implements RecordFileReader {

    private static final DigestAlgorithm DIGEST_ALGORITHM = DigestAlgorithm.SHA384;
    private static final long HASH_OBJECT_CLASS_ID = 0xf422da83a251741eL;
    private static final int OBJECT_STREAM_VERSION = 1;
    private static final long RECORD_STREAM_OBJECT_CLASS_ID = 0xe370929ba5429d8bL;
    private static final int VERSION = 5;

    @Override
    public RecordFile read(StreamFileData streamFileData, Consumer<RecordItem> itemConsumer) {
        MessageDigest mdForFile = createMessageDigest(DIGEST_ALGORITHM);
        MessageDigest mdForMetadata = createMessageDigest(DIGEST_ALGORITHM);

        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(new DigestInputStream(streamFileData.getInputStream(), mdForFile)))) {
            RecordFile recordFile = new RecordFile();
            String filename = FilenameUtils.getName(streamFileData.getFilename());

            recordFile.setName(filename);
            recordFile.setDigestAlgorithm(DIGEST_ALGORITHM);

            readHeader(dis, mdForMetadata, recordFile);
            readBody(dis, itemConsumer, mdForMetadata, recordFile);

            recordFile.setFileHash(Hex.encodeHexString(mdForFile.digest()));
            recordFile.setMetadataHash(Hex.encodeHexString(mdForMetadata.digest()));

            return recordFile;
        } catch (IOException e) {
            throw new StreamFileReaderException("Error reading record file " + streamFileData.getFilename(), e);
        }
    }

    private void readHeader(DataInputStream dis, MessageDigest mdForMetadata,
            RecordFile recordFile) throws IOException {
        int version = dis.readInt();
        Utility.validate(VERSION, version, "record file version");

        int hapiVersionMajor = dis.readInt();
        int hapiVersionMinor = dis.readInt();
        int hapiVersionPatch = dis.readInt();

        mdForMetadata.update(Ints.toByteArray(version));
        mdForMetadata.update(Ints.toByteArray(hapiVersionMajor));
        mdForMetadata.update(Ints.toByteArray(hapiVersionMinor));
        mdForMetadata.update(Ints.toByteArray(hapiVersionPatch));

        recordFile.setHapiVersionMajor(hapiVersionMajor);
        recordFile.setHapiVersionMinor(hapiVersionMinor);
        recordFile.setHapiVersionPatch(hapiVersionPatch);
        recordFile.setVersion(version);
    }

    private void readBody(DataInputStream dis, Consumer<RecordItem> itemConsumer, MessageDigest mdForMetadata,
            RecordFile recordFile) throws IOException {
        String filename = recordFile.getName();

        int objectStreamVersion = dis.readInt();
        Utility.validate(OBJECT_STREAM_VERSION, objectStreamVersion, "object stream version", filename);
        mdForMetadata.update(Ints.toByteArray(objectStreamVersion));

        // start object running hash
        byte[] startHash = readHashFromHashObject(dis, mdForMetadata, filename);

        long count = 0;
        long consensusStart = 0;
        RecordItem lastRecordItem = null;
        RecordStreamObjectBytes recordStreamObjectBytes = null;

        // read record stream objects
        while (true) {
            long classID = peakClassID(dis);
            if (classID != RECORD_STREAM_OBJECT_CLASS_ID) {
                break;
            }

            recordStreamObjectBytes = readRecordStreamObjectBytes(dis, filename);
            boolean isFirstTransaction = count == 0;
            RecordItem recordItem = null;

            if (itemConsumer != null || isFirstTransaction) {
                recordItem = recordItemFromObjectBytes(recordStreamObjectBytes);

                if (itemConsumer != null) {
                    itemConsumer.accept(recordItem);
                }

                if (isFirstTransaction) {
                    consensusStart = recordItem.getConsensusTimestamp();
                }
            }

            lastRecordItem = recordItem;
            count++;
        }

        if (lastRecordItem == null) {
            if (recordStreamObjectBytes == null) {
                throw new InvalidStreamFileException("No record stream objects in record file " + filename);
            }
            lastRecordItem = recordItemFromObjectBytes(recordStreamObjectBytes);
        }
        long consensusEnd = lastRecordItem.getConsensusTimestamp();

        // end object running hash
        byte[] endHash = readHashFromHashObject(dis, mdForMetadata, filename);
        Utility.validate(0, dis.available(), "remaining data size", filename);

        recordFile.setCount(count);
        recordFile.setConsensusEnd(consensusEnd);
        recordFile.setConsensusStart(consensusStart);
        recordFile.setEndRunningHash(Hex.encodeHexString(endHash));
        recordFile.setPreviousHash(Hex.encodeHexString(startHash));
    }

    private MessageDigest createMessageDigest(DigestAlgorithm digestAlgorithm) {
        try {
            return MessageDigest.getInstance(digestAlgorithm.getName());
        } catch (NoSuchAlgorithmException e) {
            throw new StreamFileReaderException(e);
        }
    }

    private long peakClassID(DataInputStream dis) throws IOException {
        dis.mark(Longs.BYTES);
        long classID = dis.readLong();
        dis.reset();
        return classID;
    }

    private byte[] readHashFromHashObject(DataInputStream dis, MessageDigest mdForMetadata,
            String filename) throws IOException {
        long classID = dis.readLong();
        Utility.validate(HASH_OBJECT_CLASS_ID, classID, "hash object class ID", filename);
        int classVersion = dis.readInt();

        int digestType = dis.readInt();
        Utility.validate(DIGEST_ALGORITHM.getType(), digestType, "hash object digest type");
        byte[] hash = Utility
                .readLengthAndBytes(dis, DIGEST_ALGORITHM.getSize(), DIGEST_ALGORITHM.getSize(), false, filename,
                        "hash");

        mdForMetadata.update(Longs.toByteArray(classID));
        mdForMetadata.update(Ints.toByteArray(classVersion));
        mdForMetadata.update(Ints.toByteArray(digestType));
        mdForMetadata.update(Ints.toByteArray(DIGEST_ALGORITHM.getSize()));
        mdForMetadata.update(hash);

        return hash;
    }

    private RecordStreamObjectBytes readRecordStreamObjectBytes(DataInputStream dis,
            String filename) throws IOException {
        long classID = dis.readLong();
        Utility.validate(RECORD_STREAM_OBJECT_CLASS_ID, classID, "record stream object class ID");
        dis.readInt(); // class version
        byte[] recordBytes = Utility
                .readLengthAndBytes(dis, 1, Constants.MAX_RECORD_LENGTH, false, filename, "record bytes");
        byte[] transactionBytes = Utility
                .readLengthAndBytes(dis, 1, Constants.MAX_TRANSACTION_LENGTH, false, filename, "transaction bytes");

        return new RecordStreamObjectBytes(recordBytes, transactionBytes);
    }

    private RecordItem recordItemFromObjectBytes(RecordStreamObjectBytes objectBytes) {
        return new RecordItem(objectBytes.getTransactionBytes(), objectBytes.getRecordBytes());
    }

    @Value
    private static class RecordStreamObjectBytes {
        byte[] recordBytes;
        byte[] transactionBytes;
    }
}
