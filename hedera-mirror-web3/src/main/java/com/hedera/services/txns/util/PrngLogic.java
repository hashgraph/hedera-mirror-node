/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.txns.util;

import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This is a modified copy of the PRNGLogic class from the hedera-services repository.
 *
 * The main differences from the original version are as follows:
 * - RunningHashLeafSupplier returns the latest RecordFile's running hash.
 * - Removed unused logic.
 */
public class PrngLogic {
    private static final Logger log = LogManager.getLogger(PrngLogic.class);

    public static final byte[] MISSING_BYTES = new byte[0];
    private final Supplier<byte[]> runningHashLeafSupplier;

    public PrngLogic(final Supplier<byte[]> runningHashLeafSupplier) {
        this.runningHashLeafSupplier = runningHashLeafSupplier;
    }

    public final byte[] getLatestRecordRunningHashBytes() {
        final byte[] latestRunningHashBytes;
        latestRunningHashBytes = runningHashLeafSupplier.get();
        if (latestRunningHashBytes == null || isZeroedHash(latestRunningHashBytes)) {
            log.info("No record running hash available to generate random number");
            return MISSING_BYTES;
        }
        return latestRunningHashBytes;
    }

    private boolean isZeroedHash(final byte[] hashBytes) {
        for (final byte b : hashBytes) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }
}
