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

package com.hedera.mirror.importer.parser.record.transactionhandler;

import static com.hedera.mirror.common.domain.transaction.TransactionType.TOKENCREATION;
import static com.hedera.mirror.importer.util.Utility.RECOVERABLE_ERROR;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.CustomFee;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import jakarta.inject.Named;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;

@CustomLog
@Named
@RequiredArgsConstructor
class TokenFeeScheduleUpdateTransactionHandler extends AbstractTransactionHandler {

    private final EntityListener entityListener;
    private final EntityProperties entityProperties;

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(
                recordItem.getTransactionBody().getTokenFeeScheduleUpdate().getTokenId());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.TOKENFEESCHEDULEUPDATE;
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!entityProperties.getPersist().isTokens() || !recordItem.isSuccessful()) {
            return;
        }

        var transactionBody = recordItem.getTransactionBody().getTokenFeeScheduleUpdate();
        updateCustomFees(transactionBody.getCustomFeesList(), recordItem, transaction);
    }

    /**
     * Handles custom fees. Returns the list of collectors automatically associated with the newly created token if the
     * custom fees are from a token create transaction
     *
     * @param protoCustomFees protobuf custom fee list
     * @return A list of collectors automatically associated with the token if it's a token create transaction
     */
    Set<EntityId> updateCustomFees(
            Collection<com.hederahashgraph.api.proto.java.CustomFee> protoCustomFees,
            RecordItem recordItem,
            Transaction transaction) {
        var autoAssociatedAccounts = new HashSet<EntityId>();
        var consensusTimestamp = transaction.getConsensusTimestamp();
        var tokenId = transaction.getEntityId();
        var customFee = new CustomFee();
        customFee.setTokenId(tokenId.getId());
        customFee.setCreatedTimestamp(consensusTimestamp);
        customFee.setTimestampRange(Range.atLeast(consensusTimestamp));

        // For empty custom fees, add a single row with only the timestamp and tokenId.
        if (protoCustomFees.isEmpty()) {
            entityListener.onCustomFee(customFee);
            return autoAssociatedAccounts;
        }

        for (var protoCustomFee : protoCustomFees) {
            var collector = EntityId.of(protoCustomFee.getFeeCollectorAccountId());
            var feeCase = protoCustomFee.getFeeCase();
            boolean chargedInAttachedToken;

            switch (feeCase) {
                case FIXED_FEE:
                    chargedInAttachedToken = parseFixedFee(
                            protoCustomFee.getAllCollectorsAreExempt(),
                            collector,
                            customFee,
                            protoCustomFee.getFixedFee(),
                            tokenId,
                            recordItem);
                    break;
                case FRACTIONAL_FEE:
                    // Only FT can have fractional fee
                    parseFractionalFee(
                            protoCustomFee.getAllCollectorsAreExempt(),
                            collector,
                            customFee,
                            protoCustomFee.getFractionalFee());
                    chargedInAttachedToken = true;
                    break;
                case ROYALTY_FEE:
                    // Only NFT can have royalty fee, and fee can't be paid in NFT. Thus, though royalty fee has a
                    // fixed fee fallback, the denominating token of the fixed fee can't be the NFT itself.
                    parseRoyaltyFee(
                            protoCustomFee.getAllCollectorsAreExempt(),
                            collector,
                            customFee,
                            protoCustomFee.getRoyaltyFee(),
                            tokenId,
                            recordItem);
                    chargedInAttachedToken = false;
                    break;
                default:
                    log.error(RECOVERABLE_ERROR + "Invalid CustomFee FeeCase at {}: {}", consensusTimestamp, feeCase);
                    continue;
            }

            // If it's from a token create transaction, and the fee is charged in the attached token, the attached
            // token and the collector should have been auto associated
            if (transaction.getType() == TOKENCREATION.getProtoId() && chargedInAttachedToken) {
                autoAssociatedAccounts.add(collector);
            }

            recordItem.addEntityId(collector);
        }

        // If the fee is empty do not persist it. The protoCustomFees did not contain a parseable fee, only recoverable
        // error(s).
        if (!customFee.isEmptyFee()) {
            entityListener.onCustomFee(customFee);
        }
        return autoAssociatedAccounts;
    }

    /**
     * Parse protobuf FixedFee object to domain FixedFee object.
     *
     * @param customFee the domain CustomFee object
     * @param protoFixedFee  the protobuf FixedFee object
     * @param tokenId   the attached token id
     * @return whether the fee is paid in the attached token
     */
    private boolean parseFixedFee(
            boolean allCollectorsAreExempt,
            EntityId collector,
            CustomFee customFee,
            com.hederahashgraph.api.proto.java.FixedFee protoFixedFee,
            EntityId tokenId,
            RecordItem recordItem) {
        var fixedFee = new com.hedera.mirror.common.domain.transaction.FixedFee();
        customFee.addFixedFee(fixedFee);
        fixedFee.setAllCollectorsAreExempt(allCollectorsAreExempt);
        fixedFee.setCollectorAccountId(collector);
        fixedFee.setAmount(protoFixedFee.getAmount());
        if (protoFixedFee.hasDenominatingTokenId()) {
            var denominatingTokenId = EntityId.of(protoFixedFee.getDenominatingTokenId());
            denominatingTokenId = denominatingTokenId == EntityId.EMPTY ? tokenId : denominatingTokenId;
            fixedFee.setDenominatingTokenId(denominatingTokenId);
            recordItem.addEntityId(denominatingTokenId);
            return denominatingTokenId.equals(tokenId);
        }

        return false;
    }

    /**
     * Parse protobuf FractionalFee object to domain FractionalFee object.
     *
     * @param customFee the domain CustomFee object
     * @param protoFractionalFee  the protobuf FractionalFee object
     */
    private void parseFractionalFee(
            boolean allCollectorsAreExempt,
            EntityId collector,
            CustomFee customFee,
            com.hederahashgraph.api.proto.java.FractionalFee protoFractionalFee) {
        var fractionalFee = new com.hedera.mirror.common.domain.transaction.FractionalFee();
        fractionalFee.setAmount(protoFractionalFee.getFractionalAmount().getNumerator());
        fractionalFee.setAllCollectorsAreExempt(allCollectorsAreExempt);
        fractionalFee.setCollectorAccountId(collector);
        fractionalFee.setAmountDenominator(
                protoFractionalFee.getFractionalAmount().getDenominator());

        long maximumAmount = protoFractionalFee.getMaximumAmount();
        if (maximumAmount != 0) {
            fractionalFee.setMaximumAmount(maximumAmount);
        }

        fractionalFee.setMinimumAmount(protoFractionalFee.getMinimumAmount());
        fractionalFee.setNetOfTransfers(protoFractionalFee.getNetOfTransfers());
        customFee.addFractionalFee(fractionalFee);
    }

    /**
     * Parse protobuf RoyaltyFee object to domain RoyaltyFee object.
     *
     * @param customFee the domain CustomFee object
     * @param protoRoyaltyFee  the protobuf RoyaltyFee object
     */
    private void parseRoyaltyFee(
            boolean allCollectorsAreExempt,
            EntityId collector,
            CustomFee customFee,
            com.hederahashgraph.api.proto.java.RoyaltyFee protoRoyaltyFee,
            EntityId tokenId,
            RecordItem recordItem) {
        var royaltyFee = new com.hedera.mirror.common.domain.transaction.RoyaltyFee();
        royaltyFee.setAllCollectorsAreExempt(allCollectorsAreExempt);
        royaltyFee.setCollectorAccountId(collector);
        royaltyFee.setRoyaltyNumerator(
                protoRoyaltyFee.getExchangeValueFraction().getNumerator());
        royaltyFee.setRoyaltyDenominator(
                protoRoyaltyFee.getExchangeValueFraction().getDenominator());

        if (protoRoyaltyFee.hasFallbackFee()) {
            var fixedFee = new com.hedera.mirror.common.domain.transaction.FixedFee();
            fixedFee.setCollectorAccountId(collector);
            fixedFee.setAmount(protoRoyaltyFee.getFallbackFee().getAmount());
            if (protoRoyaltyFee.getFallbackFee().hasDenominatingTokenId()) {
                var denominatingTokenId =
                        EntityId.of(protoRoyaltyFee.getFallbackFee().getDenominatingTokenId());
                denominatingTokenId = denominatingTokenId == EntityId.EMPTY ? tokenId : denominatingTokenId;
                fixedFee.setDenominatingTokenId(denominatingTokenId);
                recordItem.addEntityId(denominatingTokenId);
            }

            royaltyFee.setFallbackFee(fixedFee);
        }

        customFee.addRoyaltyFee(royaltyFee);
    }
}
