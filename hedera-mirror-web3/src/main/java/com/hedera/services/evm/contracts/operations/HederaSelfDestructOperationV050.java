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

import com.hedera.node.app.service.evm.contracts.operations.HederaExceptionalHaltReason;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.SelfDestructOperation;

/**
 * Hedera adapted version of the {@link SelfDestructOperation}.
 *
 * <p>Performs an existence check on the beneficiary {@link Address} Halts the execution of the EVM
 * transaction with {@link HederaExceptionalHaltReason#INVALID_SOLIDITY_ADDRESS} if the account does not exist, or it is
 * deleted.
 *
 * <p>Halts the execution of the EVM transaction with {@link
 * HederaExceptionalHaltReason#SELF_DESTRUCT_TO_SELF} if the beneficiary address is the same as the address being
 * destructed.
 */
public class HederaSelfDestructOperationV050 extends HederaSelfDestructOperationV046 {

    public HederaSelfDestructOperationV050(
            GasCalculator gasCalculator,
            final BiPredicate<Address, MessageFrame> addressValidator,
            final Predicate<Address> systemAccountDetector) {
        super(gasCalculator, addressValidator, systemAccountDetector, true);
    }
}
