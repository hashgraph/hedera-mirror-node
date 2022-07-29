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
import com.hedera.services.transaction.operation.context.HederaStackedWorldStateUpdater;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.internal.FixedStack;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.BiPredicate;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public class SimulatedOperationUtil {
    private SimulatedOperationUtil() {
        throw new UnsupportedOperationException("Utility Class");
    }

    /**
     * An extracted address check and execution of extended Hedera Operations. Halts the execution
     * of the EVM transaction with {@link SimulatedExceptionalHaltReason#INVALID_SOLIDITY_ADDRESS} if
     * the account does not exist, or it is deleted.
     *
     * @param frame The current message frame
     * @param supplierAddressBytes Supplier for the address bytes
     * @param supplierHaltGasCost Supplier for the gas cost
     * @param supplierExecution Supplier with the execution
     * @param addressValidator Address validator predicate
     * @return The operation result of the execution
     */
    public static Operation.OperationResult addressCheckExecution(
            MessageFrame frame,
            Supplier<Bytes> supplierAddressBytes,
            LongSupplier supplierHaltGasCost,
            Supplier<Operation.OperationResult> supplierExecution,
            BiPredicate<Address, MessageFrame> addressValidator) {
        try {
            final var address = Words.toAddress(supplierAddressBytes.get());
            if (Boolean.FALSE.equals(addressValidator.test(address, frame))) {
                return new Operation.OperationResult(
                        OptionalLong.of(supplierHaltGasCost.getAsLong()),
                        Optional.of(SimulatedExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS));
            }

            return supplierExecution.get();
        } catch (final FixedStack.UnderflowException ufe) {
            return new Operation.OperationResult(
                    OptionalLong.of(supplierHaltGasCost.getAsLong()),
                    Optional.of(ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS));
        }
    }

    /**
     * An extracted address and signature check, including a further execution of {@link
     * SimulatedCallOperation} and {@link SimulatedCallCodeOperation} Performs an existence check on the
     * {@link Address} to be called Halts the execution of the EVM transaction with {@link
     * SimulatedExceptionalHaltReason#INVALID_SOLIDITY_ADDRESS} if the account does not exist or it is
     * deleted.
     *
     * <p>If the target {@link Address} has
     * required signatures set to true,
     * verification of the provided signature is performed. If the signature is not active, the
     * execution is halted with {@link SimulatedExceptionalHaltReason#INVALID_SIGNATURE}.
     *
     * @param sigsVerifier The signature
     * @param frame The current message frame
     * @param address The target address
     * @param supplierHaltGasCost Supplier for the gas cost
     * @param supplierExecution Supplier with the execution
     * @param addressValidator Address validator predicate
     * @param precompiledContractMap Map of addresses to contracts
     * @return The operation result of the execution
     */
    public static Operation.OperationResult addressSignatureCheckExecution(
            final EvmSigsVerifier sigsVerifier,
            final MessageFrame frame,
            final Address address,
            final LongSupplier supplierHaltGasCost,
            final Supplier<Operation.OperationResult> supplierExecution,
            final BiPredicate<Address, MessageFrame> addressValidator,
            final Map<String, PrecompiledContract> precompiledContractMap) {
        // The Precompiled contracts verify their signatures themselves
        if (precompiledContractMap.containsKey(address.toShortHexString())) {
            return supplierExecution.get();
        }

        final var updater = (HederaStackedWorldStateUpdater) frame.getWorldUpdater();
        final var account = updater.get(address);
        if (Boolean.FALSE.equals(addressValidator.test(address, frame))) {
            return new Operation.OperationResult(
                    OptionalLong.of(supplierHaltGasCost.getAsLong()),
                    Optional.of(SimulatedExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS));
        }
        boolean isDelegateCall = !frame.getContractAddress().equals(frame.getRecipientAddress());
        boolean sigReqIsMet;

        // if this is a delegate call activeContract should be the recipient address
        // otherwise it should be the contract address
        if (isDelegateCall) {
            sigReqIsMet =
                    sigsVerifier.hasActiveKeyOrNoReceiverSigReq(
                            true,
                            account.getAddress(),
                            frame.getRecipientAddress(),
                            updater.trackingLedgers());
        } else {
            sigReqIsMet =
                    sigsVerifier.hasActiveKeyOrNoReceiverSigReq(
                            false,
                            account.getAddress(),
                            frame.getContractAddress(),
                            updater.trackingLedgers());
        }
        if (!sigReqIsMet) {
            return new Operation.OperationResult(
                    OptionalLong.of(supplierHaltGasCost.getAsLong()),
                    Optional.of(SimulatedExceptionalHaltReason.INVALID_SIGNATURE));
        }

        return supplierExecution.get();
    }
}
