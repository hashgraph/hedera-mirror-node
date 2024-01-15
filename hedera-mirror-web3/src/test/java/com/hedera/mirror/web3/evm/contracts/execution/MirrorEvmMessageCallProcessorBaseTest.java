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

import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.properties.TraceProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.contract.EntityAddressSequencer;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.services.contracts.gascalculator.GasCalculatorHederaV22;
import com.hedera.services.txns.crypto.AbstractAutoCreationLogic;
import java.util.Collections;
import java.util.Map;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public abstract class MirrorEvmMessageCallProcessorBaseTest {

    @Mock
    AbstractAutoCreationLogic autoCreationLogic;

    @Mock
    EntityAddressSequencer entityAddressSequencer;

    @Mock
    MirrorEvmContractAliases mirrorEvmContractAliases;

    @Mock
    EVM evm;

    @Mock
    PrecompileContractRegistry precompiles;

    @Mock
    MessageFrame messageFrame;

    @Mock
    TraceProperties traceProperties;

    @Mock
    HederaEvmStackedWorldStateUpdater updater;

    @Mock
    Store store;

    @Mock
    OperationTracer operationTracer;

    @Mock
    GasCalculatorHederaV22 gasCalculatorHederaV22;

    @SuppressWarnings("unchecked")
    final Map<String, PrecompiledContract> hederaPrecompileList = Collections.EMPTY_MAP;
}
