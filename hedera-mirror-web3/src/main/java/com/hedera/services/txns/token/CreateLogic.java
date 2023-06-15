/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.services.txns.token;

import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.services.store.models.Token.fromGrpcOpAndMeta;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEES_LIST_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FRACTIONAL_FEE_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import java.util.List;
import org.hyperledger.besu.datatypes.Address;

public class CreateLogic {
    private final Store store;

    private final TokenCreateTransactionBody op;
    private final MirrorNodeEvmProperties dynamicProperties;

    private Account treasury;
    private Account autoRenew;
    private Account collector;
    private Token provisionalToken;
    private FeeType feeType;
    private List<TokenRelationship> newRels;

    public CreateLogic(Store store, TokenCreateTransactionBody op, MirrorNodeEvmProperties dynamicProperties) {
        this.store = store;
        this.op = op;
        this.dynamicProperties = dynamicProperties;
    }

    public void create(final long now, final Address activePayer, final TokenCreateTransactionBody op) {

        // --- Create the model objects ---
        loadModelsWith(activePayer);

        // --- Do the business logic ---
        doProvisionallyWith(now, RELS_LISTING);

        // --- Persist the created model ---
        persist();
    }

    public void loadModelsWith(final Address sponsor) {
        final var hasValidOrNoExplicitExpiry = !op.hasExpiry() || validator.isValidExpiry(op.getExpiry());
        validateTrue(hasValidOrNoExplicitExpiry, INVALID_EXPIRATION_TIME);

        final var treasuryId = Id.fromGrpcAccount(op.getTreasury()).asEvmAddress();
        treasury = store.getAccount(treasuryId, OnMissing.THROW);
        autoRenew = null;
        if (op.hasAutoRenewAccount()) {
            final var autoRenewId = Id.fromGrpcAccount(op.getAutoRenewAccount()).asEvmAddress();
            autoRenew = store.getAccount(autoRenewId, OnMissing.THROW);
        }

        //        provisionalId = Id.fromGrpcToken(ids.newTokenId(sponsor));
    }

    public void doProvisionallyWith(final long now, final NewRelsListing listing) {
        final var maxCustomFees = dynamicProperties.maxCustomFeesAllowed();
        validateTrue(op.getCustomFeesCount() <= maxCustomFees, CUSTOM_FEES_LIST_TOO_LONG);

        provisionalToken = fromGrpcOpAndMeta(provisionalId, op, treasury, autoRenew, now);
        provisionalToken
                .getCustomFees()
                .forEach(fee -> validateAndFinalizeWith(provisionalToken, accountStore, tokenStore, fee));
        newRels = listing.listFrom(provisionalToken, tokenStore, dynamicProperties);
        if (op.getInitialSupply() > 0) {
            // Treasury relationship is always first
            provisionalToken.mint(newRels.get(0), op.getInitialSupply(), true);
        }
        provisionalToken.getCustomFees().forEach(FcCustomFee::nullOutCollector);
    }

    public void validateAndFinalizeWith(final Token provisionalToken, CustomFee customFee) {
        validate(provisionalToken, true, customFee);
    }

    public enum FeeType {
        FRACTIONAL_FEE,
        FIXED_FEE,
        ROYALTY_FEE
    }

    private void validate(final Token token, final boolean beingCreated, final CustomFee customFee) {
        feeType = getFeeType(customFee);
        collector = store.getAccount(getFeeCollector(customFee, feeType), OnMissing.THROW);

        switch (feeType) {
            case FIXED_FEE:
                if (beingCreated) {
                    validateAndFinalizeFixedFeeWith(token, collector, tokenStore, customFee);
                } else {
                    fixedFeeSpec.validateWith(collector, tokenStore);
                }
                break;
            case ROYALTY_FEE:
                validateTrue(token.isNonFungibleUnique(), CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE);
                if (beingCreated) {
                    royaltyFeeSpec.validateAndFinalizeWith(token, collector, tokenStore);
                } else {
                    royaltyFeeSpec.validateWith(token, collector, tokenStore);
                }
                break;
            case FRACTIONAL_FEE:
                validateTrue(token.isFungibleCommon(), CUSTOM_FRACTIONAL_FEE_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON);
                if (!beingCreated) {
                    validateTrue(tokenStore.hasAssociation(token, collector), TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR);
                }
                break;
        }
    }

    public void validateAndFinalizeFixedFeeWith(
            final Token provisionalToken,
            final Account feeCollector,
            final TypedTokenStore tokenStore,
            final CustomFee customFee) {
        Address denominatingTokenId = customFee.getFixedFee().getDenominatingTokenId();
        if (denominatingTokenId != null) {
            if (denominatingTokenId.num() == 0L) {
                validateTrue(provisionalToken.isFungibleCommon(), CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON);
                tokenDenomination.setNum(provisionalToken.getId().num());
                usedDenomWildcard = true;
            } else {
                validateExplicitlyDenominatedWith(feeCollector, tokenStore);
            }
        }
    }

    private Address getFeeCollector(CustomFee customFee, FeeType feeType) {
        if (feeType.equals(FeeType.FIXED_FEE)) {
            return customFee.getFixedFee().getFeeCollector();
        } else if (feeType.equals(FeeType.FRACTIONAL_FEE)) {
            return customFee.getFractionalFee().getFeeCollector();
        } else {
            return customFee.getRoyaltyFee().getFeeCollector();
        }
    }

    private FeeType getFeeType(CustomFee customFee) {
        if (customFee.getFixedFee() != null) {
            return FeeType.FIXED_FEE;
        } else if (customFee.getFractionalFee() != null) {
            return FeeType.FRACTIONAL_FEE;
        } else {
            return FeeType.ROYALTY_FEE;
        }
    }
}
