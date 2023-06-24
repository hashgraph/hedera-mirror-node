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

package com.hedera.services.txn.token;

import static com.hedera.services.txn.token.TokenOpsValidator.validateTokenOpsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenModificationResult;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenBurnTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Copied Logic type from hedera-services.
 *
 * Differences with the original:
 *  1. Removed validations performed in UsageLimits, since they check global node limits,
 *  while on Archive Node we are interested in transaction scope only
 *  2. Use abstraction for the state by introducing {@link Store} interface
 *  3. Use copied models from hedera-services which are enhanced with additional constructors for easier setup,
 *  those are {@link Account}, {@link Token}, {@link TokenRelationship}
 *  4. validateSyntax method uses default value of true for areNftsEnabled property
 * */
public class BurnLogic {

    private final OptionValidator validator;

    public BurnLogic(final OptionValidator validator) {
        this.validator = validator;
    }

    public void burn(final Id targetId, final long amount, List<Long> serialNumbersList, final Store store) {
        // De-duplicate serial numbers
        serialNumbersList = new ArrayList<>(new LinkedHashSet<>(serialNumbersList));

        /* --- Load the models --- */
        final var token = store.getToken(targetId.asEvmAddress(), OnMissing.THROW);
        final var tokenRelationshipKey = new TokenRelationshipKey(
                token.getId().asEvmAddress(), token.getTreasury().getAccountAddress());
        final var treasuryRel = store.getTokenRelationship(tokenRelationshipKey, OnMissing.THROW);

        /* --- Do the business logic --- */

        TokenModificationResult tokenModificationResult;
        if (token.getType().equals(TokenType.FUNGIBLE_COMMON)) {
            tokenModificationResult = token.burn(treasuryRel, amount);
        } else {
            final var tokenWithLoadedUniqueTokens = loadUniqueTokens(store, token, serialNumbersList);
            tokenModificationResult = tokenWithLoadedUniqueTokens.burn(treasuryRel, serialNumbersList);
        }

        store.updateToken(tokenModificationResult.token());
        store.updateTokenRelationship(tokenModificationResult.tokenRelationship());
        store.updateAccount(tokenModificationResult.token().getTreasury());
    }

    /**
     * Returns a {@link UniqueToken} model of the requested unique token, with operations that can
     * be used to implement business logic in a transaction.
     *
     * @param token the token model, on which to load the of the unique token
     * @param serialNumbers the serial numbers to load
     * @throws InvalidTransactionException if the requested token class is missing, deleted, or
     *     expired and pending removal
     */
    public Token loadUniqueTokens(final Store store, final Token token, final List<Long> serialNumbers) {
        final var loadedUniqueTokens = new HashMap<Long, UniqueToken>();
        for (final long serialNumber : serialNumbers) {
            final var uniqueToken = loadUniqueToken(store, token.getId(), serialNumber);
            loadedUniqueTokens.put(serialNumber, uniqueToken);
        }

        return token.setLoadedUniqueTokens(loadedUniqueTokens);
    }

    /**
     * Returns a {@link UniqueToken} model of the requested unique token, with operations that can
     * be used to implement business logic in a transaction.
     *
     * @param tokenId TokenId of the NFT
     * @param serialNum Serial number of the NFT
     * @return The {@link UniqueToken} model of the requested unique token
     */
    public UniqueToken loadUniqueToken(final Store store, final Id tokenId, final Long serialNum) {
        final var nftId = new NftId(tokenId.shard(), tokenId.realm(), tokenId.num(), serialNum);
        return store.getUniqueToken(nftId, OnMissing.THROW);
    }

    public ResponseCodeEnum validateSyntax(final TransactionBody txn) {
        final TokenBurnTransactionBody op = txn.getTokenBurn();

        if (!op.hasToken()) {
            return INVALID_TOKEN_ID;
        }

        return validateTokenOpsWith(
                op.getSerialNumbersCount(),
                op.getAmount(),
                true,
                INVALID_TOKEN_BURN_AMOUNT,
                op.getSerialNumbersList(),
                validator::maxBatchSizeBurnCheck);
    }
}
