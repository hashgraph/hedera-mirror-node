package com.hedera.mirror.importer.parser.record.pubsub;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.FileAppendTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.file.FileData;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.parser.domain.PubSubMessage;
import com.hedera.mirror.importer.parser.record.NonFeeTransferExtractionStrategy;
import com.hedera.mirror.importer.parser.record.transactionhandler.TransactionHandler;
import com.hedera.mirror.importer.parser.record.transactionhandler.TransactionHandlerFactory;
import com.hedera.mirror.importer.repository.FileDataRepository;
import com.hedera.mirror.importer.util.Utility;

@ExtendWith(MockitoExtension.class)
class PubSubRecordItemListenerTest {
    private static final Long CONSENSUS_TIMESTAMP = 100L;
    private static final TransactionRecord DEFAULT_RECORD = TransactionRecord.newBuilder()
            .setConsensusTimestamp(Utility.instantToTimestamp(Instant.ofEpochSecond(0L, CONSENSUS_TIMESTAMP)))
            .setReceipt(TransactionReceipt.newBuilder().setStatus(ResponseCodeEnum.SUCCESS).build())
            .build();
    private static final FileID ADDRESS_BOOK_FILE_ID = FileID.newBuilder().setFileNum(102).build();

    private static final NodeAddressBook UPDATED = addressBook(3);

    private static final String TOPIC_NAME = "topic-name";

    @Mock(lenient = true)
    private AddressBookService addressBookService;

    @Mock
    private FileDataRepository fileDataRepository;

    @Mock
    private NonFeeTransferExtractionStrategy nonFeeTransferExtractionStrategy;

    @Mock
    private PubSubTemplate pubSubTemplate;

    @Mock
    private TransactionHandler transactionHandler;

    private PubSubProperties pubSubProperties;
    private PubSubRecordItemListener pubSubRecordItemListener;

    private static AccountAmount buildAccountAmount(long accountNum, long amount) {
        return AccountAmount.newBuilder()
                .setAccountID(AccountID.newBuilder().setAccountNum(accountNum).build())
                .setAmount(amount)
                .build();
    }

    private static Transaction buildTransaction(Consumer<TransactionBody.Builder> transactionModifier) {
        TransactionBody.Builder transactionBodyBuilder = TransactionBody.newBuilder();
        transactionModifier.accept(transactionBodyBuilder);
        return Transaction.newBuilder()
                .setSignedTransactionBytes(
                        SignedTransaction.newBuilder()
                                .setBodyBytes(transactionBodyBuilder.build().toByteString())
                                .setSigMap(SignatureMap.getDefaultInstance())
                                .build().toByteString()
                )
                .build();
    }

    private static PubSubMessage buildPubSubTransaction(RecordItem recordItem, Transaction transaction) throws InvalidProtocolBufferException {
        var pubSubTransaction =
                new PubSubMessage.Transaction(TransactionBody.parseFrom(SignedTransaction.parseFrom(transaction.getSignedTransactionBytes())
                        .getBodyBytes()), SignatureMap.getDefaultInstance());
        var entityId = EntityId.of(10, EntityType.ACCOUNT);
        var transactionType = recordItem.getTransactionType();
        var transactionRecord = recordItem.getTransactionRecord();
        return new PubSubMessage(recordItem.getConsensusTimestamp(), entityId, transactionType, pubSubTransaction,
                transactionRecord, null);
    }

    private static NodeAddressBook addressBook(int size) {
        NodeAddressBook.Builder builder = NodeAddressBook.newBuilder();
        for (int i = 0; i < size; ++i) {
            builder.addNodeAddress(NodeAddress.newBuilder().setPortno(i).build());
        }
        return builder.build();
    }

    @BeforeEach
    void beforeEach() {
        TransactionHandlerFactory transactionHandlerFactory = mock(TransactionHandlerFactory.class);
        pubSubProperties = new PubSubProperties();
        pubSubProperties.setTopicName(TOPIC_NAME);
        when(transactionHandlerFactory.get(any())).thenReturn(transactionHandler);
        doReturn(true).when(addressBookService).isAddressBook(EntityId.of(ADDRESS_BOOK_FILE_ID));
        when(transactionHandlerFactory.get(any())).thenReturn(transactionHandler);
        var responseFuture = mock(ListenableFuture.class);
        doReturn(responseFuture).when(pubSubTemplate).publish(any(), any(), any());
        pubSubRecordItemListener = new PubSubRecordItemListener(pubSubProperties, pubSubTemplate, addressBookService,
                fileDataRepository, nonFeeTransferExtractionStrategy, transactionHandlerFactory);
    }

    @Test
    void testPubSubMessage() throws Exception {
        // given
        byte[] message = new byte[] {'a', 'b', 'c'};
        TopicID topicID = TopicID.newBuilder().setTopicNum(10L).build();
        EntityId topicIdEntity = EntityId.of(topicID);
        ConsensusSubmitMessageTransactionBody submitMessage = ConsensusSubmitMessageTransactionBody.newBuilder()
                .setMessage(ByteString.copyFrom(message))
                .setTopicID(topicID)
                .build();
        Transaction transaction = buildTransaction(builder -> builder.setConsensusSubmitMessage(submitMessage));

        // when
        doReturn(topicIdEntity).when(transactionHandler).getEntity(any());
        var successFuture = mock(ListenableFuture.class);
        when(pubSubTemplate.publish(any(), any(), any()))
                .thenReturn(successFuture);
        doAnswer(invocationOnMock -> {
            ListenableFutureCallback callback = invocationOnMock.getArgument(0);
            callback.onSuccess("success");
            return null;
        }).when(successFuture).addCallback(any(ListenableFutureCallback.class));

        var recordItem = RecordItem.builder().transactionRecord(DEFAULT_RECORD).transaction(transaction).build();
        pubSubRecordItemListener.onItem(recordItem);

        // then
        verify(successFuture).addCallback(any(ListenableFutureCallback.class));
        var pubSubMessage = assertPubSubMessage(buildPubSubTransaction(recordItem, transaction), 1);
        assertThat(pubSubMessage.getEntity()).isEqualTo(topicIdEntity);
        assertThat(pubSubMessage.getNonFeeTransfers()).isNull();
    }

    @Test
    void testPubSubMessageNullEntityId() throws Exception {
        // given
        byte[] message = new byte[] {'a', 'b', 'c'};
        TopicID topicID = TopicID.newBuilder().setTopicNum(10L).build();
        ConsensusSubmitMessageTransactionBody submitMessage = ConsensusSubmitMessageTransactionBody.newBuilder()
                .setMessage(ByteString.copyFrom(message))
                .setTopicID(topicID)
                .build();
        Transaction transaction = buildTransaction(builder -> builder.setConsensusSubmitMessage(submitMessage));
        // when
        doReturn(null).when(transactionHandler).getEntity(any());
        var recordItem = RecordItem.builder().transactionRecord(DEFAULT_RECORD).transaction(transaction).build();
        pubSubRecordItemListener.onItem(recordItem);

        // then
        var pubSubMessage = assertPubSubMessage(buildPubSubTransaction(recordItem, transaction), 1);
        assertThat(pubSubMessage.getEntity()).isNull();
        assertThat(pubSubMessage.getNonFeeTransfers()).isNull();
    }

    @Test
    void testPubSubMessageWithNonFeeTransferAndNullEntityId() throws Exception {
        // given
        List<AccountAmount> nonFeeTransfers = new ArrayList<>();
        nonFeeTransfers.add(buildAccountAmount(10L, 100L));
        nonFeeTransfers.add(buildAccountAmount(11L, 111L));
        CryptoTransferTransactionBody cryptoTransfer = CryptoTransferTransactionBody.newBuilder()
                .setTransfers(TransferList.newBuilder()
                        .addAccountAmounts(nonFeeTransfers.get(0))
                        .addAccountAmounts(nonFeeTransfers.get(1))
                        .build())
                .build();
        Transaction transaction = buildTransaction(builder -> builder.setCryptoTransfer(cryptoTransfer));
        var recordItem = RecordItem.builder().transactionRecord(DEFAULT_RECORD).transaction(transaction).build();
        when(nonFeeTransferExtractionStrategy.extractNonFeeTransfers(recordItem.getTransactionBody(),
                recordItem.getTransactionRecord())).thenReturn(cryptoTransfer.getTransfers().getAccountAmountsList());

        // when
        pubSubRecordItemListener.onItem(recordItem);

        // then
        var pubSubMessage = assertPubSubMessage(buildPubSubTransaction(recordItem, transaction), 1);
        assertThat(pubSubMessage.getEntity()).isNull();
        assertThat(pubSubMessage.getNonFeeTransfers()).isEqualTo(nonFeeTransfers);
    }

    @Test
    void testNonRetryableError() {
        // when
        CryptoTransferTransactionBody cryptoTransfer = CryptoTransferTransactionBody.newBuilder()
                .setTransfers(TransferList.newBuilder().build())
                .build();
        Transaction transaction = buildTransaction(builder -> builder.setCryptoTransfer(cryptoTransfer));
        RecordItem recordItem = RecordItem.builder().transactionRecord(DEFAULT_RECORD)
                .transaction(transaction).build();

        // when
        when(pubSubTemplate.publish(any(), any(), any())).thenThrow(RuntimeException.class);

        // then
        assertThatThrownBy(
                () -> pubSubRecordItemListener.onItem(recordItem))
                .isInstanceOf(ParserException.class)
                .hasMessageContaining("Error sending transaction to pubsub");
        verify(pubSubTemplate, times(1)).publish(any(), any(), any());
    }

    @Test
    void testSendRetries() throws Exception {
        CryptoTransferTransactionBody cryptoTransfer = CryptoTransferTransactionBody.newBuilder()
                .setTransfers(TransferList.newBuilder().build())
                .build();
        Transaction transaction = buildTransaction(builder -> builder.setCryptoTransfer(cryptoTransfer));
        pubSubProperties.setMaxSendAttempts(3);

        // when
        var failFuture = mock(ListenableFuture.class);
        when(pubSubTemplate.publish(any(), any(), any()))
                .thenReturn(failFuture);
        doAnswer(invocationOnMock -> {
            ListenableFutureCallback callback = invocationOnMock.getArgument(0);
            callback.onFailure(new RuntimeException("error"));
            return null;
        }).when(failFuture).addCallback(any(ListenableFutureCallback.class));

        var recordItem = RecordItem.builder().transactionRecord(DEFAULT_RECORD).transaction
                (transaction).build();
        pubSubRecordItemListener.onItem(recordItem);

        // then
        assertPubSubMessage(buildPubSubTransaction(recordItem, transaction), 4);
    }

    @Test
    void testNetworkAddressBookAppend() {
        // given
        byte[] fileContents = new byte[] {0b0, 0b1, 0b10};
        Transaction transaction = buildTransaction(builder -> {
            builder.setFileAppend(FileAppendTransactionBody.newBuilder()
                    .setFileID(ADDRESS_BOOK_FILE_ID)
                    .setContents(ByteString.copyFrom(fileContents))
                    .build());
        });

        // when
        EntityId entityId = EntityId.of(ADDRESS_BOOK_FILE_ID);
        doReturn(entityId).when(transactionHandler).getEntity(any());
        pubSubRecordItemListener.onItem(RecordItem.builder().transactionRecord(DEFAULT_RECORD).transaction(transaction).build());

        // then
        FileData fileData = new FileData(100L, fileContents, entityId, TransactionType.FILEAPPEND
                .getProtoId());
        verify(addressBookService).update(fileData);
    }

    @Test
    void testNetworkAddressBookUpdate() {
        // given
        byte[] fileContents = UPDATED.toByteArray();
        var fileUpdate = FileUpdateTransactionBody.newBuilder()
                .setFileID(ADDRESS_BOOK_FILE_ID)
                .setContents(ByteString.copyFrom(fileContents))
                .build();
        Transaction transaction = buildTransaction(builder -> builder.setFileUpdate(fileUpdate));

        // when
        doReturn(EntityId.of(ADDRESS_BOOK_FILE_ID)).when(transactionHandler).getEntity(any());
        pubSubRecordItemListener.onItem(RecordItem.builder().transactionRecord(DEFAULT_RECORD).transaction(transaction)
                .build());

        // then
        FileData fileData = new FileData(100L, fileContents, EntityId
                .of(ADDRESS_BOOK_FILE_ID), TransactionType.FILEUPDATE
                .getProtoId());
        verify(addressBookService).update(fileData);
    }

    private PubSubMessage assertPubSubMessage(PubSubMessage pubSubMessage, int numSendTries) {
        Map<String, String> header = Map.of("consensusTimestamp", CONSENSUS_TIMESTAMP.toString());
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PubSubMessage> pubSubMessageCaptor = ArgumentCaptor.forClass(PubSubMessage.class);
        ArgumentCaptor<Map> headerCaptor = ArgumentCaptor.forClass(Map.class);
        verify(pubSubTemplate, times(numSendTries)).publish(topicCaptor.capture(), pubSubMessageCaptor.capture(),
                headerCaptor.capture());

        var actualTopic = topicCaptor.getValue();
        assertThat(actualTopic).isEqualTo(TOPIC_NAME);

        var actualHeader = headerCaptor.getValue();
        assertThat(actualHeader).isEqualTo(header);

        var actualPubSubMessage = pubSubMessageCaptor.getValue();
        assertThat(actualPubSubMessage.getTransaction()).isEqualTo(pubSubMessage.getTransaction());
        assertThat(actualPubSubMessage.getTransactionRecord()).isEqualTo(DEFAULT_RECORD);
        return actualPubSubMessage;
    }
}
