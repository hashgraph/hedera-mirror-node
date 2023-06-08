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

package com.hedera.services.fees.calculation;

import com.hedera.mirror.web3.evm.store.StackedStateFrames;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.hapi.utils.fees.FeeObject;
import com.hedera.services.jproto.JKey;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Timestamp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implements a {@link FeeCalculator} in terms of injected usage prices, exchange rates, and collections of estimators
 * which can infer the resource usage of various transactions and queries.
 */
public class UsageBasedFeeCalculator implements FeeCalculator {
    private static final Logger log = LogManager.getLogger(UsageBasedFeeCalculator.class);

    @Override
    public FeeObject estimatePayment(
            Query query, FeeData usagePrices, StackedStateFrames<?> state, Timestamp at, ResponseType type) {
        return null;
    }

    @Override
    public long estimatedGasPriceInTinybars(HederaFunctionality function, Timestamp at) {
        return 0;
    }

    @Override
    public FeeObject computeFee(JKey payerKey, StackedStateFrames<?> state, Timestamp at) {
        return null;
    }
}
