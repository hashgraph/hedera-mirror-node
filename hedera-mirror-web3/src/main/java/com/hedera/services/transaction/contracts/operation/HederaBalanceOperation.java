package com.hedera.services.transaction.contracts.operation;

import java.util.function.BiPredicate;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.BalanceOperation;

/**
 * Hedera adapted version of the {@link BalanceOperation}. Performs an existence check on the
 * requested {@link Address} Halts the execution of the EVM transaction with {@link
 * HederaExceptionalHaltReason#INVALID_SOLIDITY_ADDRESS} if the account does not exist or it is
 * deleted.
 */
public class HederaBalanceOperation extends BalanceOperation {

    final private BiPredicate<Address, MessageFrame> addressValidator;

    public HederaBalanceOperation(
            GasCalculator gasCalculator, BiPredicate<Address, MessageFrame> addressValidator) {
        super(gasCalculator);
        this.addressValidator = addressValidator;
    }

    @Override
    public OperationResult execute(MessageFrame frame, EVM evm) {
        return HederaOperationUtil.addressCheckExecution(
                frame,
                () -> frame.getStackItem(0),
                () -> cost(true),
                () -> super.execute(frame, evm),
                addressValidator);
    }
}

