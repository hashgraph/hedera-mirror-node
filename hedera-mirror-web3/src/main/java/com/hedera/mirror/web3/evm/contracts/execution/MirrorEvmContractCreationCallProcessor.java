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

import java.util.List;
import java.util.Optional;
import lombok.CustomLog;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.ModificationNotAllowedException;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.contractvalidation.ContractValidationRule;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.tracing.OperationTracer;

@CustomLog
public class MirrorEvmContractCreationCallProcessor extends ContractCreationProcessor {

    private final long initialContractNonce;

    public MirrorEvmContractCreationCallProcessor(
            GasCalculator gasCalculator,
            EVM evm,
            boolean requireCodeDepositToSucceed,
            List<ContractValidationRule> contractValidationRules,
            long initialContractNonce) {
        super(gasCalculator, evm, requireCodeDepositToSucceed, contractValidationRules, initialContractNonce);
        this.initialContractNonce = initialContractNonce;
    }

    private static boolean accountExists(final Account account) {
        // The account exists if it has sent a transaction
        // or already has its code initialized.
        return account.getNonce() > 0 || !account.getCode().isEmpty();
    }

    /**
     * We need to override this method to allow contract creation with missing sender
     * */
    @Override
    public void start(final MessageFrame frame, final OperationTracer operationTracer) {
        if (log.isTraceEnabled()) {
            log.trace("Executing contract-creation");
        }
        try {

            // Permissively allow the sender to not pay for the contract creation, allowing eth_estimateGas executions
            // with empty or missing senders
            final var senderAccount = frame.getWorldUpdater().getSenderAccount(frame);
            if (senderAccount != null) {
                final MutableAccount sender = senderAccount.getMutable();
                sender.decrementBalance(frame.getValue());
            }

            final MutableAccount contract = frame.getWorldUpdater()
                    .getOrCreate(frame.getContractAddress())
                    .getMutable();
            if (accountExists(contract)) {
                log.trace(
                        "Contract creation error: account has already been created for address {}",
                        frame.getContractAddress());
                frame.setExceptionalHaltReason(Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
                frame.setState(MessageFrame.State.EXCEPTIONAL_HALT);
                operationTracer.traceAccountCreationResult(frame, Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
            } else {
                contract.incrementBalance(frame.getValue());
                contract.setNonce(initialContractNonce);
                contract.clearStorage();
                frame.setState(MessageFrame.State.CODE_EXECUTING);
            }
        } catch (final ModificationNotAllowedException ex) {
            log.trace("Contract creation error: attempt to mutate an immutable account");
            frame.setExceptionalHaltReason(Optional.of(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE));
            frame.setState(MessageFrame.State.EXCEPTIONAL_HALT);
        }
    }
}
