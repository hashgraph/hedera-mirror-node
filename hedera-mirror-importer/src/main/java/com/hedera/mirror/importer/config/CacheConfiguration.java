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

import java.util.Set;
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

    public static final String CACHE_ADDRESS_BOOK = "addressBook";
    public static final String CACHE_ALIAS = "alias";
    public static final String CACHE_OVERLAPPING_TIME_PARTITION = "overlappingTimePartition";
    public static final String CACHE_TIME_PARTITION = "timePartition";
    public static final String CACHE_NAME = "default";

    @Bean(CACHE_ADDRESS_BOOK)
    @Primary
    CacheManager cacheManagerAddressBook() {
        var cacheManager = cacheManager("maximumSize=100,expireAfterWrite=5m,recordStats");
        return new TransactionAwareCacheManagerProxy(cacheManager);
    }

    @Bean(CACHE_ALIAS)
    CacheManager cacheManagerAlias() {
        return cacheManager("maximumSize=100000,expireAfterWrite=30m,recordStats");
    }

    @Bean(CACHE_OVERLAPPING_TIME_PARTITION)
    CacheManager cacheManagerOverlappingTimePartition() {
        return cacheManager("maximumSize=50,expireAfterWrite=1d,recordStats");
    }

    @Bean(CACHE_TIME_PARTITION)
    CacheManager cacheManagerTimePartition() {
        return cacheManager("maximumSize=50,expireAfterWrite=1d,recordStats");
    }

    private CacheManager cacheManager(String specification) {
        var cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(Set.of(CACHE_NAME));
        cacheManager.setCacheSpecification(specification);
        return cacheManager;
    }
}
