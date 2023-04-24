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
package com.hedera.services.fees.calculation.usage.consensus;

import static com.hedera.services.hapi.utils.fees.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hedera.services.hapi.utils.fees.FeeBuilder.LONG_SIZE;
import static com.hedera.services.hapi.utils.fees.FeeBuilder.RECEIPT_STORAGE_TIME_SEC;
import static com.hedera.services.hapi.utils.fees.FeeBuilder.TX_HASH_SIZE;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.hedera.services.fees.usage.state.UsageAccumulator;
import com.hedera.services.hapi.fees.usage.BaseTransactionMeta;
import com.hedera.services.hapi.fees.usage.SigUsage;

@Singleton
public final class ConsensusOpsUsage {
    private static final long LONG_BASIC_ENTITY_ID_SIZE = BASIC_ENTITY_ID_SIZE;

    @Inject
    public ConsensusOpsUsage() {
        /* No-op */
    }

    public void submitMessageUsage(
            final SigUsage sigUsage,
            final SubmitMessageMeta submitMeta,
            final BaseTransactionMeta baseMeta,
            final UsageAccumulator accumulator) {
        accumulator.resetForTransaction(baseMeta, sigUsage);
        accumulator.addBpt(LONG_BASIC_ENTITY_ID_SIZE + submitMeta.numMsgBytes());
        /* SubmitMessage receipts include a sequence number and running hash */
        final var extraReceiptBytes = LONG_SIZE + TX_HASH_SIZE;
        accumulator.addNetworkRbs(extraReceiptBytes * RECEIPT_STORAGE_TIME_SEC);
    }
}
