/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.evm.pricing;

import static com.hedera.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_SYSTEM_FILE;
import static com.hedera.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_EXCHANGE_RATE;
import static com.hedera.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_FEE_SCHEDULE;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.file.FileData;
import com.hedera.mirror.web3.repository.FileDataRepository;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import jakarta.inject.Named;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;

/**
 * Rates and fees loader, currently working only with current timestamp.
 */
@CacheConfig(cacheManager = CACHE_MANAGER_SYSTEM_FILE)
@Named
@RequiredArgsConstructor
@CustomLog
public class RatesAndFeesLoader {
    private static final EntityId EXCHANGE_RATE_ENTITY_ID = EntityId.of(0L, 0L, 112L);
    private static final EntityId FEE_SCHEDULE_ENTITY_ID = EntityId.of(0L, 0L, 111L);
    private final FileDataRepository fileDataRepository;

    /**
     * Loads the exchange rates for a given time.
     *
     * @param nanoSeconds timestamp
     * @return exchange rates set
     */
    @Cacheable(cacheNames = CACHE_NAME_EXCHANGE_RATE, unless = "#result == null")
    public ExchangeRateSet loadExchangeRates(final long nanoSeconds) {
        try {
            return getFileData(EXCHANGE_RATE_ENTITY_ID.getId(), nanoSeconds, ExchangeRateSet::parseFrom);
        } catch (InvalidProtocolBufferException e) {
            log.warn("Corrupt rate file at {}, may require remediation!", EXCHANGE_RATE_ENTITY_ID);
            throw new IllegalStateException(String.format("Rates %s are corrupt!", EXCHANGE_RATE_ENTITY_ID));
        }
    }

    /**
     * Load the fee schedules for a given time.
     *
     * @param nanoSeconds timestamp
     * @return current and next fee schedules
     */
    @Cacheable(cacheNames = CACHE_NAME_FEE_SCHEDULE, unless = "#result == null")
    public CurrentAndNextFeeSchedule loadFeeSchedules(final long nanoSeconds) {
        try {
            return getFileData(FEE_SCHEDULE_ENTITY_ID.getId(), nanoSeconds, CurrentAndNextFeeSchedule::parseFrom);
        } catch (InvalidProtocolBufferException e) {
            log.warn("Corrupt fee schedules file at {}, may require remediation!", FEE_SCHEDULE_ENTITY_ID, e);
            throw new IllegalStateException(String.format("Fee schedule %s is corrupt!", FEE_SCHEDULE_ENTITY_ID));
        }
    }

    private <T> T getFileData(long fileId, long nanoSeconds, FileDataParser<T> parser)
            throws InvalidProtocolBufferException {
        int retry = 1;
        int maxRetries = 10;

        // Fallback to an older version of the file if the current file cannot be parsed
        while (true) {
            var fileDataList = fileDataRepository.getFileAtTimestamp(fileId, nanoSeconds);
            var fileDataBytes = getBytesFromFileData(fileDataList);
            try {
                return parser.parse(fileDataBytes.toByteArray());
            } catch (InvalidProtocolBufferException e) {
                if (retry >= maxRetries) {
                    throw e;
                }

                retry++;
                log.warn(
                        "Failed to load file data for fileId {} at {}, failing back to previous file. Retry attempt {}",
                        fileId,
                        nanoSeconds,
                        retry,
                        e);
                nanoSeconds = fileDataList.getFirst().getConsensusTimestamp() - 1;
            }
        }
    }

    private ByteArrayOutputStream getBytesFromFileData(List<FileData> files) {
        try (var bos = new ByteArrayOutputStream()) {
            for (var i = 0; i < files.size(); i++) {
                bos.write(files.get(i).getFileData());
            }
            return bos;
        } catch (IOException ex) {
            throw new IllegalStateException("Error concatenating fileData entries", ex);
        }
    }

    private interface FileDataParser<T> {
        T parse(byte[] bytes) throws InvalidProtocolBufferException;
    }
}
