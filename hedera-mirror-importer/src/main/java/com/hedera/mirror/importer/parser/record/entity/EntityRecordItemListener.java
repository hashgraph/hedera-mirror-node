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

import com.hederahashgraph.api.proto.java.ConsensusMessageChunkInfo;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoAddLiveHashTransactionBody;
import com.hederahashgraph.api.proto.java.FileAppendTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import java.util.function.Predicate;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.importer.addressbook.AddressBookService;
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
import com.hedera.mirror.importer.exception.InvalidEntityException;
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
    private final AddressBookService addressBookService;
    private final EntityRepository entityRepository;
    private final NonFeeTransferExtractionStrategy nonFeeTransfersExtractor;
    private final Predicate<TransactionFilterFields> transactionFilter;
    private final EntityListener entityListener;
    private final TransactionHandlerFactory transactionHandlerFactory;

    public EntityRecordItemListener(CommonParserProperties commonParserProperties, EntityProperties entityProperties,
                                    AddressBookService addressBookService, EntityRepository entityRepository,
                                    NonFeeTransferExtractionStrategy nonFeeTransfersExtractor,
                                    EntityListener entityListener,
                                    TransactionHandlerFactory transactionHandlerFactory) {
        this.entityProperties = entityProperties;
        this.addressBookService = addressBookService;
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

        long consensusNs = Utility.timeStampInNanos(txRecord.getConsensusTimestamp());
        EntityId entityId;
        try {
            entityId = transactionHandler.getEntity(recordItem);
        } catch (InvalidEntityException e) { // transaction can have invalid topic/contract/file id
            log.warn("Invalid entity encountered for consensusTimestamp {} : {}", consensusNs, e.getMessage());
            entityId = null;
        }

        int transactionType = recordItem.getTransactionType();
        TransactionTypeEnum transactionTypeEnum = TransactionTypeEnum.of(transactionType);
        log.debug("Processing {} transaction {} for entity {}", transactionTypeEnum, consensusNs, entityId);

        // to:do - catch Freeze transaction and update addressBook with last transaction time

        TransactionFilterFields transactionFilterFields = new TransactionFilterFields(entityId, transactionTypeEnum);
        if (!transactionFilter.test(transactionFilterFields)) {
            log.debug("Ignoring transaction. consensusTimestamp={}, transactionType={}, entityId={}",
                    consensusNs, transactionTypeEnum, entityId);
            return;
        }

        boolean isSuccessful = isSuccessful(txRecord);
        Transaction tx = buildTransaction(consensusNs, recordItem);
        transactionHandler.updateTransaction(tx, recordItem);
        if (entityId != null) {
            tx.setEntityId(entityId);
            // Irrespective of transaction failure/success, if entityId is not null, it will be inserted into repo since
            // it is guaranteed to be valid entity on network (validated to exist in pre-consensus checks).
            entityListener.onEntityId(entityId);

            if (isSuccessful && transactionHandler.updatesEntity()) {
                updateEntity(recordItem, transactionHandler, entityId);
            }
        }

        if (txRecord.hasTransferList() && entityProperties.getPersist().isCryptoTransferAmounts()) {
            // Don't add failed non-fee transfers as they can contain invalid data and we don't add failed
            // transactions for aggregated transfers
            if (isSuccessful) {
                processNonFeeTransfers(consensusNs, body, txRecord);
            }

            if (body.hasCryptoCreateAccount() && isSuccessful) {
                insertCryptoCreateTransferList(consensusNs, txRecord, body);
            } else {
                insertTransferList(consensusNs, txRecord.getTransferList());
            }
        }

        // Insert contract results even for failed transactions since they could fail during execution and we want to
        // show the gas used and call result.
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
                insertFileAppend(consensusNs, body.getFileAppend(), transactionType);
            } else if (body.hasFileCreate()) {
                insertFileData(consensusNs, body.getFileCreate().getContents().toByteArray(),
                        txRecord.getReceipt().getFileID(), transactionType);
            } else if (body.hasFileUpdate()) {
                insertFileUpdate(consensusNs, body.getFileUpdate(), transactionType);
            }
        }

        entityListener.onTransaction(tx);
        log.debug("Storing transaction: {}", tx);
    }

    private Transaction buildTransaction(long consensusTimestamp, RecordItem recordItem) {
        Transaction tx = new Transaction();
        TransactionBody body = recordItem.getTransactionBody();
        TransactionRecord txRecord = recordItem.getRecord();
        tx.setChargedTxFee(txRecord.getTransactionFee());
        tx.setConsensusNs(consensusTimestamp);
        tx.setMemo(body.getMemoBytes().toByteArray());
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
        // transactions in stream always have valid node account id and payer account id.
        var nodeAccount = EntityId.of(body.getNodeAccountID());
        tx.setNodeAccountId(nodeAccount);
        entityListener.onEntityId(nodeAccount);
        var payerAccount = EntityId.of(body.getTransactionID().getAccountID());
        tx.setPayerAccountId(payerAccount);
        entityListener.onEntityId(payerAccount);
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
                        new NonFeeTransfer(consensusTimestamp, aa.getAmount(), EntityId.of(aa.getAccountID())));
            }
        }
    }

    private void insertConsensusTopicMessage(ConsensusSubmitMessageTransactionBody transactionBody,
                                             TransactionRecord transactionRecord) {
        var receipt = transactionRecord.getReceipt();
        var topicId = transactionBody.getTopicID();
        int runningHashVersion = receipt.getTopicRunningHashVersion() == 0 ? 1 : (int) receipt
                .getTopicRunningHashVersion();
        TopicMessage topicMessage = new TopicMessage();

        // Handle optional fragmented topic message
        if (transactionBody.hasChunkInfo()) {
            ConsensusMessageChunkInfo chunkInfo = transactionBody.getChunkInfo();
            topicMessage.setChunkNum(chunkInfo.getNumber());
            topicMessage.setChunkTotal(chunkInfo.getTotal());

            if (chunkInfo.hasInitialTransactionID()) {
                TransactionID transactionID = chunkInfo.getInitialTransactionID();
                topicMessage.setPayerAccountId(EntityId.of(transactionID.getAccountID()));
                topicMessage
                        .setValidStartTimestamp(Utility.timestampInNanosMax(transactionID.getTransactionValidStart()));
            }
        }

        topicMessage.setConsensusTimestamp(Utility.timeStampInNanos(transactionRecord.getConsensusTimestamp()));
        topicMessage.setMessage(transactionBody.getMessage().toByteArray());
        topicMessage.setRealmNum((int) topicId.getRealmNum());
        topicMessage.setRunningHash(receipt.getTopicRunningHash().toByteArray());
        topicMessage.setRunningHashVersion(runningHashVersion);
        topicMessage.setSequenceNumber(receipt.getTopicSequenceNumber());
        topicMessage.setTopicNum((int) topicId.getTopicNum());
        entityListener.onTopicMessage(topicMessage);
    }

    private void insertFileAppend(long consensusTimestamp, FileAppendTransactionBody transactionBody,
                                  int transactionType) {
        byte[] contents = transactionBody.getContents().toByteArray();
        insertFileData(consensusTimestamp, contents, transactionBody.getFileID(), transactionType);
    }

    private void insertFileUpdate(long consensusTimestamp, FileUpdateTransactionBody transactionBody,
                                  int transactionType) {
        byte[] contents = transactionBody.getContents().toByteArray();
        insertFileData(consensusTimestamp, contents, transactionBody.getFileID(), transactionType);
    }

    private void insertFileData(long consensusTimestamp, byte[] contents, FileID fileID, int transactionTypeEnum) {
        EntityId entityId = EntityId.of(fileID);
        FileData fileData = new FileData(consensusTimestamp, contents, entityId, transactionTypeEnum);

        if (addressBookService.isAddressBook(entityId)) {
            // if address book allow immediate persistence instead of waiting for batch
            addressBookService.update(fileData);
        } else {
            if (entityProperties.getPersist().isFiles() ||
                    (entityProperties.getPersist().isSystemFiles() && entityId.getEntityNum() < 1000)) {
                entityListener.onFileData(fileData);
            }
        }
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
        if (entityProperties.getPersist().isContracts() && transactionRecord.hasContractCallResult()) {
            byte[] functionParams = transactionBody.getFunctionParameters().toByteArray();
            long gasSupplied = transactionBody.getGas();
            byte[] callResult = transactionRecord.getContractCallResult().toByteArray();
            long gasUsed = transactionRecord.getContractCallResult().getGasUsed();
            insertContractResults(consensusTimestamp, functionParams, gasSupplied, callResult, gasUsed);
        }
    }

    private void insertContractCreateInstance(long consensusTimestamp,
                                              ContractCreateTransactionBody transactionBody,
                                              TransactionRecord transactionRecord) {
        if (entityProperties.getPersist().isContracts() && transactionRecord.hasContractCreateResult()) {
            byte[] functionParams = transactionBody.getConstructorParameters().toByteArray();
            long gasSupplied = transactionBody.getGas();
            byte[] callResult = transactionRecord.getContractCreateResult().toByteArray();
            long gasUsed = transactionRecord.getContractCreateResult().getGasUsed();
            insertContractResults(consensusTimestamp, functionParams, gasSupplied, callResult, gasUsed);
        }
    }

    private void insertTransferList(long consensusTimestamp, TransferList transferList) {
        for (int i = 0; i < transferList.getAccountAmountsCount(); ++i) {
            var aa = transferList.getAccountAmounts(i);
            var account = EntityId.of(aa.getAccountID());
            entityListener.onEntityId(account);
            entityListener.onCryptoTransfer(new CryptoTransfer(consensusTimestamp, aa.getAmount(), account));
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
            var account = EntityId.of(aa.getAccountID());
            entityListener.onEntityId(account);
            entityListener.onCryptoTransfer(new CryptoTransfer(consensusTimestamp, aa.getAmount(), account));

            if (addInitialBalance && (initialBalance == aa.getAmount())
                    && (account.getEntityNum() == createdAccountNum)) {
                addInitialBalance = false;
            }
        }

        if (addInitialBalance) {
            var payerAccount = EntityId.of(body.getTransactionID().getAccountID());
            var createdAccount = EntityId.of(txRecord.getReceipt().getAccountID());
            entityListener.onEntityId(payerAccount);
            entityListener.onEntityId(createdAccount);
            entityListener.onCryptoTransfer(new CryptoTransfer(consensusTimestamp, -initialBalance, payerAccount));
            entityListener.onCryptoTransfer(new CryptoTransfer(consensusTimestamp, initialBalance, createdAccount));
        }
    }

    private void insertContractResults(
            long consensusTimestamp, byte[] functionParams, long gasSupplied, byte[] callResult, long gasUsed) {
        entityListener.onContractResult(
                new ContractResult(consensusTimestamp, functionParams, gasSupplied, callResult, gasUsed));
    }

    /**
     * @param entityId entity to be updated. Should not be null.
     * @return entity associated with the transaction. Entity is guaranteed to be persisted in repo.
     */
    private void updateEntity(
            RecordItem recordItem, TransactionHandler transactionHandler, EntityId entityId) {
        // TODO: remove lookup and batch this update with rest of the db operations. Options: upsert.
        Entities entity = entityRepository.findById(entityId.getId())
                .orElseGet(entityId::toEntity);
        transactionHandler.updateEntity(entity, recordItem);
        EntityId autoRenewAccount = transactionHandler.getAutoRenewAccount(recordItem);
        if (autoRenewAccount != null) {
            entityListener.onEntityId(autoRenewAccount);
            entity.setAutoRenewAccountId(autoRenewAccount);
        }
        // Stream contains transactions with proxyAccountID explicitly set to '0.0.0'. However it's not a valid entity,
        // so no need to persist it to repo.
        EntityId proxyAccount = transactionHandler.getProxyAccount(recordItem);
        if (proxyAccount != null) {
            entityListener.onEntityId(proxyAccount);
            entity.setProxyAccountId(proxyAccount);
        }
        entityRepository.save(entity);
    }
}
