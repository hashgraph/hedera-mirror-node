/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.importer.parser.record.entity;

import static com.hedera.mirror.importer.util.Utility.RECOVERABLE_ERROR;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.google.protobuf.UnknownFieldSet;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.schedule.Schedule;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.token.NftTransferId;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenAccount;
import com.hedera.mirror.common.domain.token.TokenTransfer;
import com.hedera.mirror.common.domain.transaction.AssessedCustomFee;
import com.hedera.mirror.common.domain.transaction.CryptoTransfer;
import com.hedera.mirror.common.domain.transaction.ErrataType;
import com.hedera.mirror.common.domain.transaction.NonFeeTransfer;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.StakingRewardTransfer;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionSignature;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.exception.InvalidEntityException;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.domain.ContractResultService;
import com.hedera.mirror.importer.domain.EntityIdService;
import com.hedera.mirror.importer.domain.TransactionFilterFields;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.parser.CommonParserProperties;
import com.hedera.mirror.importer.parser.contractlog.SyntheticContractLogService;
import com.hedera.mirror.importer.parser.contractlog.TransferContractLog;
import com.hedera.mirror.importer.parser.contractlog.TransferIndexedContractLog;
import com.hedera.mirror.importer.parser.record.NonFeeTransferExtractionStrategy;
import com.hedera.mirror.importer.parser.record.RecordItemListener;
import com.hedera.mirror.importer.parser.record.transactionhandler.TransactionHandler;
import com.hedera.mirror.importer.parser.record.transactionhandler.TransactionHandlerFactory;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import jakarta.inject.Named;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Named
@ConditionOnEntityRecordParser
@RequiredArgsConstructor
public class EntityRecordItemListener implements RecordItemListener {

    private final CommonParserProperties commonParserProperties;
    private final ContractResultService contractResultService;
    private final EntityIdService entityIdService;
    private final EntityListener entityListener;
    private final EntityProperties entityProperties;
    private final NonFeeTransferExtractionStrategy nonFeeTransfersExtractor;
    private final TransactionHandlerFactory transactionHandlerFactory;
    private final SyntheticContractLogService syntheticContractLogService;

    @Override
    public void onItem(RecordItem recordItem) throws ImporterException {
        TransactionRecord txRecord = recordItem.getTransactionRecord();
        int transactionTypeValue = recordItem.getTransactionType();
        TransactionType transactionType = TransactionType.of(transactionTypeValue);
        TransactionHandler transactionHandler = transactionHandlerFactory.get(transactionType);

        long consensusTimestamp = DomainUtils.timeStampInNanos(txRecord.getConsensusTimestamp());
        EntityId entityId;
        try {
            entityId = transactionHandler.getEntity(recordItem);
        } catch (InvalidEntityException e) { // transaction can have invalid topic/contract/file id
            log.error(
                    RECOVERABLE_ERROR + "Invalid entity encountered for consensusTimestamp {} : {}",
                    consensusTimestamp,
                    e.getMessage());
            entityId = EntityId.EMPTY;
        }

        // to:do - exclude Freeze from Filter transaction type
        TransactionFilterFields transactionFilterFields = getTransactionFilterFields(entityId, recordItem);
        Collection<EntityId> entities = transactionFilterFields.getEntities();
        log.debug("Processing {} transaction {} for entities {}", transactionType, consensusTimestamp, entities);
        if (!commonParserProperties.getFilter().test(transactionFilterFields)) {
            log.debug(
                    "Ignoring transaction. consensusTimestamp={}, transactionType={}, entities={}",
                    consensusTimestamp,
                    transactionType,
                    entities);
            return;
        }

        Transaction transaction = buildTransaction(recordItem);
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
                insertTransactionSignatures(
                        transaction.getEntityId(),
                        recordItem.getConsensusTimestamp(),
                        recordItem.getSignatureMap().getSigPairList());
            }

            // Only add non-fee transfers on success as the data is assured to be valid
            processNonFeeTransfers(consensusTimestamp, recordItem);
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

    private Transaction buildTransaction(RecordItem recordItem) {
        TransactionBody body = recordItem.getTransactionBody();
        TransactionRecord txRecord = recordItem.getTransactionRecord();

        Long validDurationSeconds = body.hasTransactionValidDuration()
                ? body.getTransactionValidDuration().getSeconds()
                : null;
        // transactions in stream always have valid node account id.
        var nodeAccount = EntityId.of(body.getNodeAccountID());
        var transactionId = body.getTransactionID();

        // build transaction
        Transaction transaction = new Transaction();
        transaction.setChargedTxFee(txRecord.getTransactionFee());
        transaction.setConsensusTimestamp(recordItem.getConsensusTimestamp());
        transaction.setIndex(recordItem.getTransactionIndex());
        transaction.setInitialBalance(0L);
        transaction.setMaxFee(body.getTransactionFee());
        transaction.setMemo(DomainUtils.toBytes(body.getMemoBytes()));
        transaction.setNodeAccountId(nodeAccount);
        transaction.setNonce(transactionId.getNonce());
        transaction.setPayerAccountId(recordItem.getPayerAccountId());
        transaction.setResult(txRecord.getReceipt().getStatusValue());
        transaction.setScheduled(txRecord.hasScheduleRef());
        transaction.setTransactionBytes(
                entityProperties.getPersist().isTransactionBytes() ? recordItem.getTransactionBytes() : null);
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
                var entityId = entityIdService.lookup(aa.getAccountID()).orElse(EntityId.EMPTY);
                if (EntityId.isEmpty(entityId)) {
                    log.error(
                            RECOVERABLE_ERROR + "Invalid nonFeeTransfer entity id at {}",
                            recordItem.getConsensusTimestamp());
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
        if (!transactionRecord.hasTransferList()
                || !entityProperties.getPersist().isCryptoTransferAmounts()) {
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

    private AccountAmount findAccountAmount(AccountAmount aa, TransactionBody body) {
        if (!body.hasCryptoTransfer()) {
            return null;
        }
        List<AccountAmount> accountAmountsList =
                body.getCryptoTransfer().getTransfers().getAccountAmountsList();
        for (AccountAmount a : accountAmountsList) {
            if (aa.getAmount() == a.getAmount() && aa.getAccountID().equals(a.getAccountID())) {
                return a;
            }
        }
        return null;
    }

    private AccountAmount findAccountAmount(
            Predicate<AccountAmount> accountAmountPredicate, EntityId tokenId, TransactionBody body) {
        if (!body.hasCryptoTransfer()) {
            return null;
        }
        List<TokenTransferList> tokenTransfersLists = body.getCryptoTransfer().getTokenTransfersList();
        for (TokenTransferList transferList : tokenTransfersLists) {
            if (!EntityId.of(transferList.getToken()).equals(tokenId)) {
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
            com.hederahashgraph.api.proto.java.NftTransfer nftTransfer, TokenID nftId, TransactionBody body) {
        if (!body.hasCryptoTransfer()) {
            return null;
        }
        List<TokenTransferList> tokenTransfersList = body.getCryptoTransfer().getTokenTransfersList();
        for (TokenTransferList transferList : tokenTransfersList) {
            if (!transferList.getToken().equals(nftId)) {
                continue;
            }
            for (NftTransfer transfer : transferList.getNftTransfersList()) {
                if (transfer.getSerialNumber() == nftTransfer.getSerialNumber()
                        && transfer.getReceiverAccountID().equals(nftTransfer.getReceiverAccountID())
                        && transfer.getSenderAccountID().equals(nftTransfer.getSenderAccountID())) {
                    return transfer;
                }
            }
        }
        return null;
    }

    private void insertFungibleTokenTransfers(
            RecordItem recordItem, EntityId tokenId, EntityId payerAccountId, List<AccountAmount> tokenTransfers) {
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        TransactionBody body = recordItem.getTransactionBody();
        boolean isTokenDissociate = body.hasTokenDissociate();
        int tokenTransferCount = tokenTransfers.size();

        boolean isDeletedTokenDissociate = isTokenDissociate && tokenTransferCount == 1;

        boolean isWipeOrBurn = recordItem.getTransactionType() == TransactionType.TOKENBURN.getProtoId()
                || recordItem.getTransactionType() == TransactionType.TOKENWIPE.getProtoId();
        boolean isMint = recordItem.getTransactionType() == TransactionType.TOKENMINT.getProtoId()
                || recordItem.getTransactionType() == TransactionType.TOKENCREATION.getProtoId();
        boolean isSingleTransfer = tokenTransferCount == 2;

        for (int i = 0; i < tokenTransferCount; i++) {
            AccountAmount accountAmount = tokenTransfers.get(i);
            EntityId accountId = EntityId.of(accountAmount.getAccountID());
            long amount = accountAmount.getAmount();
            TokenTransfer tokenTransfer = new TokenTransfer();
            tokenTransfer.setAmount(amount);
            tokenTransfer.setDeletedTokenDissociate(isDeletedTokenDissociate);
            tokenTransfer.setId(new TokenTransfer.Id(consensusTimestamp, tokenId, accountId));
            tokenTransfer.setIsApproval(false);
            tokenTransfer.setPayerAccountId(payerAccountId);

            handleNegativeAccountAmounts(tokenId, body, accountAmount, amount, tokenTransfer);
            entityListener.onTokenTransfer(tokenTransfer);

            if (isDeletedTokenDissociate) {
                // for the token transfer of a deleted token in a token dissociate transaction, the amount is negative
                // to bring the account's balance of the token to 0. Set the totalSupply of the token object to the
                // negative amount, later in the pipeline the token total supply will be reduced accordingly
                Token token = Token.of(tokenId);
                token.setModifiedTimestamp(consensusTimestamp);
                token.setTotalSupply(accountAmount.getAmount());
                entityListener.onToken(token);
            }

            logTokenEvents(recordItem, tokenId, isWipeOrBurn, isMint, accountId, amount);

            logTokenTransfers(recordItem, tokenId, tokenTransfers, isSingleTransfer, i, accountId, amount);
        }
    }

    private void logTokenEvents(
            RecordItem recordItem,
            EntityId tokenId,
            boolean isWipeOrBurn,
            boolean isMint,
            EntityId accountId,
            long amount) {
        if (isMint || isWipeOrBurn) {
            EntityId senderId = amount < 0 ? accountId : EntityId.EMPTY;
            EntityId receiverId = amount > 0 ? accountId : EntityId.EMPTY;
            syntheticContractLogService.create(
                    new TransferContractLog(recordItem, tokenId, senderId, receiverId, Math.abs(amount)));
        }
    }

    private void logTokenTransfers(
            RecordItem recordItem,
            EntityId tokenId,
            List<AccountAmount> tokenTransfers,
            boolean isSingleTransfer,
            int i,
            EntityId accountId,
            long amount) {
        if (isSingleTransfer && amount > 0) {
            EntityId senderId = i == 0
                    ? EntityId.of(tokenTransfers.get(1).getAccountID())
                    : EntityId.of(tokenTransfers.get(0).getAccountID());
            syntheticContractLogService.create(
                    new TransferContractLog(recordItem, tokenId, senderId, accountId, amount));
        }
    }

    private void handleNegativeAccountAmounts(
            EntityId tokenId,
            TransactionBody body,
            AccountAmount accountAmount,
            long amount,
            TokenTransfer tokenTransfer) {
        // If a record AccountAmount with amount < 0 is not in the body;
        // but an AccountAmount with the same (TokenID, AccountID) combination is in the body with is_approval=true,
        // then again set is_approval=true
        if (amount < 0) {

            // Is the accountAmount from the record also inside a body's transfer list for the given tokenId?
            AccountAmount accountAmountInsideTransferList = findAccountAmount(accountAmount::equals, tokenId, body);
            if (accountAmountInsideTransferList == null) {

                // Is there any account amount inside the body's transfer list for the given tokenId
                // with the same accountId as the accountAmount from the record?
                AccountAmount accountAmountWithSameIdInsideBody = findAccountAmount(
                        aa -> aa.getAccountID().equals(accountAmount.getAccountID()) && aa.getIsApproval(),
                        tokenId,
                        body);
                if (accountAmountWithSameIdInsideBody != null) {
                    tokenTransfer.setIsApproval(true);
                }
            } else {
                tokenTransfer.setIsApproval(accountAmountInsideTransferList.getIsApproval());
            }
        }
    }

    private void insertTokenTransfers(RecordItem recordItem) {
        if (!entityProperties.getPersist().isTokens()) {
            return;
        }
        for (int i = 0; i < recordItem.getTransactionRecord().getTokenTransferListsCount(); i++) {
            TokenTransferList tokenTransferList =
                    recordItem.getTransactionRecord().getTokenTransferLists(i);
            TokenID tokenId = tokenTransferList.getToken();
            EntityId entityTokenId = EntityId.of(tokenId);
            EntityId payerAccountId = recordItem.getPayerAccountId();

            if (tokenTransferList.getTransfersCount() > 0) {
                insertFungibleTokenTransfers(
                        recordItem, entityTokenId, payerAccountId, tokenTransferList.getTransfersList());
            }

            if (tokenTransferList.getNftTransfersCount() > 0) {
                insertNonFungibleTokenTransfers(
                        recordItem, tokenId, entityTokenId, payerAccountId, tokenTransferList.getNftTransfersList());
            }
        }
    }

    private void insertNonFungibleTokenTransfers(
            RecordItem recordItem,
            TokenID tokenId,
            EntityId entityTokenId,
            EntityId payerAccountId,
            List<com.hederahashgraph.api.proto.java.NftTransfer> nftTransfersList) {
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        TransactionBody body = recordItem.getTransactionBody();

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
            syntheticContractLogService.create(
                    new TransferIndexedContractLog(recordItem, entityTokenId, senderId, receiverId, serialNumber));
        }
    }

    private void insertAutomaticTokenAssociations(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            if (recordItem.getTransactionBody().hasTokenCreation()) {
                // Automatic token associations for token create transactions are handled by its transaction handler.
                return;
            }

            long consensusTimestamp = recordItem.getConsensusTimestamp();
            recordItem
                    .getTransactionRecord()
                    .getAutomaticTokenAssociationsList()
                    .forEach(tokenAssociation -> {
                        // The accounts and tokens in the associations should have been added to EntityListener when
                        // inserting
                        // the corresponding token transfers, so no need to duplicate the logic here
                        EntityId accountId = EntityId.of(tokenAssociation.getAccountId());
                        EntityId tokenId = EntityId.of(tokenAssociation.getTokenId());
                        TokenAccount tokenAccount = new TokenAccount();
                        tokenAccount.setAccountId(accountId.getId());
                        tokenAccount.setAssociated(true);
                        tokenAccount.setAutomaticAssociation(true);
                        tokenAccount.setCreatedTimestamp(consensusTimestamp);
                        tokenAccount.setTimestampRange(Range.atLeast(consensusTimestamp));
                        tokenAccount.setTokenId(tokenId.getId());
                        entityListener.onTokenAccount(tokenAccount);
                    });
        }
    }

    private void transferNftOwnership(
            long modifiedTimeStamp, long serialNumber, EntityId tokenId, EntityId receiverId) {
        Nft nft = new Nft(serialNumber, tokenId);
        nft.setAccountId(receiverId);
        nft.setModifiedTimestamp(modifiedTimeStamp);
        entityListener.onNft(nft);
    }

    @SuppressWarnings("java:S135")
    private void insertTransactionSignatures(
            EntityId entityId, long consensusTimestamp, List<SignaturePair> signaturePairList) {
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
                    Map<Integer, UnknownFieldSet.Field> unknownFields =
                            signaturePair.getUnknownFields().asMap();

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
                        log.error(
                                RECOVERABLE_ERROR + "Unsupported signature at {}: {}",
                                consensusTimestamp,
                                unknownFields);
                        continue;
                    }
                    break;
                default:
                    log.error(
                            RECOVERABLE_ERROR + "Unsupported signature case at {}: {}",
                            consensusTimestamp,
                            signaturePair.getSignatureCase());
                    continue;
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

    // regardless of transaction type, filter on entityId and payer account and transfer tokens/receivers/senders
    private TransactionFilterFields getTransactionFilterFields(EntityId entityId, RecordItem recordItem) {
        if (!commonParserProperties.hasFilter()) {
            return TransactionFilterFields.EMPTY;
        }

        var entities = new HashSet<EntityId>();
        entities.add(entityId);
        entities.add(recordItem.getPayerAccountId());

        recordItem
                .getTransactionRecord()
                .getTransferList()
                .getAccountAmountsList()
                .forEach(accountAmount -> entities.add(EntityId.of(accountAmount.getAccountID())));

        recordItem.getTransactionRecord().getTokenTransferListsList().forEach(transfer -> {
            entities.add(EntityId.of(transfer.getToken()));

            transfer.getTransfersList()
                    .forEach(accountAmount -> entities.add(EntityId.of(accountAmount.getAccountID())));

            transfer.getNftTransfersList().forEach(nftTransfer -> {
                entities.add(EntityId.of(nftTransfer.getReceiverAccountID()));
                entities.add(EntityId.of(nftTransfer.getSenderAccountID()));
            });
        });

        entities.remove(null);
        return new TransactionFilterFields(entities, TransactionType.of(recordItem.getTransactionType()));
    }
}
