package com.hedera.mirror.importer.parser.record.pubsub;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.integration.MessageTimeoutException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.parser.domain.PubSubMessage;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.parser.record.NonFeeTransferExtractionStrategy;
import com.hedera.mirror.importer.parser.record.NonFeeTransferExtractionStrategyImpl;
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
    private static final byte[] DEFAULT_RECORD_BYTES = DEFAULT_RECORD.toByteArray();
    private static final FileID ADDRESS_BOOK_FILE_ID = FileID.newBuilder().setFileNum(102).build();
    private static final NonFeeTransferExtractionStrategy nonFeeTransferExtractionStrategy =
            new NonFeeTransferExtractionStrategyImpl();

    private static final NodeAddressBook UPDATED = addressBook(3);

    @Mock
    private MessageChannel messageChannel;

    @Mock(lenient = true)
    private AddressBookService addressBookService;

    @Mock
    private FileDataRepository fileDataRepository;

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

    private static PubSubMessage.Transaction buildPubSubTransaction(Transaction transaction) throws InvalidProtocolBufferException {
        return new PubSubMessage.Transaction(TransactionBody.parseFrom(SignedTransaction.parseFrom(transaction.getSignedTransactionBytes())
                .getBodyBytes()), SignatureMap.getDefaultInstance());
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
        when(transactionHandlerFactory.get(any())).thenReturn(transactionHandler);
        doReturn(true).when(addressBookService).isAddressBook(EntityId.of(ADDRESS_BOOK_FILE_ID));
        pubSubRecordItemListener = new PubSubRecordItemListener(pubSubProperties, messageChannel, addressBookService,
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
        pubSubRecordItemListener.onItem(new RecordItem(transaction.toByteArray(), DEFAULT_RECORD_BYTES));

        // then
        var pubSubMessage = assertPubSubMessage(buildPubSubTransaction(transaction), 1);
        assertThat(pubSubMessage.getEntity()).isEqualTo(topicIdEntity);
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

        // when
        pubSubRecordItemListener.onItem(new RecordItem(transaction.toByteArray(), DEFAULT_RECORD_BYTES));

        // then
        var pubSubMessage = assertPubSubMessage(buildPubSubTransaction(transaction), 1);
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

        // when
        when(messageChannel.send(any())).thenThrow(RuntimeException.class);

        // then
        assertThatThrownBy(
                () -> pubSubRecordItemListener.onItem(
                        new RecordItem(transaction.toByteArray(), DEFAULT_RECORD_BYTES)))
                .isInstanceOf(ParserException.class)
                .hasMessageContaining("Error sending transaction to pubsub");
        verify(messageChannel, times(1)).send(any());
    }

    @Test
    void testSendRetries() throws Exception {
        // when
        CryptoTransferTransactionBody cryptoTransfer = CryptoTransferTransactionBody.newBuilder()
                .setTransfers(TransferList.newBuilder().build())
                .build();
        Transaction transaction = buildTransaction(builder -> builder.setCryptoTransfer(cryptoTransfer));
        pubSubProperties.setMaxSendAttempts(3);

        // when
        when(messageChannel.send(any()))
                .thenThrow(MessageTimeoutException.class)
                .thenThrow(MessageTimeoutException.class)
                .thenReturn(true);
        pubSubRecordItemListener.onItem(new RecordItem(transaction.toByteArray(), DEFAULT_RECORD_BYTES));

        // then
        var pubSubMessage = assertPubSubMessage(buildPubSubTransaction(transaction), 3);
        assertThat(pubSubMessage.getEntity()).isNull();
        assertThat(pubSubMessage.getNonFeeTransfers()).isNull();
    }

    @Test
    void testNetworkAddressBookAppend() throws Exception {
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
        doReturn(EntityId.of(ADDRESS_BOOK_FILE_ID)).when(transactionHandler).getEntity(any());
        pubSubRecordItemListener.onItem(new RecordItem(transaction.toByteArray(), DEFAULT_RECORD_BYTES));

        // then
        FileData fileData = new FileData(100L, fileContents, entityId, TransactionTypeEnum.FILEAPPEND
                .getProtoId());
        verify(addressBookService).update(fileData);
    }

    @Test
    void testNetworkAddressBookUpdate() throws Exception {
        // given
        byte[] fileContents = UPDATED.toByteArray();
        var fileUpdate = FileUpdateTransactionBody.newBuilder()
                .setFileID(ADDRESS_BOOK_FILE_ID)
                .setContents(ByteString.copyFrom(fileContents))
                .build();
        Transaction transaction = buildTransaction(builder -> builder.setFileUpdate(fileUpdate));

        // when
        EntityId entityId = EntityId.of(ADDRESS_BOOK_FILE_ID);
        doReturn(EntityId.of(ADDRESS_BOOK_FILE_ID)).when(transactionHandler).getEntity(any());
        pubSubRecordItemListener.onItem(new RecordItem(transaction.toByteArray(), DEFAULT_RECORD_BYTES));

        // then
        FileData fileData = new FileData(100L, fileContents, EntityId
                .of(ADDRESS_BOOK_FILE_ID), TransactionTypeEnum.FILEUPDATE
                .getProtoId());
        verify(addressBookService).update(fileData);
    }

    private PubSubMessage assertPubSubMessage(PubSubMessage.Transaction expectedTransaction, int numSendTries) throws Exception {
        ArgumentCaptor<Message<PubSubMessage>> argument = ArgumentCaptor.forClass(Message.class);
        verify(messageChannel, times(numSendTries)).send(argument.capture());
        var actual = argument.getValue().getPayload();
        assertThat(actual.getConsensusTimestamp()).isEqualTo(CONSENSUS_TIMESTAMP);
        assertThat(actual.getTransaction()).isEqualTo(expectedTransaction);
        assertThat(actual.getTransactionRecord()).isEqualTo(DEFAULT_RECORD);
        assertThat(argument.getValue().getHeaders()).describedAs("Headers contain consensus timestamp")
                .hasSize(3) // +2 are default attributes 'id' and 'timestamp' (publish)
                .containsEntry("consensusTimestamp", CONSENSUS_TIMESTAMP);
        return actual;
    }
}
