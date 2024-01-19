/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.AbstractFee;
import com.hedera.mirror.common.domain.token.CustomFee;
import com.hedera.mirror.common.domain.token.FallbackFee;
import com.hedera.mirror.common.domain.token.FixedFee;
import com.hedera.mirror.common.domain.token.FractionalFee;
import com.hedera.mirror.common.domain.token.RoyaltyFee;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.util.Utility;
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
        customFee.setTimestampRange(Range.atLeast(consensusTimestamp));

        // For empty custom fees, add a single row with only the timestamp and tokenId.
        if (protoCustomFees.isEmpty()) {
            entityListener.onCustomFee(customFee);
            return autoAssociatedAccounts;
        }

        for (var protoCustomFee : protoCustomFees) {
            var feeCase = protoCustomFee.getFeeCase();
            AbstractFee fee;
            EntityId denominatingTokenId = null;

            switch (feeCase) {
                case FIXED_FEE:
                    var fixedFee = parseFixedFee(protoCustomFee.getFixedFee(), tokenId);
                    customFee.addFixedFee(fixedFee);
                    denominatingTokenId = fixedFee.getDenominatingTokenId();
                    fee = fixedFee;
                    break;
                case FRACTIONAL_FEE:
                    // Only FT can have fractional fee
                    var fractionalFee = parseFractionalFee(protoCustomFee.getFractionalFee());
                    customFee.addFractionalFee(fractionalFee);
                    fee = fractionalFee;
                    break;
                case ROYALTY_FEE:
                    // Only NFT can have royalty fee, and fee can't be paid in NFT. Thus, though royalty fee has a
                    // fixed fee fallback, the denominating token of the fixed fee can't be the NFT itself.
                    var royaltyFee = parseRoyaltyFee(protoCustomFee.getRoyaltyFee(), tokenId);
                    customFee.addRoyaltyFee(royaltyFee);
                    var fallbackFee = royaltyFee.getFallbackFee();
                    if (fallbackFee != null && !EntityId.isEmpty(fallbackFee.getDenominatingTokenId())) {
                        denominatingTokenId = fallbackFee.getDenominatingTokenId();
                    }

                    fee = royaltyFee;
                    break;
                default:
                    Utility.handleRecoverableError("Invalid CustomFee FeeCase at {}: {}", consensusTimestamp, feeCase);
                    continue;
            }

            var allCollectorsAreExempt = protoCustomFee.getAllCollectorsAreExempt();
            var collector = EntityId.of(protoCustomFee.getFeeCollectorAccountId());
            fee.setAllCollectorsAreExempt(allCollectorsAreExempt);
            fee.setCollectorAccountId(collector);
            recordItem.addEntityId(collector);
            recordItem.addEntityId(denominatingTokenId);

            // If it's from a token create transaction, and the fee is charged in the attached token, the attached
            // token and the collector should have been auto associated
            if (transaction.getType() == TOKENCREATION.getProtoId() && fee.isChargedInToken(tokenId)) {
                autoAssociatedAccounts.add(collector);
            }
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
     * @param protoFixedFee the protobuf FixedFee object
     * @param tokenId       the attached token id
     * @return whether the fee is paid in the attached token
     */
    private FixedFee parseFixedFee(com.hederahashgraph.api.proto.java.FixedFee protoFixedFee, EntityId tokenId) {
        var fixedFee = new FixedFee();
        fixedFee.setAmount(protoFixedFee.getAmount());
        if (protoFixedFee.hasDenominatingTokenId()) {
            var denominatingTokenId = EntityId.of(protoFixedFee.getDenominatingTokenId());
            denominatingTokenId = denominatingTokenId == EntityId.EMPTY ? tokenId : denominatingTokenId;
            fixedFee.setDenominatingTokenId(denominatingTokenId);
        }

        return fixedFee;
    }

    /**
     * Parse protobuf FractionalFee object to domain FractionalFee object.
     *
     * @param protoFractionalFee the protobuf FractionalFee object
     */
    private FractionalFee parseFractionalFee(com.hederahashgraph.api.proto.java.FractionalFee protoFractionalFee) {
        var fractionalFee = new FractionalFee();
        fractionalFee.setDenominator(protoFractionalFee.getFractionalAmount().getDenominator());
        long maximumAmount = protoFractionalFee.getMaximumAmount();
        if (maximumAmount != 0) {
            fractionalFee.setMaximumAmount(maximumAmount);
        }
        fractionalFee.setMinimumAmount(protoFractionalFee.getMinimumAmount());
        fractionalFee.setNumerator(protoFractionalFee.getFractionalAmount().getNumerator());
        fractionalFee.setNetOfTransfers(protoFractionalFee.getNetOfTransfers());
        return fractionalFee;
    }

    /**
     * Parse protobuf RoyaltyFee object to domain RoyaltyFee object.
     *
     * @param protoRoyaltyFee the protobuf RoyaltyFee object
     */
    private RoyaltyFee parseRoyaltyFee(
            com.hederahashgraph.api.proto.java.RoyaltyFee protoRoyaltyFee, EntityId tokenId) {
        var royaltyFee = new RoyaltyFee();
        royaltyFee.setDenominator(protoRoyaltyFee.getExchangeValueFraction().getDenominator());
        royaltyFee.setNumerator(protoRoyaltyFee.getExchangeValueFraction().getNumerator());

        if (protoRoyaltyFee.hasFallbackFee()) {
            var fallbackFee = new FallbackFee();
            fallbackFee.setAmount(protoRoyaltyFee.getFallbackFee().getAmount());
            if (protoRoyaltyFee.getFallbackFee().hasDenominatingTokenId()) {
                var denominatingTokenId =
                        EntityId.of(protoRoyaltyFee.getFallbackFee().getDenominatingTokenId());
                denominatingTokenId = denominatingTokenId == EntityId.EMPTY ? tokenId : denominatingTokenId;
                fallbackFee.setDenominatingTokenId(denominatingTokenId);
            }
            royaltyFee.setFallbackFee(fallbackFee);
        }

        return royaltyFee;
    }
}
