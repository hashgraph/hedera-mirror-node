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

import com.google.common.primitives.Ints;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.function.Consumer;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FilenameUtils;

import com.hedera.mirror.importer.domain.DigestAlgorithm;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.exception.StreamFileReaderException;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.reader.ValidatedDataInputStream;

@RequiredArgsConstructor
public abstract class AbstractPreV5RecordFileReader implements RecordFileReader {

    protected static final DigestAlgorithm DIGEST_ALGORITHM = DigestAlgorithm.SHA384;
    protected static final byte PREV_HASH_MARKER = 1;
    protected static final byte RECORD_MARKER = 2;

    private final int readerVersion;

    @Override
    public RecordFile read(@NonNull StreamFileData streamFileData, Consumer<RecordItem> itemConsumer) {
        String filename = FilenameUtils.getName(streamFileData.getFilename());

        try (ValidatedDataInputStream dis = new ValidatedDataInputStream(
                new BufferedInputStream(streamFileData.getInputStream()), filename)) {
            RecordFile recordFile = new RecordFile();
            RecordFileDigest digest = getRecordFileDigest();

            recordFile.setName(filename);
            recordFile.setDigestAlgorithm(DIGEST_ALGORITHM);
            readHeader(dis, digest, recordFile);
            readBody(dis, digest, itemConsumer, recordFile);
            recordFile.setFileHash(Hex.encodeHexString(digest.digest()));

            return recordFile;
        } catch (ImporterException e) {
            throw e;
        } catch (Exception e) {
            throw new StreamFileReaderException("Error reading record file " + filename, e);
        }
    }

    protected abstract RecordFileDigest getRecordFileDigest();

    /**
     * Reads the record file header, updates the message digest with data from the header, and sets corresponding
     * {@link RecordFile} fields. {@code dis} should point at the beginning of the stream. The header should contain
     * file version, HAPI version, and the previous file hash.
     *
     * @param dis the {@link ValidatedDataInputStream} of the record file
     * @param digest the {@link RecordFileDigest} to update the digest with
     * @param recordFile the {@link RecordFile} object
     * @throws IOException
     */
    private void readHeader(ValidatedDataInputStream dis, RecordFileDigest digest,
            RecordFile recordFile) throws IOException {
        int version = dis.readInt(readerVersion, "record file version");
        int hapiVersion = dis.readInt(); // not used
        byte marker = dis.readByte(PREV_HASH_MARKER, "previous hash marker");
        byte[] prevHash = dis.readNBytes(DIGEST_ALGORITHM.getSize(), "previous hash size");

        digest.updateHeader(Ints.toByteArray(version));
        digest.updateHeader(Ints.toByteArray(hapiVersion));
        digest.updateHeader(marker);
        digest.updateHeader(prevHash);

        recordFile.setVersion(version);
        recordFile.setPreviousHash(Hex.encodeHexString(prevHash));
    }

    /**
     * Reads the record file body, updates the message digest with data from the body, and sets corresponding
     * {@link RecordFile} fields. {@code dis} should point at the beginning of the body. The body should contain
     * a variable number of transaction and record pairs ordered by consensus timestamp. The body may also contain
     * metadata to mark the boundary of the pairs.
     *
     * @param dis the {@link ValidatedDataInputStream} of the record file
     * @param digest the {@link RecordFileDigest} to update the digest with
     * @param itemConsumer the {@link Consumer} to process individual {@link RecordItem}s
     * @param recordFile the {@link RecordFile} object
     * @throws IOException
     */
    private void readBody(ValidatedDataInputStream dis, RecordFileDigest digest, Consumer<RecordItem> itemConsumer,
            RecordFile recordFile) throws IOException {
        long count = 0;
        long consensusStart = 0;
        long consensusEnd = 0;
        boolean hasItemConsumer = itemConsumer != null;

        while (dis.available() != 0) {
            byte marker = dis.readByte(RECORD_MARKER, "record marker");
            byte[] transactionBytes = dis.readLengthAndBytes(1, MAX_TRANSACTION_LENGTH, false,"transaction bytes");
            byte[] recordBytes = dis.readLengthAndBytes(1, MAX_TRANSACTION_LENGTH, false,"record bytes");

            digest.updateBody(marker);
            digest.updateBody(Ints.toByteArray(transactionBytes.length));
            digest.updateBody(transactionBytes);
            digest.updateBody(Ints.toByteArray(recordBytes.length));
            digest.updateBody(recordBytes);

            boolean isFirstTransaction = count == 0;
            boolean isLastTransaction = dis.available() == 0;

            // We need the first and last transaction timestamps for metrics
            if (hasItemConsumer || isFirstTransaction || isLastTransaction) {
                RecordItem recordItem = new RecordItem(transactionBytes, recordBytes);

                if (hasItemConsumer) {
                    itemConsumer.accept(recordItem);
                }

                if (isFirstTransaction) {
                    consensusStart = recordItem.getConsensusTimestamp();
                }

                if (isLastTransaction) {
                    consensusEnd = recordItem.getConsensusTimestamp();
                }
            }

            count++;
        }

        recordFile.setConsensusStart(consensusStart);
        recordFile.setConsensusEnd(consensusEnd);
        recordFile.setCount(count);
    }

    protected interface RecordFileDigest {
        void updateHeader(byte input);
        void updateHeader(byte[] input);
        void updateBody(byte input);
        void updateBody(byte[] input);
        byte[] digest();
    }
}
