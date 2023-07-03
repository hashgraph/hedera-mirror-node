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
import static com.hedera.services.txns.validation.ContextOptionValidator.batchSizeCheck;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_WIPING_AMOUNT;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.TokenModificationResult;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Copied Logic type from hedera-services.
 * <p>
 * Differences with the original:
 * 1. Use abstraction for the state by introducing {@link Store} interface
 * 2. validateSyntax method uses default value of true for areNftsEnabled property
 * 3. validateSyntax executes the logic directly instead of calling TokenWipeAccessor.validateSyntax
 */
@Singleton
public class WipeLogic {
    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @Inject
    public WipeLogic(final MirrorNodeEvmProperties mirrorNodeEvmProperties) {
        this.mirrorNodeEvmProperties = mirrorNodeEvmProperties;
    }

    public void wipe(
            final Id targetTokenId,
            final Id targetAccountId,
            final long amount,
            List<Long> serialNumbersList,
            final Store store) {
        // De-duplicate serial numbers
        serialNumbersList = new ArrayList<>(new LinkedHashSet<>(serialNumbersList));

        /* --- Load the model objects --- */
        final var token = store.getToken(targetTokenId.asEvmAddress(), OnMissing.THROW);
        final var tokenRelationshipKey =
                new TokenRelationshipKey(targetTokenId.asEvmAddress(), targetAccountId.asEvmAddress());
        final var accountRel = store.getTokenRelationship(tokenRelationshipKey, OnMissing.THROW);

        /* --- Do the business logic --- */
        TokenModificationResult tokenModificationResult;
        if (token.getType().equals(TokenType.FUNGIBLE_COMMON)) {
            tokenModificationResult = token.wipe(accountRel, amount);
        } else {
            final var tokenWithLoadedUniqueTokens = store.loadUniqueTokens(token, serialNumbersList);
            tokenModificationResult = tokenWithLoadedUniqueTokens.wipe(accountRel, serialNumbersList);
        }
        /* --- Persist the updated models --- */
        store.updateToken(tokenModificationResult.token());
        store.updateTokenRelationship(tokenModificationResult.tokenRelationship());
        store.updateAccount(tokenModificationResult.tokenRelationship().getAccount());
    }

    public ResponseCodeEnum validateSyntax(final TransactionBody txn) {
        TokenWipeAccountTransactionBody body = txn.getTokenWipe();
        if (!body.hasToken()) {
            return INVALID_TOKEN_ID;
        }

        if (!body.hasAccount()) {
            return INVALID_ACCOUNT_ID;
        }
        return validateTokenOpsWith(
                body.getSerialNumbersCount(),
                body.getAmount(),
                true,
                INVALID_WIPING_AMOUNT,
                body.getSerialNumbersList(),
                a -> batchSizeCheck(a, mirrorNodeEvmProperties.getMaxBatchSizeWipe()));
    }
}
