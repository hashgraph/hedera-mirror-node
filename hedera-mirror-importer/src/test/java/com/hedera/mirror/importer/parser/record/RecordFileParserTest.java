package com.hedera.mirror.importer.parser.record;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor;
import com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor.DateRangeFilter;
import com.hedera.mirror.importer.domain.DigestAlgorithm;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.exception.ParserSQLException;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.util.Utility;

@ExtendWith(MockitoExtension.class)
class RecordFileParserTest {

    @TempDir
    Path dataDir;

    @Mock
    private RecordItemListener recordItemListener;

    @Mock(lenient = true)
    private RecordStreamFileListener recordStreamFileListener;

    @Mock
    private MirrorDateRangePropertiesProcessor mirrorDateRangePropertiesProcessor;

    private RecordFileParser recordFileParser;
    private RecordParserProperties parserProperties;
    private long count = 0;

    @BeforeEach
    void before() {
        MirrorProperties mirrorProperties = new MirrorProperties();
        mirrorProperties.setDataPath(dataDir);
        parserProperties = new RecordParserProperties(mirrorProperties);

        recordFileParser = new RecordFileParser(parserProperties, new SimpleMeterRegistry(),
                recordItemListener, recordStreamFileListener, mirrorDateRangePropertiesProcessor);
    }

    @Test
    void parse() throws Exception {
        // given
        RecordFile recordFile = recordFile();

        // when
        recordFileParser.parse(recordFile);

        // then
        verify(recordItemListener).onItem(recordFile.getItems().get(0));
        verify(recordStreamFileListener).onEnd(recordFile);
        verify(recordStreamFileListener, never()).onError();
        assertFilesArchived();
        assertThat(recordFile.getBytes()).isNull();
    }

    @Test
    void disabled() throws Exception {
        // given
        parserProperties.setEnabled(false);
        parserProperties.setKeepFiles(true);
        RecordFile recordFile = recordFile();

        // when
        recordFileParser.parse(recordFile);

        // then
        verify(recordStreamFileListener, never()).onStart();
        assertFilesArchived();
    }

    @Test
    void persistBytes() throws Exception {
        // given
        parserProperties.setPersistBytes(true);
        RecordFile recordFile = recordFile();

        // when
        recordFileParser.parse(recordFile);

        // then
        verify(recordItemListener).onItem(recordFile.getItems().get(0));
        verify(recordStreamFileListener).onEnd(recordFile);
        verify(recordStreamFileListener, never()).onError();
        assertThat(recordFile.getBytes()).isNotNull();
    }

    @Test
    void keepFiles() throws Exception {
        // given
        parserProperties.setKeepFiles(true);
        RecordFile recordFile = recordFile();

        // when
        recordFileParser.parse(recordFile);

        // then
        verify(recordItemListener).onItem(recordFile.getItems().get(0));
        verify(recordStreamFileListener).onEnd(recordFile);
        verify(recordStreamFileListener, never()).onError();
        assertFilesArchived(recordFile.getName());
    }

    @Test
    void failureShouldRollback() throws Exception {
        // given
        RecordFile recordFile = recordFile();
        doThrow(ParserSQLException.class).when(recordItemListener).onItem(any());

        // when
        Assertions.assertThrows(ImporterException.class, () -> {
            recordFileParser.parse(recordFile);
        });

        // then
        verify(recordStreamFileListener, never()).onEnd(recordFile);
        verify(recordStreamFileListener).onError();
        assertFilesArchived();
    }

    @ParameterizedTest(name = "endDate with offset {0}ns")
    @CsvSource({"-1", "0", "1"})
    void endDate(long offset) {
        // given
        RecordFile recordFile = recordFile();
        long end = recordFile.getConsensusStart() + offset;
        DateRangeFilter filter = new DateRangeFilter(Instant.EPOCH, Instant.ofEpochSecond(0, end));
        doReturn(filter).when(mirrorDateRangePropertiesProcessor).getDateRangeFilter(parserProperties.getStreamType());

        // when
        recordFileParser.parse(recordFile);

        // then
        verify(recordStreamFileListener).onStart();
        if (offset >= 0) {
            verify(recordItemListener).onItem(recordFile.getItems().get(0));
        }
        verify(recordStreamFileListener).onEnd(recordFile);
    }

    @ParameterizedTest(name = "startDate with offset {0}ns")
    @CsvSource({"-1", "0", "1"})
    void startDate(long offset) {
        // given
        RecordFile recordFile = recordFile();
        long start = recordFile.getConsensusStart() + offset;
        DateRangeFilter filter = new DateRangeFilter(Instant.ofEpochSecond(0, start), null);
        doReturn(filter).when(mirrorDateRangePropertiesProcessor).getDateRangeFilter(parserProperties.getStreamType());

        // when
        recordFileParser.parse(recordFile);

        // then
        verify(recordStreamFileListener).onStart();
        if (offset < 0) {
            verify(recordItemListener).onItem(recordFile.getItems().get(0));
        }
        verify(recordStreamFileListener).onEnd(recordFile);
    }

    // Asserts that parsed directory contains exactly the files with given fileNames
    private void assertFilesArchived(String... fileNames) throws Exception {
        if (fileNames == null || fileNames.length == 0) {
            assertThat(parserProperties.getParsedPath()).doesNotExist();
            return;
        }
        assertThat(Files.walk(parserProperties.getParsedPath()))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSize(fileNames.length)
                .extracting(Path::getFileName)
                .extracting(Path::toString)
                .contains(fileNames);
    }

    private RecordFile recordFile() {
        long id = ++count;
        Instant instant = Instant.ofEpochSecond(0L, id);
        String filename = Utility.getStreamFilenameFromInstant(parserProperties.getStreamType(), instant);
        RecordFile recordFile = new RecordFile();
        recordFile.setBytes(new byte[] {0, 1, 2});
        recordFile.setConsensusEnd(id);
        recordFile.setConsensusStart(id);
        recordFile.setConsensusEnd(id);
        recordFile.setCount(id);
        recordFile.setDigestAlgorithm(DigestAlgorithm.SHA384);
        recordFile.setFileHash("fileHash" + id);
        recordFile.setHash("hash" + id);
        recordFile.setLoadEnd(id);
        recordFile.setLoadStart(id);
        recordFile.setName(filename);
        recordFile.setNodeAccountId(EntityId.of("0.0.3", EntityTypeEnum.ACCOUNT));
        recordFile.setPreviousHash("previousHash" + (id - 1));
        recordFile.setVersion(1);
        recordFile.getItems().add(recordItem(id));
        return recordFile;
    }

    private RecordItem recordItem(long timestamp) {
        CryptoTransferTransactionBody cryptoTransfer = CryptoTransferTransactionBody.newBuilder().build();
        TransactionBody transactionBody = TransactionBody.newBuilder().setCryptoTransfer(cryptoTransfer).build();
        SignedTransaction signedTransaction = SignedTransaction.newBuilder()
                .setBodyBytes(transactionBody.toByteString())
                .setSigMap(SignatureMap.newBuilder().build())
                .build();
        Transaction transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(signedTransaction.toByteString())
                .build();
        TransactionRecord transactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(Timestamp.newBuilder().setNanos((int) timestamp))
                .build();
        return new RecordItem(transaction.toByteArray(), transactionRecord.toByteArray());
    }
}
