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
import static com.hedera.services.utils.MiscUtils.asUsableFcKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEES_LIST_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

public class CreateLogic {
    private final Store store;

    private final TokenCreateTransactionBody op;
    private final MirrorNodeEvmProperties dynamicProperties;

    private Account treasury;
    private Account autoRenew;
    private Token provisionalToken;
    private List<TokenRelationship> newRels;

    public CreateLogic(Store store, TokenCreateTransactionBody op, MirrorNodeEvmProperties dynamicProperties) {
        this.store = store;
        this.op = op;
        this.dynamicProperties = dynamicProperties;
    }

    public void create(final long now, final AccountID activePayer, final TokenCreateTransactionBody op) {

        // --- Create the model objects ---
        loadModelsWith(activePayer);

        // --- Do the business logic ---
        doProvisionallyWith(now, RELS_LISTING);
    }

    public void loadModelsWith(final AccountID sponsor, final OptionValidator validator) {
        final var hasValidOrNoExplicitExpiry = !op.hasExpiry() || validator.isValidExpiry(op.getExpiry());
        validateTrue(hasValidOrNoExplicitExpiry, INVALID_EXPIRATION_TIME);

        final var treasuryId = Id.fromGrpcAccount(op.getTreasury());
        treasury = store.getAccount(treasuryId, true);
        autoRenew = null;
        if (op.hasAutoRenewAccount()) {
            final var autoRenewId = Id.fromGrpcAccount(op.getAutoRenewAccount());
            autoRenew = store.getAccount(autoRenewId, true);
        }

        provisionalId = Id.fromGrpcToken(ids.newTokenId(sponsor));
    }

    public void doProvisionallyWith(final long now, final NewRelsListing listing) {
        final var maxCustomFees = dynamicProperties.maxCustomFeesAllowed();
        validateTrue(op.getCustomFeesCount() <= maxCustomFees, CUSTOM_FEES_LIST_TOO_LONG);

        provisionalToken = fromGrpcOpAndMeta(provisionalId, op, treasury, autoRenew, now);
        provisionalToken
                .getCustomFees()
                .forEach(fee -> fee.validateAndFinalizeWith(provisionalToken, accountStore, tokenStore));
        newRels = listing.listFrom(provisionalToken, tokenStore, dynamicProperties);
        if (op.getInitialSupply() > 0) {
            // Treasury relationship is always first
            provisionalToken.mint(newRels.get(0), op.getInitialSupply(), true);
        }
        provisionalToken.getCustomFees().forEach(FcCustomFee::nullOutCollector);
    }

    /**
     * Creates a new instance of the model token, which is later persisted in state.
     *
     * @param tokenId the new token id
     * @param op the transaction body containing the necessary data for token creation
     * @param treasury treasury of the token
     * @param autoRenewAccount optional(nullable) account used for auto-renewal
     * @param consensusTimestamp the consensus time of the token create transaction
     * @return a new instance of the {@link Token} class
     */
    public static Token fromGrpcOpAndMeta(
            final Id tokenId,
            final TokenCreateTransactionBody op,
            final Account treasury,
            @Nullable final Account autoRenewAccount,
            final long consensusTimestamp) {
        final var token = new Token(tokenId);
        final var tokenExpiry = op.hasAutoRenewAccount()
                ? consensusTimestamp + op.getAutoRenewPeriod().getSeconds()
                : op.getExpiry().getSeconds();

        final var freezeKey = asUsableFcKey(op.getFreezeKey());
        final var adminKey = asUsableFcKey(op.getAdminKey());
        final var kycKey = asUsableFcKey(op.getKycKey());
        final var wipeKey = asUsableFcKey(op.getWipeKey());
        final var supplyKey = asUsableFcKey(op.getSupplyKey());
        final var feeScheduleKey = asUsableFcKey(op.getFeeScheduleKey());
        final var pauseKey = asUsableFcKey(op.getPauseKey());

        freezeKey.ifPresent(token::setFreezeKey);
        adminKey.ifPresent(token::setAdminKey);
        kycKey.ifPresent(token::setKycKey);
        wipeKey.ifPresent(token::setWipeKey);
        supplyKey.ifPresent(token::setSupplyKey);
        feeScheduleKey.ifPresent(token::setFeeScheduleKey);
        pauseKey.ifPresent(token::setPauseKey);

        token.initSupplyConstraints(TokenTypesMapper.mapToDomain(op.getSupplyType()), op.getMaxSupply());
        token.setType(TokenTypesMapper.mapToDomain(op.getTokenType()));

        token.setTreasury(treasury);
        if (autoRenewAccount != null) {
            token.setAutoRenewAccount(autoRenewAccount);
            token.setAutoRenewPeriod(op.getAutoRenewPeriod().getSeconds());
        }

        token.setExpiry(tokenExpiry);
        token.setMemo(op.getMemo());
        token.setSymbol(op.getSymbol());
        token.setDecimals(op.getDecimals());
        token.setName(op.getName());
        token.setFrozenByDefault(op.getFreezeDefault());
        token.setCustomFees(
                op.getCustomFeesList().stream().map(FcCustomFee::fromGrpc).toList());
        token.setPaused(false);

        token.setNew(true);
        return token;
    }
}
