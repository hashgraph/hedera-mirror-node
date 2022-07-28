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

import com.hedera.services.transaction.operation.context.HederaWorldUpdater;
import com.hedera.services.transaction.operation.gascalculator.StorageGasCalculator;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import javax.inject.Inject;

public class SimulatedCreateOperation extends AbstractRecordingCreateOperation {
    private final StorageGasCalculator storageGasCalculator;

    @Inject
    public SimulatedCreateOperation(
            final GasCalculator gasCalculator,
            final StorageGasCalculator storageGasCalculator) {
        super(
                0xF0,
                "ħCREATE",
                3,
                1,
                1,
                gasCalculator);
        this.storageGasCalculator = storageGasCalculator;
    }

    @Override
    public long cost(final MessageFrame frame) {
        final var calculator = gasCalculator();
        return calculator.createOperationGasCost(frame)
                + storageGasCalculator.creationGasCost(frame, calculator);
    }

    @Override
    protected Address targetContractAddress(final MessageFrame frame) {
        final var updater = (HederaWorldUpdater) frame.getWorldUpdater();
        final Address address = updater.newContractAddress(frame.getRecipientAddress());
        frame.warmUpAddress(address);
        return address;
    }
}
