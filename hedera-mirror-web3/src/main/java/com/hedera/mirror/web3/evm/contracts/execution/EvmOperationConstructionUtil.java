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

package com.hedera.mirror.web3.evm.contracts.execution;

import static com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract.EVM_HTS_PRECOMPILED_CONTRACT_ADDRESS;
import static com.hedera.services.store.contracts.precompile.ExchangeRatePrecompiledContract.EXCHANGE_RATE_SYSTEM_CONTRACT_ADDRESS;
import static com.hedera.services.store.contracts.precompile.PrngSystemPrecompiledContract.PRNG_PRECOMPILE_ADDRESS;
import static org.hyperledger.besu.evm.MainnetEVMs.registerShanghaiOperations;

import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.contract.EntityAddressSequencer;
import com.hedera.mirror.web3.evm.store.contract.precompile.MirrorHTSPrecompiledContract;
import com.hedera.node.app.service.evm.contracts.operations.CreateOperationExternalizer;
import com.hedera.node.app.service.evm.contracts.operations.HederaBalanceOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaDelegateCallOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaEvmChainIdOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaEvmCreate2Operation;
import com.hedera.node.app.service.evm.contracts.operations.HederaEvmCreateOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaEvmSLoadOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaExtCodeCopyOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaExtCodeHashOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaExtCodeSizeOperation;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmInfrastructureFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.services.evm.contracts.operations.HederaPrngSeedOperation;
import com.hedera.services.fees.BasicHbarCentExchange;
import com.hedera.services.store.contracts.precompile.ExchangeRatePrecompiledContract;
import com.hedera.services.store.contracts.precompile.HTSPrecompiledContract;
import com.hedera.services.store.contracts.precompile.PrecompileMapper;
import com.hedera.services.store.contracts.precompile.PrngSystemPrecompiledContract;
import com.hedera.services.txns.crypto.AbstractAutoCreationLogic;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import javax.inject.Provider;
import lombok.experimental.UtilityClass;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.operation.OperationRegistry;
import org.hyperledger.besu.evm.precompile.MainnetPrecompiledContracts;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;

/**
 * This is a temporary utility class for creating all besu evm related fields needed by the {@link MirrorEvmTxProcessor}
 * execution. With the introduction of the precompiles, this creation will be refactored and encapsulated in the evm
 * module library.
 */
@UtilityClass
public class EvmOperationConstructionUtil {
    private static final String EVM_VERSION_0_30 = "v0.30";
    private static final String EVM_VERSION_0_34 = "v0.34";
    public static final String EVM_VERSION = EVM_VERSION_0_34;

    public static Map<String, Provider<ContractCreationProcessor>> ccps(
            final GasCalculator gasCalculator,
            final MirrorNodeEvmProperties mirrorNodeEvmProperties,
            final HederaPrngSeedOperation prngSeedOperation) {
        final var evm = constructEvm(gasCalculator, mirrorNodeEvmProperties, prngSeedOperation);
        return Map.of(
                EVM_VERSION_0_30,
                () -> new ContractCreationProcessor(gasCalculator, evm, true, List.of(), 1),
                EVM_VERSION_0_34,
                () -> new ContractCreationProcessor(gasCalculator, evm, true, List.of(), 1));
    }

    @SuppressWarnings("java:S107")
    public static Map<String, Provider<MessageCallProcessor>> mcps(
            final GasCalculator gasCalculator,
            final AbstractAutoCreationLogic autoCreationLogic,
            final EntityAddressSequencer entityAddressSequencer,
            final MirrorEvmContractAliases mirrorEvmContractAliases,
            final MirrorNodeEvmProperties mirrorNodeEvmProperties,
            final PrecompileMapper precompileMapper,
            final BasicHbarCentExchange basicHbarCentExchange,
            final PrngSystemPrecompiledContract prngSystemPrecompiledContract,
            final HederaPrngSeedOperation prngSeedOperation) {
        final var evm = constructEvm(gasCalculator, mirrorNodeEvmProperties, prngSeedOperation);

        final var precompileContractRegistry = new PrecompileContractRegistry();
        MainnetPrecompiledContracts.populateForIstanbul(precompileContractRegistry, gasCalculator);
        return Map.of(
                EVM_VERSION_0_30,
                () -> new MessageCallProcessor(evm, precompileContractRegistry),
                EVM_VERSION_0_34,
                () -> new MirrorEvmMessageCallProcessor(
                        autoCreationLogic,
                        entityAddressSequencer,
                        mirrorEvmContractAliases,
                        evm,
                        precompileContractRegistry,
                        precompiles(
                                mirrorNodeEvmProperties,
                                precompileMapper,
                                gasCalculator,
                                basicHbarCentExchange,
                                prngSystemPrecompiledContract)));
    }

    private static Map<String, PrecompiledContract> precompiles(
            final MirrorNodeEvmProperties mirrorNodeEvmProperties,
            final PrecompileMapper precompileMapper,
            final GasCalculator gasCalculator,
            final BasicHbarCentExchange basicHbarCentExchange,
            final PrngSystemPrecompiledContract prngSystemPrecompiledContract) {
        final Map<String, PrecompiledContract> hederaPrecompiles = new HashMap<>();
        final var evmFactory = new EvmInfrastructureFactory(new EvmEncodingFacade());

        final var htsPrecompiledContractAdapter = new HTSPrecompiledContract(
                evmFactory, mirrorNodeEvmProperties, precompileMapper, new EvmHTSPrecompiledContract(evmFactory));
        hederaPrecompiles.put(
                EVM_HTS_PRECOMPILED_CONTRACT_ADDRESS,
                new MirrorHTSPrecompiledContract(evmFactory, htsPrecompiledContractAdapter));
        hederaPrecompiles.put(PRNG_PRECOMPILE_ADDRESS, prngSystemPrecompiledContract);
        hederaPrecompiles.put(
                EXCHANGE_RATE_SYSTEM_CONTRACT_ADDRESS,
                new ExchangeRatePrecompiledContract(
                        gasCalculator, basicHbarCentExchange, mirrorNodeEvmProperties, Instant.now()));

        return hederaPrecompiles;
    }

    private static EVM constructEvm(
            final GasCalculator gasCalculator,
            final MirrorNodeEvmProperties mirrorNodeEvmProperties,
            final HederaPrngSeedOperation prngSeedOperation) {
        final var operationRegistry = new OperationRegistry();
        final BiPredicate<Address, MessageFrame> validator = (Address x, MessageFrame y) -> true;

        registerShanghaiOperations(
                operationRegistry,
                gasCalculator,
                mirrorNodeEvmProperties.chainIdBytes32().toBigInteger());
        Set.of(
                        new HederaBalanceOperation(gasCalculator, validator),
                        new HederaDelegateCallOperation(gasCalculator, validator),
                        new HederaEvmChainIdOperation(gasCalculator, mirrorNodeEvmProperties),
                        new HederaEvmCreate2Operation(
                                gasCalculator, mirrorNodeEvmProperties, getDefaultCreateOperationExternalizer()),
                        new HederaEvmCreateOperation(gasCalculator, getDefaultCreateOperationExternalizer()),
                        new HederaEvmSLoadOperation(gasCalculator),
                        new HederaExtCodeCopyOperation(gasCalculator, validator),
                        new HederaExtCodeHashOperation(gasCalculator, validator),
                        new HederaExtCodeSizeOperation(gasCalculator, validator),
                        prngSeedOperation)
                .forEach(operationRegistry::put);

        return new EVM(
                operationRegistry,
                gasCalculator,
                EvmConfiguration.DEFAULT,
                mirrorNodeEvmProperties.getEvmSpecVersion());
    }

    private static CreateOperationExternalizer getDefaultCreateOperationExternalizer() {
        return new CreateOperationExternalizer() {
            @Override
            public void externalize(final MessageFrame frame, final MessageFrame childFrame) {
                // do nothing
            }

            @Override
            public boolean shouldFailBasedOnLazyCreation(final MessageFrame frame, final Address contractAddress) {
                return false;
            }
        };
    }
}
