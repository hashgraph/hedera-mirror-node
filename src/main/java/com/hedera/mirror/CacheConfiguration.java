package com.hedera.mirror;

import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfiguration {

    public static final String EXPIRE_AFTER_5M = "cacheManagerExpireAfter5m";

    @Bean(EXPIRE_AFTER_5M)
    CacheManager cacheManager() {
        CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCacheSpecification("maximumSize=100,expireAfterWrite=5m");
        return caffeineCacheManager;
    }
}
