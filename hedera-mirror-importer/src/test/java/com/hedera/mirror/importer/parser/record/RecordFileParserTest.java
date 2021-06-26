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

import static com.hedera.mirror.importer.domain.StreamFilename.FileType.DATA;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import reactor.core.publisher.Flux;

import com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor;
import com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor.DateRangeFilter;
import com.hedera.mirror.importer.domain.DigestAlgorithm;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.StreamFile;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.exception.ParserSQLException;
import com.hedera.mirror.importer.parser.AbstractStreamFileParserTest;
import com.hedera.mirror.importer.parser.domain.RecordItem;

class RecordFileParserTest extends AbstractStreamFileParserTest<RecordFileParser> {

    @Mock
    private RecordItemListener recordItemListener;

    @Mock(lenient = true)
    private RecordStreamFileListener recordStreamFileListener;

    @Mock(lenient = true)
    private MirrorDateRangePropertiesProcessor mirrorDateRangePropertiesProcessor;

    private long count = 0;

    private RecordItem recordItem;

    @Override
    protected void assertParsed(StreamFile streamFile, boolean parsed, boolean dbError) {
        RecordFile recordFile = (RecordFile) streamFile;

        if (parsed) {
            verify(recordItemListener).onItem(recordItem);
            verify(recordStreamFileListener).onEnd(recordFile);
            verify(recordStreamFileListener, never()).onError();
        } else {
            if (dbError) {
                verify(recordStreamFileListener, never()).onEnd(recordFile);
                verify(recordStreamFileListener).onError();
            } else {
                verify(recordStreamFileListener, never()).onStart();
            }
        }
    }

    @Override
    protected RecordFileParser getParser() {
        RecordParserProperties parserProperties = new RecordParserProperties();
        when(mirrorDateRangePropertiesProcessor.getDateRangeFilter(parserProperties.getStreamType()))
                .thenReturn(DateRangeFilter.all());
        return new RecordFileParser(new SimpleMeterRegistry(), parserProperties, streamFileRepository,
                recordItemListener, recordStreamFileListener, mirrorDateRangePropertiesProcessor);
    }

    @Override
    protected StreamFile getStreamFile() {
        long id = ++count;
        Instant instant = Instant.ofEpochSecond(0L, id);
        String filename = StreamFilename.getFilename(parserProperties.getStreamType(), DATA, instant);
        recordItem = recordItem(id);

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
        recordFile.setItems(Flux.just(recordItem));

        return recordFile;
    }

    @Override
    protected void mockDbFailure() {
        doThrow(ParserSQLException.class).when(recordItemListener).onItem(any());
    }

    @Test
    void allFiltered() {
        RecordFile recordFile = (RecordFile) getStreamFile();
        when(mirrorDateRangePropertiesProcessor.getDateRangeFilter(parserProperties.getStreamType()))
                .thenReturn(DateRangeFilter.empty());
        parser.parse(recordFile);
        verifyNoInteractions(recordItemListener);
        verify(recordStreamFileListener).onEnd(recordFile);
        assertPostParseStreamFile(recordFile, true);
    }

    @ParameterizedTest(name = "endDate with offset {0}ns")
    @CsvSource({"-1", "0", "1"})
    void endDate(long offset) {
        // given
        RecordFile recordFile = (RecordFile) getStreamFile();
        RecordItem firstItem = recordFile.getItems().blockFirst();
        long end = recordFile.getConsensusStart() + offset;
        DateRangeFilter filter = new DateRangeFilter(Instant.EPOCH, Instant.ofEpochSecond(0, end));
        doReturn(filter).when(mirrorDateRangePropertiesProcessor).getDateRangeFilter(parserProperties.getStreamType());

        // when
        parser.parse(recordFile);

        // then
        verify(recordStreamFileListener).onStart();
        if (offset >= 0) {
            verify(recordItemListener).onItem(firstItem);
        }
        verify(recordStreamFileListener).onEnd(recordFile);
        assertPostParseStreamFile(recordFile, true);
    }

    @ParameterizedTest(name = "startDate with offset {0}ns")
    @CsvSource({"-1", "0", "1"})
    void startDate(long offset) {
        // given
        RecordFile recordFile = (RecordFile) getStreamFile();
        RecordItem firstItem = recordFile.getItems().blockFirst();
        long start = recordFile.getConsensusStart() + offset;
        DateRangeFilter filter = new DateRangeFilter(Instant.ofEpochSecond(0, start), null);
        doReturn(filter).when(mirrorDateRangePropertiesProcessor).getDateRangeFilter(parserProperties.getStreamType());

        // when
        parser.parse(recordFile);

        // then
        verify(recordStreamFileListener).onStart();
        if (offset < 0) {
            verify(recordItemListener).onItem(firstItem);
        }
        verify(recordStreamFileListener).onEnd(recordFile);
        assertPostParseStreamFile(recordFile, true);
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
