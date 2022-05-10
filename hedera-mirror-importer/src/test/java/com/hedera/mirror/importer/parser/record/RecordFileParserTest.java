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

import static com.hedera.mirror.importer.domain.StreamFilename.FileType.DATA;
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

import com.hedera.mirror.common.domain.DigestAlgorithm;
import com.hedera.mirror.common.domain.StreamFile;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor;
import com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor.DateRangeFilter;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.exception.ParserSQLException;
import com.hedera.mirror.importer.parser.AbstractStreamFileParserTest;

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
        recordItem = cryptoTransferRecordItem(id).build();
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
        ContractFunctionResult contractCallResult1 = contractFunctionResult(
                10000000000L, new byte[] { 0, 6, 4, 0, 5, 7, 2 });
        RecordItem recordItem1 = recordItem(timestamp, contractCallResult1, txBodyForContractCreate());

        ContractFunctionResult contractCallResult2 = contractFunctionResult(
                100000000000L, new byte[] { 3, 5, 1, 7, 4, 4, 0 });
        RecordItem recordItem2 = recordItem(timestamp, contractCallResult2, txBodyForContractCall());

        ContractFunctionResult contractCallResult3 = contractFunctionResult(
                1000000000000L, new byte[] { 0, 1, 1, 2, 2, 6, 0 });
        RecordItem recordItem3 = recordItem(timestamp, contractCallResult3, txBodyForEthereum());

        RecordFile recordFile = getStreamFile(Flux.just(recordItem1, recordItem2, recordItem3), timestamp);

        parser.parse(recordFile);

        byte[] expectedLogBloom = new byte[] {
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 16, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 16, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0,
                0, 0, 16, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 16, 0, 0, 0
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

    private ContractFunctionResult contractFunctionResult(long gasUsed, byte[] logBloom) {
        return ContractFunctionResult.newBuilder()
                .setGasUsed(gasUsed)
                .setBloom(ByteString.copyFrom(logBloom))
                .build();
    }

    private RecordFile getStreamFile(final Flux<RecordItem> items, final long timestamp) {
        Instant instant = Instant.ofEpochSecond(0L, timestamp);
        String filename = StreamFilename.getFilename(parserProperties.getStreamType(), DATA, instant);

        RecordFile recordFile = new RecordFile();
        recordFile.setBytes(new byte[] { 0, 1, 2 });
        recordFile.setConsensusEnd(timestamp);
        recordFile.setConsensusStart(timestamp);
        recordFile.setConsensusEnd(timestamp);
        recordFile.setCount(timestamp);
        recordFile.setDigestAlgorithm(DigestAlgorithm.SHA384);
        recordFile.setFileHash("fileHash" + timestamp);
        recordFile.setHapiVersionMajor(0);
        recordFile.setHapiVersionMinor(23);
        recordFile.setHapiVersionPatch(0);
        recordFile.setHash("hash" + timestamp);
        recordFile.setLoadEnd(timestamp);
        recordFile.setLoadStart(timestamp);
        recordFile.setName(filename);
        recordFile.setNodeAccountId(EntityId.of("0.0.3", EntityType.ACCOUNT));
        recordFile.setPreviousHash("previousHash" + (timestamp - 1));
        recordFile.setVersion(1);
        recordFile.setItems(items);
        return recordFile;
    }

    private RecordItem.RecordItemBuilder cryptoTransferRecordItem(long timestamp) {
        CryptoTransferTransactionBody cryptoTransfer = CryptoTransferTransactionBody.newBuilder().build();
        TransactionBody transactionBody = TransactionBody.newBuilder().setCryptoTransfer(cryptoTransfer).build();

        return recordItem(timestamp, transactionBody);
    }

    private RecordItem recordItem(long timestamp, ContractFunctionResult contractFunctionResult,
            TransactionBody transactionBody) {
        TransactionRecord transactionRecord = TransactionRecord.newBuilder()
                .setContractCallResult(contractFunctionResult)
                .setConsensusTimestamp(Timestamp.newBuilder().setNanos((int) timestamp))
                .build();
        return recordItem(transactionBody, transactionRecord).build();
    }

    private RecordItem.RecordItemBuilder recordItem(long timestamp, TransactionBody transactionBody) {
        TransactionRecord transactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(Timestamp.newBuilder().setNanos((int) timestamp))
                .build();
        return recordItem(transactionBody, transactionRecord);
    }

    private RecordItem.RecordItemBuilder recordItem(TransactionBody transactionBody,
            TransactionRecord transactionRecord) {
        SignedTransaction signedTransaction = SignedTransaction.newBuilder()
                .setBodyBytes(transactionBody.toByteString())
                .setSigMap(SignatureMap.newBuilder().build())
                .build();
        Transaction transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(signedTransaction.toByteString())
                .build();
        return RecordItem.builder()
                .record(transactionRecord)
                .transaction(transaction)
                .transactionBytes(transaction.toByteArray());
    }

    private TransactionBody txBodyForContractCreate() {
        ContractCreateTransactionBody contractCreateTx = ContractCreateTransactionBody.newBuilder().build();
        return TransactionBody.newBuilder()
                .setContractCreateInstance(contractCreateTx)
                .build();
    }

    private TransactionBody txBodyForContractCall() {
        ContractCallTransactionBody contractCallTx = ContractCallTransactionBody.newBuilder().build();
        return TransactionBody.newBuilder()
                .setContractCall(contractCallTx)
                .build();
    }

    private TransactionBody txBodyForEthereum() {
        EthereumTransactionBody ethereumTx = EthereumTransactionBody.newBuilder().build();
        return TransactionBody.newBuilder()
                .setEthereumTransaction(ethereumTx)
                .build();
    }
}
