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
import com.hederahashgraph.api.proto.java.ConsensusMessageChunkInfo;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoAddLiveHashTransactionBody;
import com.hederahashgraph.api.proto.java.FileAppendTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.FractionalFee;
import com.hederahashgraph.api.proto.java.RoyaltyFee;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenAssociation;
import com.hederahashgraph.api.proto.java.TokenBurnTransactionBody;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenFeeScheduleUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenFreezeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TokenGrantKycTransactionBody;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TokenPauseTransactionBody;
import com.hederahashgraph.api.proto.java.TokenRevokeKycTransactionBody;
import com.hederahashgraph.api.proto.java.TokenUnfreezeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TokenUnpauseTransactionBody;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hedera.mirror.importer.domain.AssessedCustomFee;
import com.hedera.mirror.importer.domain.CryptoTransfer;
import com.hedera.mirror.importer.domain.CustomFee;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.LiveHash;
import com.hedera.mirror.importer.domain.Nft;
import com.hedera.mirror.importer.domain.NftTransfer;
import com.hedera.mirror.importer.domain.NftTransferId;
import com.hedera.mirror.importer.domain.NonFeeTransfer;
import com.hedera.mirror.importer.domain.Schedule;
import com.hedera.mirror.importer.domain.Token;
import com.hedera.mirror.importer.domain.TokenAccount;
import com.hedera.mirror.importer.domain.TokenFreezeStatusEnum;
import com.hedera.mirror.importer.domain.TokenId;
import com.hedera.mirror.importer.domain.TokenKycStatusEnum;
import com.hedera.mirror.importer.domain.TokenPauseStatusEnum;
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
import com.hedera.mirror.importer.repository.FileDataRepository;
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
    private final FileDataRepository fileDataRepository;

    public EntityRecordItemListener(CommonParserProperties commonParserProperties, EntityProperties entityProperties,
                                    AddressBookService addressBookService,
                                    NonFeeTransferExtractionStrategy nonFeeTransfersExtractor,
                                    EntityListener entityListener,
                                    TransactionHandlerFactory transactionHandlerFactory,
                                    FileDataRepository fileDataRepository) {
        this.entityProperties = entityProperties;
        this.addressBookService = addressBookService;
        this.nonFeeTransfersExtractor = nonFeeTransfersExtractor;
        this.entityListener = entityListener;
        this.transactionHandlerFactory = transactionHandlerFactory;
        this.fileDataRepository = fileDataRepository;
        transactionFilter = commonParserProperties.getFilter();
    }

    @Override
    public void onItem(RecordItem recordItem) throws ImporterException {
        TransactionRecord txRecord = recordItem.getRecord();
        TransactionBody body = recordItem.getTransactionBody();
        int transactionType = recordItem.getTransactionType();
        TransactionTypeEnum transactionTypeEnum = TransactionTypeEnum.of(transactionType);
        TransactionHandler transactionHandler = transactionHandlerFactory.get(transactionTypeEnum);

        long consensusTimestamp = Utility.timeStampInNanos(txRecord.getConsensusTimestamp());
        EntityId entityId;
        try {
            entityId = transactionHandler.getEntity(recordItem);
        } catch (InvalidEntityException e) { // transaction can have invalid topic/contract/file id
            log.warn("Invalid entity encountered for consensusTimestamp {} : {}", consensusTimestamp, e.getMessage());
            entityId = null;
        }

        log.debug("Processing {} transaction {} for entity {}", transactionTypeEnum, consensusTimestamp, entityId);

        // to:do - exclude Freeze from Filter transaction type
        TransactionFilterFields transactionFilterFields = new TransactionFilterFields(entityId, transactionTypeEnum);
        if (!transactionFilter.test(transactionFilterFields)) {
            log.debug("Ignoring transaction. consensusTimestamp={}, transactionType={}, entityId={}",
                    consensusTimestamp, transactionTypeEnum, entityId);
            return;
        }

        Transaction transaction = buildTransaction(consensusTimestamp, recordItem);
        transaction.setEntityId(entityId);
        transactionHandler.updateTransaction(transaction, recordItem);

        if (txRecord.hasTransferList() && entityProperties.getPersist().isCryptoTransferAmounts()) {
            if (body.hasCryptoCreateAccount() && recordItem.isSuccessful()) {
                insertCryptoCreateTransferList(consensusTimestamp, recordItem);
            } else {
                insertTransferList(consensusTimestamp, txRecord.getTransferList(), recordItem.getPayerAccountId());
            }
        }

        // handle scheduled transaction, even on failure
        if (transaction.isScheduled()) {
            onScheduledTransaction(recordItem);
        }

        if (recordItem.isSuccessful()) {
            // Non null entityIds can be retrieved from transactionBody which may not yet exist on network.
            // entityIds from successful transactions are guaranteed to be valid entities on network
            // (validated to exist in pre-consensus checks).
            entityListener.onEntityId(entityId);

            if (entityProperties.getPersist().getTransactionSignatures().contains(transactionTypeEnum)) {
                insertTransactionSignatures(
                        transaction.getEntityId(),
                        recordItem.getConsensusTimestamp(),
                        recordItem.getSignatureMap().getSigPairList());
            }

            // Only add non-fee transfers on success as the data is assured to be valid
            processNonFeeTransfers(consensusTimestamp, recordItem);

            if (body.hasConsensusSubmitMessage()) {
                insertConsensusTopicMessage(body.getConsensusSubmitMessage(), txRecord);
            } else if (body.hasCryptoAddLiveHash()) {
                insertCryptoAddLiveHash(consensusTimestamp, body.getCryptoAddLiveHash());
            } else if (body.hasFileAppend()) {
                insertFileAppend(consensusTimestamp, body.getFileAppend(), transactionType);
            } else if (body.hasFileCreate()) {
                insertFileData(consensusTimestamp, Utility.toBytes(body.getFileCreate().getContents()),
                        txRecord.getReceipt().getFileID(), transactionType);
            } else if (body.hasFileUpdate()) {
                insertFileUpdate(consensusTimestamp, body.getFileUpdate(), transactionType);
            } else if (body.hasTokenAssociate()) {
                insertTokenAssociate(recordItem);
            } else if (body.hasTokenBurn()) {
                insertTokenBurn(recordItem);
            } else if (body.hasTokenCreation()) {
                insertTokenCreate(recordItem);
            } else if (body.hasTokenDissociate()) {
                insertTokenDissociate(recordItem);
            } else if (body.hasTokenFeeScheduleUpdate()) {
                insertTokenFeeScheduleUpdate(recordItem);
            } else if (body.hasTokenFreeze()) {
                insertTokenAccountFreezeBody(recordItem);
            } else if (body.hasTokenGrantKyc()) {
                insertTokenAccountGrantKyc(recordItem);
            } else if (body.hasTokenMint()) {
                insertTokenMint(recordItem);
            } else if (body.hasTokenPause()) {
                insertTokenPause(recordItem);
            } else if (body.hasTokenRevokeKyc()) {
                insertTokenAccountRevokeKyc(recordItem);
            } else if (body.hasTokenUnfreeze()) {
                insertTokenAccountUnfreeze(recordItem);
            } else if (body.hasTokenUnpause()) {
                insertTokenUnpause(recordItem);
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
            insertAutomaticTokenAssociations(recordItem);
        }

        entityListener.onTransaction(transaction);
        log.debug("Storing transaction: {}", transaction);
    }

    private Transaction buildTransaction(long consensusTimestamp, RecordItem recordItem) {
        TransactionBody body = recordItem.getTransactionBody();
        TransactionRecord txRecord = recordItem.getRecord();

        Long validDurationSeconds = body.hasTransactionValidDuration() ?
                body.getTransactionValidDuration().getSeconds() : null;
        // transactions in stream always have valid node account id.
        var nodeAccount = EntityId.of(body.getNodeAccountID());

        entityListener.onEntityId(nodeAccount);
        entityListener.onEntityId(recordItem.getPayerAccountId());

        // build transaction
        Transaction transaction = new Transaction();
        transaction.setChargedTxFee(txRecord.getTransactionFee());
        transaction.setConsensusTimestamp(consensusTimestamp);
        transaction.setInitialBalance(0L);
        transaction.setMaxFee(body.getTransactionFee());
        transaction.setMemo(Utility.toBytes(body.getMemoBytes()));
        transaction.setNodeAccountId(nodeAccount);
        transaction.setPayerAccountId(recordItem.getPayerAccountId());
        transaction.setResult(txRecord.getReceipt().getStatusValue());
        transaction.setScheduled(txRecord.hasScheduleRef());
        transaction.setTransactionBytes(entityProperties.getPersist().isTransactionBytes() ?
                recordItem.getTransactionBytes() : null);
        transaction.setTransactionHash(Utility.toBytes(txRecord.getTransactionHash()));
        transaction.setType(recordItem.getTransactionType());
        transaction.setValidDurationSeconds(validDurationSeconds);
        transaction.setValidStartNs(Utility.timeStampInNanos(body.getTransactionID().getTransactionValidStart()));

        return transaction;
    }

    /**
     * Additionally store rows in the non_fee_transactions table if applicable. This will allow the rest-api to create
     * an itemized set of transfers that reflects non-fees (explicit transfers), threshold records, node fee, and
     * network+service fee (paid to treasury).
     */
    private void processNonFeeTransfers(long consensusTimestamp, RecordItem recordItem) {
        if (!entityProperties.getPersist().isNonFeeTransfers()) {
            return;
        }

        var body = recordItem.getTransactionBody();
        var transactionRecord = recordItem.getRecord();
        for (var aa : nonFeeTransfersExtractor.extractNonFeeTransfers(body, transactionRecord)) {
            if (aa.getAmount() != 0) {
                NonFeeTransfer nonFeeTransfer = new NonFeeTransfer();
                nonFeeTransfer.setAmount(aa.getAmount());
                nonFeeTransfer.setId(new NonFeeTransfer.Id(consensusTimestamp, EntityId.of(aa.getAccountID())));
                nonFeeTransfer.setPayerAccountId(recordItem.getPayerAccountId());
                entityListener.onNonFeeTransfer(nonFeeTransfer);
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
        topicMessage.setMessage(Utility.toBytes(transactionBody.getMessage()));
        topicMessage.setRunningHash(Utility.toBytes(receipt.getTopicRunningHash()));
        topicMessage.setRunningHashVersion(runningHashVersion);
        topicMessage.setSequenceNumber(receipt.getTopicSequenceNumber());
        topicMessage.setTopicId(EntityId.of(topicId));
        entityListener.onTopicMessage(topicMessage);
    }

    private void insertFileAppend(long consensusTimestamp, FileAppendTransactionBody transactionBody,
                                  int transactionType) {
        byte[] contents = Utility.toBytes(transactionBody.getContents());
        insertFileData(consensusTimestamp, contents, transactionBody.getFileID(), transactionType);
    }

    private void insertFileUpdate(long consensusTimestamp, FileUpdateTransactionBody transactionBody,
                                  int transactionType) {
        byte[] contents = Utility.toBytes(transactionBody.getContents());
        insertFileData(consensusTimestamp, contents, transactionBody.getFileID(), transactionType);
    }

    private void insertFileData(long consensusTimestamp, byte[] contents, FileID fileID, int transactionTypeEnum) {
        EntityId entityId = EntityId.of(fileID);
        FileData fileData = new FileData(consensusTimestamp, contents, entityId, transactionTypeEnum);

        // We always store file data for address books since they're used by the address book service
        if (addressBookService.isAddressBook(entityId)) {
            fileDataRepository.save(fileData);
            addressBookService.update(fileData);
        } else if (entityProperties.getPersist().isFiles() ||
                (entityProperties.getPersist().isSystemFiles() && entityId.getEntityNum() < 1000)) {
            entityListener.onFileData(fileData);
        }
    }

    private void insertCryptoAddLiveHash(long consensusTimestamp,
                                         CryptoAddLiveHashTransactionBody transactionBody) {
        if (entityProperties.getPersist().isClaims()) {
            byte[] liveHash = Utility.toBytes(transactionBody.getLiveHash().getHash());
            entityListener.onLiveHash(new LiveHash(consensusTimestamp, liveHash));
        }
    }

    private void insertTransferList(long consensusTimestamp, TransferList transferList, EntityId payerAccountId) {
        for (int i = 0; i < transferList.getAccountAmountsCount(); ++i) {
            var aa = transferList.getAccountAmounts(i);
            var account = EntityId.of(aa.getAccountID());
            entityListener.onEntityId(account);
            CryptoTransfer cryptoTransfer = new CryptoTransfer(consensusTimestamp, aa.getAmount(), account);
            cryptoTransfer.setPayerAccountId(payerAccountId);
            entityListener.onCryptoTransfer(cryptoTransfer);
        }
    }

    private void insertCryptoCreateTransferList(long consensusTimestamp, RecordItem recordItem) {
        var record = recordItem.getRecord();
        var body = recordItem.getTransactionBody();
        long initialBalance = body.getCryptoCreateAccount().getInitialBalance();
        EntityId createdAccount = EntityId.of(record.getReceipt().getAccountID());
        boolean addInitialBalance = true;
        TransferList transferList = record.getTransferList();

        for (int i = 0; i < transferList.getAccountAmountsCount(); ++i) {
            var aa = transferList.getAccountAmounts(i);
            var account = EntityId.of(aa.getAccountID());
            entityListener.onEntityId(account);
            CryptoTransfer cryptoTransfer = new CryptoTransfer(consensusTimestamp, aa.getAmount(), account);
            cryptoTransfer.setPayerAccountId(recordItem.getPayerAccountId());
            entityListener.onCryptoTransfer(cryptoTransfer);

            // Don't manually add an initial balance transfer if the transfer list contains it already
            if (initialBalance == aa.getAmount() && createdAccount.equals(account)) {
                addInitialBalance = false;
            }
        }

        if (addInitialBalance) {
            entityListener.onEntityId(recordItem.getPayerAccountId());
            entityListener.onEntityId(createdAccount);

            CryptoTransfer transferOut = new CryptoTransfer(consensusTimestamp, -initialBalance, recordItem
                    .getPayerAccountId());
            transferOut.setPayerAccountId(recordItem.getPayerAccountId());
            entityListener.onCryptoTransfer(transferOut);

            CryptoTransfer transferIn = new CryptoTransfer(consensusTimestamp, initialBalance, createdAccount);
            transferIn.setPayerAccountId(recordItem.getPayerAccountId());
            entityListener.onCryptoTransfer(transferIn);
        }
    }

    private void insertTokenAssociate(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            TokenAssociateTransactionBody transactionBody = recordItem.getTransactionBody().getTokenAssociate();
            EntityId accountId = EntityId.of(transactionBody.getAccount());
            long consensusTimestamp = recordItem.getConsensusTimestamp();

            transactionBody.getTokensList().forEach(token -> {
                EntityId tokenId = EntityId.of(token);
                TokenAccount tokenAccount = getAssociatedTokenAccount(accountId, false, consensusTimestamp, tokenId);
                entityListener.onEntityId(tokenId);
                entityListener.onTokenAccount(tokenAccount);
            });
        }
    }

    private void insertTokenBurn(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            TokenBurnTransactionBody tokenBurnTransactionBody = recordItem.getTransactionBody().getTokenBurn();
            EntityId tokenId = EntityId.of(tokenBurnTransactionBody.getToken());
            long consensusTimestamp = recordItem.getConsensusTimestamp();

            updateTokenSupply(
                    tokenId,
                    recordItem.getRecord().getReceipt().getNewTotalSupply(),
                    consensusTimestamp);

            tokenBurnTransactionBody.getSerialNumbersList().forEach(serialNumber ->
                    updateNftDeleteStatus(consensusTimestamp, serialNumber, tokenId)
            );
        }
    }

    private void insertTokenCreate(RecordItem recordItem) {
        if (!entityProperties.getPersist().isTokens()) {
            return;
        }

        // pull token details from TokenCreation body and TokenId from receipt
        TokenCreateTransactionBody tokenCreateTransactionBody = recordItem.getTransactionBody().getTokenCreation();
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        EntityId tokenId = EntityId.of(recordItem.getRecord().getReceipt().getTokenID());
        EntityId treasury = EntityId.of(tokenCreateTransactionBody.getTreasury());
        Token token = new Token();
        token.setCreatedTimestamp(consensusTimestamp);
        token.setDecimals(tokenCreateTransactionBody.getDecimals());
        token.setFreezeDefault(tokenCreateTransactionBody.getFreezeDefault());
        token.setInitialSupply(tokenCreateTransactionBody.getInitialSupply());
        token.setMaxSupply(tokenCreateTransactionBody.getMaxSupply());
        token.setModifiedTimestamp(consensusTimestamp);
        token.setName(tokenCreateTransactionBody.getName());
        token.setSupplyType(TokenSupplyTypeEnum.fromId(tokenCreateTransactionBody.getSupplyTypeValue()));
        token.setSymbol(tokenCreateTransactionBody.getSymbol());
        token.setTokenId(new TokenId(tokenId));
        token.setTotalSupply(tokenCreateTransactionBody.getInitialSupply());
        token.setTreasuryAccountId(treasury);
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

        if (tokenCreateTransactionBody.hasPauseKey()) {
            token.setPauseKey(tokenCreateTransactionBody.getPauseKey().toByteArray());
            token.setPauseStatus(TokenPauseStatusEnum.UNPAUSED);
        } else {
            token.setPauseStatus(TokenPauseStatusEnum.NOT_APPLICABLE);
        }

        if (tokenCreateTransactionBody.hasSupplyKey()) {
            token.setSupplyKey(tokenCreateTransactionBody.getSupplyKey().toByteArray());
        }

        if (tokenCreateTransactionBody.hasWipeKey()) {
            token.setWipeKey(tokenCreateTransactionBody.getWipeKey().toByteArray());
        }

        Set<EntityId> autoAssociatedAccounts = insertCustomFees(tokenCreateTransactionBody.getCustomFeesList(),
                consensusTimestamp, true, tokenId);
        autoAssociatedAccounts.add(treasury);
        if (recordItem.getRecord().getAutomaticTokenAssociationsCount() > 0) {
            // automatic_token_associations does not exist prior to services 0.18.0
            autoAssociatedAccounts.clear();
            recordItem.getRecord().getAutomaticTokenAssociationsList().stream()
                    .map(TokenAssociation::getAccountId)
                    .map(EntityId::of)
                    .forEach(autoAssociatedAccounts::add);
        }

        TokenFreezeStatusEnum freezeStatus = token.getFreezeKey() != null ? TokenFreezeStatusEnum.UNFROZEN :
                TokenFreezeStatusEnum.NOT_APPLICABLE;
        TokenKycStatusEnum kycStatus = token.getKycKey() != null ? TokenKycStatusEnum.GRANTED :
                TokenKycStatusEnum.NOT_APPLICABLE;
        autoAssociatedAccounts.forEach(account -> {
            TokenAccount tokenAccount = getAssociatedTokenAccount(account, false, consensusTimestamp, freezeStatus,
                    kycStatus, tokenId);
            entityListener.onEntityId(account);
            entityListener.onTokenAccount(tokenAccount);
        });

        entityListener.onToken(token);
    }

    private TokenAccount getAssociatedTokenAccount(EntityId accountId, boolean autoAssociation, long consensusTimestamp,
                                                   EntityId tokenId) {
        // if null, freeze and kyc status will be set during db upsert flow
        return getAssociatedTokenAccount(accountId, autoAssociation, consensusTimestamp, null, null, tokenId);
    }

    private TokenAccount getAssociatedTokenAccount(EntityId accountId, boolean automaticAssociation,
                                                   long consensusTimestamp, TokenFreezeStatusEnum freezeStatus,
                                                   TokenKycStatusEnum kycStatus, EntityId tokenId) {
        TokenAccount tokenAccount = new TokenAccount(tokenId, accountId, consensusTimestamp);
        tokenAccount.setAssociated(true);
        tokenAccount.setAutomaticAssociation(automaticAssociation);
        tokenAccount.setCreatedTimestamp(consensusTimestamp);
        tokenAccount.setFreezeStatus(freezeStatus);
        tokenAccount.setKycStatus(kycStatus);
        return tokenAccount;
    }

    private void insertTokenDissociate(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            TokenDissociateTransactionBody tokenDissociateTransactionBody = recordItem.getTransactionBody()
                    .getTokenDissociate();
            EntityId accountId = EntityId.of(tokenDissociateTransactionBody.getAccount());
            long consensusTimestamp = recordItem.getConsensusTimestamp();

            tokenDissociateTransactionBody.getTokensList().forEach(token -> {
                EntityId tokenId = EntityId.of(token);
                entityListener.onEntityId(tokenId);

                TokenAccount tokenAccount = new TokenAccount(tokenId, accountId, consensusTimestamp);
                tokenAccount.setAssociated(false);
                entityListener.onTokenAccount(tokenAccount);
            });
        }
    }

    private void insertTokenAccountFreezeBody(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            TokenFreezeAccountTransactionBody transactionBody = recordItem.getTransactionBody().getTokenFreeze();
            EntityId tokenId = EntityId.of(transactionBody.getToken());
            entityListener.onEntityId(tokenId);

            EntityId accountId = EntityId.of(transactionBody.getAccount());
            TokenAccount tokenAccount = new TokenAccount(tokenId, accountId, recordItem.getConsensusTimestamp());
            tokenAccount.setFreezeStatus(TokenFreezeStatusEnum.FROZEN);
            entityListener.onTokenAccount(tokenAccount);
        }
    }

    private void insertTokenAccountGrantKyc(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            TokenGrantKycTransactionBody transactionBody = recordItem.getTransactionBody().getTokenGrantKyc();
            EntityId tokenId = EntityId.of(transactionBody.getToken());
            entityListener.onEntityId(tokenId);

            EntityId accountId = EntityId.of(transactionBody.getAccount());
            TokenAccount tokenAccount = new TokenAccount(tokenId, accountId, recordItem.getConsensusTimestamp());
            tokenAccount.setKycStatus(TokenKycStatusEnum.GRANTED);
            entityListener.onTokenAccount(tokenAccount);
        }
    }

    private void insertTokenMint(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            TokenMintTransactionBody tokenMintTransactionBody = recordItem.getTransactionBody().getTokenMint();
            long consensusTimestamp = recordItem.getConsensusTimestamp();
            EntityId tokenId = EntityId.of(tokenMintTransactionBody.getToken());

            updateTokenSupply(
                    tokenId,
                    recordItem.getRecord().getReceipt().getNewTotalSupply(),
                    consensusTimestamp);

            List<Long> serialNumbers = recordItem.getRecord().getReceipt().getSerialNumbersList();
            for (int i = 0; i < serialNumbers.size(); i++) {
                Nft nft = new Nft(serialNumbers.get(i), tokenId);
                nft.setCreatedTimestamp(consensusTimestamp);
                nft.setDeleted(false);
                nft.setMetadata(Utility.toBytes(tokenMintTransactionBody.getMetadata(i)));
                nft.setModifiedTimestamp(consensusTimestamp);
                entityListener.onNft(nft);
            }
        }
    }

    private void insertTokenAccountRevokeKyc(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            TokenRevokeKycTransactionBody tokenRevokeKycTransactionBody = recordItem.getTransactionBody()
                    .getTokenRevokeKyc();
            EntityId tokenId = EntityId.of(tokenRevokeKycTransactionBody.getToken());
            entityListener.onEntityId(tokenId);

            EntityId accountId = EntityId.of(tokenRevokeKycTransactionBody.getAccount());
            TokenAccount tokenAccount = new TokenAccount(tokenId, accountId, recordItem.getConsensusTimestamp());
            tokenAccount.setKycStatus(TokenKycStatusEnum.REVOKED);
            entityListener.onTokenAccount(tokenAccount);
        }
    }

    private void insertTokenTransfers(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            long consensusTimestamp = recordItem.getConsensusTimestamp();
            TransactionBody body = recordItem.getTransactionBody();
            boolean isTokenDissociate = body.hasTokenDissociate();

            recordItem.getRecord().getTokenTransferListsList().forEach(tokenTransferList -> {
                EntityId tokenId = EntityId.of(tokenTransferList.getToken());
                entityListener.onEntityId(tokenId);

                tokenTransferList.getTransfersList().forEach(accountAmount -> {
                    EntityId accountId = EntityId.of(accountAmount.getAccountID());
                    entityListener.onEntityId(accountId);

                    long amount = accountAmount.getAmount();
                    TokenTransfer tokenTransfer = new TokenTransfer();
                    tokenTransfer.setAmount(amount);
                    tokenTransfer.setId(new TokenTransfer.Id(consensusTimestamp, tokenId, accountId));
                    tokenTransfer.setPayerAccountId(recordItem.getPayerAccountId());
                    tokenTransfer.setTokenDissociate(isTokenDissociate);
                    entityListener.onTokenTransfer(tokenTransfer);

                    if (isTokenDissociate) {
                        // token transfers in token dissociate are for deleted tokens and the amount is negative to
                        // bring the account's balance of the token to 0. Set the totalSupply of the token object to the
                        // negative amount, later in the pipeline the token total supply will be reduced accordingly
                        Token token = Token.of(tokenId);
                        token.setModifiedTimestamp(consensusTimestamp);
                        token.setTotalSupply(accountAmount.getAmount());
                        entityListener.onToken(token);
                    }
                });

                tokenTransferList.getNftTransfersList().forEach(nftTransfer -> {
                    long serialNumber = nftTransfer.getSerialNumber();
                    if (serialNumber == NftTransferId.WILDCARD_SERIAL_NUMBER) {
                        // do not persist nft transfers with the wildcard serial number (-1) which signify an nft token
                        // treasury change
                        return;
                    }

                    EntityId receiverId = EntityId.of(nftTransfer.getReceiverAccountID());
                    entityListener.onEntityId(receiverId);

                    EntityId senderId = EntityId.of(nftTransfer.getSenderAccountID());
                    entityListener.onEntityId(senderId);

                    NftTransfer nftTransferDomain = new NftTransfer();
                    nftTransferDomain.setId(new NftTransferId(consensusTimestamp, serialNumber, tokenId));
                    nftTransferDomain.setReceiverAccountId(receiverId);
                    nftTransferDomain.setSenderAccountId(senderId);
                    nftTransferDomain.setPayerAccountId(recordItem.getPayerAccountId());

                    entityListener.onNftTransfer(nftTransferDomain);
                    if (!EntityId.isEmpty(receiverId)) {
                        transferNftOwnership(consensusTimestamp, serialNumber, tokenId, receiverId);
                    }
                });
            });
        }
    }

    private void insertAutomaticTokenAssociations(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            if (recordItem.getTransactionBody().hasTokenCreation()) {
                // automatic token associations for token create transactions are handled in insertTokenCreate
                return;
            }

            long consensusTimestamp = recordItem.getConsensusTimestamp();
            recordItem.getRecord().getAutomaticTokenAssociationsList().forEach(tokenAssociation -> {
                // the only other transaction which creates auto associations is crypto transfer. The accounts and
                // tokens in the associations should have been added to EntityListener when inserting the corresponding
                // token transfers, so no need to duplicate the logic here
                EntityId accountId = EntityId.of(tokenAssociation.getAccountId());
                EntityId tokenId = EntityId.of(tokenAssociation.getTokenId());
                TokenAccount tokenAccount = getAssociatedTokenAccount(accountId, true, consensusTimestamp, tokenId);
                entityListener.onTokenAccount(tokenAccount);
            });
        }
    }

    private void transferNftOwnership(long modifiedTimeStamp, long serialNumber, EntityId tokenId,
                                      EntityId receiverId) {
        Nft nft = new Nft(serialNumber, tokenId);
        nft.setAccountId(receiverId);
        nft.setModifiedTimestamp(modifiedTimeStamp);
        entityListener.onNft(nft);
    }

    private void insertTokenUpdate(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            long consensusTimestamp = recordItem.getConsensusTimestamp();
            TokenUpdateTransactionBody tokenUpdateTransactionBody = recordItem.getTransactionBody().getTokenUpdate();

            Token token = Token.of(EntityId.of(tokenUpdateTransactionBody.getToken()));

            if (tokenUpdateTransactionBody.hasFeeScheduleKey()) {
                token.setFeeScheduleKey(tokenUpdateTransactionBody.getFeeScheduleKey().toByteArray());
            }

            if (tokenUpdateTransactionBody.hasFreezeKey()) {
                token.setFreezeKey(tokenUpdateTransactionBody.getFreezeKey().toByteArray());
            }

            if (tokenUpdateTransactionBody.hasKycKey()) {
                token.setKycKey(tokenUpdateTransactionBody.getKycKey().toByteArray());
            }

            if (tokenUpdateTransactionBody.hasPauseKey()) {
                token.setPauseKey(tokenUpdateTransactionBody.getPauseKey().toByteArray());
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

            updateToken(token, consensusTimestamp);
        }
    }

    private void insertTokenAccountUnfreeze(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            TokenUnfreezeAccountTransactionBody tokenUnfreezeAccountTransactionBody = recordItem.getTransactionBody()
                    .getTokenUnfreeze();
            EntityId tokenId = EntityId.of(tokenUnfreezeAccountTransactionBody.getToken());
            EntityId accountId = EntityId.of(tokenUnfreezeAccountTransactionBody.getAccount());
            entityListener.onEntityId(tokenId);

            long consensusTimestamp = recordItem.getConsensusTimestamp();
            TokenAccount tokenAccount = new TokenAccount(tokenId, accountId, consensusTimestamp);
            tokenAccount.setFreezeStatus(TokenFreezeStatusEnum.UNFROZEN);
            entityListener.onTokenAccount(tokenAccount);
        }
    }

    private void insertTokenAccountWipe(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            TokenWipeAccountTransactionBody tokenWipeAccountTransactionBody = recordItem.getTransactionBody()
                    .getTokenWipe();
            EntityId tokenId = EntityId.of(tokenWipeAccountTransactionBody.getToken());
            long consensusTimestamp = recordItem.getConsensusTimestamp();

            updateTokenSupply(
                    tokenId,
                    recordItem.getRecord().getReceipt().getNewTotalSupply(),
                    consensusTimestamp);

            tokenWipeAccountTransactionBody.getSerialNumbersList().forEach(serialNumber ->
                    updateNftDeleteStatus(consensusTimestamp, serialNumber, tokenId));
        }
    }

    private void insertTokenFeeScheduleUpdate(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            TokenFeeScheduleUpdateTransactionBody transactionBody = recordItem.getTransactionBody()
                    .getTokenFeeScheduleUpdate();
            EntityId tokenId = EntityId.of(transactionBody.getTokenId());
            long consensusTimestamp = recordItem.getConsensusTimestamp();

            insertCustomFees(transactionBody.getCustomFeesList(), consensusTimestamp, false, tokenId);
        }
    }

    private void insertTokenPause(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            long consensusTimestamp = recordItem.getConsensusTimestamp();
            TokenPauseTransactionBody transactionBody = recordItem.getTransactionBody().getTokenPause();

            Token token = Token.of(EntityId.of(transactionBody.getToken()));
            token.setPauseStatus(TokenPauseStatusEnum.PAUSED);

            updateToken(token, consensusTimestamp);
        }
    }

    private void insertTokenUnpause(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            long consensusTimestamp = recordItem.getConsensusTimestamp();
            TokenUnpauseTransactionBody transactionBody = recordItem.getTransactionBody().getTokenUnpause();

            Token token = Token.of(EntityId.of(transactionBody.getToken()));
            token.setPauseStatus(TokenPauseStatusEnum.UNPAUSED);

            updateToken(token, consensusTimestamp);
        }
    }

    private void updateToken(Token token, long modifiedTimestamp) {
        token.setModifiedTimestamp(modifiedTimestamp);
        entityListener.onToken(token);
    }

    private void updateNftDeleteStatus(long modifiedTimeStamp, long serialNumber, EntityId tokenId) {
        Nft nft = new Nft(serialNumber, tokenId);
        nft.setDeleted(true);
        nft.setModifiedTimestamp(modifiedTimeStamp);
        entityListener.onNft(nft);
    }

    private void updateTokenSupply(EntityId tokenId, long newTotalSupply, long modifiedTimestamp) {
        Token token = Token.of(tokenId);
        token.setTotalSupply(newTotalSupply);
        updateToken(token, modifiedTimestamp);
    }

    private void insertScheduleCreate(RecordItem recordItem) {
        if (entityProperties.getPersist().isSchedules()) {
            ScheduleCreateTransactionBody scheduleCreateTransactionBody = recordItem.getTransactionBody()
                    .getScheduleCreate();
            long consensusTimestamp = recordItem.getConsensusTimestamp();
            var scheduleId = EntityId.of(recordItem.getRecord().getReceipt().getScheduleID());
            var creatorAccount = recordItem.getPayerAccountId();
            var payerAccount = creatorAccount;
            if (scheduleCreateTransactionBody.hasPayerAccountID()) {
                payerAccount = EntityId.of(scheduleCreateTransactionBody.getPayerAccountID());
                entityListener.onEntityId(payerAccount);
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
                        Utility.toBytes(prefix)));
                transactionSignature.setEntityId(entityId);
                transactionSignature.setSignature(Utility.toBytes(signaturePair.getEd25519()));

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
                // the effective payers must also appear in the *transfer lists of this transaction and the
                // corresponding EntityIds should have been added to EntityListener, so skip it here.
                List<EntityId> payerEntityIds = protoAssessedCustomFee.getEffectivePayerAccountIdList().stream()
                        .map(EntityId::of)
                        .collect(Collectors.toList());
                AssessedCustomFee assessedCustomFee = new AssessedCustomFee();
                assessedCustomFee.setAmount(protoAssessedCustomFee.getAmount());
                assessedCustomFee.setEffectivePayerEntityIds(payerEntityIds);
                assessedCustomFee.setId(new AssessedCustomFee.Id(collectorAccountId, consensusTimestamp));
                assessedCustomFee.setTokenId(EntityId.of(protoAssessedCustomFee.getTokenId()));
                assessedCustomFee.setPayerAccountId(recordItem.getPayerAccountId());
                entityListener.onAssessedCustomFee(assessedCustomFee);
            }
        }
    }

    /**
     * Inserts custom fees. Returns the list of collectors automatically associated with the newly created token if the
     * custom fees are from a token create transaction
     *
     * @param customFeeList      protobuf custom fee list
     * @param consensusTimestamp consensus timestamp of the corresponding transaction
     * @param isTokenCreate      if the transaction with the custom fees is a token create
     * @param tokenId            the token id the custom fees are attached to
     * @return A list of collectors automatically associated with the token if it's a token create transaction
     */
    private Set<EntityId> insertCustomFees(List<com.hederahashgraph.api.proto.java.CustomFee> customFeeList,
                                           long consensusTimestamp, boolean isTokenCreate, EntityId tokenId) {
        Set<EntityId> autoAssociatedAccounts = new HashSet<>();
        CustomFee.Id id = new CustomFee.Id(consensusTimestamp, tokenId);

        for (var protoCustomFee : customFeeList) {
            EntityId collector = EntityId.of(protoCustomFee.getFeeCollectorAccountId());
            CustomFee customFee = new CustomFee();
            customFee.setId(id);
            customFee.setCollectorAccountId(collector);

            var feeCase = protoCustomFee.getFeeCase();
            boolean chargedInAttachedToken;
            switch (feeCase) {
                case FIXED_FEE:
                    chargedInAttachedToken = parseFixedFee(customFee, protoCustomFee.getFixedFee(), tokenId);
                    break;
                case FRACTIONAL_FEE:
                    // only FT can have fractional fee
                    parseFractionalFee(customFee, protoCustomFee.getFractionalFee());
                    chargedInAttachedToken = true;
                    break;
                case ROYALTY_FEE:
                    // only NFT can have royalty fee, and fee can't be paid in NFT. Thus though royalty fee has a
                    // fixed fee fallback, the denominating token of the fixed fee can't be the NFT itself
                    parseRoyaltyFee(customFee, protoCustomFee.getRoyaltyFee(), tokenId);
                    chargedInAttachedToken = false;
                    break;
                default:
                    log.error("Invalid CustomFee FeeCase {}", feeCase);
                    throw new InvalidDatasetException(String.format("Invalid CustomFee FeeCase %s", feeCase));
            }

            if (isTokenCreate && chargedInAttachedToken) {
                // if it's from a token create transaction, and the fee is charged in the attached token, the attached
                // token and the collector should have been auto associated
                autoAssociatedAccounts.add(collector);
            }

            entityListener.onCustomFee(customFee);
        }

        if (customFeeList.isEmpty()) {
            // for empty custom fees, add a single row with only the timestamp and tokenId.
            CustomFee customFee = new CustomFee();
            customFee.setId(id);

            entityListener.onCustomFee(customFee);
        }

        return autoAssociatedAccounts;
    }

    /**
     * Parse protobuf FixedFee object to domain CustomFee object.
     *
     * @param customFee the domain CustomFee object
     * @param fixedFee  the protobuf FixedFee object
     * @param tokenId   the attached token id
     * @return whether the fee is paid in the attached token
     */
    private boolean parseFixedFee(CustomFee customFee, FixedFee fixedFee, EntityId tokenId) {
        customFee.setAmount(fixedFee.getAmount());

        if (fixedFee.hasDenominatingTokenId()) {
            EntityId denominatingTokenId = EntityId.of(fixedFee.getDenominatingTokenId());
            denominatingTokenId = denominatingTokenId == EntityId.EMPTY ? tokenId : denominatingTokenId;
            customFee.setDenominatingTokenId(denominatingTokenId);
            return denominatingTokenId.equals(tokenId);
        }

        return false;
    }

    private void parseFractionalFee(CustomFee customFee, FractionalFee fractionalFee) {
        customFee.setAmount(fractionalFee.getFractionalAmount().getNumerator());
        customFee.setAmountDenominator(fractionalFee.getFractionalAmount().getDenominator());

        long maximumAmount = fractionalFee.getMaximumAmount();
        if (maximumAmount != 0) {
            customFee.setMaximumAmount(maximumAmount);
        }

        customFee.setMinimumAmount(fractionalFee.getMinimumAmount());
        customFee.setNetOfTransfers(fractionalFee.getNetOfTransfers());
    }

    private void parseRoyaltyFee(CustomFee customFee, RoyaltyFee royaltyFee, EntityId tokenId) {
        customFee.setRoyaltyNumerator(royaltyFee.getExchangeValueFraction().getNumerator());
        customFee.setRoyaltyDenominator(royaltyFee.getExchangeValueFraction().getDenominator());

        if (royaltyFee.hasFallbackFee()) {
            parseFixedFee(customFee, royaltyFee.getFallbackFee(), tokenId);
        }
    }
}
