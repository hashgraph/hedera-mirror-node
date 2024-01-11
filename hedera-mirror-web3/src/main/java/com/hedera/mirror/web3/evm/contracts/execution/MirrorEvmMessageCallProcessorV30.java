/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import com.hedera.mirror.web3.evm.config.PrecompiledContractProvider;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmMessageCallProcessor;
import com.hedera.services.contracts.gascalculator.GasCalculatorHederaV22;
import jakarta.inject.Named;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.precompile.MainnetPrecompiledContracts;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;

@Named
public class MirrorEvmMessageCallProcessorV30 extends HederaEvmMessageCallProcessor {

    public MirrorEvmMessageCallProcessorV30(
            @Named("evm030") EVM v30,
            PrecompileContractRegistry precompiles,
            final PrecompiledContractProvider precompilesHolder,
            final GasCalculatorHederaV22 gasCalculator) {
        super(v30, precompiles, precompilesHolder.getHederaPrecompiles());
        MainnetPrecompiledContracts.populateForIstanbul(precompiles, gasCalculator);
    }
}
