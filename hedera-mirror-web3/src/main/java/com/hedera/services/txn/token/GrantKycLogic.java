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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.TokenRelationship;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenGrantKycTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;

/**
 * Copied Logic type from hedera-services.
 * <p>
 * Differences with the original:
 * 1. Use abstraction for the state by introducing {@link Store} interface
 * 2. Used tokenRelationship.setKycGranted instead of tokenRelationship.changeKycState (like in services)
 * 3. Used store.updateTokenRelationship(tokenRelationship)
 *    instead of tokenStore.commitTokenRelationships(List.of(tokenRelationship)) (like in services)
 */
public class GrantKycLogic {

    public TokenRelationship grantKyc(final Id targetTokenId, final Id targetAccountId, final Store store) {
        /* --- Load the model objects --- */
        final var tokenRelationshipKey =
                new TokenRelationshipKey(targetTokenId.asEvmAddress(), targetAccountId.asEvmAddress());
        final var tokenRelationship = store.getTokenRelationship(tokenRelationshipKey, OnMissing.THROW);

        /* --- Do the business logic --- */
        TokenRelationship tokenRelationshipResult = tokenRelationship.setKycGranted(true);

        /* --- Persist the updated models --- */
        store.updateTokenRelationship(tokenRelationshipResult);
        return tokenRelationshipResult;
    }

    public ResponseCodeEnum validate(final TransactionBody txnBody) {
        TokenGrantKycTransactionBody op = txnBody.getTokenGrantKyc();

        if (!op.hasToken()) {
            return INVALID_TOKEN_ID;
        }

        if (!op.hasAccount()) {
            return INVALID_ACCOUNT_ID;
        }

        return OK;
    }
}
