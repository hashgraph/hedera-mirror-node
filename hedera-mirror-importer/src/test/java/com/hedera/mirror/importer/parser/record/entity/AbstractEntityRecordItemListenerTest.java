package com.hedera.mirror.importer.parser.record.entity;

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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.from;

import com.google.common.collect.Iterables;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Resource;
import org.springframework.transaction.support.TransactionTemplate;

import com.hedera.mirror.common.domain.DigestAlgorithm;
import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.entity.AbstractEntity;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.transaction.CryptoTransfer;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.parser.domain.RecordItemBuilder;
import com.hedera.mirror.importer.parser.record.RecordStreamFileListener;
import com.hedera.mirror.importer.repository.ContractRepository;
import com.hedera.mirror.importer.repository.ContractResultRepository;
import com.hedera.mirror.importer.repository.CryptoTransferRepository;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.LiveHashRepository;
import com.hedera.mirror.importer.repository.NonFeeTransferRepository;
import com.hedera.mirror.importer.repository.TopicMessageRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;
import com.hedera.mirror.importer.util.Utility;

public abstract class AbstractEntityRecordItemListenerTest extends IntegrationTest {

    protected static final SignatureMap DEFAULT_SIG_MAP = getDefaultSigMap();
    protected static final String KEY = "0a2212200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110fff";
    protected static final String KEY2 = "0a3312200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e92";
    protected static final AccountID PAYER = AccountID.newBuilder().setAccountNum(2002).build();
    protected static final AccountID PAYER2 = AccountID.newBuilder().setAccountNum(2003).build();
    protected static final AccountID RECEIVER = AccountID.newBuilder().setAccountNum(2004).build();
    protected static final AccountID DEFAULT_ACCOUNT_ID = AccountID.getDefaultInstance();
    protected static final AccountID NODE = AccountID.newBuilder().setAccountNum(3).build();
    protected static final EntityId NODE_ACCOUNT_ID = EntityId.of(NODE);
    protected static final AccountID TREASURY = AccountID.newBuilder().setAccountNum(98).build();
    protected static final AccountID PROXY = AccountID.newBuilder().setAccountNum(1003).build();
    protected static final AccountID PROXY_UPDATE = AccountID.newBuilder().setAccountNum(3000).build();
    protected static final String TRANSACTION_MEMO = "transaction memo";

    @Resource
    protected DomainBuilder domainBuilder;
    @Resource
    protected TransactionRepository transactionRepository;
    @Resource
    protected EntityRepository entityRepository;
    @Resource
    protected ContractRepository contractRepository;
    @Resource
    protected ContractResultRepository contractResultRepository;
    @Resource
    protected CryptoTransferRepository cryptoTransferRepository;
    @Resource
    protected LiveHashRepository liveHashRepository;
    @Resource
    protected TopicMessageRepository topicMessageRepository;
    @Resource
    protected NonFeeTransferRepository nonFeeTransferRepository;

    @Resource
    protected EntityRecordItemListener entityRecordItemListener;

    @Resource
    protected EntityProperties entityProperties;

    @Resource
    protected RecordItemBuilder recordItemBuilder;

    @Resource
    protected RecordStreamFileListener recordStreamFileListener;

    @Resource
    private TransactionTemplate transactionTemplate;

    private long nextIndex = 0L;

    private static SignatureMap getDefaultSigMap() {
        String key1 = "11111111111111111111c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e91";
        String signature1 = "Signature 1 here";
        String key2 = "22222222222222222222c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e91";
        String signature2 = "Signature 2 here";

        SignatureMap.Builder sigMap = SignatureMap.newBuilder();
        SignaturePair.Builder sigPair = SignaturePair.newBuilder();
        sigPair.setEd25519(ByteString.copyFromUtf8(signature1));
        sigPair.setPubKeyPrefix(ByteString.copyFromUtf8(key1));

        sigMap.addSigPair(sigPair);

        sigPair = SignaturePair.newBuilder();
        sigPair.setEd25519(ByteString.copyFromUtf8(signature2));
        sigPair.setPubKeyPrefix(ByteString.copyFromUtf8(key2));

        sigMap.addSigPair(sigPair);

        return sigMap.build();
    }

    protected static Key keyFromString(String key) {
        return Key.newBuilder().setEd25519(ByteString.copyFromUtf8(key)).build();
    }

    private static Builder defaultTransactionBodyBuilder() {
        TransactionBody.Builder body = TransactionBody.newBuilder();
        body.setTransactionFee(100L);
        body.setMemo(TRANSACTION_MEMO);
        body.setNodeAccountID(AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(3).build());
        body.setTransactionID(Utility.getTransactionId(PAYER));
        body.setTransactionValidDuration(Duration.newBuilder().setSeconds(120).build());
        return body;
    }

    protected final void assertAccount(AccountID accountId, Entity dbEntity) {
        assertThat(accountId)
                .isNotEqualTo(AccountID.getDefaultInstance())
                .extracting(AccountID::getShardNum, AccountID::getRealmNum, AccountID::getAccountNum)
                .containsExactly(dbEntity.getShard(), dbEntity.getRealm(), dbEntity.getNum());
        assertThat(dbEntity.getType())
                .isEqualTo(EntityType.ACCOUNT);
    }

    protected void parseRecordItemAndCommit(RecordItem recordItem) {
        transactionTemplate.executeWithoutResult(status -> {
            Instant instant = Instant.ofEpochSecond(0, recordItem.getConsensusTimestamp());
            String filename = StreamFilename.getFilename(StreamType.RECORD, DATA, instant);
            long consensusStart = recordItem.getConsensusTimestamp();
            RecordFile recordFile = recordFile(consensusStart, consensusStart + 1, filename);

            recordStreamFileListener.onStart();
            entityRecordItemListener.onItem(recordItem);
            // commit, close connection
            recordStreamFileListener.onEnd(recordFile);
        });
    }

    protected void parseRecordItemsAndCommit(List<RecordItem> recordItems) {
        transactionTemplate.executeWithoutResult(status -> {
            Instant instant = Instant.ofEpochSecond(0, recordItems.get(0).getConsensusTimestamp());
            String filename = StreamFilename.getFilename(StreamType.RECORD, DATA, instant);
            long consensusStart = recordItems.get(0).getConsensusTimestamp();
            long consensusEnd = recordItems.get(recordItems.size() - 1).getConsensusTimestamp();
            RecordFile recordFile = recordFile(consensusStart, consensusEnd, filename);

            recordStreamFileListener.onStart();

            // process each record item
            for (RecordItem recordItem : recordItems) {
                entityRecordItemListener.onItem(recordItem);
            }

            // commit, close connection
            recordStreamFileListener.onEnd(recordFile);
        });
    }

    protected void assertRecordTransfers(TransactionRecord record) {
        long consensusTimestamp = DomainUtils.timeStampInNanos(record.getConsensusTimestamp());
        if (entityProperties.getPersist().isCryptoTransferAmounts()) {
            TransferList transferList = record.getTransferList();
            for (AccountAmount accountAmount : transferList.getAccountAmountsList()) {
                EntityId account = EntityId.of(accountAmount.getAccountID());
                assertThat(cryptoTransferRepository
                        .findById(new CryptoTransfer.Id(accountAmount.getAmount(), consensusTimestamp, account)))
                        .isPresent();
            }
        } else {
            assertThat(cryptoTransferRepository.count()).isEqualTo(0L);
        }
    }

    protected void assertTransactionAndRecord(TransactionBody transactionBody, TransactionRecord record) {
        var dbTransaction = getDbTransaction(record.getConsensusTimestamp());
        assertTransaction(transactionBody, dbTransaction);
        assertRecord(record, dbTransaction);
    }

    private void assertRecord(TransactionRecord record, Transaction dbTransaction) {
        assertThat(dbTransaction)
                .isNotNull()
                .returns(DomainUtils.timeStampInNanos(record.getConsensusTimestamp()),
                        Transaction::getConsensusTimestamp)
                .returns(record.getTransactionFee(), Transaction::getChargedTxFee)
                .returns(record.getReceipt().getStatusValue(), Transaction::getResult)
                .returns(record.getTransactionHash().toByteArray(), Transaction::getTransactionHash)
                .returns(record.hasScheduleRef(), Transaction::isScheduled);
        assertRecordTransfers(record);
    }

    private void assertTransaction(TransactionBody transactionBody, Transaction dbTransaction) {
        var transactionId = transactionBody.getTransactionID();
        var validDurationSeconds = transactionBody.getTransactionValidDuration().getSeconds();
        var validStart = DomainUtils.timeStampInNanos(transactionId.getTransactionValidStart());

        assertThat(dbTransaction)
                .isNotNull()
                .returns(transactionBody.getTransactionFee(), Transaction::getMaxFee)
                .returns(transactionBody.getMemoBytes().toByteArray(), Transaction::getMemo)
                .returns(EntityId.of(transactionBody.getNodeAccountID()), Transaction::getNodeAccountId)
                .returns(EntityId.of(transactionId.getAccountID()), Transaction::getPayerAccountId)
                .returns(null, Transaction::getTransactionBytes)
                .returns(transactionBody.getDataCase().getNumber(), Transaction::getType)
                .returns(validStart, Transaction::getValidStartNs)
                .returns(validDurationSeconds, Transaction::getValidDurationSeconds);
    }

    protected com.hederahashgraph.api.proto.java.Transaction buildTransaction(Consumer<Builder> customBuilder,
                                                                              SignatureMap sigMap) {
        TransactionBody.Builder bodyBuilder = defaultTransactionBodyBuilder();
        customBuilder.accept(bodyBuilder);

        return com.hederahashgraph.api.proto.java.Transaction.newBuilder()
                .setSignedTransactionBytes(SignedTransaction.newBuilder()
                        .setBodyBytes(bodyBuilder.build().toByteString())
                        .setSigMap(sigMap)
                        .build().toByteString())
                .build();
    }

    protected com.hederahashgraph.api.proto.java.Transaction buildTransaction(Consumer<Builder> customBuilder) {
        return buildTransaction(customBuilder, DEFAULT_SIG_MAP);
    }

    protected TransactionRecord buildTransactionRecord(
            Consumer<TransactionRecord.Builder> customBuilder, TransactionBody transactionBody, int status) {
        TransactionRecord.Builder recordBuilder = TransactionRecord.newBuilder();
        recordBuilder.setConsensusTimestamp(Utility.instantToTimestamp(Instant.now()));
        recordBuilder.setMemoBytes(ByteString.copyFromUtf8(transactionBody.getMemo()));
        recordBuilder.setTransactionFee(transactionBody.getTransactionFee());
        recordBuilder.setTransactionHash(ByteString.copyFromUtf8("TransactionHash"));
        recordBuilder.setTransactionID(transactionBody.getTransactionID());
        recordBuilder.getReceiptBuilder().setStatusValue(status);

        // Give from payer to treasury and node
        long[] transferAccounts = {PAYER.getAccountNum(), TREASURY.getAccountNum(), NODE.getAccountNum()};
        long[] transferAmounts = {-2000, 1000, 1000};
        TransferList.Builder transferList = recordBuilder.getTransferListBuilder();
        for (int i = 0; i < transferAccounts.length; i++) {
            // Irrespective of transaction success, node and network fees are present.
            transferList.addAccountAmounts(accountAmount(transferAccounts[i], transferAmounts[i]));
        }
        if (transactionBody.hasCryptoTransfer() && status == ResponseCodeEnum.SUCCESS.getNumber()) {
            for (var aa : transactionBody.getCryptoTransfer().getTransfers().getAccountAmountsList()) {
                // handle alias case.
                // Network will correctly populate accountNum in record, ignore for test case
                if (aa.getAccountID().getAccountCase() == AccountID.AccountCase.ALIAS) {
                    continue;
                }

                transferList.addAccountAmounts(aa);
            }
        }
        customBuilder.accept(recordBuilder);
        return recordBuilder.build();
    }

    protected Transaction getDbTransaction(Timestamp consensusTimestamp) {
        return transactionRepository.findById(DomainUtils.timeStampInNanos(consensusTimestamp)).get();
    }

    protected <T extends AbstractEntity> T getTransactionEntity(Timestamp consensusTimestamp) {
        var transaction = transactionRepository.findById(DomainUtils.timeStampInNanos(consensusTimestamp)).get();
        return getEntity(transaction.getEntityId());
    }

    protected <T extends AbstractEntity> T getEntity(EntityId entityId) {
        Optional<Contract> contract = contractRepository.findById(entityId.getId());
        if (contract.isPresent()) {
            return (T) contract.get();
        }
        return (T) entityRepository.findById(entityId.getId()).get();
    }

    protected AccountAmount.Builder accountAmount(long accountNum, long amount) {
        return AccountAmount.newBuilder().setAccountID(AccountID.newBuilder().setAccountNum(accountNum))
                .setAmount(amount);
    }

    protected AccountAmount.Builder accountAliasAmount(ByteString alias, long amount) {
        return AccountAmount.newBuilder().setAccountID(AccountID.newBuilder().setAlias(alias))
                .setAmount(amount);
    }

    protected TransactionBody getTransactionBody(com.hederahashgraph.api.proto.java.Transaction transaction) {
        try {
            return TransactionBody.parseFrom(
                    SignedTransaction.parseFrom(transaction.getSignedTransactionBytes()).getBodyBytes());
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    protected Entity createEntity(EntityId entityId, Key adminKey, EntityId autoRenewAccountId, Long autoRenewPeriod,
                                  Boolean deleted, Long expiryTimeNs, String memo, Key submitKey,
                                  Long createdTimestamp, Long modifiedTimestamp) {
        byte[] adminKeyBytes = rawBytesFromKey(adminKey);
        byte[] submitKeyBytes = rawBytesFromKey(submitKey);

        Entity entity = entityId.toEntity();
        entity.setAutoRenewAccountId(autoRenewAccountId);
        entity.setAutoRenewPeriod(autoRenewPeriod);
        entity.setCreatedTimestamp(createdTimestamp);
        entity.setDeleted(deleted);
        entity.setExpirationTimestamp(expiryTimeNs);
        entity.setMemo(memo);
        entity.setTimestampLower(modifiedTimestamp);
        entity.setKey(adminKeyBytes);
        entity.setSubmitKey(submitKeyBytes);

        return entity;
    }

    private byte[] rawBytesFromKey(Key key) {
        return key != null ? key.toByteArray() : null;
    }

    protected void assertTransactionInRepository(
            ResponseCodeEnum responseCode, long consensusTimestamp, Long entityId) {
        var transaction = transactionRepository.findById(consensusTimestamp).get();
        assertThat(transaction)
                .returns(responseCode.getNumber(), from(Transaction::getResult))
                .returns(TRANSACTION_MEMO.getBytes(), from(Transaction::getMemo));
        if (entityId != null) {
            assertThat(transaction)
                    .returns(entityId, t -> t.getEntityId().getId());
        }
    }

    protected void assertEntities(EntityId... entityIds) {
        if (entityIds == null) {
            return;
        }

        assertThat(Iterables.concat(contractRepository.findAll(), entityRepository.findAll()))
                .hasSize(entityIds.length)
                .allMatch(entity -> entity.getId() > 0)
                .allMatch(entity -> entity.getType() != null)
                .extracting(AbstractEntity::toEntityId)
                .containsExactlyInAnyOrder(entityIds);
    }

    protected void assertEntity(AbstractEntity expected) {
        AbstractEntity actual = getEntity(expected.toEntityId());
        assertThat(actual).isEqualTo(expected);
    }

    protected <T extends AbstractEntity> T getEntityWithDefaultMemo(EntityId entityId) {
        T entity = entityId.toEntity();
        entity.setMemo("");
        return entity;
    }

    private RecordFile recordFile(long consensusStart, long consensusEnd, String filename) {
        return RecordFile.builder()
                .consensusStart(consensusStart)
                .consensusEnd(consensusEnd)
                .count(0L)
                .digestAlgorithm(DigestAlgorithm.SHA384)
                .fileHash(UUID.randomUUID().toString())
                .hash(UUID.randomUUID().toString())
                .index(nextIndex++)
                .loadEnd(Instant.now().getEpochSecond())
                .loadStart(Instant.now().getEpochSecond())
                .name(filename)
                .nodeAccountId(NODE_ACCOUNT_ID)
                .previousHash("")
                .build();
    }
}
