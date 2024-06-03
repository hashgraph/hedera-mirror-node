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

import com.google.common.primitives.Bytes;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.file.FileData;
import com.hedera.mirror.web3.repository.FileDataRepository;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;

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
    private static final String RETRY_NANOS = "nanos";
    private final RetryListener retryListener = new RetryListener() {
        @Override
        public <T, E extends Throwable> void onError(
                RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
            var fileId = (long) context.getAttribute("fileId");
            var nanosLog = (long) context.getAttribute("nanosLog");
            log.warn(
                    "Failed to load file data for fileId {} at {}, failing back to previous file. Retry attempt {}. Exception: ",
                    fileId,
                    nanosLog,
                    context.getRetryCount(),
                    throwable);
        }
    };
    private final RetryTemplate retryTemplate = RetryTemplate.builder()
            .maxAttempts(10)
            .withListener(retryListener)
            .retryOn(InvalidProtocolBufferException.class)
            .build();

    private final FileDataRepository fileDataRepository;

    /**
     * Loads the exchange rates for a given time. Currently, works only with current timestamp.
     *
     * @param nanoSeconds timestamp
     * @return exchange rates set
     */
    @Cacheable(cacheNames = CACHE_NAME_EXCHANGE_RATE, key = "'now'", unless = "#result == null")
    public ExchangeRateSet loadExchangeRates(final long nanoSeconds) {
        try {
            return getFileData(EXCHANGE_RATE_ENTITY_ID.getId(), nanoSeconds, ExchangeRateSet::parseFrom);
        } catch (InvalidProtocolBufferException e) {
            log.warn("Corrupt rate file at {}, may require remediation!", EXCHANGE_RATE_ENTITY_ID);
            throw new IllegalStateException(String.format("Rates %s are corrupt!", EXCHANGE_RATE_ENTITY_ID));
        }
    }

    /**
     * Load the fee schedules for a given time. Currently, works only with current timestamp.
     *
     * @param nanoSeconds timestamp
     * @return current and next fee schedules
     */
    @Cacheable(cacheNames = CACHE_NAME_FEE_SCHEDULE, key = "'now'", unless = "#result == null")
    public CurrentAndNextFeeSchedule loadFeeSchedules(final long nanoSeconds) {
        try {
            return getFileData(FEE_SCHEDULE_ENTITY_ID.getId(), nanoSeconds, CurrentAndNextFeeSchedule::parseFrom);
        } catch (InvalidProtocolBufferException e) {
            log.warn("Corrupt fee schedules file at {}, may require remediation!", FEE_SCHEDULE_ENTITY_ID, e);
            throw new IllegalStateException(String.format("Fee schedule %s is corrupt!", FEE_SCHEDULE_ENTITY_ID));
        }
    }

    private <T> T getFileData(long fileId, final long nanoSeconds, FileDataParser<T> parser)
            throws InvalidProtocolBufferException {
        return retryTemplate.execute(retryContext -> {
            long nanos = retryContext.hasAttribute(RETRY_NANOS)
                    ? (long) retryContext.getAttribute(RETRY_NANOS)
                    : nanoSeconds;

            var fileDataList = fileDataRepository.getFileAtTimestamp(fileId, nanos);

            // Set the values used by the RetryListener
            retryContext.setAttribute("fileId", fileId);
            retryContext.setAttribute("nanosLog", nanos);
            retryContext.setAttribute(RETRY_NANOS, fileDataList.getFirst().getConsensusTimestamp() - 1);

            var fileDataBytes = getBytesFromFileData(fileDataList);
            return parser.parse(fileDataBytes);
        });
    }

    private byte[] getBytesFromFileData(List<FileData> files) {
        List<byte[]> fileDataBytesList = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            fileDataBytesList.add(files.get(i).getFileData());
        }
        return Bytes.concat(fileDataBytesList.toArray(new byte[0][]));
    }

    private interface FileDataParser<T> {
        T parse(byte[] bytes) throws InvalidProtocolBufferException;
    }
}
