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

import static com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract.EVM_HTS_PRECOMPILED_CONTRACT_ADDRESS;
import static com.hedera.services.store.contracts.precompile.ExchangeRatePrecompiledContract.EXCHANGE_RATE_SYSTEM_CONTRACT_ADDRESS;
import static com.hedera.services.store.contracts.precompile.PrngSystemPrecompiledContract.PRNG_PRECOMPILE_ADDRESS;
import static org.mockito.Mockito.mockStatic;

import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.pricing.RatesAndFeesLoader;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.properties.TraceProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.contract.EntityAddressSequencer;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmInfrastructureFactory;
import com.hedera.services.contracts.execution.LivePricesSource;
import com.hedera.services.contracts.gascalculator.GasCalculatorHederaV22;
import com.hedera.services.fees.BasicHbarCentExchange;
import com.hedera.services.fees.calculation.BasicFcfsUsagePrices;
import com.hedera.services.store.contracts.precompile.ExchangeRatePrecompiledContract;
import com.hedera.services.store.contracts.precompile.PrngSystemPrecompiledContract;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.txns.crypto.AbstractAutoCreationLogic;
import com.hedera.services.txns.util.PrngLogic;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.utility.CommonUtils;
import java.time.Instant;
import java.util.Map;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public abstract class MirrorEvmMessageCallProcessorBaseTest {

    private static final Hash WELL_KNOWN_HASH = new Hash(CommonUtils.unhex(
            "65386630386164632d356537632d343964342d623437372d62636134346538386338373133633038316162372d616300"));

    private static MockedStatic<ContractCallContext> contextMockedStatic;

    @Spy
    private ContractCallContext contractCallContext;

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

    @Mock
    EvmInfrastructureFactory evmInfrastructureFactory;

    @Mock
    PrecompilePricingUtils precompilePricingUtils;

    @Mock
    RatesAndFeesLoader ratesAndFeesLoader;

    final EvmHTSPrecompiledContract htsPrecompiledContract = new EvmHTSPrecompiledContract(evmInfrastructureFactory);

    final ExchangeRatePrecompiledContract exchangeRatePrecompiledContract = new ExchangeRatePrecompiledContract(
            gasCalculatorHederaV22,
            new BasicHbarCentExchange(ratesAndFeesLoader),
            new MirrorNodeEvmProperties(),
            Instant.now());

    final PrngSystemPrecompiledContract prngSystemPrecompiledContract = new PrngSystemPrecompiledContract(
            gasCalculatorHederaV22,
            new PrngLogic(WELL_KNOWN_HASH::getValue),
            new LivePricesSource(
                    new BasicHbarCentExchange(ratesAndFeesLoader), new BasicFcfsUsagePrices(ratesAndFeesLoader)),
            precompilePricingUtils);

    final Map<String, PrecompiledContract> hederaPrecompileList = Map.of(
            EVM_HTS_PRECOMPILED_CONTRACT_ADDRESS, htsPrecompiledContract,
            EXCHANGE_RATE_SYSTEM_CONTRACT_ADDRESS, exchangeRatePrecompiledContract,
            PRNG_PRECOMPILE_ADDRESS, prngSystemPrecompiledContract);

    @BeforeAll
    static void initStaticMocks() {
        contextMockedStatic = mockStatic(ContractCallContext.class);
    }

    @AfterAll
    static void closeStaticMocks() {
        contextMockedStatic.close();
    }

    @BeforeEach
    void setUpContext() {
        contextMockedStatic.when(ContractCallContext::get).thenReturn(contractCallContext);
    }
}
