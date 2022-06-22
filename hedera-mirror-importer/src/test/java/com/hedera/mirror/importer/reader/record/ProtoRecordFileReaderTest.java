package com.hedera.mirror.importer.reader.record;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.io.ByteArrayOutputStream;
import java.util.function.Function;
import lombok.SneakyThrows;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;
import com.hedera.services.stream.proto.HashAlgorithm;
import com.hedera.services.stream.proto.HashObject;
import com.hedera.services.stream.proto.RecordStreamFile;
import com.hedera.services.stream.proto.RecordStreamItem;

class ProtoRecordFileReaderTest extends AbstractRecordFileReaderTest {

    private static final String FILENAME = "2022-06-21T09_15_38.325469003Z.rcd.gz";

    @Override
    protected RecordFileReader getRecordFileReader() {
        return new ProtoRecordFileReader();
    }

    @Override
    protected boolean filterFile(int version) {
        return version == 6;
    }

    @Test
    void testEmptyRecordStreamItems() {
        var bytes = gzip(ProtoRecordStreamFile.of(RecordStreamFile.Builder::clearRecordStreamItems));
        var reader = new ProtoRecordFileReader();
        var exception = assertThrows(InvalidStreamFileException.class,
                () -> reader.read(StreamFileData.from(FILENAME, bytes)));
        var expected = "No record stream objects in record file " + FILENAME;
        assertEquals(expected, exception.getMessage());
    }

    @Test
    void testInvalidHashAlgorithm() {
        var bytes = gzip(ProtoRecordStreamFile.of(b -> {
            b.getStartObjectRunningHashBuilder().setAlgorithm(HashAlgorithm.HASH_ALGORITHM_UNKNOWN);
            b.getEndObjectRunningHashBuilder().setAlgorithm(HashAlgorithm.HASH_ALGORITHM_UNKNOWN);
            return b;
        }));
        var reader = new ProtoRecordFileReader();
        var exception = assertThrows(InvalidStreamFileException.class,
                () -> reader.read(StreamFileData.from(FILENAME, bytes)));
        assertThat(exception.getMessage()).contains(FILENAME);
        assertThat(exception.getCause()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testMismatchRunningObjectHashAlgorithms() {
        var bytes = gzip(ProtoRecordStreamFile.of(b -> {
            b.getStartObjectRunningHashBuilder().setAlgorithm(HashAlgorithm.HASH_ALGORITHM_UNKNOWN);
            return b;
        }));
        var reader = new ProtoRecordFileReader();
        var exception = assertThrows(InvalidStreamFileException.class,
                () -> reader.read(StreamFileData.from(FILENAME, bytes)));
        var expected = String.format("File %s has mismatch start and end object running hash algorithms " +
                "[HASH_ALGORITHM_UNKNOWN, SHA_384]", FILENAME);
        assertEquals(expected, exception.getMessage());
    }

    @SneakyThrows
    private byte[] gzip(byte[] data) {
        try (var byteArrayOutputStream = new ByteArrayOutputStream();
             var compressorOutputStream = new GzipCompressorOutputStream(byteArrayOutputStream)) {
            compressorOutputStream.write(data);
            compressorOutputStream.finish();
            return byteArrayOutputStream.toByteArray();
        }
    }

    private static class ProtoRecordStreamFile {

        private static final int VERSION = 6;

        private static byte[] of(Function<RecordStreamFile.Builder, RecordStreamFile.Builder> customizer) {
            return Bytes.concat(Ints.toByteArray(VERSION),
                    customizer.apply(getDefaultRecordStreamFileBuilder()).build().toByteArray());
        }

        private static RecordStreamFile.Builder getDefaultRecordStreamFileBuilder() {
            var hashObject = HashObject.newBuilder()
                    .setAlgorithm(HashAlgorithm.SHA_384)
                    .setLength(48);
            var recordStreamItem = RecordStreamItem.newBuilder()
                    .setTransaction(Transaction.newBuilder()
                            .setSignedTransactionBytes(SignedTransaction.newBuilder()
                                    .setBodyBytes(TransactionBody.newBuilder()
                                            .setCryptoTransfer(CryptoTransferTransactionBody.newBuilder().build())
                                            .build()
                                            .toByteString())
                                    .build()
                                    .toByteString()))
                    .setRecord(TransactionRecord.newBuilder())
                    .build();
            return RecordStreamFile.newBuilder()
                    .setHapiProtoVersion(SemanticVersion.newBuilder().setMajor(27))
                    .setStartObjectRunningHash(hashObject
                            .setHash(DomainUtils.fromBytes(TestUtils.generateRandomByteArray(48))).build())
                    .addRecordStreamItems(recordStreamItem)
                    .setEndObjectRunningHash(hashObject
                            .setHash(DomainUtils.fromBytes(TestUtils.generateRandomByteArray(48))).build())
                    .setBlockNumber(100L);
        }
    }
}
