/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;

/**
 * Copied Logic type from hedera-services.
 * <p>
 * Differences with the original:
 * 1. Use abstraction for the state by introducing {@link Store} interface
 * 2. Removed SigImpactHistorian
 */
public class DeleteLogic {
    public void delete(TokenID grpcTokenId, final Store store) {
        // --- Convert to model id ---
        final var targetTokenId = EntityIdUtils.asTypedEvmAddress(grpcTokenId);
        // --- Load the model object ---
        final var token = store.getToken(targetTokenId, Store.OnMissing.THROW);

        // --- Do the business logic ---
        final var deletedToken = token.delete();

        // --- Persist the updated model ---
        store.updateToken(deletedToken);
    }

    public ResponseCodeEnum validate(final TransactionBody txnBody) {
        final TokenDeleteTransactionBody op = txnBody.getTokenDeletion();

        if (!op.hasToken()) {
            return INVALID_TOKEN_ID;
        }

        return OK;
    }
}
