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

import com.google.common.primitives.Longs;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Consumer;
import javax.inject.Named;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FilenameUtils;

import com.hedera.mirror.importer.domain.DigestAlgorithm;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;
import com.hedera.mirror.importer.exception.StreamFileReaderException;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.reader.HashObject;
import com.hedera.mirror.importer.reader.ValidatedDataInputStream;

@Named
public class RecordFileReaderImplV5 implements RecordFileReader {

    private static final DigestAlgorithm DIGEST_ALGORITHM = DigestAlgorithm.SHA384;
    private static final int VERSION = 5;

    @Override
    public RecordFile read(StreamFileData streamFileData, Consumer<RecordItem> itemConsumer) {
        MessageDigest messageDigestFile = createMessageDigest(DIGEST_ALGORITHM);
        MessageDigest messageDigestMetadata = createMessageDigest(DIGEST_ALGORITHM);
        String filename = FilenameUtils.getName(streamFileData.getFilename());

        try (DigestInputStream digestInputStreamMetadata = new DigestInputStream(
                new BufferedInputStream(new DigestInputStream(streamFileData.getInputStream(), messageDigestFile)),
                messageDigestMetadata);
             ValidatedDataInputStream dis = new ValidatedDataInputStream(digestInputStreamMetadata, filename)) {
            RecordFile recordFile = new RecordFile();

            recordFile.setName(filename);
            recordFile.setDigestAlgorithm(DIGEST_ALGORITHM);

            readHeader(dis, recordFile);
            readBody(dis, itemConsumer, digestInputStreamMetadata, recordFile);

            recordFile.setFileHash(Hex.encodeHexString(messageDigestFile.digest()));
            recordFile.setMetadataHash(Hex.encodeHexString(messageDigestMetadata.digest()));

            return recordFile;
        } catch (IOException e) {
            throw new StreamFileReaderException("Error reading record file " + filename, e);
        }
    }

    private void readHeader(ValidatedDataInputStream dis, RecordFile recordFile) throws IOException {
        dis.readInt(VERSION, "record file version");
        recordFile.setHapiVersionMajor(dis.readInt());
        recordFile.setHapiVersionMinor(dis.readInt());
        recordFile.setHapiVersionPatch(dis.readInt());
        recordFile.setVersion(VERSION);
    }

    private void readBody(ValidatedDataInputStream dis, Consumer<RecordItem> itemConsumer,
            DigestInputStream digestInputStreamMetadata, RecordFile recordFile) throws IOException {
        String filename = recordFile.getName();

        dis.readInt(); // object stream version

        // start object running hash
        HashObject startHashObject = HashObject.read(dis, DIGEST_ALGORITHM);
        digestInputStreamMetadata.on(false);
        long hashObjectClassId = startHashObject.getClassId();

        long count = 0;
        long consensusStart = 0;
        RecordStreamObject lastRecordStreamObject = null;

        // read record stream objects
        while (!isHashObject(dis, hashObjectClassId)) {
            RecordStreamObject recordStreamObject = readRecordStreamObject(dis);

            if (itemConsumer != null) {
                itemConsumer.accept(recordStreamObject.getRecordItem());
            }

            if (count == 0) {
                consensusStart = recordStreamObject.getRecordItem().getConsensusTimestamp();
            }

            lastRecordStreamObject = recordStreamObject;
            count++;
        }

        if (count == 0) {
            throw new InvalidStreamFileException("No record stream objects in record file " + filename);
        }
        long consensusEnd = lastRecordStreamObject.getRecordItem().getConsensusTimestamp();

        // end object running hash
        digestInputStreamMetadata.on(true);
        HashObject endHashObject = HashObject.read(dis, DIGEST_ALGORITHM);

        if (dis.available() != 0) {
            throw new InvalidStreamFileException("Extra data discovered in record file " + filename);
        }

        recordFile.setCount(count);
        recordFile.setConsensusEnd(consensusEnd);
        recordFile.setConsensusStart(consensusStart);
        recordFile.setEndRunningHash(Hex.encodeHexString(endHashObject.getHash()));
        recordFile.setPreviousHash(Hex.encodeHexString(startHashObject.getHash()));
    }

    private MessageDigest createMessageDigest(DigestAlgorithm digestAlgorithm) {
        try {
            return MessageDigest.getInstance(digestAlgorithm.getName());
        } catch (NoSuchAlgorithmException e) {
            throw new StreamFileReaderException(e);
        }
    }

    private boolean isHashObject(DataInputStream dis, long hashObjectClassId) throws IOException {
        dis.mark(Longs.BYTES);
        long classId = dis.readLong();
        dis.reset();

        return classId == hashObjectClassId;
    }

    private RecordStreamObject readRecordStreamObject(ValidatedDataInputStream dis) throws IOException {
        long classId = dis.readLong();
        int classVersion = dis.readInt();
        byte[] recordBytes = dis.readLengthAndBytes(1, MAX_RECORD_LENGTH, false, "record bytes");
        byte[] transactionBytes = dis.readLengthAndBytes(1, MAX_TRANSACTION_LENGTH, false, "transaction bytes");

        return new RecordStreamObject(classId, classVersion, recordBytes, transactionBytes);
    }

    @Getter
    @RequiredArgsConstructor
    private static class RecordStreamObject {

        private final long classId;
        private final int classVersion;
        private final byte[] recordBytes;
        private final byte[] transactionBytes;
        private RecordItem recordItem;

        public RecordItem getRecordItem() {
            if (recordBytes == null || transactionBytes == null) {
                return null;
            }

            if (recordItem == null) {
                recordItem = new RecordItem(transactionBytes, recordBytes);
            }

            return recordItem;
        }
    }
}
