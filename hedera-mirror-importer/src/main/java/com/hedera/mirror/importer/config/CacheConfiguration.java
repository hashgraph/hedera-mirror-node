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

package com.hedera.mirror.importer.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@ConditionalOnProperty(prefix = "spring.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableCaching
@RequiredArgsConstructor
public class CacheConfiguration {

    public static final String EXPIRE_AFTER_5M = "cacheManagerExpireAfter5m";
    public static final String CACHE_MANAGER_ALIAS = "cacheManagerAlias";
    public static final String CACHE_MANAGER_TABLE_TIME_PARTITION = "cacheManagerTableTimePartition";

    @Bean(EXPIRE_AFTER_5M)
    @Primary
    CacheManager cacheManager5m() {
        var caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCacheSpecification("maximumSize=100,expireAfterWrite=5m");
        return new TransactionAwareCacheManagerProxy(caffeineCacheManager);
    }

    @Bean(CACHE_MANAGER_ALIAS)
    CacheManager cacheManagerAlias() {
        var caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCacheSpecification("maximumSize=100000,expireAfterWrite=30m");
        return caffeineCacheManager;
    }

    @Bean(CACHE_MANAGER_TABLE_TIME_PARTITION)
    CacheManager cacheManagerTableTimePartition() {
        var caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCacheSpecification("maximumSize=50,expireAfterWrite=1d");
        return caffeineCacheManager;
    }
}
