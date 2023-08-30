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

package com.hedera.mirror.web3.evm.utils;

import com.hedera.mirror.web3.evm.exception.MissingResultException;
import com.hedera.mirror.web3.repository.RecordFileRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PrngLogic {
    private static final Logger log = LogManager.getLogger(PrngLogic.class);

    public static final byte[] MISSING_BYTES = new byte[0];
    private final RecordFileRepository recordFileRepository;

    public PrngLogic(final RecordFileRepository recordFileRepository) {
        this.recordFileRepository = recordFileRepository;
    }

    public final byte[] getLatestRecordRunningHashBytes() {
        final String latestRunningHash;
        final var latestRecordFile = recordFileRepository
                .findLatest()
                .orElseThrow(() -> new MissingResultException("No record file available."));
        latestRunningHash = latestRecordFile.getHash();
        if (latestRunningHash == null) {
            log.info("No record running hash available to generate random number");
            return MISSING_BYTES;
        }
        return latestRunningHash.getBytes();
    }
}
