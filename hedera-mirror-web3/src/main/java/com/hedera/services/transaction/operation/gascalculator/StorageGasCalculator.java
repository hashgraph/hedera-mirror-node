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
package com.hedera.services.transaction.operation.gascalculator;

import com.hedera.services.transaction.operation.helpers.StorageExpiry;

import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import javax.inject.Inject;

public class StorageGasCalculator {
    private static final int SECONDS_PER_HOUR = 3600;
    public static final String SBH_CONTEXT_KEY = "sbh";
    public static final String EXPIRY_ORACLE_CONTEXT_KEY = "expiryOracle";

    @Inject
    public StorageGasCalculator() {
        // Dagger2
    }

    public long gasCostOfStorageIn(final MessageFrame frame) {
        final var baseFrame = base(frame);
        final var expectedLifetimeSecs = effStorageLifetime(frame, baseFrame);
        final long sbhPrice = baseFrame.getContextVariable(SBH_CONTEXT_KEY);
        final var storagePrice = (expectedLifetimeSecs * sbhPrice) / SECONDS_PER_HOUR;
        final var gasPrice = frame.getGasPrice().toLong();
        return storagePrice / gasPrice;
    }

    private static long effStorageLifetime(final MessageFrame frame, final MessageFrame baseFrame) {
        final StorageExpiry.Oracle expiryOracle =
                baseFrame.getContextVariable(EXPIRY_ORACLE_CONTEXT_KEY);
        final var now = frame.getBlockValues().getTimestamp();
        final var expectedLifetimeSecs = expiryOracle.storageExpiryIn(frame) - now;
        return Math.max(0, expectedLifetimeSecs);
    }

    private static MessageFrame base(final MessageFrame frame) {
        return frame.getMessageFrameStack().getLast();
    }

    public long creationGasCost(MessageFrame frame, GasCalculator calculator) {
        return 0;
    }
}
