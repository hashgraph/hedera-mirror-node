package com.hedera.mirror.importer.parser.record.entity;

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

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoAddLiveHashTransactionBody;
import com.hederahashgraph.api.proto.java.FileAppendTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import java.util.function.Predicate;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.importer.addressbook.NetworkAddressBook;
import com.hedera.mirror.importer.domain.ContractResult;
import com.hedera.mirror.importer.domain.CryptoTransfer;
import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.LiveHash;
import com.hedera.mirror.importer.domain.NonFeeTransfer;
import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.domain.TransactionFilterFields;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.parser.CommonParserProperties;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.parser.record.NonFeeTransferExtractionStrategy;
import com.hedera.mirror.importer.parser.record.RecordItemListener;
import com.hedera.mirror.importer.parser.record.transactionhandler.TransactionHandler;
import com.hedera.mirror.importer.parser.record.transactionhandler.TransactionHandlerFactory;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.util.Utility;

@Log4j2
@Named
@ConditionOnEntityRecordParser
public class EntityRecordItemListener implements RecordItemListener {
    private final EntityProperties entityProperties;
    private final NetworkAddressBook networkAddressBook;
    private final EntityRepository entityRepository;
    private final NonFeeTransferExtractionStrategy nonFeeTransfersExtractor;
    private final Predicate<TransactionFilterFields> transactionFilter;
    private final EntityListener entityListener;
    private final TransactionHandlerFactory transactionHandlerFactory;

    public EntityRecordItemListener(CommonParserProperties commonParserProperties, EntityProperties entityProperties,
                                    NetworkAddressBook networkAddressBook, EntityRepository entityRepository,
                                    NonFeeTransferExtractionStrategy nonFeeTransfersExtractor,
                                    EntityListener entityListener,
                                    TransactionHandlerFactory transactionHandlerFactory) {
        this.entityProperties = entityProperties;
        this.networkAddressBook = networkAddressBook;
        this.entityRepository = entityRepository;
        this.nonFeeTransfersExtractor = nonFeeTransfersExtractor;
        this.entityListener = entityListener;
        this.transactionHandlerFactory = transactionHandlerFactory;
        transactionFilter = commonParserProperties.getFilter();
    }

    public static boolean isSuccessful(TransactionRecord transactionRecord) {
        return ResponseCodeEnum.SUCCESS == transactionRecord.getReceipt().getStatus();
    }

    @Override
    public void onItem(RecordItem recordItem) throws ImporterException {
        TransactionRecord txRecord = recordItem.getRecord();
        TransactionBody body = recordItem.getTransactionBody();
        TransactionHandler transactionHandler = transactionHandlerFactory.create(body);
        log.trace("Processing transaction : {}", () -> Utility.printProtoMessage(body));

        long consensusNs = Utility.timeStampInNanos(txRecord.getConsensusTimestamp());
        EntityId entityId = transactionHandler.getEntityId(recordItem);
        TransactionTypeEnum transactionTypeEnum = TransactionTypeEnum.of(recordItem.getTransactionType());

        TransactionFilterFields transactionFilterFields = new TransactionFilterFields(entityId, transactionTypeEnum);
        if (!transactionFilter.test(transactionFilterFields)) {
            log.debug("Ignoring transaction. consensusTimestamp={}, transactionType={}, entityId={}",
                    consensusNs, transactionTypeEnum, entityId);
            return;
        }

        boolean isSuccessful = isSuccessful(txRecord);
        Transaction tx = buildTransaction(consensusNs, recordItem);
        transactionHandler.updateTransaction(tx, recordItem);
        tx.setEntity(getEntity(recordItem, transactionHandler, entityId, isSuccessful));

        if ((txRecord.hasTransferList()) && entityProperties.getPersist().isCryptoTransferAmounts()) {
            processNonFeeTransfers(consensusNs, body, txRecord);
            if (body.hasCryptoCreateAccount() && isSuccessful(txRecord)) {
                insertCryptoCreateTransferList(consensusNs, txRecord, body);
            } else {
                insertTransferList(consensusNs, txRecord.getTransferList());
            }
        }

        // TransactionBody-specific handlers.
        if (body.hasContractCall()) {
            insertContractCall(consensusNs, body.getContractCall(), txRecord);
        } else if (body.hasContractCreateInstance()) {
            insertContractCreateInstance(consensusNs, body.getContractCreateInstance(), txRecord);
        }
        if (isSuccessful) {
            if (body.hasConsensusSubmitMessage()) {
                insertConsensusTopicMessage(body.getConsensusSubmitMessage(), txRecord);
            } else if (body.hasCryptoAddLiveHash()) {
                insertCryptoAddLiveHash(consensusNs, body.getCryptoAddLiveHash());
            } else if (body.hasFileAppend()) {
                insertFileAppend(consensusNs, body.getFileAppend());
            } else if (body.hasFileCreate()) {
                insertFileData(consensusNs, body.getFileCreate().getContents().toByteArray(),
                        txRecord.getReceipt().getFileID());
            } else if (body.hasFileUpdate()) {
                insertFileUpdate(consensusNs, body.getFileUpdate());
            }
        }
        entityListener.onTransaction(tx);
        log.debug("Storing transaction: {}", tx);

        if (NetworkAddressBook.isAddressBook(entityId)) {
            networkAddressBook.updateFrom(body);
        }
    }

    private Transaction buildTransaction(long consensusTimestamp, RecordItem recordItem) {
        Transaction tx = new Transaction();
        TransactionBody body = recordItem.getTransactionBody();
        TransactionRecord txRecord = recordItem.getRecord();
        tx.setChargedTxFee(txRecord.getTransactionFee());
        tx.setConsensusNs(consensusTimestamp);
        tx.setMemo(body.getMemo().getBytes());
        tx.setMaxFee(body.getTransactionFee());
        tx.setResult(txRecord.getReceipt().getStatusValue());
        tx.setType(recordItem.getTransactionType());
        tx.setTransactionBytes(entityProperties.getPersist().isTransactionBytes() ?
                recordItem.getTransactionBytes() : null);
        tx.setTransactionHash(txRecord.getTransactionHash().toByteArray());
        Long validDurationSeconds = body.hasTransactionValidDuration() ?
                body.getTransactionValidDuration().getSeconds() : null;
        tx.setValidDurationSeconds(validDurationSeconds);
        tx.setValidStartNs(Utility.timeStampInNanos(body.getTransactionID().getTransactionValidStart()));
        tx.setNodeAccountId(entityRepository.lookupOrCreateId(EntityId.of(body.getNodeAccountID())));
        tx.setPayerAccountId(entityRepository.lookupOrCreateId(EntityId.of(body.getTransactionID().getAccountID())));
        tx.setInitialBalance(0L);
        return tx;
    }

    /**
     * Additionally store rows in the non_fee_transactions table if applicable. This will allow the rest-api to create
     * an itemized set of transfers that reflects non-fees (explicit transfers), threshold records, node fee, and
     * network+service fee (paid to treasury).
     */
    private void processNonFeeTransfers(
            long consensusTimestamp, TransactionBody body, TransactionRecord transactionRecord) {
        if (!entityProperties.getPersist().isNonFeeTransfers()) {
            return;
        }
        for (var aa : nonFeeTransfersExtractor.extractNonFeeTransfers(body, transactionRecord)) {
            if (aa.getAmount() != 0) {
                entityListener.onNonFeeTransfer(
                        new NonFeeTransfer(consensusTimestamp, aa.getAccountID().getRealmNum(),
                                aa.getAccountID().getAccountNum(), aa.getAmount()));
            }
        }
    }

    private void insertConsensusTopicMessage(ConsensusSubmitMessageTransactionBody transactionBody,
                                             TransactionRecord transactionRecord) {
        var receipt = transactionRecord.getReceipt();
        var topicId = transactionBody.getTopicID();
        int runningHashVersion = receipt.getTopicRunningHashVersion() == 0 ? 1 : (int) receipt
                .getTopicRunningHashVersion();
        TopicMessage topicMessage = new TopicMessage(
                Utility.timeStampInNanos(transactionRecord.getConsensusTimestamp()),
                transactionBody.getMessage().toByteArray(), (int) topicId.getRealmNum(),
                receipt.getTopicRunningHash().toByteArray(), receipt.getTopicSequenceNumber(),
                (int) topicId.getTopicNum(), runningHashVersion);
        entityListener.onTopicMessage(topicMessage);
    }

    private void insertFileData(long consensusTimestamp, byte[] contents, FileID fileID) {
        if (entityProperties.getPersist().isFiles() ||
                (entityProperties.getPersist().isSystemFiles() && fileID.getFileNum() < 1000)) {
            entityListener.onFileData(new FileData(consensusTimestamp, contents));
        }
    }

    private void insertFileAppend(long consensusTimestamp, FileAppendTransactionBody transactionBody) {
        byte[] contents = transactionBody.getContents().toByteArray();
        insertFileData(consensusTimestamp, contents, transactionBody.getFileID());
    }

    private void insertCryptoAddLiveHash(long consensusTimestamp,
                                         CryptoAddLiveHashTransactionBody transactionBody) {
        if (entityProperties.getPersist().isClaims()) {
            byte[] liveHash = transactionBody.getLiveHash().getHash().toByteArray();
            entityListener.onLiveHash(new LiveHash(consensusTimestamp, liveHash));
        }
    }

    private void insertContractCall(long consensusTimestamp,
                                    ContractCallTransactionBody transactionBody,
                                    TransactionRecord transactionRecord) {
        if (entityProperties.getPersist().isContracts()) {
            byte[] functionParams = transactionBody.getFunctionParameters().toByteArray();
            long gasSupplied = transactionBody.getGas();
            byte[] callResult = new byte[0];
            long gasUsed = 0;
            if (transactionRecord.hasContractCallResult()) {
                callResult = transactionRecord.getContractCallResult().toByteArray();
                gasUsed = transactionRecord.getContractCallResult().getGasUsed();
            }
            insertContractResults(consensusTimestamp, functionParams, gasSupplied, callResult, gasUsed);
        }
    }

    private void insertContractCreateInstance(long consensusTimestamp,
                                              ContractCreateTransactionBody transactionBody,
                                              TransactionRecord transactionRecord) {
        if (entityProperties.getPersist().isContracts()) {
            byte[] functionParams = transactionBody.getConstructorParameters().toByteArray();
            long gasSupplied = transactionBody.getGas();
            byte[] callResult = new byte[0];
            long gasUsed = 0;
            if (transactionRecord.hasContractCreateResult()) {
                callResult = transactionRecord.getContractCreateResult().toByteArray();
                gasUsed = transactionRecord.getContractCreateResult().getGasUsed();
            }
            insertContractResults(consensusTimestamp, functionParams, gasSupplied, callResult, gasUsed);
        }
    }

    private void insertTransferList(long consensusTimestamp, TransferList transferList) {
        for (int i = 0; i < transferList.getAccountAmountsCount(); ++i) {
            var aa = transferList.getAccountAmounts(i);
            var accountId = aa.getAccountID();
            entityRepository.lookupOrCreateId(EntityId.of(aa.getAccountID())); // ensures existence of entity in db
            addCryptoTransferList(consensusTimestamp, accountId.getRealmNum(), accountId.getAccountNum(), aa
                    .getAmount());
        }
    }

    private void insertCryptoCreateTransferList(
            long consensusTimestamp, TransactionRecord txRecord, TransactionBody body) {

        long initialBalance = 0;
        long createdAccountNum = 0;

        // no need to add missing initial balance to transfer list if this is realm and shard <> 0
        boolean addInitialBalance = (txRecord.getReceipt().getAccountID().getShardNum() == 0) && (txRecord.getReceipt()
                .getAccountID().getRealmNum() == 0);

        if (addInitialBalance) {
            initialBalance = body.getCryptoCreateAccount().getInitialBalance();
            createdAccountNum = txRecord.getReceipt().getAccountID().getAccountNum();
        }
        TransferList transferList = txRecord.getTransferList();
        for (int i = 0; i < transferList.getAccountAmountsCount(); ++i) {
            var aa = transferList.getAccountAmounts(i);
            var accountId = aa.getAccountID();
            long accountNum = accountId.getAccountNum();
            entityRepository.lookupOrCreateId(EntityId.of(accountId)); // ensures existence of entity in db
            addCryptoTransferList(consensusTimestamp, accountId.getRealmNum(), accountNum, aa.getAmount());

            if (addInitialBalance && (initialBalance == aa.getAmount()) && (accountNum == createdAccountNum)) {
                addInitialBalance = false;
            }
        }

        if (addInitialBalance) {
            AccountID payerAccountId = body.getTransactionID().getAccountID();
            AccountID createdAccountId = txRecord.getReceipt().getAccountID();
            entityRepository.lookupOrCreateId(EntityId.of(payerAccountId)); // ensures existence of entity in db
            addCryptoTransferList(consensusTimestamp, payerAccountId.getRealmNum(), payerAccountId
                    .getAccountNum(), -initialBalance);
            entityRepository.lookupOrCreateId(EntityId.of(createdAccountId)); // ensures existence of entity in db
            addCryptoTransferList(consensusTimestamp, createdAccountId
                    .getRealmNum(), createdAccountNum, initialBalance);
        }
    }

    private void addCryptoTransferList(long consensusTimestamp, long realmNum, long accountNum, long amount) {
        entityListener
                .onCryptoTransfer(new CryptoTransfer(consensusTimestamp, amount, realmNum, accountNum));
    }

    private void insertFileUpdate(long consensusTimestamp, FileUpdateTransactionBody transactionBody) {
        FileID fileId = transactionBody.getFileID();
        byte[] contents = transactionBody.getContents().toByteArray();
        insertFileData(consensusTimestamp, contents, fileId);
    }

    private void insertContractResults(
            long consensusTimestamp, byte[] functionParams, long gasSupplied, byte[] callResult, long gasUsed) {
        entityListener.onContractResult(
                new ContractResult(consensusTimestamp, functionParams, gasSupplied, callResult, gasUsed));
    }

    /**
     * @return entity associated with the transaction. Entity is guaranteed to be persisted in repo.
     */
    private Entities getEntity(
            RecordItem recordItem, TransactionHandler transactionHandler, EntityId entityId, boolean isSuccessful) {
        // Irrespective of transaction failure/success, if entityId is not null, it will be inserted into repo since:
        //   (1) it is guaranteed to be valid entity on network (validated to exist in pre-consensus checks)
        //   (2) fk_cud_entity_id is foreign key in t_transactions
        //
        // Additionally, if transaction is successful:
        // - Fields of 'entity' will be updated.
        // - proxyAccountId/autoRenewAccountId: If present, the account's id are looked up (from big cache) or created
        //   immediately in TransactionHandler.updateEntity(..).
        if (transactionHandler.updatesEntity() && isSuccessful && entityId != null) {
            Entities entity = entityRepository.findByPrimaryKey(
                    entityId.getShardNum(), entityId.getRealmNum(), entityId.getEntityNum())
                    .orElseGet(entityId::toEntity);
            transactionHandler.updateEntity(entity, recordItem);
            return entityRepository.save(entity);
        } else if (entityId != null) {
            Entities entity = entityId.toEntity();
            entity.setId(entityRepository.lookupOrCreateId(entityId)); // look up in big cache
            return entity;
        }
        // else leave tx.entity null
        return null;
    }
}
