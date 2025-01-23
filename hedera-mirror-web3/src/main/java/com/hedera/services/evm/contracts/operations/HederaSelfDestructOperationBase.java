/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.services.evm.contracts.operations;

import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.node.app.service.evm.contracts.operations.HederaExceptionalHaltReason;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.SelfDestructOperation;

public class HederaSelfDestructOperationBase extends SelfDestructOperation {

    public HederaSelfDestructOperationBase(final GasCalculator gasCalculator, final boolean eip6780Semantics) {
        super(gasCalculator, eip6780Semantics);
    }

    @Nullable
    protected ExceptionalHaltReason reasonToHalt(
            final Address toBeDeleted,
            final Address beneficiaryAddress,
            final HederaEvmStackedWorldStateUpdater updater) {
        if (toBeDeleted.equals(beneficiaryAddress)) {
            return HederaExceptionalHaltReason.SELF_DESTRUCT_TO_SELF;
        }

        if (updater.contractIsTokenTreasury(toBeDeleted)) {
            return HederaExceptionalHaltReason.CONTRACT_IS_TREASURY;
        }

        if (updater.contractHasAnyBalance(toBeDeleted)) {
            return HederaExceptionalHaltReason.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
        }

        if (updater.contractOwnsNfts(toBeDeleted)) {
            return HederaExceptionalHaltReason.CONTRACT_STILL_OWNS_NFTS;
        }
        return null;
    }

    protected OperationResult reversionWith(final Account beneficiary, final ExceptionalHaltReason reason) {
        final long cost = gasCalculator().selfDestructOperationGasCost(beneficiary, Wei.ONE);
        return new OperationResult(cost, reason);
    }
}
