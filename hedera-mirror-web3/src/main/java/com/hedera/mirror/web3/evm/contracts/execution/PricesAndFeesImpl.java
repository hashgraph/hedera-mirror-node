/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.evm.contracts.execution;

import com.hedera.node.app.service.evm.contracts.execution.PricesAndFeesProvider;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import jakarta.inject.Named;
import java.time.Instant;

@Named
public class PricesAndFeesImpl implements PricesAndFeesProvider {
    // FEATURE WORK - precise gas price calculation to be provided with eth_estimateGas implementation
    private static final long GAS_PRICE = 1000L;

    @Override
    public long currentGasPrice(final Instant now, final HederaFunctionality function) {
        return GAS_PRICE;
    }
}
