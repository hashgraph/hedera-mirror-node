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
import com.hederahashgraph.api.proto.java.ConsensusMessageChunkInfo;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoAddLiveHashTransactionBody;
import com.hederahashgraph.api.proto.java.FileAppendTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenBurnTransactionBody;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
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
import java.util.Optional;
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
import com.hedera.mirror.importer.domain.Token;
import com.hedera.mirror.importer.domain.TokenAccount;
import com.hedera.mirror.importer.domain.TokenFreezeStatusEnum;
import com.hedera.mirror.importer.domain.TokenKycStatusEnum;
import com.hedera.mirror.importer.domain.TokenTransfer;
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
import com.hedera.mirror.importer.repository.TokenAccountRepository;
import com.hedera.mirror.importer.repository.TokenRepository;
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
    private final TokenRepository tokenRepository;
    private final TokenAccountRepository tokenAccountRepository;
    private static final String MISSING_TOKEN_MESSAGE = "Missing token entity {}, unable to persist transaction type " +
            "{} with timestamp {}";
    private static final String MISSING_TOKEN_ACCOUNT_MESSAGE = "Missing token_account for token {} and account {}, " +
            "unable to persist transaction type {} with timestamp {}";

    public EntityRecordItemListener(CommonParserProperties commonParserProperties, EntityProperties entityProperties,
                                    AddressBookService addressBookService, EntityRepository entityRepository,
                                    NonFeeTransferExtractionStrategy nonFeeTransfersExtractor,
                                    EntityListener entityListener,
                                    TransactionHandlerFactory transactionHandlerFactory,
                                    TokenRepository tokenRepository, TokenAccountRepository tokenAccountRepository) {
        this.entityProperties = entityProperties;
        this.addressBookService = addressBookService;
        this.entityRepository = entityRepository;
        this.nonFeeTransfersExtractor = nonFeeTransfersExtractor;
        this.entityListener = entityListener;
        this.transactionHandlerFactory = transactionHandlerFactory;
        transactionFilter = commonParserProperties.getFilter();
        this.tokenRepository = tokenRepository;
        this.tokenAccountRepository = tokenAccountRepository;
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
            } else if (body.hasTokenGrantKyc()) {
                insertTokenAccountGrantKyc(recordItem);
            } else if (body.hasTokenMint()) {
                insertTokenMint(recordItem);
            } else if (body.hasTokenRevokeKyc()) {
                insertTokenAccountRevokeKyc(recordItem);
            } else if (body.hasTokenTransfers()) {
                insertTokenTransfers(recordItem);
            } else if (body.hasTokenUnfreeze()) {
                insertTokenAccountUnfreeze(recordItem);
            } else if (body.hasTokenUpdate()) {
                insertTokenUpdate(recordItem);
            } else if (body.hasTokenWipe()) {
                insertTokenAccountWipe(recordItem);
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
            entityListener.onEntityId(account);
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
            entityListener.onEntityId(account);
            entityListener.onCryptoTransfer(new CryptoTransfer(consensusTimestamp, aa.getAmount(), account));

            // Don't manually add an initial balance transfer if the transfer list contains it already
            if (initialBalance == aa.getAmount() && createdAccount.equals(account)) {
                addInitialBalance = false;
            }
        }

        if (addInitialBalance) {
            var payerAccount = EntityId.of(body.getTransactionID().getAccountID());
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

    private void insertTokenAssociate(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            TokenAssociateTransactionBody tokenAssociateTransactionBody = recordItem.getTransactionBody()
                    .getTokenAssociate();
            AccountID accountID = tokenAssociateTransactionBody.getAccount();

            tokenAssociateTransactionBody.getTokensList().forEach(token -> {
                entityListener.onEntityId(EntityId.of(token));

                long consensusTimeStamp = recordItem.getConsensusTimestamp();
                Optional<TokenAccount> optionalTokenAccount = retrieveTokenAccount(token, accountID,
                        TransactionTypeEnum.TOKENASSOCIATE, consensusTimeStamp);
                TokenAccount tokenAccount = null;
                if (optionalTokenAccount.isPresent()) {
                    tokenAccount = optionalTokenAccount.get();
                } else {
                    // if not onboarded create TokenAccount based on Token details
                    EntityId tokenID = EntityId.of(token);
                    Optional<Token> optionalToken = retrieveToken(token, TransactionTypeEnum.TOKENASSOCIATE,
                            consensusTimeStamp);
                    if (optionalToken.isPresent()) {
                        Token storedToken = optionalToken.get();
                        tokenAccount = new TokenAccount(tokenID, EntityId.of(accountID));
                        tokenAccount.setCreatedTimestamp(consensusTimeStamp);
                        tokenAccount.setFreezeStatus(storedToken.getNewAccountFreezeStatus());
                        tokenAccount.setKycStatus(storedToken.getNewAccountKycStatus());
                    } else {
                        return;
                    }
                }

                tokenAccount.setAssociated(true);
                tokenAccount.setModifiedTimestamp(consensusTimeStamp);
                entityListener.onTokenAccount(tokenAccount);
            });
        }
    }

    private void insertTokenBurn(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            TokenBurnTransactionBody tokenBurnTransactionBody = recordItem.getTransactionBody().getTokenBurn();
            TokenID tokenID = tokenBurnTransactionBody.getToken();
            long consensusTimeStamp = recordItem.getConsensusTimestamp();

            Optional<Token> optionalToken = retrieveToken(tokenID, TransactionTypeEnum.TOKENBURN, consensusTimeStamp);
            if (optionalToken.isPresent()) {
                Token token = optionalToken.get();
                token.setModifiedTimestamp(consensusTimeStamp);
                // mirror will calculate new totalSupply as an interim solution until network returns it
                token.setTotalSupply(token.getTotalSupply() - tokenBurnTransactionBody.getAmount());

                entityListener.onToken(token);
            }
        }
    }

    private void insertTokenCreate(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            // pull token details from TokenCreation body and TokenId from receipt
            TokenCreateTransactionBody tokenCreateTransactionBody = recordItem.getTransactionBody().getTokenCreation();
            long consensusTimeStamp = recordItem.getConsensusTimestamp();
            Token token = new Token();
            token.setCreatedTimestamp(consensusTimeStamp);
            token.setDecimals(tokenCreateTransactionBody.getDecimals());
            token.setFreezeDefault(tokenCreateTransactionBody.getFreezeDefault());
            token.setInitialSupply(tokenCreateTransactionBody.getInitialSupply());
            token.setModifiedTimestamp(consensusTimeStamp);
            token.setName(tokenCreateTransactionBody.getName());
            token.setSymbol(tokenCreateTransactionBody.getSymbol());
            token.setTokenId(new Token.Id(EntityId.of(recordItem.getRecord().getReceipt().getTokenId())));

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
                entityListener.onEntityId(treasuryEntityId);
                token.setTreasuryAccountId(treasuryEntityId);
            }

            if (tokenCreateTransactionBody.hasWipeKey()) {
                token.setWipeKey(tokenCreateTransactionBody.getWipeKey().toByteArray());
            }

            entityListener.onToken(token);
        }
    }

    private void insertTokenDissociate(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            TokenDissociateTransactionBody tokenDissociateTransactionBody = recordItem.getTransactionBody()
                    .getTokenDissociate();
            AccountID accountID = tokenDissociateTransactionBody.getAccount();

            tokenDissociateTransactionBody.getTokensList().forEach(token -> {
                entityListener.onEntityId(EntityId.of(token));

                long consensusTimeStamp = recordItem.getConsensusTimestamp();
                Optional<TokenAccount> optionalTokenAccount = retrieveTokenAccount(token, accountID,
                        TransactionTypeEnum.TOKENDISSOCIATE, consensusTimeStamp);
                if (optionalTokenAccount.isPresent()) {
                    TokenAccount tokenAccount = optionalTokenAccount.get();
                    tokenAccount.setAssociated(false);
                    tokenAccount.setModifiedTimestamp(consensusTimeStamp);
                    entityListener.onTokenAccount(tokenAccount);
                }
            });
        }
    }

    private void insertTokenAccountFreezeBody(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            TokenFreezeAccountTransactionBody tokenFreezeAccountTransactionBody = recordItem.getTransactionBody()
                    .getTokenFreeze();
            TokenID tokenID = tokenFreezeAccountTransactionBody.getToken();
            AccountID accountID = tokenFreezeAccountTransactionBody.getAccount();
            entityListener.onEntityId(EntityId.of(tokenID));

            long consensusTimeStamp = recordItem.getConsensusTimestamp();
            Optional<TokenAccount> optionalTokenAccount = retrieveTokenAccount(tokenID, accountID,
                    TransactionTypeEnum.TOKENFREEZE, consensusTimeStamp);
            if (optionalTokenAccount.isPresent()) {
                TokenAccount tokenAccount = optionalTokenAccount.get();
                tokenAccount.setFreezeStatus(TokenFreezeStatusEnum.FROZEN);
                tokenAccount.setModifiedTimestamp(consensusTimeStamp);
                entityListener.onTokenAccount(tokenAccount);
            }
        }
    }

    private void insertTokenAccountGrantKyc(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            TokenGrantKycTransactionBody tokenGrantKycTransactionBody = recordItem.getTransactionBody()
                    .getTokenGrantKyc();
            TokenID tokenID = tokenGrantKycTransactionBody.getToken();
            AccountID accountID = tokenGrantKycTransactionBody.getAccount();
            entityListener.onEntityId(EntityId.of(tokenID));

            long consensusTimeStamp = recordItem.getConsensusTimestamp();
            Optional<TokenAccount> optionalTokenAccount = retrieveTokenAccount(tokenID, accountID,
                    TransactionTypeEnum.TOKENGRANTKYC, consensusTimeStamp);
            if (optionalTokenAccount.isPresent()) {
                TokenAccount tokenAccount = optionalTokenAccount.get();
                tokenAccount.setKycStatus(TokenKycStatusEnum.GRANTED);
                tokenAccount.setModifiedTimestamp(consensusTimeStamp);
                entityListener.onTokenAccount(tokenAccount);
            }
        }
    }

    private void insertTokenMint(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            TokenMintTransactionBody tokenMintTransactionBody = recordItem.getTransactionBody().getTokenMint();
            TokenID tokenID = tokenMintTransactionBody.getToken();
            long consensusTimeStamp = recordItem.getConsensusTimestamp();

            Optional<Token> optionalToken = retrieveToken(tokenID, TransactionTypeEnum.TOKENMINT, consensusTimeStamp);
            if (optionalToken.isPresent()) {
                Token token = optionalToken.get();
                token.setModifiedTimestamp(consensusTimeStamp);
                // mirror will calculate new totalSupply as an interim solution until network returns it
                token.setTotalSupply(token.getTotalSupply() + tokenMintTransactionBody.getAmount());

                entityListener.onToken(token);
            }
        }
    }

    private void insertTokenAccountRevokeKyc(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            TokenRevokeKycTransactionBody tokenRevokeKycTransactionBody = recordItem.getTransactionBody()
                    .getTokenRevokeKyc();
            TokenID tokenID = tokenRevokeKycTransactionBody.getToken();
            AccountID accountID = tokenRevokeKycTransactionBody.getAccount();
            entityListener.onEntityId(EntityId.of(tokenID));

            long consensusTimeStamp = recordItem.getConsensusTimestamp();
            Optional<TokenAccount> optionalTokenAccount = retrieveTokenAccount(tokenID, accountID,
                    TransactionTypeEnum.TOKENREVOKEKYC, consensusTimeStamp);
            if (optionalTokenAccount.isPresent()) {
                TokenAccount tokenAccount = optionalTokenAccount.get();
                tokenAccount.setKycStatus(TokenKycStatusEnum.REVOKED);
                tokenAccount.setModifiedTimestamp(consensusTimeStamp);
                entityListener.onTokenAccount(tokenAccount);
            }
        }
    }

    private void insertTokenTransfers(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            recordItem.getRecord().getTokenTransferListsList().forEach(tokenTransferList -> {
                EntityId tokenId = EntityId.of(tokenTransferList.getToken());
                entityListener.onEntityId(tokenId);

                long consensusTimeStamp = recordItem.getConsensusTimestamp();
                tokenTransferList.getTransfersList().forEach(accountAmount -> {
                    EntityId accountId = EntityId.of(accountAmount.getAccountID());
                    entityListener.onEntityId(accountId);

                    entityListener.onTokenTransfer(new TokenTransfer(consensusTimeStamp, accountAmount
                            .getAmount(), tokenId, accountId));
                });
            });
        }
    }

    private void insertTokenUpdate(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            TokenUpdateTransactionBody tokenUpdateTransactionBody = recordItem.getTransactionBody().getTokenUpdate();
            TokenID tokenID = tokenUpdateTransactionBody.getToken();

            long consensusTimestamp = recordItem.getConsensusTimestamp();
            Optional<Token> optionalToken = retrieveToken(tokenID, TransactionTypeEnum.TOKENDELETION,
                    consensusTimestamp);
            if (optionalToken.isPresent()) {
                Token token = optionalToken.get();
                token.setModifiedTimestamp(recordItem.getConsensusTimestamp());

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
                    entityListener.onEntityId(treasuryEntityId);
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
    }

    private void insertTokenAccountUnfreeze(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            TokenUnfreezeAccountTransactionBody tokenUnfreezeAccountTransactionBody = recordItem.getTransactionBody()
                    .getTokenUnfreeze();
            TokenID tokenID = tokenUnfreezeAccountTransactionBody.getToken();
            AccountID accountID = tokenUnfreezeAccountTransactionBody.getAccount();
            entityListener.onEntityId(EntityId.of(tokenID));

            long consensusTimeStamp = recordItem.getConsensusTimestamp();
            Optional<TokenAccount> optionalTokenAccount = retrieveTokenAccount(tokenID, accountID,
                    TransactionTypeEnum.TOKENFREEZE, consensusTimeStamp);
            if (optionalTokenAccount.isPresent()) {
                TokenAccount tokenAccount = optionalTokenAccount.get();
                tokenAccount.setFreezeStatus(TokenFreezeStatusEnum.UNFROZEN);
                tokenAccount.setModifiedTimestamp(consensusTimeStamp);
                entityListener.onTokenAccount(tokenAccount);
            }
        }
    }

    private void insertTokenAccountWipe(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            TokenWipeAccountTransactionBody tokenWipeAccountTransactionBody = recordItem.getTransactionBody()
                    .getTokenWipe();
            TokenID tokenID = tokenWipeAccountTransactionBody.getToken();
            long consensusTimeStamp = recordItem.getConsensusTimestamp();

            Optional<Token> optionalToken = retrieveToken(tokenID, TransactionTypeEnum.TOKENWIPE, consensusTimeStamp);
            if (optionalToken.isPresent()) {
                Token token = optionalToken.get();
                token.setModifiedTimestamp(consensusTimeStamp);
                // mirror will calculate new totalSupply as an interim solution until network returns it
                token.setTotalSupply(token.getTotalSupply() - tokenWipeAccountTransactionBody.getAmount());

                entityListener.onToken(token);
            }
        }
    }

    private Optional<TokenAccount> retrieveTokenAccount(TokenID tokenID, AccountID accountID,
                                                        TransactionTypeEnum transactionTypeEnum,
                                                        long currentTransactionTimestamp) {
        Optional<TokenAccount> optionalTokenAccount = tokenAccountRepository
                .findByTokenIdAndAccountId(EntityId.of(tokenID).getId(), EntityId.of(accountID).getId());

        if (optionalTokenAccount.isEmpty()) {
            log.warn(MISSING_TOKEN_ACCOUNT_MESSAGE, tokenID, accountID, transactionTypeEnum,
                    currentTransactionTimestamp);
        }

        return optionalTokenAccount;
    }

    private Optional<Token> retrieveToken(TokenID tokenID, TransactionTypeEnum transactionTypeEnum,
                                          long currentTransactionTimestamp) {
        Optional<Token> optionalToken = tokenRepository
                .findById(new Token.Id(EntityId.of(tokenID)));

        if (optionalToken.isEmpty()) {
            log.warn(MISSING_TOKEN_MESSAGE, tokenID, transactionTypeEnum, currentTransactionTimestamp);
        }

        return optionalToken;
    }
}
