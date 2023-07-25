/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenPauseTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;

/**
 * Copied Logic type from hedera-services.
 * <p>
 * Differences with the original:
 * 1. Use abstraction for the state by introducing {@link Store} interface
 * 2. Used token.setPaused instead of token.changePauseStatus (like in services)
 * 3. Used store.updateToken(token)
 *    instead of tokenStore.commitToken(token) (like in services)
 */
public class PauseLogic {

    public void pause(final Id targetTokenId, final Store store) {
        /* --- Load the model objects --- */
        var token = store.loadPossiblyPausedToken(targetTokenId.asEvmAddress());

        /* --- Do the business logic --- */
        var pausedToken = token.setPaused(true);

        /* --- Persist the updated models --- */
        store.updateToken(pausedToken);
    }

    public ResponseCodeEnum validateSyntax(final TransactionBody txnBody) {
        TokenPauseTransactionBody op = txnBody.getTokenPause();

        if (!op.hasToken()) {
            return INVALID_TOKEN_ID;
        }

        return OK;
    }
}
