package com.hedera.mirror.importer.parser.record;

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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.EthereumTransactionBody;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import reactor.core.publisher.Flux;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.StreamFile;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor;
import com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor.DateRangeFilter;
import com.hedera.mirror.importer.exception.ParserSQLException;
import com.hedera.mirror.importer.parser.AbstractStreamFileParserTest;
import com.hedera.mirror.importer.parser.domain.RecordItemBuilder;

class RecordFileParserTest extends AbstractStreamFileParserTest<RecordFileParser> {

    @Mock
    private RecordItemListener recordItemListener;

    @Mock(lenient = true)
    private RecordStreamFileListener recordStreamFileListener;

    @Mock(lenient = true)
    private MirrorDateRangePropertiesProcessor mirrorDateRangePropertiesProcessor;

    private DomainBuilder domainBuilder = new DomainBuilder();

    private RecordItemBuilder recordItemBuilder = new RecordItemBuilder();

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
        recordItem = cryptoTransferRecordItem(id);
        return getStreamFile(Flux.just(recordItem), id);
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

    @Test
    void totalGasUsedMustBeCorrect() {
        when(mirrorDateRangePropertiesProcessor.getDateRangeFilter(parserProperties.getStreamType()))
                .thenReturn(DateRangeFilter.all());

        long timestamp = ++count;
        ContractFunctionResult contractFunctionResult1 = contractFunctionResult(
                10000000000L, new byte[] { 0, 6, 4, 0, 5, 7, 2 });
        RecordItem recordItem1 = contractCreate(contractFunctionResult1, timestamp, 0);

        ContractFunctionResult contractFunctionResult2 = contractFunctionResult(
                100000000000L, new byte[] { 3, 5, 1, 7, 4, 4, 0 });
        RecordItem recordItem2 = contractCall(contractFunctionResult2, timestamp, 0);

        ContractFunctionResult contractFunctionResult3 = contractFunctionResult(
                1000000000000L, new byte[] { 0, 1, 1, 2, 2, 6, 0 });
        RecordItem recordItem3 = ethereumTransaction(contractFunctionResult3, timestamp, 0);

        ContractFunctionResult contractFunctionResult4 = contractFunctionResult(
                1000000000000L, new byte[] { 0, 1, 1, 2, 2, 6, 0 });
        RecordItem recordItem4 = ethereumTransaction(contractFunctionResult4, timestamp, 1);

        RecordFile recordFile = getStreamFile(Flux.just(recordItem1, recordItem2, recordItem3, recordItem4), timestamp);

        parser.parse(recordFile);

        byte[] expectedLogBloom = new byte[] {
                3, 7, 5, 7, 7, 7, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        };
        assertAll(
                () -> assertEquals(10000000000L + 100000000000L + 1000000000000L, recordFile.getGasUsed()),
                () -> assertArrayEquals(expectedLogBloom, recordFile.getLogsBloom()),
                () -> verify(recordStreamFileListener, times(1)).onStart(),
                () -> verify(recordStreamFileListener, times(1)).onEnd(recordFile),
                () -> verify(recordItemListener, times(1)).onItem(recordItem1),
                () -> verify(recordItemListener, times(1)).onItem(recordItem2),
                () -> verify(recordItemListener, times(1)).onItem(recordItem3)
        );
    }

    @ParameterizedTest(name = "startDate with offset {0}ns")
    @CsvSource({ "-1", "0", "1" })
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

    private RecordItem contractCall(ContractFunctionResult contractFunctionResult, long timestamp,
            int transactionIdNonce) {
        return recordItemBuilder
                .contractCall()
                .record(builder -> builder.setContractCallResult(contractFunctionResult)
                        .setConsensusTimestamp(Timestamp.newBuilder().setNanos((int) timestamp))
                        .setTransactionID(TransactionID.newBuilder().setNonce(transactionIdNonce).build())
                )
                .transactionBody(builder -> builder.mergeFrom(ContractCallTransactionBody.newBuilder().build()))
                .build();
    }

    private RecordItem contractCreate(ContractFunctionResult contractFunctionResult, long timestamp,
            int transactionIdNonce) {
        return recordItemBuilder
                .contractCreate()
                .record(builder -> builder.setContractCreateResult(contractFunctionResult)
                        .setConsensusTimestamp(Timestamp.newBuilder().setNanos((int) timestamp))
                        .setTransactionID(TransactionID.newBuilder().setNonce(transactionIdNonce).build())
                )
                .transactionBody(builder -> builder.mergeFrom(ContractCreateTransactionBody.newBuilder().build()))
                .build();
    }

    private ContractFunctionResult contractFunctionResult(long gasUsed, byte[] logBloom) {
        return ContractFunctionResult.newBuilder()
                .setGasUsed(gasUsed)
                .setBloom(ByteString.copyFrom(logBloom))
                .build();
    }

    private RecordItem cryptoTransferRecordItem(long timestamp) {
        CryptoTransferTransactionBody cryptoTransfer = CryptoTransferTransactionBody.newBuilder().build();
        TransactionBody transactionBody = TransactionBody.newBuilder().setCryptoTransfer(cryptoTransfer).build();
        TransactionRecord transactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(Timestamp.newBuilder().setNanos((int) timestamp))
                .build();
        return RecordItem.builder()
                .record(transactionRecord)
                .transactionBody(transactionBody)
                .build();
    }

    private RecordItem ethereumTransaction(ContractFunctionResult contractFunctionResult, long timestamp,
            int transactionIdNonce) {
        return recordItemBuilder
                .ethereumTransaction(true)
                .record(builder -> builder.setContractCallResult(contractFunctionResult)
                        .setConsensusTimestamp(Timestamp.newBuilder().setNanos((int) timestamp))
                        .setTransactionID(TransactionID.newBuilder().setNonce(transactionIdNonce).build())
                )
                .transactionBody(builder -> builder.mergeFrom(EthereumTransactionBody.newBuilder().build()))
                .build();
    }

    private RecordFile getStreamFile(final Flux<RecordItem> items, final long timestamp) {
        return domainBuilder
                .recordFile()
                .customize(recordFileBuilder -> recordFileBuilder.bytes(new byte[] { 0, 1, 2 })
                        .consensusEnd(timestamp)
                        .consensusStart(timestamp)
                        .items(items)
                )
                .get();
    }
}
