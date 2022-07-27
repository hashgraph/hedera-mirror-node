package com.hedera.services.transaction.operation;

import com.hedera.services.stream.proto.SidecarType;

import com.hedera.services.transaction.operation.gascalculator.StorageGasCalculator;

import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.AbstractOperation;
import org.hyperledger.besu.evm.operation.Operation;
import javax.inject.Inject;
import java.util.Optional;
import java.util.OptionalLong;

import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.ILLEGAL_STATE_CHANGE;

public class SimulateSStoreOperation extends AbstractOperation {
    private static final Operation.OperationResult ILLEGAL_STATE_CHANGE_RESULT =
            new Operation.OperationResult(OptionalLong.empty(), Optional.of(ILLEGAL_STATE_CHANGE));

//    private final boolean checkSuperCost;
    boolean checkSuperCost = true;
    private final StorageGasCalculator storageGasCalculator;
//    private final GlobalDynamicProperties dynamicProperties;

    @Inject
    public SimulateSStoreOperation(
            final GasCalculator gasCalculator,
            final StorageGasCalculator storageGasCalculator
//            final GlobalDynamicProperties dynamicProperties
    ) {
        super(0x55, "SSTORE", 2, 0, 1, gasCalculator);
//        checkSuperCost = !(gasCalculator instanceof GasCalculatorHederaV18);
//        this.dynamicProperties = dynamicProperties;
        this.storageGasCalculator = storageGasCalculator;
    }

    @Override
    public Operation.OperationResult execute(final MessageFrame frame, final EVM evm) {
        final UInt256 key = UInt256.fromBytes(frame.popStackItem());
        final UInt256 value = UInt256.fromBytes(frame.popStackItem());

        final MutableAccount account =
                frame.getWorldUpdater().getAccount(frame.getRecipientAddress()).getMutable();
        if (account == null) {
            return ILLEGAL_STATE_CHANGE_RESULT;
        }

        UInt256 currentValue = account.getStorageValue(key);
        boolean currentZero = currentValue.isZero();
        boolean newZero = value.isZero();
        boolean checkCalculator = checkSuperCost;
        long gasCost = 0L;
        if (currentZero && !newZero) {
            gasCost = storageGasCalculator.gasCostOfStorageIn(frame);

            //FUTURE WORK finish implementation when we introduce WorldUpdater
//            ((HederaWorldUpdater) frame.getWorldUpdater()).addSbhRefund(gasCost);
        } else {
            checkCalculator = true;
        }

        if (checkCalculator) {
            final var address = account.getAddress();
            final var slotIsWarm = frame.warmUpStorage(address, key);
            final var calculator = gasCalculator();
            final var calcGasCost =
                    calculator.calculateStorageCost(account, key, value)
                            + (slotIsWarm ? 0L : calculator.getColdSloadCost());
            gasCost = Math.max(gasCost, calcGasCost);
            frame.incrementGasRefund(
                    gasCalculator().calculateStorageRefundAmount(account, key, value));
        }

        final var optionalCost = OptionalLong.of(gasCost);
        final long remainingGas = frame.getRemainingGas();
        if (frame.isStatic()) {
            return new Operation.OperationResult(optionalCost, Optional.of(ILLEGAL_STATE_CHANGE));
        } else if (remainingGas < gasCost) {
            return new Operation.OperationResult(
                    optionalCost, Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
        }

//        if (dynamicProperties.enabledSidecars().contains(SidecarType.CONTRACT_STATE_CHANGE)) {
//            cacheExistingValue(frame, account.getAddress(), key, currentValue);
//        }

        account.setStorageValue(key, value);
        frame.storageWasUpdated(key, value);
        return new Operation.OperationResult(optionalCost, Optional.empty());
    }
}
