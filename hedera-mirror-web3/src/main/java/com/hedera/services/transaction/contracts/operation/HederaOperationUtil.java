package com.hedera.services.transaction.contracts.operation;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.BiPredicate;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.internal.FixedStack;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.operation.Operation;

/** Utility methods used by Hedera adapted {@link org.hyperledger.besu.evm.operation.Operation} */
public final class HederaOperationUtil {
    private HederaOperationUtil() {
        throw new UnsupportedOperationException("Utility Class");
    }

    private static final ExceptionalHaltReason INVALID_SOLIDITY_ADDRESS =
            HederaExceptionalHalt.INVALID_SOLIDITY_ADDRESS;

    enum HederaExceptionalHalt implements ExceptionalHaltReason {
        INVALID_SOLIDITY_ADDRESS("Invalid account reference");

        final String description;

        HederaExceptionalHalt(final String description) {
            this.description = description;
        }

        @Override
        public String getDescription() {
            return description;
        }
    }

    /**
     * An extracted address check and execution of extended Hedera Operations. Halts the execution
     * of the EVM transaction with {INVALID_SOLIDITY_ADDRESS} if
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
                        Optional.of(INVALID_SOLIDITY_ADDRESS));
            }

            return supplierExecution.get();
        } catch (final FixedStack.UnderflowException ufe) {
            return new Operation.OperationResult(
                    OptionalLong.of(supplierHaltGasCost.getAsLong()),
                    Optional.of(ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS));
        }
    }
}
