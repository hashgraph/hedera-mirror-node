package com.hedera.services.transaction.operation;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.ExtCodeCopyOperation;
import javax.inject.Inject;
import java.util.function.BiPredicate;

import static org.hyperledger.besu.evm.internal.Words.clampedToLong;

public class SimulateExtCodeCopyOperation extends ExtCodeCopyOperation {
    private final BiPredicate<Address, MessageFrame> addressValidator;

    @Inject
    public SimulateExtCodeCopyOperation(
            GasCalculator gasCalculator, BiPredicate<Address, MessageFrame> addressValidator) {
        super(gasCalculator);
        this.addressValidator = addressValidator;
    }

    @Override
    public OperationResult execute(MessageFrame frame, EVM evm) {
        final long memOffset = clampedToLong(frame.getStackItem(1));
        final long numBytes = clampedToLong(frame.getStackItem(3));

        return SimulateOperationUtil.addressCheckExecution(
                frame,
                () -> frame.getStackItem(0),
                () -> cost(frame, memOffset, numBytes, true),
                () -> super.execute(frame, evm),
                addressValidator);
    }
}
