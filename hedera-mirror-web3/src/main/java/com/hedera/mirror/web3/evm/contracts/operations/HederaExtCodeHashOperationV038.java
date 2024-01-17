/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

import com.hedera.node.app.service.evm.contracts.operations.HederaExceptionalHaltReason;
import javax.inject.Named;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.FixedStack;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.operation.ExtCodeHashOperation;

/**
 * This class is copied from hedera-services
 *
 * Hedera adapted version of the {@link ExtCodeHashOperation}.
 *
 * <p>Performs an existence check on the requested {@link Address} Halts the execution of the EVM
 * transaction with {@link HederaExceptionalHaltReason#INVALID_SOLIDITY_ADDRESS} if the account does
 * not exist, or it is deleted.
 */
@Named
public class HederaExtCodeHashOperationV038 extends ExtCodeHashOperation {

    public HederaExtCodeHashOperationV038(GasCalculator gasCalculator) {
        super(gasCalculator);
    }

    @Override
    public OperationResult execute(MessageFrame frame, EVM evm) {
        try {
            final Address address = Words.toAddress(frame.popStackItem());
            if (isSystemAccount(address)) {
                frame.pushStackItem(UInt256.ZERO);
                return new OperationResult(cost(true), null);
            }
            final var account = frame.getWorldUpdater().get(address);
            if (account == null) {
                return new OperationResult(cost(true), HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS);
            }
            boolean accountIsWarm =
                    frame.warmUpAddress(address) || this.gasCalculator().isPrecompile(address);
            long localCost = cost(accountIsWarm);
            if (frame.getRemainingGas() < localCost) {
                return new OperationResult(localCost, ExceptionalHaltReason.INSUFFICIENT_GAS);
            } else {
                if (!account.isEmpty()) {
                    frame.pushStackItem(UInt256.fromBytes(account.getCodeHash()));
                } else {
                    frame.pushStackItem(UInt256.ZERO);
                }

                return new OperationResult(localCost, null);
            }
        } catch (final FixedStack.UnderflowException ufe) {
            return new OperationResult(cost(true), ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);
        } catch (final FixedStack.OverflowException ofe) {
            return new OperationResult(cost(true), ExceptionalHaltReason.TOO_MANY_STACK_ITEMS);
        }
    }
}
