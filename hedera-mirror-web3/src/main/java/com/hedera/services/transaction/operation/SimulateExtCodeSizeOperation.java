package com.hedera.services.transaction.operation;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.ExtCodeSizeOperation;
import javax.inject.Inject;
import java.util.function.BiPredicate;

public class SimulateExtCodeSizeOperation extends ExtCodeSizeOperation {
    private final BiPredicate<Address, MessageFrame> addressValidator;

    @Inject
    public SimulateExtCodeSizeOperation(
            GasCalculator gasCalculator, BiPredicate<Address, MessageFrame> addressValidator) {
        super(gasCalculator);
        this.addressValidator = addressValidator;
    }

    @Override
    public OperationResult execute(MessageFrame frame, EVM evm) {
        return SimulateOperationUtil.addressCheckExecution(
                frame,
                () -> frame.getStackItem(0),
                () -> cost(true),
                () -> super.execute(frame, evm),
                addressValidator);
    }
}
