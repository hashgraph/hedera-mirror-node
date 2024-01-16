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

package com.hedera.mirror.web3.evm.contracts.operations;

import static com.hedera.mirror.web3.evm.store.contract.AbstractEvmStackedLedgerUpdater.isSystemAccount;
import static com.hedera.node.app.service.evm.contracts.operations.HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS;

import edu.umd.cs.findbugs.annotations.NonNull;
import jakarta.inject.Named;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.FixedStack;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.operation.ExtCodeHashOperation;

@Named
public class CustomExtCodeHashOperation extends ExtCodeHashOperation {
    private static final OperationResult UNDERFLOW_RESPONSE =
            new OperationResult(0, ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);

    public CustomExtCodeHashOperation(@NonNull final GasCalculator gasCalculator) {
        super(gasCalculator);
    }

    @Override
    public OperationResult execute(@NonNull final MessageFrame frame, @NonNull final EVM evm) {
        try {
            final var address = Words.toAddress(frame.getStackItem(0));
            // Special behavior for long-zero addresses below 0.0.1001
            if (isSystemAccount(address)) {
                frame.popStackItem();
                frame.pushStackItem(UInt256.ZERO);
                return new OperationResult(cost(true), null);
            }
            // Otherwise the address must be present
            if (frame.getWorldUpdater().get(address) == null) {
                return new OperationResult(cost(true), INVALID_SOLIDITY_ADDRESS);
            }
            return super.execute(frame, evm);
        } catch (FixedStack.UnderflowException ignore) {
            return UNDERFLOW_RESPONSE;
        }
    }
}
