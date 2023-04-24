/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.evm.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.hedera.mirror.web3.evm.pricing.RatesAndFeesLoader;
import com.hedera.mirror.web3.repository.properties.CacheProperties;
import com.hedera.services.contracts.gascalculator.GasCalculatorHederaV22;
import com.hedera.services.fees.BasicHbarCentExchange;
import com.hedera.services.fees.calculation.BasicFcfsUsagePrices;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableCaching
@RequiredArgsConstructor
public class EvmConfiguration {

    private final CacheProperties cacheProperties;

    public static final String CACHE_MANAGER_FEE = "cacheManagerFee";
    public static final String CACHE_MANAGER_10MIN = "cacheManager10Min";
    public static final String CACHE_MANAGER_500MS = "cacheManager500Ms";
    public static final String CACHE_MANAGER_STATE = "cacheManagerState";
    public static final String CACHE_MANAGER_ENTITY = "cacheManagerEntity";
    public static final String CACHE_MANAGER_TOKEN = "cacheManagerToken";

    @Bean(CACHE_MANAGER_STATE)
    CacheManager cacheManagerState() {
        final CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCacheSpecification(cacheProperties.getContractState());
        return caffeineCacheManager;
    }

    @Bean(CACHE_MANAGER_ENTITY)
    CacheManager cacheManagerEntity() {
        final CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCacheSpecification(cacheProperties.getEntity());
        return caffeineCacheManager;
    }

    @Bean(CACHE_MANAGER_TOKEN)
    CacheManager cacheManagerToken() {
        final CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCacheSpecification(cacheProperties.getToken());
        return caffeineCacheManager;
    }

    @Bean(CACHE_MANAGER_FEE)
    CacheManager cacheManagerFee() {
        final CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCacheSpecification(cacheProperties.getFee());
        return caffeineCacheManager;
    }

    @Bean(CACHE_MANAGER_10MIN)
    @Primary
    CacheManager cacheManager10Min() {
        final var caffeine =
                Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(10000);
        final CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCaffeine(caffeine);
        return caffeineCacheManager;
    }

    @Bean(CACHE_MANAGER_500MS)
    CacheManager cacheManager500MS() {
        final var caffeine = Caffeine.newBuilder()
                .expireAfterWrite(500, TimeUnit.MILLISECONDS)
                .maximumSize(1);
        final CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCaffeine(caffeine);
        return caffeineCacheManager;
    }

    @Bean
    GasCalculatorHederaV22 gasCalculatorHederaV22(
            BasicFcfsUsagePrices usagePricesProvider, BasicHbarCentExchange hbarCentExchange) {
        return new GasCalculatorHederaV22(usagePricesProvider, hbarCentExchange);
    }

    @Bean
    BasicFcfsUsagePrices basicFcfsUsagePrices(RatesAndFeesLoader ratesAndFeesLoader) {
        return new BasicFcfsUsagePrices(ratesAndFeesLoader);
    }

    @Bean
    BasicHbarCentExchange basicHbarCentExchange(RatesAndFeesLoader ratesAndFeesLoader) {
        return new BasicHbarCentExchange(ratesAndFeesLoader);
    }
}
