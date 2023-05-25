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
import static org.hyperledger.besu.evm.MainnetEVMs.registerParisOperations;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.StackedStateFrames;
import com.hedera.mirror.web3.evm.store.contract.precompile.MirrorHTSPrecompiledContract;
import com.hedera.mirror.web3.evm.store.contract.precompile.PrecompileMapper;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmMessageCallProcessor;
import com.hedera.node.app.service.evm.contracts.operations.HederaBalanceOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaDelegateCallOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaEvmSLoadOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaExtCodeCopyOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaExtCodeHashOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaExtCodeSizeOperation;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmInfrastructureFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import java.math.BigInteger;
import java.util.*;
import java.util.function.BiPredicate;
import javax.inject.Provider;
import lombok.experimental.UtilityClass;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.operation.OperationRegistry;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;

/**
 * This is a temporary utility class for creating all besu evm related fields needed by the
 * {@link MirrorEvmTxProcessor} execution. With the introduction of the precompiles, this creation will be refactored
 * and encapsulated in the evm module library.
 */
@UtilityClass
public class EvmOperationConstructionUtil {
    private static final String EVM_VERSION_0_30 = "v0.30";
    private static final String EVM_VERSION_0_34 = "v0.34";
    public static final String EVM_VERSION = EVM_VERSION_0_34;

    public static Map<String, Provider<ContractCreationProcessor>> ccps(GasCalculator gasCalculator) {
        final var evm = constructEvm(gasCalculator);
        return Map.of(
                EVM_VERSION_0_30,
                () -> new ContractCreationProcessor(gasCalculator, evm, true, List.of(), 1),
                EVM_VERSION_0_34,
                () -> new ContractCreationProcessor(gasCalculator, evm, true, List.of(), 1));
    }

    public static Map<String, Provider<MessageCallProcessor>> mcps(
            final GasCalculator gasCalculator, final StackedStateFrames<Object> stackedStateFrames) {
        final var evm = constructEvm(gasCalculator);

        return Map.of(
                EVM_VERSION_0_30,
                () -> new MessageCallProcessor(evm, new PrecompileContractRegistry()),
                EVM_VERSION_0_34,
                () -> new HederaEvmMessageCallProcessor(
                        evm, new PrecompileContractRegistry(), precompiles(stackedStateFrames)));
    }

    private static Map<String, PrecompiledContract> precompiles(final StackedStateFrames<Object> stackedStateFrames) {
        final Map<String, PrecompiledContract> hederaPrecompiles = new HashMap<>();
        final var evmFactory = new EvmInfrastructureFactory(new EvmEncodingFacade());
        final var mirrorNodeEvmProperties = new MirrorNodeEvmProperties();
        final var precompileFactory = new PrecompileMapper(new HashSet<>());
        hederaPrecompiles.put(
                EVM_HTS_PRECOMPILED_CONTRACT_ADDRESS,
                new MirrorHTSPrecompiledContract(
                        evmFactory, mirrorNodeEvmProperties, stackedStateFrames, precompileFactory));

        return hederaPrecompiles;
    }

    private static EVM constructEvm(GasCalculator gasCalculator) {
        var operationRegistry = new OperationRegistry();
        BiPredicate<Address, MessageFrame> validator = (Address x, MessageFrame y) -> true;

        registerParisOperations(operationRegistry, gasCalculator, BigInteger.ZERO);
        Set.of(
                        new HederaBalanceOperation(gasCalculator, validator),
                        new HederaDelegateCallOperation(gasCalculator, validator),
                        new HederaExtCodeCopyOperation(gasCalculator, validator),
                        new HederaExtCodeHashOperation(gasCalculator, validator),
                        new HederaExtCodeSizeOperation(gasCalculator, validator),
                        new HederaEvmSLoadOperation(gasCalculator))
                .forEach(operationRegistry::put);

        return new EVM(operationRegistry, gasCalculator, EvmConfiguration.DEFAULT, EvmSpecVersion.PARIS);
    }
}
