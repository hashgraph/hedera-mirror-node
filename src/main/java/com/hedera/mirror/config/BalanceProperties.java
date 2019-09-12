package com.hedera.mirror.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.inject.Named;
import javax.validation.constraints.Min;
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
    public class BalanceDownloaderProperties implements CommonDownloaderProperties {
        private Duration frequency = Duration.ofMillis(100L);

        @Min(1)
        private int coreThreads = 5;
        @Min(1)
        private int maxThreads = 5;
        @Min(1)
        private int taskQueueSize = 50;
        @Min(1)
        private int listObjectsMaxKeys = 100;
        @Min(1)
        private int maxDownloadItems = 5;
    }
}
