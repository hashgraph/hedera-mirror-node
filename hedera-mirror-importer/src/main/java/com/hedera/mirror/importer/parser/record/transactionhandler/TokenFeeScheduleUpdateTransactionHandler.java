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

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.CustomFee;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.FractionalFee;
import com.hederahashgraph.api.proto.java.RoyaltyFee;
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
     * @param customFeeList protobuf custom fee list
     * @return A list of collectors automatically associated with the token if it's a token create transaction
     */
    Set<EntityId> updateCustomFees(
            Collection<com.hederahashgraph.api.proto.java.CustomFee> customFeeList,
            RecordItem recordItem,
            Transaction transaction) {
        var autoAssociatedAccounts = new HashSet<EntityId>();
        var consensusTimestamp = transaction.getConsensusTimestamp();
        var tokenId = transaction.getEntityId();
        var id = new CustomFee.Id(consensusTimestamp, tokenId);

        for (var protoCustomFee : customFeeList) {
            var collector = EntityId.of(protoCustomFee.getFeeCollectorAccountId());
            var customFee = new CustomFee();
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
                    // Only FT can have fractional fee
                    parseFractionalFee(customFee, protoCustomFee.getFractionalFee());
                    chargedInAttachedToken = true;
                    break;
                case ROYALTY_FEE:
                    // Only NFT can have royalty fee, and fee can't be paid in NFT. Thus, though royalty fee has a
                    // fixed fee fallback, the denominating token of the fixed fee can't be the NFT itself.
                    parseRoyaltyFee(customFee, protoCustomFee.getRoyaltyFee(), tokenId);
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

            entityListener.onCustomFee(customFee);

            recordItem.addEntityId(collector);
            recordItem.addEntityId(customFee.getDenominatingTokenId());
        }

        // For empty custom fees, add a single row with only the timestamp and tokenId.
        if (customFeeList.isEmpty()) {
            var customFee = new CustomFee();
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
