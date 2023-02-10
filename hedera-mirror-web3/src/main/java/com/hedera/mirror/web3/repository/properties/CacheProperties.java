package com.hedera.mirror.web3.repository.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "hedera.mirror.web3.cache")
public class CacheProperties {

    private long stateCacheSeconds = 5;
    private long entityCacheSeconds = 30;

}
