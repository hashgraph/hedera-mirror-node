package com.hedera.mirror.web3.evm.config;

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

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public
class EvmConfiguration {

    public static final String CACHE_MANAGER_10MIN = "cacheManager10Min";
    public static final String CACHE_MANAGER_500MS = "cacheManager500Ms";

    @Bean
    Caffeine caffeineConfig() {
        return Caffeine.newBuilder();
    }

    @Bean(CACHE_MANAGER_10MIN)
    CacheManager cacheManager10MIN() {
        final var caffeine =
                Caffeine.newBuilder().expireAfterAccess(10, TimeUnit.MINUTES).maximumSize(10000);
        final CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCaffeine(caffeine);
        return caffeineCacheManager;
    }

    @Bean(CACHE_MANAGER_500MS)
    CacheManager cacheManager500MS() {
        final var caffeine =
                Caffeine.newBuilder().expireAfterAccess(500, TimeUnit.MILLISECONDS).maximumSize(1);
        final CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCaffeine(caffeine);
        return caffeineCacheManager;
    }
}
