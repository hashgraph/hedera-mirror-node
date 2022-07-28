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

import com.google.common.primitives.Longs;

import com.hedera.services.transaction.operation.context.HederaStackedWorldStateUpdater;
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
import java.util.Arrays;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.BiPredicate;

public class SimulatedSelfDestructOperation extends SelfDestructOperation {
    private final TransactionContext txnCtx;

    private final BiPredicate<Address, MessageFrame> addressValidator;

    @Inject
    public SimulatedSelfDestructOperation(
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
        final var updater = (HederaStackedWorldStateUpdater) frame.getWorldUpdater();

        final var beneficiaryAddress = Words.toAddress(frame.getStackItem(0));
        final var toBeDeleted = frame.getRecipientAddress();
        if (!addressValidator.test(beneficiaryAddress, frame)) {
            return reversionWith(null, SimulatedExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS);
        }
        final var beneficiary = updater.get(beneficiaryAddress);

        final var exceptionalHaltReason = reasonToHalt(toBeDeleted, beneficiaryAddress, null);
        if (exceptionalHaltReason != null) {
            return reversionWith(beneficiary, exceptionalHaltReason);
        }

        final var tbdNum = numOfMirror(updater.permissivelyUnaliased(toBeDeleted.toArrayUnsafe()));
        final var beneficiaryNum =
                numOfMirror(updater.permissivelyUnaliased(beneficiaryAddress.toArrayUnsafe()));
        txnCtx.recordBeneficiaryOfDeleted(tbdNum, beneficiaryNum);

        return super.execute(frame, evm);
    }

    public static long numOfMirror(final byte[] evmAddress) {
        return Longs.fromByteArray(Arrays.copyOfRange(evmAddress, 12, 20));
    }

    @Nullable
    private ExceptionalHaltReason reasonToHalt(
            final Address toBeDeleted,
            final Address beneficiaryAddress,

            //FUTURE WORK finish implementation when we introduce StackedUpdaters
            final HederaStackedWorldStateUpdater updater

    ) {
        if (toBeDeleted.equals(beneficiaryAddress)) {
            return SimulatedExceptionalHaltReason.SELF_DESTRUCT_TO_SELF;
        }
        if (updater.contractIsTokenTreasury(toBeDeleted)) {
            return SimulatedExceptionalHaltReason.CONTRACT_IS_TREASURY;
        }
        if (updater.contractHasAnyBalance(toBeDeleted)) {
            return SimulatedExceptionalHaltReason.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
        }
        if (updater.contractOwnsNfts(toBeDeleted)) {
            return SimulatedExceptionalHaltReason.CONTRACT_STILL_OWNS_NFTS;
        }
        return null;
    }

    private Operation.OperationResult reversionWith(
            final Account beneficiary, final ExceptionalHaltReason reason) {
        final long cost = gasCalculator().selfDestructOperationGasCost(beneficiary, Wei.ONE);
        return new Operation.OperationResult(OptionalLong.of(cost), Optional.of(reason));
    }
}
