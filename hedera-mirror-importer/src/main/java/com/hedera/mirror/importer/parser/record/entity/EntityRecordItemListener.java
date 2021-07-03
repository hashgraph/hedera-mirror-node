package com.hedera.mirror.importer.parser.record.entity;

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

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusMessageChunkInfo;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoAddLiveHashTransactionBody;
import com.hederahashgraph.api.proto.java.FileAppendTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.FractionalFee;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenBurnTransactionBody;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenFeeScheduleUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenFreezeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TokenGrantKycTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TokenRevokeKycTransactionBody;
import com.hederahashgraph.api.proto.java.TokenUnfreezeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hedera.mirror.importer.domain.AssessedCustomFee;
import com.hedera.mirror.importer.domain.ContractResult;
import com.hedera.mirror.importer.domain.CryptoTransfer;
import com.hedera.mirror.importer.domain.CustomFee;
import com.hedera.mirror.importer.domain.Entity;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.LiveHash;
import com.hedera.mirror.importer.domain.Nft;
import com.hedera.mirror.importer.domain.NftId;
import com.hedera.mirror.importer.domain.NftTransfer;
import com.hedera.mirror.importer.domain.NftTransferId;
import com.hedera.mirror.importer.domain.NonFeeTransfer;
import com.hedera.mirror.importer.domain.Schedule;
import com.hedera.mirror.importer.domain.Token;
import com.hedera.mirror.importer.domain.TokenAccount;
import com.hedera.mirror.importer.domain.TokenFreezeStatusEnum;
import com.hedera.mirror.importer.domain.TokenId;
import com.hedera.mirror.importer.domain.TokenKycStatusEnum;
import com.hedera.mirror.importer.domain.TokenSupplyTypeEnum;
import com.hedera.mirror.importer.domain.TokenTransfer;
import com.hedera.mirror.importer.domain.TokenTypeEnum;
import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.domain.TransactionFilterFields;
import com.hedera.mirror.importer.domain.TransactionSignature;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.exception.InvalidDatasetException;
import com.hedera.mirror.importer.exception.InvalidEntityException;
import com.hedera.mirror.importer.parser.CommonParserProperties;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.parser.record.NonFeeTransferExtractionStrategy;
import com.hedera.mirror.importer.parser.record.RecordItemListener;
import com.hedera.mirror.importer.parser.record.transactionhandler.TransactionHandler;
import com.hedera.mirror.importer.parser.record.transactionhandler.TransactionHandlerFactory;
import com.hedera.mirror.importer.repository.NftRepository;
import com.hedera.mirror.importer.util.Utility;

@Log4j2
@Named
@ConditionOnEntityRecordParser
public class EntityRecordItemListener implements RecordItemListener {
    private final EntityProperties entityProperties;
    private final AddressBookService addressBookService;
    private final NonFeeTransferExtractionStrategy nonFeeTransfersExtractor;
    private final EntityListener entityListener;
    private final TransactionHandlerFactory transactionHandlerFactory;
    private final Predicate<TransactionFilterFields> transactionFilter;
    private final NftRepository nftRepository;

    public EntityRecordItemListener(CommonParserProperties commonParserProperties, EntityProperties entityProperties,
                                    AddressBookService addressBookService,
                                    NonFeeTransferExtractionStrategy nonFeeTransfersExtractor,
                                    EntityListener entityListener,
                                    TransactionHandlerFactory transactionHandlerFactory,
                                    NftRepository nftRepository) {
        this.entityProperties = entityProperties;
        this.addressBookService = addressBookService;
        this.nonFeeTransfersExtractor = nonFeeTransfersExtractor;
        this.entityListener = entityListener;
        this.transactionHandlerFactory = transactionHandlerFactory;
        this.nftRepository = nftRepository;
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

        // to:do - exclude Freeze from Filter transaction type

        TransactionFilterFields transactionFilterFields = new TransactionFilterFields(entityId, transactionTypeEnum);
        if (!transactionFilter.test(transactionFilterFields)) {
            log.debug("Ignoring transaction. consensusTimestamp={}, transactionType={}, entityId={}",
                    consensusNs, transactionTypeEnum, entityId);
            return;
        }

        boolean isSuccessful = isSuccessful(txRecord);
        Transaction tx = buildTransaction(consensusNs, recordItem);
        transactionHandler.updateTransaction(tx, recordItem);
        tx.setEntityId(entityId);

        if (txRecord.hasTransferList() && entityProperties.getPersist().isCryptoTransferAmounts()) {
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

        // handle scheduled transaction, even on failure
        if (tx.isScheduled()) {
            onScheduledTransaction(recordItem);
        }

        if (isSuccessful) {
            if (!EntityId.isEmpty(entityId)) {
                // Only insert entityId on successful transaction, both create and update transactions update entities
                if (transactionHandler.updatesEntity()) {
                    insertEntityCreateOrUpdate(recordItem, transactionHandler, entityId);
                } else {
                    // Non null entityIds can be retrieved from transactionBody which may not yet exist on network.
                    // entityIds from successful transactions are guaranteed to be valid entities on network
                    // (validated to exist in pre-consensus checks).
                    entityListener.onEntity(entityId.toEntity());
                }
            }

            if (entityProperties.getPersist().getTransactionSignatures().contains(transactionTypeEnum)) {
                insertTransactionSignatures(
                        tx.getEntityId(),
                        recordItem.getConsensusTimestamp(),
                        recordItem.getSignatureMap().getSigPairList());
            }

            // Only add non-fee transfers on success as the data is assured to be valid
            processNonFeeTransfers(consensusNs, body, txRecord);

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
            } else if (body.hasTokenAssociate()) {
                insertTokenAssociate(recordItem);
            } else if (body.hasTokenBurn()) {
                insertTokenBurn(recordItem);
            } else if (body.hasTokenCreation()) {
                insertTokenCreate(recordItem);
            } else if (body.hasTokenDissociate()) {
                insertTokenDissociate(recordItem);
            } else if (body.hasTokenFreeze()) {
                insertTokenAccountFreezeBody(recordItem);
            } else if (body.hasTokenFeeScheduleUpdate()) {
                insertTokenFeeScheduleUpdate(recordItem);
            } else if (body.hasTokenGrantKyc()) {
                insertTokenAccountGrantKyc(recordItem);
            } else if (body.hasTokenMint()) {
                insertTokenMint(recordItem);
            } else if (body.hasTokenRevokeKyc()) {
                insertTokenAccountRevokeKyc(recordItem);
            } else if (body.hasTokenUnfreeze()) {
                insertTokenAccountUnfreeze(recordItem);
            } else if (body.hasTokenUpdate()) {
                insertTokenUpdate(recordItem);
            } else if (body.hasTokenWipe()) {
                insertTokenAccountWipe(recordItem);
            } else if (body.hasScheduleCreate()) {
                insertScheduleCreate(recordItem);
            }

            // Record token transfers can be populated for multiple transaction types
            insertTokenTransfers(recordItem);
            insertAssessedCustomFees(recordItem);
        }

        entityListener.onTransaction(tx);
        log.debug("Storing transaction: {}", tx);
    }

    private Transaction buildTransaction(long consensusTimestamp, RecordItem recordItem) {
        TransactionBody body = recordItem.getTransactionBody();
        TransactionRecord txRecord = recordItem.getRecord();

        Long validDurationSeconds = body.hasTransactionValidDuration() ?
                body.getTransactionValidDuration().getSeconds() : null;
        // transactions in stream always have valid node account id and payer account id.
        var payerAccount = EntityId.of(body.getTransactionID().getAccountID());
        var nodeAccount = EntityId.of(body.getNodeAccountID());

        entityListener.onEntity(nodeAccount.toEntity());
        entityListener.onEntity(payerAccount.toEntity());

        // build transaction
        Transaction tx = new Transaction();
        tx.setChargedTxFee(txRecord.getTransactionFee());
        tx.setConsensusNs(consensusTimestamp);
        tx.setInitialBalance(0L);
        tx.setMaxFee(body.getTransactionFee());
        tx.setMemo(body.getMemoBytes().toByteArray());
        tx.setNodeAccountId(nodeAccount);
        tx.setPayerAccountId(payerAccount);
        tx.setResult(txRecord.getReceipt().getStatusValue());
        tx.setScheduled(txRecord.hasScheduleRef());
        tx.setTransactionBytes(entityProperties.getPersist().isTransactionBytes() ?
                recordItem.getTransactionBytes() : null);
        tx.setTransactionHash(txRecord.getTransactionHash().toByteArray());
        tx.setType(recordItem.getTransactionType());
        tx.setValidDurationSeconds(validDurationSeconds);
        tx.setValidStartNs(Utility.timeStampInNanos(body.getTransactionID().getTransactionValidStart()));

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
                        new NonFeeTransfer(aa.getAmount(), new NonFeeTransfer.Id(consensusTimestamp, EntityId
                                .of(aa.getAccountID()))));
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
        boolean addressBook = addressBookService.isAddressBook(entityId);

        // We always store file data for address books since they're used by the address book service
        if (addressBook || entityProperties.getPersist().isFiles() ||
                (entityProperties.getPersist().isSystemFiles() && entityId.getEntityNum() < 1000)) {
            entityListener.onFileData(fileData);
        }

        if (addressBook) {
            addressBookService.update(fileData);
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
            entityListener.onEntity(account.toEntity());
            entityListener.onCryptoTransfer(new CryptoTransfer(consensusTimestamp, aa.getAmount(), account));
        }
    }

    private void insertCryptoCreateTransferList(
            long consensusTimestamp, TransactionRecord txRecord, TransactionBody body) {

        long initialBalance = body.getCryptoCreateAccount().getInitialBalance();
        EntityId createdAccount = EntityId.of(txRecord.getReceipt().getAccountID());
        boolean addInitialBalance = true;
        TransferList transferList = txRecord.getTransferList();

        for (int i = 0; i < transferList.getAccountAmountsCount(); ++i) {
            var aa = transferList.getAccountAmounts(i);
            var account = EntityId.of(aa.getAccountID());
            entityListener.onEntity(account.toEntity());
            entityListener.onCryptoTransfer(new CryptoTransfer(consensusTimestamp, aa.getAmount(), account));

            // Don't manually add an initial balance transfer if the transfer list contains it already
            if (initialBalance == aa.getAmount() && createdAccount.equals(account)) {
                addInitialBalance = false;
            }
        }

        if (addInitialBalance) {
            var payerAccount = EntityId.of(body.getTransactionID().getAccountID());
            entityListener.onEntity(payerAccount.toEntity());
            entityListener.onEntity(createdAccount.toEntity());
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
    private void insertEntityCreateOrUpdate(
            RecordItem recordItem, TransactionHandler transactionHandler, EntityId entityId) {
        Entity entity = entityId.toEntity();
        transactionHandler.updateEntity(entity, recordItem);
        entityListener.onEntityId(entity.getAutoRenewAccountId());
        entityListener.onEntityId(entity.getProxyAccountId());
        entityListener.onEntity(entity);
    }

    private void insertTokenAssociate(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            TokenAssociateTransactionBody tokenAssociateTransactionBody = recordItem.getTransactionBody()
                    .getTokenAssociate();
            AccountID accountID = tokenAssociateTransactionBody.getAccount();

            tokenAssociateTransactionBody.getTokensList().forEach(token -> {
                EntityId tokenId = EntityId.of(token);
                entityListener.onEntity(tokenId.toEntity());

                long consensusTimeStamp = recordItem.getConsensusTimestamp();
                TokenAccount tokenAccount = new TokenAccount(tokenId, EntityId.of(accountID));
                tokenAccount.setCreatedTimestamp(consensusTimeStamp);
                // freeze and kyc status will be set during db upsert flow
                tokenAccount.setAssociated(true);
                tokenAccount.setModifiedTimestamp(consensusTimeStamp);
                entityListener.onTokenAccount(tokenAccount);
            });
        }
    }

    private void insertTokenBurn(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            TokenBurnTransactionBody tokenBurnTransactionBody = recordItem.getTransactionBody().getTokenBurn();
            EntityId tokenId = EntityId.of(tokenBurnTransactionBody.getToken());
            long consensusTimeStamp = recordItem.getConsensusTimestamp();

            updateTokenSupply(
                    tokenId,
                    recordItem.getRecord().getReceipt().getNewTotalSupply(),
                    consensusTimeStamp);

            tokenBurnTransactionBody.getSerialNumbersList().forEach(serialNumber ->
                    nftRepository.burnOrWipeNft(new NftId(serialNumber, tokenId), consensusTimeStamp)
            );
        }
    }

    private void insertTokenCreate(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            // pull token details from TokenCreation body and TokenId from receipt
            TokenCreateTransactionBody tokenCreateTransactionBody = recordItem.getTransactionBody().getTokenCreation();
            long consensusTimeStamp = recordItem.getConsensusTimestamp();
            EntityId tokenId = EntityId.of(recordItem.getRecord().getReceipt().getTokenID());
            Token token = new Token();
            token.setCreatedTimestamp(consensusTimeStamp);
            token.setDecimals(tokenCreateTransactionBody.getDecimals());
            token.setFreezeDefault(tokenCreateTransactionBody.getFreezeDefault());
            token.setInitialSupply(tokenCreateTransactionBody.getInitialSupply());
            token.setMaxSupply(tokenCreateTransactionBody.getMaxSupply());
            token.setModifiedTimestamp(consensusTimeStamp);
            token.setName(tokenCreateTransactionBody.getName());
            token.setSupplyType(TokenSupplyTypeEnum.fromId(tokenCreateTransactionBody.getSupplyTypeValue()));
            token.setSymbol(tokenCreateTransactionBody.getSymbol());
            token.setTokenId(new TokenId(tokenId));
            token.setTotalSupply(tokenCreateTransactionBody.getInitialSupply());
            token.setType(TokenTypeEnum.fromId(tokenCreateTransactionBody.getTokenTypeValue()));

            if (tokenCreateTransactionBody.hasFeeScheduleKey()) {
                token.setFeeScheduleKey(tokenCreateTransactionBody.getFeeScheduleKey().toByteArray());
            }

            if (tokenCreateTransactionBody.hasFreezeKey()) {
                token.setFreezeKey(tokenCreateTransactionBody.getFreezeKey().toByteArray());
            }

            if (tokenCreateTransactionBody.hasKycKey()) {
                token.setKycKey(tokenCreateTransactionBody.getKycKey().toByteArray());
            }

            if (tokenCreateTransactionBody.hasSupplyKey()) {
                token.setSupplyKey(tokenCreateTransactionBody.getSupplyKey().toByteArray());
            }

            if (tokenCreateTransactionBody.hasTreasury()) {
                EntityId treasuryEntityId = EntityId.of(tokenCreateTransactionBody.getTreasury());
                entityListener.onEntity(treasuryEntityId.toEntity());
                token.setTreasuryAccountId(treasuryEntityId);
            }

            if (tokenCreateTransactionBody.hasWipeKey()) {
                token.setWipeKey(tokenCreateTransactionBody.getWipeKey().toByteArray());
            }

            insertCustomFees(tokenCreateTransactionBody.getCustomFeesList(), consensusTimeStamp, tokenId);

            entityListener.onToken(token);
        }
    }

    private void insertTokenDissociate(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            TokenDissociateTransactionBody tokenDissociateTransactionBody = recordItem.getTransactionBody()
                    .getTokenDissociate();
            AccountID accountID = tokenDissociateTransactionBody.getAccount();

            tokenDissociateTransactionBody.getTokensList().forEach(token -> {
                entityListener.onEntity(EntityId.of(token).toEntity());

                long consensusTimeStamp = recordItem.getConsensusTimestamp();
                TokenAccount tokenAccount = new TokenAccount(EntityId.of(token), EntityId.of(accountID));
                tokenAccount.setAssociated(false);
                tokenAccount.setModifiedTimestamp(consensusTimeStamp);
                entityListener.onTokenAccount(tokenAccount);
            });
        }
    }

    private void insertTokenAccountFreezeBody(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            TokenFreezeAccountTransactionBody tokenFreezeAccountTransactionBody = recordItem.getTransactionBody()
                    .getTokenFreeze();
            TokenID tokenID = tokenFreezeAccountTransactionBody.getToken();
            AccountID accountID = tokenFreezeAccountTransactionBody.getAccount();
            entityListener.onEntity(EntityId.of(tokenID).toEntity());

            long consensusTimeStamp = recordItem.getConsensusTimestamp();
            TokenAccount tokenAccount = new TokenAccount(EntityId.of(tokenID), EntityId.of(accountID));
            tokenAccount.setFreezeStatus(TokenFreezeStatusEnum.FROZEN);
            tokenAccount.setModifiedTimestamp(consensusTimeStamp);
            entityListener.onTokenAccount(tokenAccount);
        }
    }

    private void insertTokenAccountGrantKyc(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            TokenGrantKycTransactionBody tokenGrantKycTransactionBody = recordItem.getTransactionBody()
                    .getTokenGrantKyc();
            TokenID tokenID = tokenGrantKycTransactionBody.getToken();
            AccountID accountID = tokenGrantKycTransactionBody.getAccount();
            entityListener.onEntity(EntityId.of(tokenID).toEntity());

            long consensusTimeStamp = recordItem.getConsensusTimestamp();
            TokenAccount tokenAccount = new TokenAccount(EntityId.of(tokenID), EntityId.of(accountID));
            tokenAccount.setKycStatus(TokenKycStatusEnum.GRANTED);
            tokenAccount.setModifiedTimestamp(consensusTimeStamp);
            entityListener.onTokenAccount(tokenAccount);
        }
    }

    private void insertTokenMint(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            TokenMintTransactionBody tokenMintTransactionBody = recordItem.getTransactionBody().getTokenMint();
            long consensusTimeStamp = recordItem.getConsensusTimestamp();
            EntityId tokenId = EntityId.of(tokenMintTransactionBody.getToken());

            updateTokenSupply(
                    tokenId,
                    recordItem.getRecord().getReceipt().getNewTotalSupply(),
                    consensusTimeStamp);

            List<Long> serialNumbers = recordItem.getRecord().getReceipt().getSerialNumbersList();
            List<Nft> nfts = new ArrayList<>();
            for (int i = 0; i < serialNumbers.size(); i++) {
                Nft nft = new Nft();
                nft.setCreatedTimestamp(consensusTimeStamp);
                nft.setId(new NftId(serialNumbers.get(i), tokenId));
                nft.setMetadata(tokenMintTransactionBody.getMetadata(i).toByteArray());
                nft.setModifiedTimestamp(consensusTimeStamp);
                nfts.add(nft);
            }
            nftRepository.saveAll(nfts);
        }
    }

    private void insertTokenAccountRevokeKyc(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            TokenRevokeKycTransactionBody tokenRevokeKycTransactionBody = recordItem.getTransactionBody()
                    .getTokenRevokeKyc();
            TokenID tokenID = tokenRevokeKycTransactionBody.getToken();
            AccountID accountID = tokenRevokeKycTransactionBody.getAccount();
            entityListener.onEntity(EntityId.of(tokenID).toEntity());

            long consensusTimeStamp = recordItem.getConsensusTimestamp();
            TokenAccount tokenAccount = new TokenAccount(EntityId.of(tokenID), EntityId.of(accountID));
            tokenAccount.setKycStatus(TokenKycStatusEnum.REVOKED);
            tokenAccount.setModifiedTimestamp(consensusTimeStamp);
            entityListener.onTokenAccount(tokenAccount);
        }
    }

    private void insertTokenTransfers(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            recordItem.getRecord().getTokenTransferListsList().forEach(tokenTransferList -> {
                EntityId tokenId = EntityId.of(tokenTransferList.getToken());
                entityListener.onEntity(tokenId.toEntity());

                long consensusTimeStamp = recordItem.getConsensusTimestamp();
                tokenTransferList.getTransfersList().forEach(accountAmount -> {
                    EntityId accountId = EntityId.of(accountAmount.getAccountID());
                    entityListener.onEntity(accountId.toEntity());

                    entityListener.onTokenTransfer(new TokenTransfer(consensusTimeStamp, accountAmount
                            .getAmount(), tokenId, accountId));
                });

                tokenTransferList.getNftTransfersList().forEach(nftTransfer -> {
                    EntityId receiverId = EntityId.of(nftTransfer.getReceiverAccountID());
                    entityListener.onEntity(receiverId.toEntity());

                    EntityId senderId = EntityId.of(nftTransfer.getSenderAccountID());
                    entityListener.onEntity(senderId.toEntity());

                    long serialNumber = nftTransfer.getSerialNumber();
                    NftTransfer nftTransferDomain = new NftTransfer();
                    nftTransferDomain.setId(new NftTransferId(consensusTimeStamp, serialNumber, tokenId));
                    nftTransferDomain.setReceiverAccountId(receiverId);
                    nftTransferDomain.setSenderAccountId(senderId);

                    entityListener.onNftTransfer(nftTransferDomain);
                    if (!EntityId.isEmpty(receiverId)) {
                        nftRepository.transferNftOwnership(new NftId(serialNumber, tokenId), receiverId,
                                consensusTimeStamp);
                    }
                });
            });
        }
    }

    private void insertTokenUpdate(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            long consensusTimestamp = recordItem.getConsensusTimestamp();
            TokenUpdateTransactionBody tokenUpdateTransactionBody = recordItem.getTransactionBody().getTokenUpdate();

            EntityId tokenId = EntityId.of(tokenUpdateTransactionBody.getToken());
            Token token = new Token();
            token.setTokenId(new TokenId(tokenId));
            token.setModifiedTimestamp(consensusTimestamp);

            if (tokenUpdateTransactionBody.hasFeeScheduleKey()) {
                token.setFeeScheduleKey(tokenUpdateTransactionBody.getFeeScheduleKey().toByteArray());
            }

            if (tokenUpdateTransactionBody.hasFreezeKey()) {
                token.setFreezeKey(tokenUpdateTransactionBody.getFreezeKey().toByteArray());
            }

            if (tokenUpdateTransactionBody.hasKycKey()) {
                token.setKycKey(tokenUpdateTransactionBody.getKycKey().toByteArray());
            }

            if (tokenUpdateTransactionBody.hasSupplyKey()) {
                token.setSupplyKey(tokenUpdateTransactionBody.getSupplyKey().toByteArray());
            }

            if (tokenUpdateTransactionBody.hasTreasury()) {
                EntityId treasuryEntityId = EntityId.of(tokenUpdateTransactionBody.getTreasury());
                entityListener.onEntity(treasuryEntityId.toEntity());
                token.setTreasuryAccountId(treasuryEntityId);
            }

            if (tokenUpdateTransactionBody.hasWipeKey()) {
                token.setWipeKey(tokenUpdateTransactionBody.getWipeKey().toByteArray());
            }

            if (!tokenUpdateTransactionBody.getName().isEmpty()) {
                token.setName(tokenUpdateTransactionBody.getName());
            }

            if (!tokenUpdateTransactionBody.getSymbol().isEmpty()) {
                token.setSymbol(tokenUpdateTransactionBody.getSymbol());
            }

            entityListener.onToken(token);
        }
    }

    private void insertTokenAccountUnfreeze(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            TokenUnfreezeAccountTransactionBody tokenUnfreezeAccountTransactionBody = recordItem.getTransactionBody()
                    .getTokenUnfreeze();
            TokenID tokenID = tokenUnfreezeAccountTransactionBody.getToken();
            AccountID accountID = tokenUnfreezeAccountTransactionBody.getAccount();
            entityListener.onEntity(EntityId.of(tokenID).toEntity());

            long consensusTimeStamp = recordItem.getConsensusTimestamp();
            TokenAccount tokenAccount = new TokenAccount(EntityId.of(tokenID), EntityId.of(accountID));
            tokenAccount.setFreezeStatus(TokenFreezeStatusEnum.UNFROZEN);
            tokenAccount.setModifiedTimestamp(consensusTimeStamp);
            entityListener.onTokenAccount(tokenAccount);
        }
    }

    private void insertTokenAccountWipe(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            TokenWipeAccountTransactionBody tokenWipeAccountTransactionBody = recordItem.getTransactionBody()
                    .getTokenWipe();
            EntityId tokenId = EntityId.of(tokenWipeAccountTransactionBody.getToken());
            long consensusTimeStamp = recordItem.getConsensusTimestamp();

            updateTokenSupply(
                    tokenId,
                    recordItem.getRecord().getReceipt().getNewTotalSupply(),
                    consensusTimeStamp);

            tokenWipeAccountTransactionBody.getSerialNumbersList().forEach(serialNumber ->
                    nftRepository.burnOrWipeNft(new NftId(serialNumber, tokenId), consensusTimeStamp));
        }
    }

    private void insertTokenFeeScheduleUpdate(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            TokenFeeScheduleUpdateTransactionBody transactionBody = recordItem.getTransactionBody()
                    .getTokenFeeScheduleUpdate();
            EntityId tokenId = EntityId.of(transactionBody.getTokenId());
            long consensusTimeStamp = recordItem.getConsensusTimestamp();

            insertCustomFees(transactionBody.getCustomFeesList(), consensusTimeStamp, tokenId);
        }
    }

    private void updateTokenSupply(EntityId tokenId, long newTotalSupply, long modifiedTimestamp) {
        Token token = new Token();
        token.setTokenId(new TokenId(tokenId));
        token.setTotalSupply(newTotalSupply);
        token.setModifiedTimestamp(modifiedTimestamp);
        entityListener.onToken(token);
    }

    private void insertScheduleCreate(RecordItem recordItem) {
        if (entityProperties.getPersist().isSchedules()) {
            ScheduleCreateTransactionBody scheduleCreateTransactionBody = recordItem.getTransactionBody()
                    .getScheduleCreate();
            long consensusTimestamp = recordItem.getConsensusTimestamp();
            var scheduleId = EntityId.of(recordItem.getRecord().getReceipt().getScheduleID());
            var creatorAccount = EntityId.of(recordItem.getTransactionBody().getTransactionID().getAccountID());
            var payerAccount = creatorAccount;
            if (scheduleCreateTransactionBody.hasPayerAccountID()) {
                payerAccount = EntityId.of(scheduleCreateTransactionBody.getPayerAccountID());
                entityListener.onEntity(payerAccount.toEntity());
            }

            Schedule schedule = new Schedule();
            schedule.setConsensusTimestamp(consensusTimestamp);
            schedule.setCreatorAccountId(creatorAccount);
            schedule.setPayerAccountId(payerAccount);
            schedule.setScheduleId(scheduleId);
            schedule.setTransactionBody(scheduleCreateTransactionBody.getScheduledTransactionBody().toByteArray());
            entityListener.onSchedule(schedule);
        }
    }

    private void insertTransactionSignatures(EntityId entityId, long consensusTimestamp,
                                             List<SignaturePair> signaturePairList) {
        HashSet<ByteString> publicKeyPrefixes = new HashSet<>();
        signaturePairList.forEach(signaturePair -> {
            // currently only Ed25519 signature is supported
            SignaturePair.SignatureCase signatureCase = signaturePair.getSignatureCase();
            if (signatureCase != SignaturePair.SignatureCase.ED25519) {
                throw new InvalidDatasetException("Unsupported signature case encountered: " + signatureCase);
            }

            // handle potential public key prefix collisions by taking first occurrence only ignoring duplicates
            ByteString prefix = signaturePair.getPubKeyPrefix();
            if (publicKeyPrefixes.add(prefix)) {
                TransactionSignature transactionSignature = new TransactionSignature();
                transactionSignature.setId(new TransactionSignature.Id(
                        consensusTimestamp,
                        prefix.toByteArray()));
                transactionSignature.setEntityId(entityId);
                transactionSignature.setSignature(signaturePair.getEd25519().toByteArray());

                entityListener.onTransactionSignature(transactionSignature);
            }
        });
    }

    private void onScheduledTransaction(RecordItem recordItem) {
        if (entityProperties.getPersist().isSchedules()) {
            long consensusTimestamp = recordItem.getConsensusTimestamp();
            TransactionRecord transactionRecord = recordItem.getRecord();

            // update schedule execute time
            Schedule schedule = new Schedule();
            schedule.setScheduleId(EntityId.of(transactionRecord.getScheduleRef()));
            schedule.setExecutedTimestamp(consensusTimestamp);
            entityListener.onSchedule(schedule);
        }
    }

    private void insertAssessedCustomFees(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            long consensusTimestamp = recordItem.getConsensusTimestamp();
            for (var protoAssessedCustomFee : recordItem.getRecord().getAssessedCustomFeesList()) {
                EntityId collectorAccountId = EntityId.of(protoAssessedCustomFee.getFeeCollectorAccountId());
                EntityId tokenId = EntityId.of(protoAssessedCustomFee.getTokenId());
                if (EntityId.isEmpty((tokenId))) {
                    tokenId = null;
                }

                AssessedCustomFee assessedCustomFee = new AssessedCustomFee();
                assessedCustomFee.setAmount(protoAssessedCustomFee.getAmount());
                assessedCustomFee.setId(new AssessedCustomFee.Id(collectorAccountId, consensusTimestamp));
                assessedCustomFee.setTokenId(tokenId);
                entityListener.onAssessedCustomFee(assessedCustomFee);
            }
        }
    }

    private void insertCustomFees(List<com.hederahashgraph.api.proto.java.CustomFee> customFeeList,
                                  long consensusTimestamp, EntityId tokenId) {
        CustomFee.Id id = new CustomFee.Id(consensusTimestamp, tokenId);

        for (var protoCustomFee : customFeeList) {
            CustomFee customFee = new CustomFee();
            customFee.setId(id);
            customFee.setCollectorAccountId(EntityId.of(protoCustomFee.getFeeCollectorAccountId()));

            switch (protoCustomFee.getFeeCase()) {
                case FIXED_FEE:
                    FixedFee fixedFee = protoCustomFee.getFixedFee();
                    customFee.setAmount(fixedFee.getAmount());
                    EntityId denominatingTokenId = EntityId.of(fixedFee.getDenominatingTokenId());
                    if (!EntityId.isEmpty(denominatingTokenId)) {
                        customFee.setDenominatingTokenId(denominatingTokenId);
                    }
                    break;
                case FRACTIONAL_FEE:
                    FractionalFee fractionalFee = protoCustomFee.getFractionalFee();
                    customFee.setAmount(fractionalFee.getFractionalAmount().getNumerator());
                    customFee.setAmountDenominator(fractionalFee.getFractionalAmount().getDenominator());

                    long maximumAmount = fractionalFee.getMaximumAmount();
                    if (maximumAmount != 0) {
                        customFee.setMaximumAmount(maximumAmount);
                    }

                    long minimumAmount = fractionalFee.getMinimumAmount();
                    if (minimumAmount != 0) {
                        customFee.setMinimumAmount(minimumAmount);
                    }

                    break;
                default:
                    break;
            }

            entityListener.onCustomFee(customFee);
        }

        if (customFeeList.isEmpty()) {
            // for empty custom fees, add a single row with only the timestamp and tokenId.
            CustomFee customFee = new CustomFee();
            customFee.setId(id);

            entityListener.onCustomFee(customFee);
        }
    }
}
