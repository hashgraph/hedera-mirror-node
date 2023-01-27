package com.hedera.mirror.web3.evm.contracts.execution;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import static org.hyperledger.besu.evm.MainnetEVMs.registerParisOperations;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import javax.inject.Provider;
import lombok.experimental.UtilityClass;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.gascalculator.LondonGasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.operation.OperationRegistry;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;

import com.hedera.node.app.service.evm.contracts.operations.HederaBalanceOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaDelegateCallOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaEvmSLoadOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaExtCodeCopyOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaExtCodeHashOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaExtCodeSizeOperation;

/**
 * This is a temporary utility class for creating all besu evm related fields needed by the
 * {@link MirrorEvmTxProcessor} execution. With the introduction of the precompiles, this creation will be refactored
 * and encapsulated in the evm module library.
 */
@UtilityClass
public class EvmOperationConstructionUtil {
    private static final String EVM_VERSION_0_30 = "v0.30";
    private static final String EVM_VERSION_0_32 = "v0.32";
    static final GasCalculator gasCalculator = new LondonGasCalculator();
    private static final EVM evm = constructEvm();

   public static Map<String, Provider<ContractCreationProcessor>> ccps() {
        return Map.of(
                EVM_VERSION_0_30,
                () -> new ContractCreationProcessor(
                        gasCalculator, evm, true, List.of(), 1),
                EVM_VERSION_0_32,
                () -> new ContractCreationProcessor(
                        gasCalculator, evm, true, List.of(), 1));
    }

   public static Map<String, Provider<MessageCallProcessor>> mcps() {
        return Map.of(
                EVM_VERSION_0_30,
                () -> new MessageCallProcessor(
                        evm, new PrecompileContractRegistry()),
                EVM_VERSION_0_32,
                () -> new MessageCallProcessor(
                        evm, new PrecompileContractRegistry()));
    }

    private static EVM constructEvm() {
        var operationRegistry = new OperationRegistry();
        BiPredicate<Address, MessageFrame> validator = (Address x, MessageFrame y) -> true;

         Set.of(new HederaBalanceOperation(gasCalculator, validator),
                new HederaDelegateCallOperation(gasCalculator, validator),
                new HederaExtCodeCopyOperation(gasCalculator, validator),
                new HederaExtCodeHashOperation(gasCalculator, validator),
                new HederaExtCodeSizeOperation(gasCalculator, validator),
                new HederaEvmSLoadOperation(gasCalculator)).forEach(operationRegistry::put);
        registerParisOperations(operationRegistry, gasCalculator, BigInteger.ZERO);

        return new EVM(operationRegistry, gasCalculator, EvmConfiguration.DEFAULT, EvmSpecVersion.LONDON);
    }
}
