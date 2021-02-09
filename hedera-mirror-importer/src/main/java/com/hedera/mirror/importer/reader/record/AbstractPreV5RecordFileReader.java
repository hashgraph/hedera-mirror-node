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

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.function.Consumer;
import lombok.Getter;
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

        try (RecordFileDigest digest = getRecordFileDigest(streamFileData.getInputStream());
             ValidatedDataInputStream vdis = new ValidatedDataInputStream(digest.getDigestInputStream(), filename)) {
            RecordFile recordFile = new RecordFile();
            recordFile.setBytes(streamFileData.getBytes());
            recordFile.setLoadStart(Instant.now().getEpochSecond());
            recordFile.setName(filename);
            recordFile.setDigestAlgorithm(DIGEST_ALGORITHM);

            readHeader(vdis, recordFile);
            readBody(vdis, digest, itemConsumer, recordFile);

            return recordFile;
        } catch (ImporterException e) {
            throw e;
        } catch (Exception e) {
            throw new StreamFileReaderException("Error reading record file " + filename, e);
        }
    }

    protected abstract RecordFileDigest getRecordFileDigest(InputStream is);

    /**
     * Reads the record file header, updates the message digest with data from the header, and sets corresponding {@link
     * RecordFile} fields. {@code vdis} should point at the beginning of the stream. The header should contain file
     * version, HAPI version, and the previous file hash.
     *
     * @param vdis       the {@link ValidatedDataInputStream} of the record file
     * @param recordFile the {@link RecordFile} object
     * @throws IOException
     */
    private void readHeader(ValidatedDataInputStream vdis, RecordFile recordFile) throws IOException {
        int version = vdis.readInt(readerVersion, "record file version");
        vdis.readInt(); // HAPI version, not used
        vdis.readByte(PREV_HASH_MARKER, "previous hash marker");
        byte[] prevHash = vdis.readNBytes(DIGEST_ALGORITHM.getSize(), "previous hash size");

        recordFile.setVersion(version);
        recordFile.setPreviousHash(Hex.encodeHexString(prevHash));
    }

    /**
     * Reads the record file body, updates the message digest with data from the body, and sets corresponding {@link
     * RecordFile} fields. {@code vdis} should point at the beginning of the body. The body should contain a variable
     * number of transaction and record pairs ordered by consensus timestamp. The body may also contain metadata to mark
     * the boundary of the pairs.
     *
     * @param vdis         the {@link ValidatedDataInputStream} of the record file
     * @param digest       the {@link RecordFileDigest} to update the digest with
     * @param itemConsumer the {@link Consumer} to process individual {@link RecordItem}s
     * @param recordFile   the {@link RecordFile} object
     * @throws IOException
     */
    private void readBody(ValidatedDataInputStream vdis, RecordFileDigest digest, Consumer<RecordItem> itemConsumer,
                          RecordFile recordFile) throws IOException {
        long count = 0;
        long consensusStart = 0;
        long consensusEnd = 0;
        boolean hasItemConsumer = itemConsumer != null;
        digest.startBody();

        while (vdis.available() != 0) {
            vdis.readByte(RECORD_MARKER, "record marker");
            byte[] transactionBytes = vdis.readLengthAndBytes(1, MAX_TRANSACTION_LENGTH, false, "transaction bytes");
            byte[] recordBytes = vdis.readLengthAndBytes(1, MAX_TRANSACTION_LENGTH, false, "record bytes");

            boolean isFirstTransaction = count == 0;
            boolean isLastTransaction = vdis.available() == 0;

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

        String fileHash = Hex.encodeHexString(digest.digest());

        recordFile.setConsensusStart(consensusStart);
        recordFile.setConsensusEnd(consensusEnd);
        recordFile.setCount(count);
        recordFile.setFileHash(fileHash);
        recordFile.setHash(fileHash);
    }

    protected static class RecordFileDigest implements AutoCloseable {

        @Getter
        private final DigestInputStream digestInputStream;

        private final MessageDigest messageDigestFile;

        private final MessageDigest messageDigestBody;

        public RecordFileDigest(InputStream is, boolean simple) {
            try {
                messageDigestFile = MessageDigest.getInstance(DIGEST_ALGORITHM.getName());
                digestInputStream = new DigestInputStream(is, messageDigestFile);

                if (simple) {
                    messageDigestBody = null;
                } else {
                    // calculate the hash of the body separately, and the file hash is calculated as
                    // h(header | h(body))
                    messageDigestBody = MessageDigest.getInstance(DIGEST_ALGORITHM.getName());
                }
            } catch (NoSuchAlgorithmException e) {
                throw new StreamFileReaderException("Unable to instantiate RecordFileDigest", e);
            }
        }

        public byte[] digest() {
            if (messageDigestBody != null) {
                messageDigestFile.update(messageDigestBody.digest());
            }

            return messageDigestFile.digest();
        }

        public void startBody() {
            if (messageDigestBody != null) {
                digestInputStream.setMessageDigest(messageDigestBody);
            }
        }

        @Override
        public void close() throws IOException {
            digestInputStream.close();
        }
    }
}
