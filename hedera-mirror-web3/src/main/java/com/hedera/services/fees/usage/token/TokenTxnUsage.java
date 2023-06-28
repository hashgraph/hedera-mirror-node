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

package com.hedera.services.fees.usage.token;

import static com.hedera.services.fees.usage.token.entities.TokenEntitySizes.TOKEN_ENTITY_SIZES;

import com.hedera.services.fees.usage.token.entities.TokenEntitySizes;
import com.hedera.services.hapi.fees.usage.TxnUsage;
import com.hedera.services.hapi.fees.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.TransactionBody;

/**
 *  Exact copy from hedera-services
 */
public abstract class TokenTxnUsage<T extends TokenTxnUsage<T>> extends TxnUsage {
    static TokenEntitySizes tokenEntitySizes = TOKEN_ENTITY_SIZES;

    protected TokenTxnUsage(final TransactionBody tokenOp, final TxnUsageEstimator usageEstimator) {
        super(tokenOp, usageEstimator);
    }

    abstract T self();

    void addTokenTransfersRecordRb(final int numTokens, final int fungibleNumTransfers, final int uniqueNumTransfers) {
        addRecordRb(
                tokenEntitySizes.bytesUsedToRecordTokenTransfers(numTokens, fungibleNumTransfers, uniqueNumTransfers));
    }

    public T novelRelsLasting(final int n, final long secs) {
        usageEstimator.addRbs(n * tokenEntitySizes.bytesUsedPerAccountRelationship() * secs);
        return self();
    }
}
