/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.importer.parser.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.importer.config.DateRangeCalculator;
import com.hedera.mirror.importer.config.DateRangeCalculator.DateRangeFilter;
import com.hedera.mirror.importer.exception.HashMismatchException;
import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.parser.AbstractStreamFileParserTest;
import com.hedera.mirror.importer.parser.domain.RecordItemBuilder;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.StreamFileRepository;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Flux;

class RecordFileParserTest extends AbstractStreamFileParserTest<RecordFile, RecordFileParser> {

    private final DomainBuilder domainBuilder = new DomainBuilder();
    private final RecordItemBuilder recordItemBuilder = new RecordItemBuilder();

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private RecordFileRepository recordFileRepository;

    @Mock
    private RecordItemListener recordItemListener;

    @Mock(strictness = LENIENT)
    private RecordStreamFileListener recordStreamFileListener;

    @Mock(strictness = LENIENT)
    private DateRangeCalculator dateRangeCalculator;

    private long count = 0;

    private RecordItem recordItem;

    @Override
    protected void assertParsed(RecordFile recordFile, boolean parsed, boolean dbError) {
        super.assertParsed(recordFile, parsed, dbError);

        if (parsed) {
            verify(recordItemListener).onItem(recordItem);
            verify(recordStreamFileListener).onEnd(recordFile);
            verify(recordStreamFileListener, never()).onError();
            // Can't verify the event object since ApplicationEvent has a timestamp field for when the event happened
            verify(applicationEventPublisher)
                    .publishEvent(argThat(e -> e instanceof RecordFileParsedEvent recordFileParsedEvent
                            && recordFileParsedEvent.getConsensusEnd() == recordFile.getConsensusEnd()));
        } else {
            if (dbError) {
                verify(recordStreamFileListener, never()).onEnd(recordFile);
                verify(recordStreamFileListener).onError();
            } else {
                verify(recordStreamFileListener, never()).onStart();
            }

            verify(applicationEventPublisher, never()).publishEvent(any());
        }
    }

    @Override
    protected RecordFileParser getParser() {
        RecordParserProperties parserProperties = new RecordParserProperties();
        when(dateRangeCalculator.getFilter(parserProperties.getStreamType())).thenReturn(DateRangeFilter.all());
        return new RecordFileParser(
                applicationEventPublisher,
                new SimpleMeterRegistry(),
                parserProperties,
                recordFileRepository,
                recordItemListener,
                recordStreamFileListener,
                dateRangeCalculator);
    }

    @Override
    protected RecordFile getStreamFile() {
        long id = ++count * 100;
        recordItem = cryptoTransferRecordItem(id);
        return getStreamFile(Flux.just(recordItem), id);
    }

    @Override
    protected StreamFileRepository<RecordFile, ?> getStreamFileRepository() {
        return recordFileRepository;
    }

    @Override
    protected void mockDbFailure(ParserException e) {
        doThrow(e).when(recordItemListener).onItem(any());
    }

    @Test
    void allFiltered() {
        RecordFile recordFile = getStreamFile();
        when(dateRangeCalculator.getFilter(parserProperties.getStreamType())).thenReturn(DateRangeFilter.empty());
        parser.parse(recordFile);
        verifyNoInteractions(recordItemListener);
        verify(recordStreamFileListener).onEnd(recordFile);
    }

    @ParameterizedTest(name = "endDate with offset {0}ns")
    @CsvSource({"-1", "0", "1"})
    void endDate(long offset) {
        // given
        RecordFile recordFile = getStreamFile();
        RecordItem firstItem = recordFile.getItems().blockFirst();
        long end = recordFile.getConsensusStart() + offset;
        DateRangeFilter filter = new DateRangeFilter(Instant.EPOCH, Instant.ofEpochSecond(0, end));
        doReturn(filter).when(dateRangeCalculator).getFilter(parserProperties.getStreamType());

        // when
        parser.parse(recordFile);

        // then
        verify(recordStreamFileListener).onStart();
        if (offset >= 0) {
            verify(recordItemListener).onItem(firstItem);
        }
        verify(recordStreamFileListener).onEnd(recordFile);
    }

    @Test
    void totalGasUsedMustBeCorrect() {
        when(dateRangeCalculator.getFilter(parserProperties.getStreamType())).thenReturn(DateRangeFilter.all());

        long timestamp = ++count;
        var contractFunctionResult1 = contractFunctionResult(10000000000L, new byte[] {0, 6, 4, 0, 5, 7, 2});
        RecordItem recordItem1 = contractCreate(contractFunctionResult1, timestamp, 0);

        var contractFunctionResult2 = contractFunctionResult(100000000000L, new byte[] {3, 5, 1, 7, 4, 4, 0});
        RecordItem recordItem2 = contractCall(contractFunctionResult2, timestamp, 0);

        var contractFunctionResult3 = contractFunctionResult(1000000000000L, new byte[] {0, 1, 1, 2, 2, 6, 0});
        RecordItem recordItem3 = ethereumTransaction(contractFunctionResult3, timestamp, 0);

        var contractFunctionResult4 = contractFunctionResult(1000000000000L, new byte[] {0, 1, 1, 2, 2, 6, 0});
        RecordItem recordItem4 = ethereumTransaction(contractFunctionResult4, timestamp, 1);

        RecordItem recordItem5 = cryptoTransferRecordItem(1L);

        var items = Flux.just(recordItem1, recordItem2, recordItem3, recordItem4, recordItem5);
        var recordFile = spy(getStreamFile(items, timestamp));
        doNothing().when(recordFile).clear();

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
                () -> verify(recordItemListener, times(1)).onItem(recordItem3));
    }

    @ParameterizedTest(name = "startDate with offset {0}ns")
    @CsvSource({"-1", "0", "1"})
    void startDate(long offset) {
        // given
        RecordFile recordFile = getStreamFile();
        RecordItem firstItem = recordFile.getItems().blockFirst();
        long start = recordFile.getConsensusStart() + offset;
        DateRangeFilter filter = new DateRangeFilter(Instant.ofEpochSecond(0, start), null);
        doReturn(filter).when(dateRangeCalculator).getFilter(parserProperties.getStreamType());

        // when
        parser.parse(recordFile);

        // then
        verify(recordStreamFileListener).onStart();
        if (offset < 0) {
            verify(recordItemListener).onItem(firstItem);
        }
        verify(recordStreamFileListener).onEnd(recordFile);
    }

    @Test
    void blockNumberMigration() {
        // given
        int offset = 2;
        var streamFile1 = getStreamFile();
        var streamFile2 = getStreamFile();
        streamFile1.setIndex(streamFile2.getIndex() - offset);
        streamFile1.setVersion(5);
        streamFile2.setPreviousHash(streamFile1.getHash());

        // when
        parser.parse(streamFile1);
        parser.parse(streamFile2);

        // then
        assertParsed(streamFile1, true, false);
        assertParsed(streamFile2, true, false);
        verify(recordFileRepository).updateIndex(offset - 1);
    }

    @Test
    void hashMismatch(CapturedOutput output) {
        // given
        var streamFile1 = getStreamFile();
        var streamFile2 = getStreamFile();
        streamFile1.setIndex(streamFile2.getIndex() - 2);
        streamFile1.setVersion(5);
        when(recordFileRepository.findLatest()).thenReturn(Optional.of(streamFile1));

        // when
        assertThatThrownBy(() -> parser.parse(streamFile2)).isInstanceOf(HashMismatchException.class);
        assertThat(output.getOut()).contains("Error parsing file").contains("hash mismatch for file");
    }

    @Test
    void noExistingRecordFile() {
        // given
        int offset = 2;
        var streamFile = getStreamFile();
        streamFile.setIndex(3L);
        streamFile.setVersion(5);
        when(recordFileRepository.findLatest()).thenReturn(Optional.empty());

        // when
        parser.parse(streamFile);

        // then
        assertParsed(streamFile, true, false);
    }

    @Test
    void blockNumberMigrationOnStartup() {
        // given
        int offset = 2;
        var streamFile1 = getStreamFile();
        var streamFile2 = getStreamFile();
        streamFile1.setIndex(streamFile2.getIndex() - offset);
        streamFile1.setVersion(5);
        streamFile2.setPreviousHash(streamFile1.getHash());
        when(recordFileRepository.findLatest()).thenReturn(Optional.of(streamFile1));

        // when
        parser.parse(streamFile2);

        // then
        assertParsed(streamFile2, true, false);
        verify(recordFileRepository).updateIndex(offset - 1);
    }

    @Test
    void blockNumberMigrationNotV6() {
        // given
        var streamFile1 = getStreamFile();
        var streamFile2 = getStreamFile();
        streamFile1.setIndex(streamFile2.getIndex() - 2);
        streamFile1.setVersion(5);
        streamFile2.setVersion(5);
        streamFile2.setPreviousHash(streamFile1.getHash());

        // when
        parser.parse(streamFile1);
        parser.parse(streamFile2);

        // then
        assertParsed(streamFile1, true, false);
        assertParsed(streamFile2, true, false);
        verify(recordFileRepository, never()).updateIndex(anyLong());
    }

    @Test
    void blockNumberMigrationUnnecessary() {
        // given
        var streamFile1 = getStreamFile();
        var streamFile2 = getStreamFile();
        streamFile1.setIndex(streamFile2.getIndex() - 1);
        streamFile2.setPreviousHash(streamFile1.getHash());

        // when
        parser.parse(streamFile1);
        parser.parse(streamFile2);

        // then
        assertParsed(streamFile1, true, false);
        assertParsed(streamFile2, true, false);
        verify(recordFileRepository, never()).updateIndex(anyLong());
    }

    private RecordItem contractCall(
            ContractFunctionResult contractFunctionResult, long timestamp, int transactionIdNonce) {
        return recordItemBuilder
                .contractCall()
                .record(builder -> builder.setContractCallResult(contractFunctionResult)
                        .setConsensusTimestamp(Timestamp.newBuilder().setNanos((int) timestamp))
                        .setTransactionID(TransactionID.newBuilder()
                                .setNonce(transactionIdNonce)
                                .build()))
                .build();
    }

    private RecordItem contractCreate(
            ContractFunctionResult contractFunctionResult, long timestamp, int transactionIdNonce) {
        return recordItemBuilder
                .contractCreate()
                .record(builder -> builder.setContractCreateResult(contractFunctionResult)
                        .setConsensusTimestamp(Timestamp.newBuilder().setNanos((int) timestamp))
                        .setTransactionID(TransactionID.newBuilder()
                                .setNonce(transactionIdNonce)
                                .build()))
                .build();
    }

    private ContractFunctionResult contractFunctionResult(long gasUsed, byte[] logBloom) {
        return ContractFunctionResult.newBuilder()
                .setGasUsed(gasUsed)
                .setBloom(ByteString.copyFrom(logBloom))
                .build();
    }

    private RecordItem cryptoTransferRecordItem(long timestamp) {
        CryptoTransferTransactionBody cryptoTransfer =
                CryptoTransferTransactionBody.newBuilder().build();
        TransactionBody transactionBody =
                TransactionBody.newBuilder().setCryptoTransfer(cryptoTransfer).build();
        TransactionRecord transactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(Timestamp.newBuilder().setNanos((int) timestamp))
                .build();
        SignedTransaction signedTransaction = SignedTransaction.newBuilder()
                .setBodyBytes(transactionBody.toByteString())
                .setSigMap(SignatureMap.newBuilder().build())
                .build();
        Transaction transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(signedTransaction.toByteString())
                .build();
        return RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build();
    }

    private RecordItem ethereumTransaction(
            ContractFunctionResult contractFunctionResult, long timestamp, int transactionIdNonce) {
        return recordItemBuilder
                .ethereumTransaction(true)
                .record(builder -> builder.setContractCallResult(contractFunctionResult)
                        .setConsensusTimestamp(Timestamp.newBuilder().setNanos((int) timestamp))
                        .setTransactionID(TransactionID.newBuilder()
                                .setNonce(transactionIdNonce)
                                .build()))
                .build();
    }

    private RecordFile getStreamFile(final Flux<RecordItem> items, final long timestamp) {
        return domainBuilder
                .recordFile()
                .customize(recordFileBuilder -> recordFileBuilder
                        .bytes(new byte[] {0, 1, 2})
                        .consensusEnd(timestamp + 1)
                        .consensusStart(timestamp)
                        .gasUsed(0L)
                        .items(items))
                .get();
    }
}
