package com.hedera.services.transaction.operation;

import com.hedera.services.transaction.operation.context.TransactionContext;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.SelfDestructOperation;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.BiPredicate;

public class SimulateSelfDestructOperation extends SelfDestructOperation {
    private final TransactionContext txnCtx;

    private final BiPredicate<Address, MessageFrame> addressValidator;

    @Inject
    public SimulateSelfDestructOperation(
            final GasCalculator gasCalculator,
            final TransactionContext txnCtx,
            final BiPredicate<Address, MessageFrame> addressValidator) {
        super(gasCalculator);
        this.txnCtx = txnCtx;
        this.addressValidator = addressValidator;
    }

    @Override
    public Operation.OperationResult execute(final MessageFrame frame, final EVM evm) {
        //FUTURE WORK finish implementation when we introduce StackedUpdaters

//        final var updater = (HederaStackedWorldStateUpdater) frame.getWorldUpdater();
        final var beneficiaryAddress = Words.toAddress(frame.getStackItem(0));
        final var toBeDeleted = frame.getRecipientAddress();
        if (!addressValidator.test(beneficiaryAddress, frame)) {
            return reversionWith(null, SimulateExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS);
        }
//        final var beneficiary = updater.get(beneficiaryAddress);

//        final var exceptionalHaltReason = reasonToHalt(toBeDeleted, beneficiaryAddress, null);
//        if (exceptionalHaltReason != null) {
//            return reversionWith(beneficiary, exceptionalHaltReason);
//        }

//        final var tbdNum = numOfMirror(updater.permissivelyUnaliased(toBeDeleted.toArrayUnsafe()));
//        final var beneficiaryNum =
//                numOfMirror(updater.permissivelyUnaliased(beneficiaryAddress.toArrayUnsafe()));
//        txnCtx.recordBeneficiaryOfDeleted(tbdNum, beneficiaryNum);
        txnCtx.recordBeneficiaryOfDeleted(0, 0);

        return super.execute(frame, evm);
    }

    @Nullable
    private ExceptionalHaltReason reasonToHalt(
            final Address toBeDeleted,
            final Address beneficiaryAddress

            //FUTURE WORK finish implementation when we introduce StackedUpdaters

//            final HederaStackedWorldStateUpdater updater

    ) {
        if (toBeDeleted.equals(beneficiaryAddress)) {
            return SimulateExceptionalHaltReason.SELF_DESTRUCT_TO_SELF;
        }
//        if (updater.contractIsTokenTreasury(toBeDeleted)) {
//            return SimulateExceptionalHaltReason.CONTRACT_IS_TREASURY;
//        }
//        if (updater.contractHasAnyBalance(toBeDeleted)) {
//            return SimulateExceptionalHaltReason.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
//        }
//        if (updater.contractOwnsNfts(toBeDeleted)) {
//            return SimulateExceptionalHaltReason.CONTRACT_STILL_OWNS_NFTS;
//        }
        return null;
    }

    private Operation.OperationResult reversionWith(
            final Account beneficiary, final ExceptionalHaltReason reason) {
        final long cost = gasCalculator().selfDestructOperationGasCost(beneficiary, Wei.ONE);
        return new Operation.OperationResult(OptionalLong.of(cost), Optional.of(reason));
    }
}
