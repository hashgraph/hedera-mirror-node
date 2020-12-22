package com.hedera.mirror.importer.reader.record;

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

import com.google.common.primitives.Ints;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.function.Consumer;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.exception.InvalidRecordFileException;
import com.hedera.mirror.importer.exception.RecordFileReaderException;
import com.hedera.mirror.importer.parser.domain.RecordItem;

@RequiredArgsConstructor
public abstract class AbstractRecordFileReader implements RecordFileReader {

    protected static final String HASH_ALGORITHM = "SHA-384";
    protected static final int HASH_SIZE = 48; // 48-byte SHA-384 hash
    protected static final byte RECORD_FILE_MARKER_PREV_HASH = 1;
    protected static final byte RECORD_FILE_MARKER_RECORD = 2;

    protected final Logger log = LogManager.getLogger(getClass());
    private final int readerVersion;

    @Override
    public RecordFile read(@NonNull StreamFileData streamFileData, Consumer<RecordItem> itemConsumer) {
        try (DataInputStream dis = new DataInputStream(streamFileData.getBufferedInputStream())) {
            RecordFile recordFile = new RecordFile();
            RecordFileDigest digest = getRecordFileDigest();

            recordFile.setName(FilenameUtils.getName(streamFileData.getFilename()));
            readHeader(dis, digest, recordFile);
            readBody(dis, digest, itemConsumer, recordFile);
            recordFile.setFileHash(Hex.encodeHexString(digest.digest()));

            return recordFile;
        } catch (ImporterException e) {
            throw e;
        }  catch (Exception e) {
            throw new RecordFileReaderException("Error reading record file " + streamFileData.getFilename(), e);
        }
    }

    protected abstract RecordFileDigest getRecordFileDigest();

    private void readHeader(DataInputStream dis, RecordFileDigest digest, RecordFile recordFile) throws IOException {
        // record file version
        int version = dis.readInt();
        checkField(version, readerVersion, "record file version", recordFile.getName());

        int hapiVersion = dis.readInt();

        // previous record file hash
        byte marker = dis.readByte();
        checkField(marker, RECORD_FILE_MARKER_PREV_HASH, "RECORD_FILE_MARKER_PREV_HASH", recordFile.getName());
        byte[] prevHash = dis.readNBytes(HASH_SIZE);
        checkField(prevHash.length, HASH_SIZE, "previous hash size", recordFile.getName());

        digest.updateHeader(Ints.toByteArray(version));
        digest.updateHeader(Ints.toByteArray(hapiVersion));
        digest.updateHeader(marker);
        digest.updateHeader(prevHash);

        recordFile.setRecordFormatVersion(version);
        recordFile.setPreviousHash(Hex.encodeHexString(prevHash));
    }

    private void readBody(DataInputStream dis, RecordFileDigest digest, Consumer<RecordItem> itemConsumer,
            RecordFile recordFile) throws IOException {
        long count = 0;
        long consensusStart = 0;
        long consensusEnd = 0;

        while (dis.available() != 0) {
            byte marker = dis.readByte();
            checkField(marker, RECORD_FILE_MARKER_RECORD, "RECORD_FILE_MARKER_RECORD", recordFile.getName());

            byte[] transactionBytes = readLengthAndBytes(dis);
            byte[] recordBytes = readLengthAndBytes(dis);

            digest.updateBody(marker);
            digest.updateBody(Ints.toByteArray(transactionBytes.length));
            digest.updateBody(transactionBytes);
            digest.updateBody(Ints.toByteArray(recordBytes.length));
            digest.updateBody(recordBytes);

            boolean isFirstTransaction = count == 0;
            boolean isLastTransaction = dis.available() == 0;

            // We need the first and last transaction timestamps for metrics
            if (itemConsumer != null || isFirstTransaction || isLastTransaction) {
                RecordItem recordItem = new RecordItem(transactionBytes, recordBytes);

                if (itemConsumer != null) {
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

    private byte[] readLengthAndBytes(DataInputStream dis) throws IOException {
        int len = dis.readInt();
        byte[] bytes = new byte[len];
        dis.readFully(bytes);
        return bytes;
    }

    private <T> void checkField(T actual, T expected, String name, String filename) {
        if (actual != expected) {
            throw new InvalidRecordFileException(String.format("Expect %s (%s) got %s for record file %s",
                    name, expected, actual, filename));
        }
    }
}
