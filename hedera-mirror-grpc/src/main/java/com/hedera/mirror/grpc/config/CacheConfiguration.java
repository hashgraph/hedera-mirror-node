/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.grpc.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.hedera.mirror.grpc.GrpcProperties;
import com.hedera.mirror.grpc.service.AddressBookProperties;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@ConditionalOnProperty(prefix = "spring.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableCaching
public class CacheConfiguration {

    public static final String ADDRESS_BOOK_ENTRY_CACHE = "addressBookEntryCache";
    public static final String NODE_STAKE_CACHE = "nodeStakeCache";
    public static final String ENTITY_CACHE = "entityCache";
    public static final String CACHE_NAME = "default";

    @Bean(ADDRESS_BOOK_ENTRY_CACHE)
    CacheManager addressBookEntryCache(AddressBookProperties addressBookProperties) {
        CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCacheNames(Set.of(CACHE_NAME)); // We have to eagerly set cache name to register metrics
        caffeineCacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(addressBookProperties.getCacheExpiry())
                .maximumSize(addressBookProperties.getCacheSize())
                .recordStats());
        return caffeineCacheManager;
    }

    @Bean(NODE_STAKE_CACHE)
    CacheManager nodeStakeCache(AddressBookProperties addressBookProperties) {
        CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCacheNames(Set.of(CACHE_NAME));
        caffeineCacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(addressBookProperties.getNodeStakeCacheExpiry())
                .maximumSize(addressBookProperties.getNodeStakeCacheSize())
                .recordStats());
        return caffeineCacheManager;
    }

    @Bean(ENTITY_CACHE)
    @Primary
    CacheManager entityCache(GrpcProperties grpcProperties) {
        int cacheSize = grpcProperties.getEntityCacheSize();
        CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCacheNames(Set.of(CACHE_NAME));
        caffeineCacheManager.setCacheSpecification("recordStats,expireAfterWrite=24h,maximumSize=" + cacheSize);
        return caffeineCacheManager;
    }
}
