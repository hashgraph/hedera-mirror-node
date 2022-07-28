package com.hedera.mirror.web3.evm.fees;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.Timestamp;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.web3.repository.FileDataRepository;
import com.hedera.services.transaction.fees.HbarCentExchange;

@Singleton
@RequiredArgsConstructor
public class SimulatedHbarCentExchange implements HbarCentExchange {
    private static final Logger log = LogManager.getLogger(
            SimulatedHbarCentExchange.class);

    private static final EntityId EXCHANGE_RATE_ENTITY_ID = new EntityId(0L, 0L, 112L, EntityType.FILE);
    private ExchangeRateSet grpcRates = null;

    private FileDataRepository fileDataRepository;

    public void loadRates(final long now) {
        final var ratesFile = fileDataRepository.findFileByEntityIdAndClosestPreviousTimestamp(now, EXCHANGE_RATE_ENTITY_ID.getId());

        try {
            this.grpcRates = ExchangeRateSet.parseFrom(ratesFile.getFileData());
        } catch (InvalidProtocolBufferException e) {
            log.warn("Corrupt rate file at {}, may require remediation!", EXCHANGE_RATE_ENTITY_ID.toString(), e);
            throw new IllegalStateException(
                    String.format("Rates %s are corrupt!", EXCHANGE_RATE_ENTITY_ID));
        }
    }

    @Override
    public ExchangeRate rate(final Timestamp now) {
        return rateAt(now.getSeconds());
    }

    private ExchangeRate rateAt(final long now) {
        loadRates(now);
        final var currentRate = grpcRates.getCurrentRate();
        final var currentExpiry = currentRate.getExpirationTime().getSeconds();
        return (now < currentExpiry) ? currentRate : grpcRates.getNextRate();
    }
}
