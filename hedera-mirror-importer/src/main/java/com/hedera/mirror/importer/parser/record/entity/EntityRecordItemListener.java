package com.hedera.mirror.importer.parser.record.entity;

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

import static com.hedera.mirror.importer.util.Utility.RECOVERABLE_ERROR;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.google.protobuf.UnknownFieldSet;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.ConsensusMessageChunkInfo;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoAddLiveHashTransactionBody;
import com.hederahashgraph.api.proto.java.FileAppendTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.FractionalFee;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.RoyaltyFee;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenAssociation;
import com.hederahashgraph.api.proto.java.TokenBurnTransactionBody;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenFeeScheduleUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenFreezeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TokenGrantKycTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TokenPauseTransactionBody;
import com.hederahashgraph.api.proto.java.TokenRevokeKycTransactionBody;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TokenUnfreezeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TokenUnpauseTransactionBody;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.file.FileData;
import com.hedera.mirror.common.domain.schedule.Schedule;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.token.NftTransferId;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenAccount;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenId;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.common.domain.token.TokenPauseStatusEnum;
import com.hedera.mirror.common.domain.token.TokenSupplyTypeEnum;
import com.hedera.mirror.common.domain.token.TokenTransfer;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.common.domain.topic.TopicMessage;
import com.hedera.mirror.common.domain.transaction.AssessedCustomFee;
import com.hedera.mirror.common.domain.transaction.CryptoTransfer;
import com.hedera.mirror.common.domain.transaction.CustomFee;
import com.hedera.mirror.common.domain.transaction.ErrataType;
import com.hedera.mirror.common.domain.transaction.LiveHash;
import com.hedera.mirror.common.domain.transaction.NonFeeTransfer;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.StakingRewardTransfer;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionSignature;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hedera.mirror.importer.domain.ContractResultService;
import com.hedera.mirror.importer.domain.EntityIdService;
import com.hedera.mirror.importer.domain.TransactionFilterFields;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.parser.CommonParserProperties;
import com.hedera.mirror.importer.parser.record.NonFeeTransferExtractionStrategy;
import com.hedera.mirror.importer.parser.record.RecordItemListener;
import com.hedera.mirror.importer.parser.record.transactionhandler.TransactionHandler;
import com.hedera.mirror.importer.parser.record.transactionhandler.TransactionHandlerFactory;
import com.hedera.mirror.importer.repository.FileDataRepository;

@Log4j2
@Named
@ConditionOnEntityRecordParser
@RequiredArgsConstructor
public class EntityRecordItemListener implements RecordItemListener {

    private final AddressBookService addressBookService;
    private final CommonParserProperties commonParserProperties;
    private final ContractResultService contractResultService;
    private final EntityIdService entityIdService;
    private final EntityListener entityListener;
    private final EntityProperties entityProperties;
    private final FileDataRepository fileDataRepository;
    private final NonFeeTransferExtractionStrategy nonFeeTransfersExtractor;
    private final TransactionHandlerFactory transactionHandlerFactory;

    @Override
    public void onItem(RecordItem recordItem) throws ImporterException {
        TransactionRecord txRecord = recordItem.getTransactionRecord();
        int transactionTypeValue = recordItem.getTransactionType();
        TransactionType transactionType = TransactionType.of(transactionTypeValue);
        TransactionHandler transactionHandler = transactionHandlerFactory.get(transactionType);

        long consensusTimestamp = DomainUtils.timeStampInNanos(txRecord.getConsensusTimestamp());
        var entityId = transactionHandler.getEntity(recordItem);

        // to:do - exclude Freeze from Filter transaction type
        TransactionFilterFields transactionFilterFields = getTransactionFilterFields(entityId, recordItem);
        Collection<EntityId> entities = transactionFilterFields.getEntities();
        log.debug("Processing {} transaction {} for entities {}", transactionType, consensusTimestamp, entities);
        if (!commonParserProperties.getFilter().test(transactionFilterFields)) {
            log.debug("Ignoring transaction. consensusTimestamp={}, transactionType={}, entities={}",
                    consensusTimestamp, transactionType, entities);
            return;
        }

        Transaction transaction = buildTransaction(consensusTimestamp, recordItem);
        transaction.setEntityId(entityId);
        transactionHandler.updateTransaction(transaction, recordItem);

        // Insert transfers even on failure
        insertTransferList(recordItem);
        insertStakingRewardTransfers(recordItem);

        // handle scheduled transaction, even on failure
        if (transaction.isScheduled()) {
            onScheduledTransaction(recordItem);
        }

        if (recordItem.isSuccessful()) {
            if (entityProperties.getPersist().getTransactionSignatures().contains(transactionType)) {
                var validSignatures = insertTransactionSignatures(
                        transaction.getEntityId(),
                        recordItem.getConsensusTimestamp(),
                        recordItem.getSignatureMap().getSigPairList());

                if (!validSignatures) {
                    return;
                }
            }

            // Only add non-fee transfers on success as the data is assured to be valid
            processNonFeeTransfers(consensusTimestamp, recordItem);
            processTransaction(recordItem);
        }

        var status = recordItem.getTransactionRecord().getReceipt().getStatus();

        // Errata records can fail with FAIL_INVALID but still have items in the record committed to state.
        if (recordItem.isSuccessful() || status == ResponseCodeEnum.FAIL_INVALID) {
            insertAutomaticTokenAssociations(recordItem);
            // Record token transfers can be populated for multiple transaction types
            insertTokenTransfers(recordItem);
            insertAssessedCustomFees(recordItem);
        }

        contractResultService.process(recordItem, transaction);

        entityListener.onTransaction(transaction);
        log.debug("Storing transaction: {}", transaction);
    }

    // reduce Cognitive Complexity of onItem() by moving the "dispatcher" part of the code into a separate method.
    // This still has Cognitive Complexity of 19, but it really doesn't make sense to split into two parts.
    @SuppressWarnings("java:S3776")
    private void processTransaction(RecordItem recordItem) {
        TransactionRecord txRecord = recordItem.getTransactionRecord();
        int transactionTypeValue = recordItem.getTransactionType();
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        TransactionBody body = recordItem.getTransactionBody();

        if (body.hasConsensusSubmitMessage()) {
            insertConsensusTopicMessage(recordItem);
        } else if (body.hasCryptoAddLiveHash()) {
            insertCryptoAddLiveHash(consensusTimestamp, body.getCryptoAddLiveHash());
        } else if (body.hasFileAppend()) {
            insertFileAppend(consensusTimestamp, body.getFileAppend(), transactionTypeValue);
        } else if (body.hasFileCreate()) {
            insertFileData(consensusTimestamp, DomainUtils.toBytes(body.getFileCreate().getContents()),
                    txRecord.getReceipt().getFileID(), transactionTypeValue);
        } else if (body.hasFileUpdate()) {
            insertFileUpdate(consensusTimestamp, body.getFileUpdate(), transactionTypeValue);
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
        }
    }

    private Transaction buildTransaction(long consensusTimestamp, RecordItem recordItem) {
        TransactionBody body = recordItem.getTransactionBody();
        TransactionRecord txRecord = recordItem.getTransactionRecord();

        Long validDurationSeconds = body.hasTransactionValidDuration() ?
                body.getTransactionValidDuration().getSeconds() : null;
        // transactions in stream always have valid node account id.
        var nodeAccount = EntityId.of(body.getNodeAccountID());
        var transactionId = body.getTransactionID();

        // build transaction
        Transaction transaction = new Transaction();
        transaction.setChargedTxFee(txRecord.getTransactionFee());
        transaction.setConsensusTimestamp(consensusTimestamp);
        transaction.setIndex(recordItem.getTransactionIndex());
        transaction.setInitialBalance(0L);
        transaction.setMaxFee(body.getTransactionFee());
        transaction.setMemo(DomainUtils.toBytes(body.getMemoBytes()));
        transaction.setNodeAccountId(nodeAccount);
        transaction.setNonce(transactionId.getNonce());
        transaction.setPayerAccountId(recordItem.getPayerAccountId());
        transaction.setResult(txRecord.getReceipt().getStatusValue());
        transaction.setScheduled(txRecord.hasScheduleRef());
        transaction.setTransactionBytes(entityProperties.getPersist().isTransactionBytes() ?
                recordItem.getTransactionBytes() : null);
        transaction.setTransactionHash(DomainUtils.toBytes(txRecord.getTransactionHash()));
        transaction.setType(recordItem.getTransactionType());
        transaction.setValidDurationSeconds(validDurationSeconds);
        transaction.setValidStartNs(DomainUtils.timeStampInNanos(transactionId.getTransactionValidStart()));

        if (txRecord.hasParentConsensusTimestamp()) {
            transaction.setParentConsensusTimestamp(
                    DomainUtils.timestampInNanosMax(txRecord.getParentConsensusTimestamp()));
        }

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
        var transactionRecord = recordItem.getTransactionRecord();
        for (var aa : nonFeeTransfersExtractor.extractNonFeeTransfers(body, transactionRecord)) {
            if (aa.getAmount() != 0) {
                var entityId = entityIdService.lookup(aa.getAccountID());
                if (EntityId.isEmpty(entityId)) {
                    continue;
                }

                NonFeeTransfer nonFeeTransfer = new NonFeeTransfer();
                nonFeeTransfer.setAmount(aa.getAmount());
                nonFeeTransfer.setConsensusTimestamp(consensusTimestamp);
                nonFeeTransfer.setEntityId(entityId);
                nonFeeTransfer.setIsApproval(aa.getIsApproval());
                nonFeeTransfer.setPayerAccountId(recordItem.getPayerAccountId());
                entityListener.onNonFeeTransfer(nonFeeTransfer);
            }
        }
    }

    private void insertConsensusTopicMessage(RecordItem recordItem) {
        if (!entityProperties.getPersist().isTopics()) {
            return;
        }

        ConsensusSubmitMessageTransactionBody transactionBody = recordItem.getTransactionBody()
                .getConsensusSubmitMessage();
        TransactionRecord transactionRecord = recordItem.getTransactionRecord();
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
                topicMessage.setInitialTransactionId(chunkInfo.getInitialTransactionID().toByteArray());
            }
        }

        topicMessage.setConsensusTimestamp(DomainUtils.timeStampInNanos(transactionRecord.getConsensusTimestamp()));
        topicMessage.setMessage(DomainUtils.toBytes(transactionBody.getMessage()));
        topicMessage.setPayerAccountId(recordItem.getPayerAccountId());
        topicMessage.setRunningHash(DomainUtils.toBytes(receipt.getTopicRunningHash()));
        topicMessage.setRunningHashVersion(runningHashVersion);
        topicMessage.setSequenceNumber(receipt.getTopicSequenceNumber());
        topicMessage.setTopicId(EntityId.of(topicId));
        entityListener.onTopicMessage(topicMessage);
    }

    private void insertFileAppend(long consensusTimestamp, FileAppendTransactionBody transactionBody,
                                  int transactionType) {
        byte[] contents = DomainUtils.toBytes(transactionBody.getContents());
        insertFileData(consensusTimestamp, contents, transactionBody.getFileID(), transactionType);
    }

    private void insertFileUpdate(long consensusTimestamp, FileUpdateTransactionBody transactionBody,
                                  int transactionType) {
        byte[] contents = DomainUtils.toBytes(transactionBody.getContents());
        insertFileData(consensusTimestamp, contents, transactionBody.getFileID(), transactionType);
    }

    private void insertFileData(long consensusTimestamp, byte[] contents, FileID fileID, int transactionTypeValue) {
        EntityId entityId = EntityId.of(fileID);
        FileData fileData = new FileData(consensusTimestamp, contents, entityId, transactionTypeValue);

        // We always store file data for address books since they're used by the address book service
        if (addressBookService.isAddressBook(entityId)) {
            fileDataRepository.save(fileData);
            addressBookService.update(fileData);
        } else if (entityProperties.getPersist().isFiles() ||
                (entityProperties.getPersist().isSystemFiles() && entityId.getEntityNum() < 1000)) {
            entityListener.onFileData(fileData);
        }
    }

    private void insertCryptoAddLiveHash(long consensusTimestamp, CryptoAddLiveHashTransactionBody transactionBody) {
        if (entityProperties.getPersist().isClaims()) {
            LiveHash liveHash = new LiveHash();
            liveHash.setConsensusTimestamp(consensusTimestamp);
            liveHash.setLivehash(DomainUtils.toBytes(transactionBody.getLiveHash().getHash()));
            entityListener.onLiveHash(liveHash);
        }
    }

    private void insertStakingRewardTransfers(RecordItem recordItem) {
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        var payerAccountId = recordItem.getPayerAccountId();

        for (var aa : recordItem.getTransactionRecord().getPaidStakingRewardsList()) {
            var accountId = EntityId.of(aa.getAccountID());
            var stakingRewardTransfer = new StakingRewardTransfer();
            stakingRewardTransfer.setAccountId(accountId.getId());
            stakingRewardTransfer.setAmount(aa.getAmount());
            stakingRewardTransfer.setConsensusTimestamp(consensusTimestamp);
            stakingRewardTransfer.setPayerAccountId(payerAccountId);
            entityListener.onStakingRewardTransfer(stakingRewardTransfer);
        }
    }

    /*
     * Extracts crypto transfers from the record. The extra logic around 'failedTransfer' is to detect and remove
     * spurious non-fee transfers that occurred due to a services bug in the past as documented in
     * ErrataMigration.spuriousTransfers().
     */
    private void insertTransferList(RecordItem recordItem) {
        var transactionRecord = recordItem.getTransactionRecord();
        if (!transactionRecord.hasTransferList() || !entityProperties.getPersist().isCryptoTransferAmounts()) {
            return;
        }

        long consensusTimestamp = recordItem.getConsensusTimestamp();
        var transferList = transactionRecord.getTransferList();
        EntityId payerAccountId = recordItem.getPayerAccountId();
        var body = recordItem.getTransactionBody();
        boolean failedTransfer =
                !recordItem.isSuccessful() && body.hasCryptoTransfer() && consensusTimestamp < 1577836799000000000L;

        for (int i = 0; i < transferList.getAccountAmountsCount(); ++i) {
            var aa = transferList.getAccountAmounts(i);
            var account = EntityId.of(aa.getAccountID());
            CryptoTransfer cryptoTransfer = new CryptoTransfer();
            cryptoTransfer.setAmount(aa.getAmount());
            cryptoTransfer.setConsensusTimestamp(consensusTimestamp);
            cryptoTransfer.setEntityId(account.getId());
            cryptoTransfer.setIsApproval(false);
            cryptoTransfer.setPayerAccountId(payerAccountId);

            AccountAmount accountAmountInsideBody = null;
            if (cryptoTransfer.getAmount() < 0 || failedTransfer) {
                accountAmountInsideBody = findAccountAmount(aa, body);
            }

            if (accountAmountInsideBody != null) {
                cryptoTransfer.setIsApproval(accountAmountInsideBody.getIsApproval());
                if (failedTransfer) {
                    cryptoTransfer.setErrata(ErrataType.DELETE);
                }
            }
            entityListener.onCryptoTransfer(cryptoTransfer);
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
                    recordItem.getTransactionRecord().getReceipt().getNewTotalSupply(),
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
        EntityId tokenId = EntityId.of(recordItem.getTransactionRecord().getReceipt().getTokenID());
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
        if (recordItem.getTransactionRecord().getAutomaticTokenAssociationsCount() > 0) {
            // automatic_token_associations does not exist prior to services 0.18.0
            autoAssociatedAccounts.clear();
            recordItem.getTransactionRecord().getAutomaticTokenAssociationsList().stream()
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
        TokenAccount tokenAccount = new TokenAccount();
        tokenAccount.setAccountId(accountId.getId());
        tokenAccount.setAssociated(true);
        tokenAccount.setAutomaticAssociation(automaticAssociation);
        tokenAccount.setCreatedTimestamp(consensusTimestamp);
        tokenAccount.setFreezeStatus(freezeStatus);
        tokenAccount.setKycStatus(kycStatus);
        tokenAccount.setTimestampRange(Range.atLeast(consensusTimestamp));
        tokenAccount.setTokenId(tokenId.getId());
        return tokenAccount;
    }

    private void insertTokenDissociate(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            TokenDissociateTransactionBody tokenDissociateTransactionBody = recordItem.getTransactionBody()
                    .getTokenDissociate();
            EntityId accountId = EntityId.of(tokenDissociateTransactionBody.getAccount());

            tokenDissociateTransactionBody.getTokensList().forEach(token -> {
                EntityId tokenId = EntityId.of(token);
                TokenAccount tokenAccount = new TokenAccount();
                tokenAccount.setAccountId(accountId.getId());
                tokenAccount.setAssociated(false);
                tokenAccount.setTimestampRange(Range.atLeast(recordItem.getConsensusTimestamp()));
                tokenAccount.setTokenId(tokenId.getId());
                entityListener.onTokenAccount(tokenAccount);
            });
        }
    }

    private void insertTokenAccountFreezeBody(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            TokenFreezeAccountTransactionBody transactionBody = recordItem.getTransactionBody().getTokenFreeze();
            EntityId tokenId = EntityId.of(transactionBody.getToken());

            EntityId accountId = EntityId.of(transactionBody.getAccount());
            TokenAccount tokenAccount = new TokenAccount();
            tokenAccount.setAccountId(accountId.getId());
            tokenAccount.setFreezeStatus(TokenFreezeStatusEnum.FROZEN);
            tokenAccount.setTimestampRange(Range.atLeast(recordItem.getConsensusTimestamp()));
            tokenAccount.setTokenId(tokenId.getId());
            entityListener.onTokenAccount(tokenAccount);
        }
    }

    private void insertTokenAccountGrantKyc(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            TokenGrantKycTransactionBody transactionBody = recordItem.getTransactionBody().getTokenGrantKyc();
            EntityId tokenId = EntityId.of(transactionBody.getToken());

            EntityId accountId = EntityId.of(transactionBody.getAccount());
            TokenAccount tokenAccount = new TokenAccount();
            tokenAccount.setAccountId(accountId.getId());
            tokenAccount.setKycStatus(TokenKycStatusEnum.GRANTED);
            tokenAccount.setTimestampRange(Range.atLeast(recordItem.getConsensusTimestamp()));
            tokenAccount.setTokenId(tokenId.getId());
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
                    recordItem.getTransactionRecord().getReceipt().getNewTotalSupply(),
                    consensusTimestamp);

            List<Long> serialNumbers = recordItem.getTransactionRecord().getReceipt().getSerialNumbersList();
            for (int i = 0; i < serialNumbers.size(); i++) {
                Nft nft = new Nft(serialNumbers.get(i), tokenId);
                nft.setCreatedTimestamp(consensusTimestamp);
                nft.setDeleted(false);
                nft.setMetadata(DomainUtils.toBytes(tokenMintTransactionBody.getMetadata(i)));
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

            EntityId accountId = EntityId.of(tokenRevokeKycTransactionBody.getAccount());
            TokenAccount tokenAccount = new TokenAccount();
            tokenAccount.setAccountId(accountId.getId());
            tokenAccount.setKycStatus(TokenKycStatusEnum.REVOKED);
            tokenAccount.setTimestampRange(Range.atLeast(recordItem.getConsensusTimestamp()));
            tokenAccount.setTokenId(tokenId.getId());
            entityListener.onTokenAccount(tokenAccount);
        }
    }

    private AccountAmount findAccountAmount(AccountAmount aa, TransactionBody body) {
        if (!body.hasCryptoTransfer()) {
            return null;
        }
        List<AccountAmount> accountAmountsList = body.getCryptoTransfer().getTransfers().getAccountAmountsList();
        for (AccountAmount a : accountAmountsList) {
            if (aa.getAmount() == a.getAmount() && aa.getAccountID().equals(a.getAccountID())) {
                return a;
            }
        }
        return null;
    }

    private AccountAmount findAccountAmount(Predicate<AccountAmount> accountAmountPredicate, TokenID tokenId,
                                            TransactionBody body) {
        if (!body.hasCryptoTransfer()) {
            return null;
        }
        List<TokenTransferList> tokenTransfersLists = body.getCryptoTransfer().getTokenTransfersList();
        for (TokenTransferList transferList : tokenTransfersLists) {
            if (!transferList.getToken().equals(tokenId)) {
                continue;
            }
            for (AccountAmount aa : transferList.getTransfersList()) {
                if (accountAmountPredicate.test(aa)) {
                    return aa;
                }
            }
        }
        return null;
    }

    private com.hederahashgraph.api.proto.java.NftTransfer findNftTransferInsideBody(
            com.hederahashgraph.api.proto.java.NftTransfer nftTransfer,
            TokenID nftId,
            TransactionBody body) {
        if (!body.hasCryptoTransfer()) {
            return null;
        }
        List<TokenTransferList> tokenTransfersList = body.getCryptoTransfer().getTokenTransfersList();
        for (TokenTransferList transferList : tokenTransfersList) {
            if (!transferList.getToken().equals(nftId)) {
                continue;
            }
            for (NftTransfer transfer : transferList.getNftTransfersList()) {
                if (transfer.getSerialNumber() == nftTransfer.getSerialNumber() &&
                        transfer.getReceiverAccountID().equals(nftTransfer.getReceiverAccountID()) &&
                        transfer.getSenderAccountID().equals(nftTransfer.getSenderAccountID())) {
                    return transfer;
                }
            }
        }
        return null;
    }

    private void insertFungibleTokenTransfers(
            long consensusTimestamp, TransactionBody body, boolean isTokenDissociate,
            TokenID tokenId, EntityId entityTokenId, EntityId payerAccountId, List<AccountAmount> tokenTransfers) {
        for (AccountAmount accountAmount : tokenTransfers) {
            EntityId accountId = EntityId.of(accountAmount.getAccountID());
            long amount = accountAmount.getAmount();
            TokenTransfer tokenTransfer = new TokenTransfer();
            tokenTransfer.setAmount(amount);
            tokenTransfer.setId(new TokenTransfer.Id(consensusTimestamp, entityTokenId, accountId));
            tokenTransfer.setIsApproval(false);
            tokenTransfer.setPayerAccountId(payerAccountId);
            tokenTransfer.setTokenDissociate(isTokenDissociate);

            // If a record AccountAmount with amount < 0 is not in the body;
            // but an AccountAmount with the same (TokenID, AccountID) combination is in the body with is_approval=true,
            // then again set is_approval=true
            if (amount < 0) {

                // Is the accountAmount from the record also inside a body's transfer list for the given tokenId?
                AccountAmount accountAmountInsideTransferList =
                        findAccountAmount(
                                accountAmount::equals, tokenId, body);
                if (accountAmountInsideTransferList == null) {

                    // Is there any account amount inside the body's transfer list for the given tokenId
                    // with the same accountId as the accountAmount from the record?
                    AccountAmount accountAmountWithSameIdInsideBody = findAccountAmount(
                            aa -> aa.getAccountID().equals(accountAmount.getAccountID()) && aa.getIsApproval(),
                            tokenId, body);
                    if (accountAmountWithSameIdInsideBody != null) {
                        tokenTransfer.setIsApproval(true);
                    }
                } else {
                    tokenTransfer.setIsApproval(accountAmountInsideTransferList.getIsApproval());
                }
            }
            entityListener.onTokenTransfer(tokenTransfer);

            if (isTokenDissociate) {
                // token transfers in token dissociate are for deleted tokens and the amount is negative to
                // bring the account's balance of the token to 0. Set the totalSupply of the token object to the
                // negative amount, later in the pipeline the token total supply will be reduced accordingly
                Token token = Token.of(entityTokenId);
                token.setModifiedTimestamp(consensusTimestamp);
                token.setTotalSupply(accountAmount.getAmount());
                entityListener.onToken(token);
            }
        }
    }

    private void insertTokenTransfers(RecordItem recordItem) {
        if (!entityProperties.getPersist().isTokens()) {
            return;
        }

        long consensusTimestamp = recordItem.getConsensusTimestamp();
        TransactionBody body = recordItem.getTransactionBody();
        boolean isTokenDissociate = body.hasTokenDissociate();

        recordItem.getTransactionRecord().getTokenTransferListsList().forEach(tokenTransferList -> {
            TokenID tokenId = tokenTransferList.getToken();
            EntityId entityTokenId = EntityId.of(tokenId);
            EntityId payerAccountId = recordItem.getPayerAccountId();

            insertFungibleTokenTransfers(
                    consensusTimestamp, body, isTokenDissociate,
                    tokenId, entityTokenId, payerAccountId,
                    tokenTransferList.getTransfersList());

            insertNonFungibleTokenTransfers(
                    consensusTimestamp, body, tokenId, entityTokenId,
                    payerAccountId, tokenTransferList.getNftTransfersList());
        });
    }

    private void insertNonFungibleTokenTransfers(
            long consensusTimestamp, TransactionBody body, TokenID tokenId,
            EntityId entityTokenId, EntityId payerAccountId,
            List<com.hederahashgraph.api.proto.java.NftTransfer> nftTransfersList) {
        for (NftTransfer nftTransfer : nftTransfersList) {
            long serialNumber = nftTransfer.getSerialNumber();
            EntityId receiverId = EntityId.of(nftTransfer.getReceiverAccountID());
            EntityId senderId = EntityId.of(nftTransfer.getSenderAccountID());

            var nftTransferDomain = new com.hedera.mirror.common.domain.token.NftTransfer();
            nftTransferDomain.setId(new NftTransferId(consensusTimestamp, serialNumber, entityTokenId));
            nftTransferDomain.setIsApproval(false);
            nftTransferDomain.setReceiverAccountId(receiverId);
            nftTransferDomain.setSenderAccountId(senderId);
            nftTransferDomain.setPayerAccountId(payerAccountId);

            var nftTransferInsideBody = findNftTransferInsideBody(nftTransfer, tokenId, body);
            if (nftTransferInsideBody != null) {
                nftTransferDomain.setIsApproval(nftTransferInsideBody.getIsApproval());
            }

            entityListener.onNftTransfer(nftTransferDomain);
            if (!EntityId.isEmpty(receiverId)) {
                transferNftOwnership(consensusTimestamp, serialNumber, entityTokenId, receiverId);
            }
        }
    }

    private void insertAutomaticTokenAssociations(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            if (recordItem.getTransactionBody().hasTokenCreation()) {
                // automatic token associations for token create transactions are handled in insertTokenCreate
                return;
            }

            long consensusTimestamp = recordItem.getConsensusTimestamp();
            recordItem.getTransactionRecord().getAutomaticTokenAssociationsList().forEach(tokenAssociation -> {
                // The accounts and tokens in the associations should have been added to EntityListener when inserting
                // the corresponding token transfers, so no need to duplicate the logic here
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
        if (!entityProperties.getPersist().isTokens()) {
            return;
        }

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
            token.setTreasuryAccountId(EntityId.of(tokenUpdateTransactionBody.getTreasury()));
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

    private void insertTokenAccountUnfreeze(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            TokenUnfreezeAccountTransactionBody tokenUnfreezeAccountTransactionBody = recordItem.getTransactionBody()
                    .getTokenUnfreeze();
            EntityId tokenId = EntityId.of(tokenUnfreezeAccountTransactionBody.getToken());
            EntityId accountId = EntityId.of(tokenUnfreezeAccountTransactionBody.getAccount());

            TokenAccount tokenAccount = new TokenAccount();
            tokenAccount.setAccountId(accountId.getId());
            tokenAccount.setFreezeStatus(TokenFreezeStatusEnum.UNFROZEN);
            tokenAccount.setTimestampRange(Range.atLeast(recordItem.getConsensusTimestamp()));
            tokenAccount.setTokenId(tokenId.getId());
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
                    recordItem.getTransactionRecord().getReceipt().getNewTotalSupply(),
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

    private boolean insertTransactionSignatures(EntityId entityId, long consensusTimestamp,
                                                List<SignaturePair> signaturePairList) {
        Set<ByteString> publicKeyPrefixes = new HashSet<>();
        for (SignaturePair signaturePair : signaturePairList) {
            ByteString prefix = signaturePair.getPubKeyPrefix();
            ByteString signature = null;
            var signatureCase = signaturePair.getSignatureCase();
            int type = signatureCase.getNumber();

            switch (signatureCase) {
                case CONTRACT:
                    signature = signaturePair.getContract();
                    break;
                case ECDSA_384:
                    signature = signaturePair.getECDSA384();
                    break;
                case ECDSA_SECP256K1:
                    signature = signaturePair.getECDSASecp256K1();
                    break;
                case ED25519:
                    signature = signaturePair.getEd25519();
                    break;
                case RSA_3072:
                    signature = signaturePair.getRSA3072();
                    break;
                case SIGNATURE_NOT_SET:
                    Map<Integer, UnknownFieldSet.Field> unknownFields = signaturePair.getUnknownFields().asMap();

                    // If we encounter a signature that our version of the protobuf does not yet support, it will
                    // return SIGNATURE_NOT_SET. Hence we should look in the unknown fields for the new signature.
                    // ByteStrings are stored as length-delimited on the wire, so we search the unknown fields for a
                    // field that has exactly one length-delimited value and assume it's our new signature bytes.
                    for (Map.Entry<Integer, UnknownFieldSet.Field> entry : unknownFields.entrySet()) {
                        UnknownFieldSet.Field field = entry.getValue();
                        if (field.getLengthDelimitedList().size() == 1) {
                            signature = field.getLengthDelimitedList().get(0);
                            type = entry.getKey();
                            break;
                        }
                    }

                    if (signature == null) {
                        log.error(RECOVERABLE_ERROR + "Unsupported signature at {}: {}", consensusTimestamp,
                                unknownFields);
                        return false;
                    }
                    break;
                default:
                    log.error(RECOVERABLE_ERROR + "Unsupported signature case at {}: {}", consensusTimestamp,
                            signaturePair.getSignatureCase());
                    return false;
            }

            // Handle potential public key prefix collisions by taking first occurrence only ignoring duplicates
            if (publicKeyPrefixes.add(prefix)) {
                TransactionSignature transactionSignature = new TransactionSignature();
                transactionSignature.setConsensusTimestamp(consensusTimestamp);
                transactionSignature.setEntityId(entityId);
                transactionSignature.setPublicKeyPrefix(DomainUtils.toBytes(prefix));
                transactionSignature.setSignature(DomainUtils.toBytes(signature));
                transactionSignature.setType(type);
                entityListener.onTransactionSignature(transactionSignature);
            }
        }

        return true;
    }

    private void onScheduledTransaction(RecordItem recordItem) {
        if (entityProperties.getPersist().isSchedules()) {
            long consensusTimestamp = recordItem.getConsensusTimestamp();
            TransactionRecord transactionRecord = recordItem.getTransactionRecord();

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
            for (var protoAssessedCustomFee : recordItem.getTransactionRecord().getAssessedCustomFeesList()) {
                EntityId collectorAccountId = EntityId.of(protoAssessedCustomFee.getFeeCollectorAccountId());
                // the effective payers must also appear in the *transfer lists of this transaction and the
                // corresponding EntityIds should have been added to EntityListener, so skip it here.
                List<EntityId> payerEntityIds = protoAssessedCustomFee.getEffectivePayerAccountIdList().stream()
                        .map(EntityId::of)
                        .toList();
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
            customFee.setAllCollectorsAreExempt(protoCustomFee.getAllCollectorsAreExempt());

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
                    log.error(RECOVERABLE_ERROR + "Invalid CustomFee FeeCase at {}: {}", consensusTimestamp,
                            feeCase);
                    continue;
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

    // regardless of transaction type, filter on entityId and payer account and transfer tokens/receivers/senders
    private TransactionFilterFields getTransactionFilterFields(EntityId entityId, RecordItem recordItem) {
        if (!commonParserProperties.hasFilter()) {
            return TransactionFilterFields.EMPTY;
        }

        var entities = new HashSet<EntityId>();
        entities.add(entityId);
        entities.add(recordItem.getPayerAccountId());

        recordItem.getTransactionRecord().getTransferList().getAccountAmountsList().forEach(accountAmount ->
                entities.add(EntityId.of(accountAmount.getAccountID()))
        );

        recordItem.getTransactionRecord().getTokenTransferListsList().forEach(transfer -> {
            entities.add(EntityId.of(transfer.getToken()));

            transfer.getTransfersList().forEach(accountAmount ->
                    entities.add(EntityId.of(accountAmount.getAccountID()))
            );

            transfer.getNftTransfersList().forEach(nftTransfer -> {
                entities.add(EntityId.of(nftTransfer.getReceiverAccountID()));
                entities.add(EntityId.of(nftTransfer.getSenderAccountID()));
            });
        });

        entities.remove(null);
        return new TransactionFilterFields(entities, TransactionType.of(recordItem.getTransactionType()));
    }
}
