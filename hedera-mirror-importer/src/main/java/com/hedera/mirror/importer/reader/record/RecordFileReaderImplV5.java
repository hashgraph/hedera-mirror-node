package com.hedera.mirror.importer.reader.record;

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

import com.google.common.primitives.Longs;
import java.io.DataInputStream;
import java.io.IOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.function.Consumer;
import javax.inject.Named;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.codec.binary.Hex;

import com.hedera.mirror.importer.domain.DigestAlgorithm;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;
import com.hedera.mirror.importer.exception.StreamFileReaderException;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.reader.AbstractStreamObject;
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
        String filename = streamFileData.getFilename();

        // the first DigestInputStream is for file hash and the second is for metadata hash. Any BufferedInputStream
        // should not wrap, directly or indirectly, the second DigestInputStream. The BufferedInputStream after the
        // first DigestInputStream is needed to avoid digesting some class ID fields twice.
        try (DigestInputStream digestInputStream = new DigestInputStream(new DigestInputStream(streamFileData
                .getInputStream(), messageDigestFile),
                messageDigestMetadata);
             ValidatedDataInputStream vdis = new ValidatedDataInputStream(digestInputStream, filename)) {
            RecordFile recordFile = new RecordFile();
            recordFile.setBytes(streamFileData.getBytes());
            recordFile.setDigestAlgorithm(DIGEST_ALGORITHM);
            recordFile.setLoadStart(Instant.now().getEpochSecond());
            recordFile.setName(filename);

            readHeader(vdis, recordFile);
            readBody(vdis, itemConsumer, digestInputStream, recordFile);

            recordFile.setFileHash(Hex.encodeHexString(messageDigestFile.digest()));
            recordFile.setMetadataHash(Hex.encodeHexString(messageDigestMetadata.digest()));

            return recordFile;
        } catch (IOException e) {
            throw new StreamFileReaderException("Error reading record file " + filename, e);
        }
    }

    private void readHeader(ValidatedDataInputStream vdis, RecordFile recordFile) throws IOException {
        vdis.readInt(VERSION, "record file version");
        recordFile.setHapiVersionMajor(vdis.readInt());
        recordFile.setHapiVersionMinor(vdis.readInt());
        recordFile.setHapiVersionPatch(vdis.readInt());
        recordFile.setVersion(VERSION);
    }

    private void readBody(ValidatedDataInputStream vdis, Consumer<RecordItem> itemConsumer,
                          DigestInputStream metadataDigestInputStream, RecordFile recordFile) throws IOException {
        String filename = recordFile.getName();

        vdis.readInt(); // object stream version

        // start object running hash
        HashObject startHashObject = new HashObject(vdis, DIGEST_ALGORITHM);
        metadataDigestInputStream.on(false); // metadata hash is not calculated on record stream objects
        long hashObjectClassId = startHashObject.getClassId();

        long count = 0;
        long consensusStart = 0;
        RecordStreamObject lastRecordStreamObject = null;

        // read record stream objects
        while (!isHashObject(vdis, hashObjectClassId)) {
            RecordStreamObject recordStreamObject = new RecordStreamObject(vdis);

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

        // end object running hash, metadata hash is calculated on it
        metadataDigestInputStream.on(true);
        HashObject endHashObject = new HashObject(vdis, DIGEST_ALGORITHM);

        if (vdis.available() != 0) {
            throw new InvalidStreamFileException("Extra data discovered in record file " + filename);
        }

        recordFile.setCount(count);
        recordFile.setConsensusEnd(consensusEnd);
        recordFile.setConsensusStart(consensusStart);
        recordFile.setHash(Hex.encodeHexString(endHashObject.getHash()));
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

    @EqualsAndHashCode(callSuper = true)
    @Getter
    private static class RecordStreamObject extends AbstractStreamObject {

        private static final int MAX_RECORD_LENGTH = 64 * 1024;

        private final byte[] recordBytes;
        private final byte[] transactionBytes;
        private RecordItem recordItem;

        RecordStreamObject(ValidatedDataInputStream vdis) {
            super(vdis);

            try {
                recordBytes = vdis.readLengthAndBytes(1, MAX_RECORD_LENGTH, false, "record bytes");
                transactionBytes = vdis.readLengthAndBytes(1, MAX_TRANSACTION_LENGTH, false, "transaction bytes");
            } catch (IOException e) {
                throw new InvalidStreamFileException(e);
            }
        }

        RecordItem getRecordItem() {
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
