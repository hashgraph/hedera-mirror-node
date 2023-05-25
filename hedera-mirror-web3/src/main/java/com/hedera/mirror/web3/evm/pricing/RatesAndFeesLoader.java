/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_FEE;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.web3.repository.FileDataRepository;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import jakarta.inject.Named;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;

/**
 * Rates and fees loader, currently working only with current timestamp.
 */
@Named
@RequiredArgsConstructor
@CustomLog
public class RatesAndFeesLoader {
    private final FileDataRepository fileDataRepository;
    private static final EntityId EXCHANGE_RATE_ENTITY_ID = new EntityId(0L, 0L, 112L, EntityType.FILE);
    private static final EntityId FEE_SCHEDULE_ENTITY_ID = new EntityId(0L, 0L, 111L, EntityType.FILE);

    /**
     * Loads the exchange rates for a given time. Currently, works only with current timestamp.
     * @param nanoSeconds timestamp
     * @return exchange rates set
     */
    @Cacheable(
            cacheNames = "rates_and_fee.exchange_rate",
            cacheManager = CACHE_MANAGER_FEE,
            key = "'now'",
            unless = "#result == null")
    public ExchangeRateSet loadExchangeRates(final long nanoSeconds) {
        final var ratesFile = fileDataRepository.getFileAtTimestamp(EXCHANGE_RATE_ENTITY_ID.getId(), nanoSeconds);
        try {
            return ExchangeRateSet.parseFrom(ratesFile);
        } catch (InvalidProtocolBufferException e) {
            log.warn("Corrupt rate file at {}, may require remediation!", EXCHANGE_RATE_ENTITY_ID, e);
            throw new IllegalStateException(String.format("Rates %s are corrupt!", EXCHANGE_RATE_ENTITY_ID));
        }
    }

    /**
     * Load the fee schedules for a given time. Currently, works only with current timestamp.
     * @param nanoSeconds timestamp
     * @return current and next fee schedules
     */
    @Cacheable(
            cacheNames = "rates_and_fee.fee_schedules",
            cacheManager = CACHE_MANAGER_FEE,
            key = "'now'",
            unless = "#result == null")
    public CurrentAndNextFeeSchedule loadFeeSchedules(final long nanoSeconds) {
        final var feeScheduleFile = fileDataRepository.getFileAtTimestamp(FEE_SCHEDULE_ENTITY_ID.getId(), nanoSeconds);

        try {
            return CurrentAndNextFeeSchedule.parseFrom(feeScheduleFile);
        } catch (InvalidProtocolBufferException e) {
            log.warn("Corrupt fee schedules file at {}, may require remediation!", FEE_SCHEDULE_ENTITY_ID, e);
            throw new IllegalStateException(String.format("Fee schedule %s is corrupt!", FEE_SCHEDULE_ENTITY_ID));
        }
    }
}
