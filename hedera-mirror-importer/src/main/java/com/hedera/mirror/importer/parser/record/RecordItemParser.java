package com.hedera.mirror.importer.parser.record;

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
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoAddClaimTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.FileAppendTransactionBody;
import com.hederahashgraph.api.proto.java.FileCreateTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import java.io.IOException;
import java.util.Set;
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
import com.hedera.mirror.importer.domain.TransactionFilterFields;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.parser.CommonParserProperties;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.parser.record.transactionhandler.TransactionHandler;
import com.hedera.mirror.importer.parser.record.transactionhandler.TransactionHandlerFactory;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.util.Utility;

@Log4j2
@Named
public class RecordItemParser implements RecordItemListener {
    private final RecordParserProperties parserProperties;
    private final NetworkAddressBook networkAddressBook;
    private final EntityRepository entityRepository;
    private final NonFeeTransferExtractionStrategy nonFeeTransfersExtractor;
    private final Predicate<TransactionFilterFields> transactionFilter;
    private final RecordParsedItemHandler recordParsedItemHandler;
    private final TransactionHandlerFactory transactionHandlerFactory;

    public RecordItemParser(CommonParserProperties commonParserProperties, RecordParserProperties parserProperties,
                            NetworkAddressBook networkAddressBook, EntityRepository entityRepository,
                            NonFeeTransferExtractionStrategy nonFeeTransfersExtractor,
                            RecordParsedItemHandler recordParsedItemHandler,
                            TransactionHandlerFactory transactionHandlerFactory) {
        this.parserProperties = parserProperties;
        this.networkAddressBook = networkAddressBook;
        this.entityRepository = entityRepository;
        this.nonFeeTransfersExtractor = nonFeeTransfersExtractor;
        this.recordParsedItemHandler = recordParsedItemHandler;
        this.transactionHandlerFactory = transactionHandlerFactory;
        transactionFilter = commonParserProperties.getFilter();
    }

    public static boolean isSuccessful(TransactionRecord transactionRecord) {
        return ResponseCodeEnum.SUCCESS == transactionRecord.getReceipt().getStatus();
    }

    private static boolean isFileAddressBook(FileID fileId) {
        return (fileId.getFileNum() == 102) && (fileId.getShardNum() == 0) && (fileId.getRealmNum() == 0);
    }

    /**
     * Because body.getDataCase() can return null for unknown transaction types, we instead get oneof generically
     *
     * @param body
     * @return The protobuf ID that represents the transaction type
     */
    private static int getTransactionType(TransactionBody body) {
        TransactionBody.DataCase dataCase = body.getDataCase();

        if (dataCase == null || dataCase == TransactionBody.DataCase.DATA_NOT_SET) {
            Set<Integer> unknownFields = body.getUnknownFields().asMap().keySet();

            if (unknownFields.size() != 1) {
                throw new IllegalStateException("Unable to guess correct transaction type since there's not exactly " +
                        "one: " + unknownFields);
            }

            int transactionType = unknownFields.iterator().next();
            log.warn("Encountered unknown transaction type: {}", transactionType);
            return transactionType;
        }

        return dataCase.getNumber();
    }

    @Override
    public void onItem(RecordItem recordItem) throws ImporterException {
        TransactionRecord txRecord = recordItem.getRecord();
        TransactionBody body = recordItem.getTransactionBody();
        TransactionHandler transactionHandler = transactionHandlerFactory.create(body);
        log.trace("Storing transaction body: {}", () -> Utility.printProtoMessage(body));

        int transactionType = getTransactionType(body);
        long consensusNs = Utility.timeStampInNanos(txRecord.getConsensusTimestamp());
        EntityId entityId = transactionHandler.getEntityId(recordItem);

        TransactionFilterFields transactionFilterFields =
                new TransactionFilterFields(entityId, TransactionTypeEnum.of(transactionType));
        if (!transactionFilter.test(transactionFilterFields)) {
            log.debug("Ignoring transaction. consensusTimestamp={}, transactionType={}, entityId={}",
                    consensusNs, TransactionTypeEnum.of(transactionType), entityId);
            return;
        }

        // TODO: updatesEntity() is true for all and only the transaction types which are in body.has*() if-else
        //   conditions below. This is temporary to keep scope of changes in single PR limited and will be fixed in
        //   followup PR quickly. All if-else conditions will be replaced by:
        //     transactionHandler.updateEntity(entity, recordItem).
        Entities entity = null; // Entity used when t_entities row must be updated.
        if (transactionHandler.updatesEntity()) {
            entity = getEntity(entityId);
        }

        // Only when transaction is successful:
        // - Fields of 'entity' will be updated. Fields are not updated for failed transactions since 'entity' may be an
        //   instance from cache and reused in future.
        // - proxyAccountId/autoRenewAccountId: If present, the account's id will be looked up (from big cache)
        //   or created immediately.

        // For all transactions:
        // - 'entity' (may have been updated or not) is always inserted into repo since
        //   (1) it is guaranteed to be valid entity on network (validated to exist in pre-consensus checks)
        //   (2) fk_cud_entity_id is foreign key in t_transactions
        boolean doUpdateEntity = isSuccessful(txRecord);
        long initialBalance = 0;

        if (entity == null) {
            // Do nothing. This can be true if transaction is of type that doesn't update the entity. Or, if the
            // transaction doesn't contain non-zero entity id (i.e. with entityNum != 0).
        } else if (body.hasContractCreateInstance()) {
            if (txRecord.getReceipt().hasContractID()) { // implies SUCCESS
                ContractCreateTransactionBody txMessage = body.getContractCreateInstance();
                setProxyAccountID(txMessage.getProxyAccountID(), entity);
                if (txMessage.hasAutoRenewPeriod()) {
                    entity.setAutoRenewPeriod(txMessage.getAutoRenewPeriod().getSeconds());
                }
                // Can't clear memo on contracts. 0 length indicates no change
                if (txMessage.getMemo() != null && txMessage.getMemo().length() > 0) {
                    entity.setMemo(txMessage.getMemo());
                }

                if (txMessage.hasAdminKey()) {
                    entity.setKey(txMessage.getAdminKey().toByteArray());
                }
            }

            initialBalance = body.getContractCreateInstance().getInitialBalance();
        } else if (body.hasContractDeleteInstance()) {
            if (body.getContractDeleteInstance().hasContractID()) {
                if (doUpdateEntity) {
                    entity.setDeleted(true);
                }
            }
        } else if (body.hasContractUpdateInstance()) {
            ContractUpdateTransactionBody txMessage = body.getContractUpdateInstance();
            if (doUpdateEntity) {
                setProxyAccountID(txMessage.getProxyAccountID(), entity);
                if (txMessage.hasExpirationTime()) {
                    entity.setExpiryTimeNs(Utility.timestampInNanosMax(txMessage.getExpirationTime()));
                }
                if (txMessage.hasAutoRenewPeriod()) {
                    entity.setAutoRenewPeriod(txMessage.getAutoRenewPeriod().getSeconds());
                }
                if (txMessage.hasAdminKey()) {
                    entity.setKey(txMessage.getAdminKey().toByteArray());
                }
                // Can't clear memo on contracts. 0 length indicates no change
                if (txMessage.getMemo() != null && txMessage.getMemo().length() > 0) {
                    entity.setMemo(txMessage.getMemo());
                }
            }
        } else if (body.hasCryptoCreateAccount()) {
            if (txRecord.getReceipt().hasAccountID()) { // Implies SUCCESS
                CryptoCreateTransactionBody txMessage = body.getCryptoCreateAccount();
                setProxyAccountID(txMessage.getProxyAccountID(), entity);
                if (txMessage.hasAutoRenewPeriod()) {
                    entity.setAutoRenewPeriod(txMessage.getAutoRenewPeriod().getSeconds());
                }
                if (txMessage.hasKey()) {
                    entity.setKey(txMessage.getKey().toByteArray());
                }
            }

            initialBalance = body.getCryptoCreateAccount().getInitialBalance();
        } else if (body.hasCryptoDelete()) {
            if (body.getCryptoDelete().hasDeleteAccountID()) {
                if (doUpdateEntity) {
                    entity.setDeleted(true);
                }
            }
        } else if (body.hasCryptoUpdateAccount()) {
            CryptoUpdateTransactionBody txMessage = body.getCryptoUpdateAccount();
            if (doUpdateEntity) {
                setProxyAccountID(txMessage.getProxyAccountID(), entity);
                if (txMessage.hasExpirationTime()) {
                    entity.setExpiryTimeNs(Utility.timestampInNanosMax(txMessage.getExpirationTime()));
                }
                if (txMessage.hasAutoRenewPeriod()) {
                    entity.setAutoRenewPeriod(txMessage.getAutoRenewPeriod().getSeconds());
                }
                if (txMessage.hasKey()) {
                    entity.setKey(txMessage.getKey().toByteArray());
                }
            }
        } else if (body.hasFileCreate()) {
            if (txRecord.getReceipt().hasFileID()) { // Implies SUCCESS
                FileCreateTransactionBody txMessage = body.getFileCreate();
                if (txMessage.hasExpirationTime()) {
                    entity.setExpiryTimeNs(Utility.timestampInNanosMax(txMessage.getExpirationTime()));
                }

                if (txMessage.hasKeys()) {
                    entity.setKey(txMessage.getKeys().toByteArray());
                }
            }
        } else if (body.hasFileDelete()) {
            if (body.getFileDelete().hasFileID()) {
                if (doUpdateEntity) {
                    entity.setDeleted(true);
                }
            }
        } else if (body.hasFileUpdate()) {
            FileUpdateTransactionBody txMessage = body.getFileUpdate();
            if (doUpdateEntity) {
                if (txMessage.hasExpirationTime()) {
                    entity.setExpiryTimeNs(Utility.timestampInNanosMax(txMessage.getExpirationTime()));
                }

                if (txMessage.hasKeys()) {
                    entity.setKey(txMessage.getKeys().toByteArray());
                }
            }
        } else if (body.hasSystemDelete()) {
            if (doUpdateEntity) {
                entity.setDeleted(true);
            }
        } else if (body.hasSystemUndelete()) {
            if (doUpdateEntity) {
                entity.setDeleted(false);
            }
        } else if (body.hasConsensusCreateTopic()) {
            consensusCreateTopicUpdateEntity(entity, body, txRecord);
        } else if (body.hasConsensusUpdateTopic()) {
            consensusUpdateTopicUpdateEntity(entity, body, txRecord);
        } else if (body.hasConsensusDeleteTopic()) {
            consensusDeleteTopicUpdateEntity(entity, body, txRecord);
        }

        TransactionID transactionID = body.getTransactionID();
        long validDurationSeconds = body.hasTransactionValidDuration() ? body.getTransactionValidDuration()
                .getSeconds() : null;
        long validStartNs = Utility.timeStampInNanos(transactionID.getTransactionValidStart());
        AccountID payerAccountId = transactionID.getAccountID();

        com.hedera.mirror.importer.domain.Transaction tx = new com.hedera.mirror.importer.domain.Transaction();
        tx.setChargedTxFee(txRecord.getTransactionFee());
        tx.setConsensusNs(consensusNs);
        tx.setInitialBalance(initialBalance);
        tx.setMemo(body.getMemo().getBytes());
        tx.setMaxFee(body.getTransactionFee());
        tx.setResult(txRecord.getReceipt().getStatusValue());
        tx.setType(transactionType);
        tx.setTransactionBytes(parserProperties.getPersist().isTransactionBytes() ?
                recordItem.getTransactionBytes() : null);
        tx.setTransactionHash(txRecord.getTransactionHash().toByteArray());
        tx.setValidDurationSeconds(validDurationSeconds);
        tx.setValidStartNs(validStartNs);
        if (entity != null) {
            tx.setEntity(entity);
            entityRepository.save(entity);
        } else if (entityId != null) {
            Entities tempEntity = entityId.toEntity();
            tempEntity.setId(lookupOrCreateId(entityId)); // look up in big cache
            tx.setEntity(tempEntity);
        }  // else leave tx.entity null
        tx.setNodeAccountId(lookupOrCreateId(EntityId.of(body.getNodeAccountID())));
        tx.setPayerAccountId(lookupOrCreateId(EntityId.of(payerAccountId)));

        if ((txRecord.hasTransferList()) && parserProperties.getPersist().isCryptoTransferAmounts()) {
            processNonFeeTransfers(consensusNs, payerAccountId, body, txRecord);
            if (body.hasCryptoCreateAccount() && isSuccessful(txRecord)) {
                insertCryptoCreateTransferList(consensusNs, txRecord, body, txRecord.getReceipt()
                        .getAccountID(), payerAccountId);
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
        if (doUpdateEntity) {
            if (body.hasConsensusSubmitMessage()) {
                insertConsensusTopicMessage(body.getConsensusSubmitMessage(), txRecord);
            } else if (body.hasCryptoAddClaim()) {
                insertCryptoAddClaim(consensusNs, body.getCryptoAddClaim());
            } else if (body.hasFileAppend()) {
                insertFileAppend(consensusNs, body.getFileAppend());
            } else if (body.hasFileCreate()) {
                insertFileData(consensusNs, body.getFileCreate().getContents().toByteArray(),
                        txRecord.getReceipt().getFileID());
            } else if (body.hasFileUpdate()) {
                insertFileUpdate(consensusNs, body.getFileUpdate());
            }
        }

        recordParsedItemHandler.onTransaction(tx);
        log.debug("Storing transaction: {}", tx);
    }

    private void setProxyAccountID(AccountID proxyAccountID, Entities entity) {
        // Stream contains transactions with proxyAccountID explicitly set to '0.0.0' i.e. hasProxyAccountID returns
        // true. 0.0.0 is not a valid entity and maybe consensus nodes just ignore that value. Either ways, no need to
        // persist 0.0.0 in database on mirror node side.
        EntityId proxyAccountEntityId = EntityId.of(proxyAccountID);
        if (proxyAccountEntityId != null) {
            entity.setProxyAccountId(lookupOrCreateId(proxyAccountEntityId));
        }
    }

    /**
     * Should the given transaction/record generate non_fee_transfers based on what type the transaction is, it's
     * status, and run-time configuration concerning which situations warrant storing.
     */
    private boolean shouldStoreNonFeeTransfers(TransactionBody body) {
        if (!body.hasCryptoCreateAccount() && !body.hasContractCreateInstance() && !body.hasCryptoTransfer() && !body
                .hasContractCall()) {
            return false;
        }
        return parserProperties.getPersist().isNonFeeTransfers();
    }

    /**
     * Additionally store rows in the non_fee_transactions table if applicable. This will allow the rest-api to create
     * an itemized set of transfers that reflects non-fees (explicit transfers), threshold records, node fee, and
     * network+service fee (paid to treasury).
     */
    private void processNonFeeTransfers(long consensusTimestamp, AccountID payerAccountId,
                                        TransactionBody body, TransactionRecord transactionRecord) {
        if (!shouldStoreNonFeeTransfers(body)) {
            return;
        }

        for (var aa : nonFeeTransfersExtractor.extractNonFeeTransfers(payerAccountId, body, transactionRecord)) {
            addNonFeeTransferInserts(consensusTimestamp, aa.getAccountID().getRealmNum(),
                    aa.getAccountID().getAccountNum(), aa.getAmount());
        }
    }

    private void addNonFeeTransferInserts(long consensusTimestamp, long realm, long accountNum, long amount) {
        if (0 != amount) {
            recordParsedItemHandler.onNonFeeTransfer(
                    new NonFeeTransfer(consensusTimestamp, realm, accountNum, amount));
        }
    }

    /**
     * Store ConsensusCreateTopic transaction in the database.
     *
     * @throws IllegalArgumentException
     */
    private void consensusCreateTopicUpdateEntity(
            Entities entity, TransactionBody body, TransactionRecord transactionRecord) {
        if (!body.hasConsensusCreateTopic()) {
            throw new IllegalArgumentException("transaction is not a ConsensusCreateTopic");
        }

        if (!transactionRecord.getReceipt().hasTopicID()) { // tx not successful. Equivalent to doUpdateEntity check.
            return;
        }

        var transactionBody = body.getConsensusCreateTopic();

        if (transactionBody.hasAutoRenewAccount()) {
            // Looks up (in the big cache) or creates new id.
            entity.setAutoRenewAccountId(lookupOrCreateId(EntityId.of(transactionBody.getAutoRenewAccount())));
        }

        if (transactionBody.hasAutoRenewPeriod()) {
            entity.setAutoRenewPeriod(transactionBody.getAutoRenewPeriod().getSeconds());
        }

        // If either key is empty, they should end up as empty bytea in the DB to indicate that there is
        // explicitly no value, as opposed to null which has been used to indicate the value is unknown.
        var adminKey = transactionBody.hasAdminKey() ? transactionBody.getAdminKey().toByteArray() : new byte[0];
        var submitKey = transactionBody.hasSubmitKey() ? transactionBody.getSubmitKey().toByteArray() : new byte[0];

        entity.setMemo(transactionBody.getMemo());
        entity.setKey(adminKey);
        entity.setSubmitKey(submitKey);
    }

    /**
     * Store ConsensusUpdateTopic transaction in the database.
     *
     * @throws IllegalArgumentException
     */
    private void consensusUpdateTopicUpdateEntity(
            Entities entity, TransactionBody body, TransactionRecord transactionRecord) {
        if (!body.hasConsensusUpdateTopic()) {
            throw new IllegalArgumentException("transaction is not a ConsensusUpdateTopic");
        }

        var transactionBody = body.getConsensusUpdateTopic();
        if (!transactionBody.hasTopicID()) {
            log.warn("Encountered a ConsensusUpdateTopic transaction without topic ID: {}", body);
            return;
        }

        if (isSuccessful(transactionRecord)) {
            if (transactionBody.hasExpirationTime()) {
                Timestamp expirationTime = transactionBody.getExpirationTime();
                entity.setExpiryTimeNs(Utility.timestampInNanosMax(expirationTime));
            }

            if (transactionBody.hasAutoRenewAccount()) {
                // Looks up (in the big cache) or creates new id.
                entity.setAutoRenewAccountId(lookupOrCreateId(EntityId.of(transactionBody.getAutoRenewAccount())));
            }

            if (transactionBody.hasAutoRenewPeriod()) {
                entity.setAutoRenewPeriod(transactionBody.getAutoRenewPeriod().getSeconds());
            }

            if (transactionBody.hasAdminKey()) {
                entity.setKey(transactionBody.getAdminKey().toByteArray());
            }

            if (transactionBody.hasSubmitKey()) {
                entity.setSubmitKey(transactionBody.getSubmitKey().toByteArray());
            }

            if (transactionBody.hasMemo()) {
                entity.setMemo(transactionBody.getMemo().getValue());
            }
        }
    }

    /**
     * Store ConsensusDeleteTopic transaction in the database.
     *
     * @throws IllegalArgumentException
     */
    private void consensusDeleteTopicUpdateEntity(
            Entities entity, TransactionBody body, TransactionRecord transactionRecord) {
        if (!body.hasConsensusDeleteTopic()) {
            throw new IllegalArgumentException("transaction is not a ConsensusDeleteTopic");
        }

        var transactionBody = body.getConsensusDeleteTopic();
        if (!transactionBody.hasTopicID()) {
            log.warn("Encountered a ConsensusDeleteTopic transaction without topic ID: {}", body);
            return;
        }

        if (isSuccessful(transactionRecord)) {
            entity.setDeleted(true);
        }
    }

    private void insertConsensusTopicMessage(ConsensusSubmitMessageTransactionBody transactionBody,
                                             TransactionRecord transactionRecord) {
        var receipt = transactionRecord.getReceipt();
        var topicId = transactionBody.getTopicID();
        TopicMessage topicMessage = new TopicMessage(
                Utility.timeStampInNanos(transactionRecord.getConsensusTimestamp()),
                transactionBody.getMessage().toByteArray(), (int) topicId.getRealmNum(),
                receipt.getTopicRunningHash().toByteArray(), receipt.getTopicSequenceNumber(),
                (int) topicId.getTopicNum());
        recordParsedItemHandler.onTopicMessage(topicMessage);
    }

    private void insertFileData(long consensusTimestamp, byte[] contents, FileID fileID) {
        if (parserProperties.getPersist().isFiles() ||
                (parserProperties.getPersist().isSystemFiles() && fileID.getFileNum() < 1000)) {
            recordParsedItemHandler.onFileData(new FileData(consensusTimestamp, contents));
        }
    }

    private void insertFileAppend(long consensusTimestamp, FileAppendTransactionBody transactionBody) {
        byte[] contents = transactionBody.getContents().toByteArray();
        insertFileData(consensusTimestamp, contents, transactionBody.getFileID());
        // we have an address book update, refresh the local file
        if (isFileAddressBook(transactionBody.getFileID())) {
            try {
                networkAddressBook.append(contents);
            } catch (IOException e) {
                throw new ParserException("Error appending to network address book", e);
            }
        }
    }

    private void insertCryptoAddClaim(long consensusTimestamp,
                                      CryptoAddClaimTransactionBody transactionBody) {
        if (parserProperties.getPersist().isClaims()) {
            byte[] claim = transactionBody.getClaim().getHash().toByteArray();
            recordParsedItemHandler.onLiveHash(new LiveHash(consensusTimestamp, claim));
        }
    }

    private void insertContractCall(long consensusTimestamp,
                                    ContractCallTransactionBody transactionBody,
                                    TransactionRecord transactionRecord) {
        if (parserProperties.getPersist().isContracts()) {
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
        if (parserProperties.getPersist().isContracts()) {
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
            lookupOrCreateId(EntityId.of(aa.getAccountID())); // ensures existence of entity in db
            addCryptoTransferList(consensusTimestamp, accountId.getRealmNum(), accountId.getAccountNum(), aa
                    .getAmount());
        }
    }

    private void insertCryptoCreateTransferList(long consensusTimestamp,
                                                TransactionRecord txRecord,
                                                TransactionBody body,
                                                AccountID createdAccountId,
                                                AccountID payerAccountId) {

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
            lookupOrCreateId(EntityId.of(accountId)); // ensures existence of entity in db
            addCryptoTransferList(consensusTimestamp, accountId.getRealmNum(), accountNum, aa.getAmount());

            if (addInitialBalance && (initialBalance == aa.getAmount()) && (accountNum == createdAccountNum)) {
                addInitialBalance = false;
            }
        }

        if (addInitialBalance) {
            lookupOrCreateId(EntityId.of(payerAccountId)); // ensures existence of entity in db
            addCryptoTransferList(consensusTimestamp, payerAccountId.getRealmNum(), payerAccountId
                    .getAccountNum(), -initialBalance);
            lookupOrCreateId(EntityId.of(createdAccountId)); // ensures existence of entity in db
            addCryptoTransferList(consensusTimestamp, createdAccountId
                    .getRealmNum(), createdAccountNum, initialBalance);
        }
    }

    private void addCryptoTransferList(long consensusTimestamp, long realmNum, long accountNum, long amount) {
        recordParsedItemHandler
                .onCryptoTransferList(new CryptoTransfer(consensusTimestamp, amount, realmNum, accountNum));
    }

    private void insertFileUpdate(long consensusTimestamp, FileUpdateTransactionBody transactionBody) {
        FileID fileId = transactionBody.getFileID();
        byte[] contents = transactionBody.getContents().toByteArray();
        insertFileData(consensusTimestamp, contents, fileId);
        // we have an address book update, refresh the local file
        if (isFileAddressBook(fileId)) {
            try {
                networkAddressBook.update(contents);
            } catch (IOException e) {
                throw new ParserException("Error appending to network address book", e);
            }
        }
    }

    private void insertContractResults(
            long consensusTimestamp, byte[] functionParams, long gasSupplied, byte[] callResult, long gasUsed) {
        recordParsedItemHandler.onContractResult(
                new ContractResult(consensusTimestamp, functionParams, gasSupplied, callResult, gasUsed));
    }

    /**
     * @return entity looked up (using shard/realm/num of given entityId) from the repo. If no entity is found, then a
     * new entity is returned without being persisted to the repo.
     */
    private Entities getEntity(EntityId entityId) {
        if (entityId == null) {
            return null;
        }
        return entityRepository.findByPrimaryKey(
                entityId.getEntityShard(), entityId.getEntityRealm(), entityId.getEntityNum())
                .orElseGet(entityId::toEntity);
    }

    /**
     * @param entityId for which the id needs to be looked up (from cache/repo). If no id is found, the the entity is
     *                 inserted into the repo and the newly minted id is returned.
     * @return looked up/newly minted id of the given entityId.
     */
    public long lookupOrCreateId(EntityId entityId) {
        log.debug("lookupOrCreateId for {}", entityId);
        if (entityId.getId() != null && entityId.getId() != 0) {
            return entityId.getId();
        }
        return entityRepository.findEntityIdByNativeIds(
                entityId.getEntityShard(), entityId.getEntityRealm(), entityId.getEntityNum())
                .orElseGet(() -> entityRepository.saveAndCacheEntityId(entityId.toEntity()))
                .getId();
    }
}
