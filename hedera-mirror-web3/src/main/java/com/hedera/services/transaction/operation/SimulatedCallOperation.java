/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.transaction.operation;

import com.hedera.services.transaction.operation.context.EvmSigsVerifier;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.CallOperation;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import javax.inject.Inject;
import java.util.Map;
import java.util.function.BiPredicate;

public class SimulatedCallOperation extends CallOperation {
    private final EvmSigsVerifier sigsVerifier;
    private final BiPredicate<Address, MessageFrame> addressValidator;
    private final Map<String, PrecompiledContract> precompiledContractMap;

    @Inject
    public SimulatedCallOperation(
            final EvmSigsVerifier sigsVerifier,
            final GasCalculator gasCalculator,
            final BiPredicate<Address, MessageFrame> addressValidator,
            final Map<String, PrecompiledContract> precompiledContractMap) {
        super(gasCalculator);
        this.sigsVerifier = sigsVerifier;
        this.addressValidator = addressValidator;
        this.precompiledContractMap = precompiledContractMap;
    }

    @Override
    public Operation.OperationResult execute(final MessageFrame frame, final EVM evm) {
        return SimulatedOperationUtil.addressSignatureCheckExecution(
                sigsVerifier,
                frame,
                to(frame),
                () -> cost(frame),
                () -> super.execute(frame, evm),
                addressValidator,
                precompiledContractMap);
    }
}
