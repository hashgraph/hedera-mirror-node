package com.hedera.mirror.web3.repository.properties;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CachePropertiesTest {

    private long entityCacheSeconds = 30;
    private long stateCacheSeconds = 5;

    private CacheProperties cacheProperties;

    @BeforeEach
    void setUp() {
        cacheProperties = new CacheProperties();
    }

    @Test
    void correctPropertiesEvaluation() {
        assertThat(cacheProperties.getEntityCacheSeconds()).isEqualTo(entityCacheSeconds);
        assertThat(cacheProperties.getStateCacheSeconds()).isEqualTo(stateCacheSeconds);
    }
}
