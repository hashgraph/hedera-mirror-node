package com.hedera.services.transaction.operation;

import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.FixedStack;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.operation.ExtCodeHashOperation;
import javax.inject.Inject;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.BiPredicate;

public class SimulateExtCodeHashOperation extends ExtCodeHashOperation {
    private final BiPredicate<Address, MessageFrame> addressValidator;

    @Inject
    public SimulateExtCodeHashOperation(
            GasCalculator gasCalculator, BiPredicate<Address, MessageFrame> addressValidator) {
        super(gasCalculator);
        this.addressValidator = addressValidator;
    }

    @Override
    public OperationResult execute(MessageFrame frame, EVM evm) {
        try {
            final Address address = Words.toAddress(frame.popStackItem());
            if (!addressValidator.test(address, frame)) {
                return new OperationResult(
                        OptionalLong.of(cost(true)),
                        Optional.of(SimulateExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS));
            }
            final var account = frame.getWorldUpdater().get(address);
            boolean accountIsWarm =
                    frame.warmUpAddress(address) || this.gasCalculator().isPrecompile(address);
            OptionalLong optionalCost = OptionalLong.of(this.cost(accountIsWarm));
            if (frame.getRemainingGas() < optionalCost.getAsLong()) {
                return new OperationResult(
                        optionalCost, Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
            } else {
                if (!account.isEmpty()) {
                    frame.pushStackItem(UInt256.fromBytes(account.getCodeHash()));
                } else {
                    frame.pushStackItem(UInt256.ZERO);
                }

                return new OperationResult(optionalCost, Optional.empty());
            }
        } catch (final FixedStack.UnderflowException ufe) {
            return new OperationResult(
                    OptionalLong.of(cost(true)),
                    Optional.of(ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS));
        }
    }
}
