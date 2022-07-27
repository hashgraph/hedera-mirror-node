package com.hedera.services.transaction.operation;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.DelegateCallOperation;
import org.hyperledger.besu.evm.operation.Operation;
import javax.inject.Inject;
import java.util.function.BiPredicate;

public class SimulateDelegateCallOperation extends DelegateCallOperation {
    private final BiPredicate<Address, MessageFrame> addressValidator;

    @Inject
    public SimulateDelegateCallOperation(
            GasCalculator gasCalculator, BiPredicate<Address, MessageFrame> addressValidator) {
        super(gasCalculator);
        this.addressValidator = addressValidator;
    }

    @Override
    public Operation.OperationResult execute(MessageFrame frame, EVM evm) {
        return SimulateOperationUtil.addressCheckExecution(
                frame,
                () -> to(frame),
                () -> cost(frame),
                () -> super.execute(frame, evm),
                addressValidator);
    }
}
