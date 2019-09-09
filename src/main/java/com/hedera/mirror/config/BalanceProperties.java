package com.hedera.mirror.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.inject.Named;
import javax.validation.constraints.NotNull;
import java.time.Duration;

@Data
@Named
@ConfigurationProperties("hedera.mirror.balance")
public class BalanceProperties {

    private boolean enabled = true;

    @NotNull
    private BalanceDownloaderProperties downloader = new BalanceDownloaderProperties();

    @Data
    public class BalanceDownloaderProperties {
        private Duration frequency = Duration.ofMillis(100L);
    }
}
