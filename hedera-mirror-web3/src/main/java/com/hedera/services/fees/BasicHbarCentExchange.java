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

package com.hedera.services.fees;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.web3.repository.PricesAndFeesRepository;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.Timestamp;
import javax.inject.Named;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Temporary extracted class from services.
 */
@Named
public final class BasicHbarCentExchange implements HbarCentExchange {
    private final PricesAndFeesRepository pricesAndFeesRepository;
    private static final Logger log = LogManager.getLogger(BasicHbarCentExchange.class);
    private static final EntityId EXCHANGE_RATE_ENTITY_ID = new EntityId(0L, 0L, 112L, EntityType.FILE);

    private ExchangeRateSet exchangeRates;

    public BasicHbarCentExchange(PricesAndFeesRepository pricesAndFeesRepository) {
        this.pricesAndFeesRepository = pricesAndFeesRepository;
    }

    @Override
    public ExchangeRate rate(final Timestamp now) {
        updateExchangeRates(now.getSeconds());
        return rateAt(now.getSeconds());
    }

    private void updateExchangeRates(final long now) {
        final var ratesFile = pricesAndFeesRepository.getExchangeRate(now);

        try {
            exchangeRates = ExchangeRateSet.parseFrom(ratesFile);
        } catch (InvalidProtocolBufferException e) {
            log.warn("Corrupt rate file at {}, may require remediation!", EXCHANGE_RATE_ENTITY_ID, e);
            throw new IllegalStateException(String.format("Rates %s are corrupt!", EXCHANGE_RATE_ENTITY_ID));
        }
    }

    private ExchangeRate rateAt(final long now) {
        final var currentRate = exchangeRates.getCurrentRate();
        final var currentExpiry = currentRate.getExpirationTime().getSeconds();
        return (now < currentExpiry) ? currentRate : exchangeRates.getNextRate();
    }
}
