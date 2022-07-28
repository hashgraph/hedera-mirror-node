/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

import com.hedera.services.transaction.operation.context.HederaStackedWorldStateUpdater;
import com.hedera.services.transaction.operation.gascalculator.StorageGasCalculator;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import javax.inject.Inject;

import static com.hedera.services.transaction.operation.util.MiscUtils.keccak256DigestOf;
import static org.hyperledger.besu.evm.internal.Words.clampedToLong;

public class SimulatedCreate2Operation extends AbstractRecordingCreateOperation {
    private static final Bytes PREFIX = Bytes.fromHexString("0xFF");

    private final StorageGasCalculator storageGasCalculator;

    @Inject
    public SimulatedCreate2Operation(
            final GasCalculator gasCalculator,
            final StorageGasCalculator storageGasCalculator) {
        super(
                0xF5,
                "ħCREATE2",
                4,
                1,
                1,
                gasCalculator);
        this.storageGasCalculator = storageGasCalculator;
    }

    @Override
    protected long cost(final MessageFrame frame) {
        final var calculator = gasCalculator();
        return calculator.create2OperationGasCost(frame)
                + storageGasCalculator.creationGasCost(frame, calculator);
    }

    @Override
    protected Address targetContractAddress(final MessageFrame frame) {
        final var sourceAddressOrAlias = frame.getRecipientAddress();
        final var offset = clampedToLong(frame.getStackItem(1));
        final var length = clampedToLong(frame.getStackItem(2));

        final var updater = (HederaStackedWorldStateUpdater) frame.getWorldUpdater();
        final var source = updater.priorityAddress(sourceAddressOrAlias);

        final Bytes32 salt = UInt256.fromBytes(frame.getStackItem(3));
        final var initCode = frame.readMutableMemory(offset, length);
        final var hash = keccak256(Bytes.concatenate(PREFIX, source, salt, keccak256(initCode)));
        final var alias = Address.wrap(hash.slice(12, 20));

        final Address address = updater.newAliasedContractAddress(sourceAddressOrAlias, alias);
        frame.warmUpAddress(address);
        frame.warmUpAddress(alias);
        return alias;
    }

    private static Bytes32 keccak256(final Bytes input) {
        return Bytes32.wrap(keccak256DigestOf(input.toArrayUnsafe()));
    }
}
