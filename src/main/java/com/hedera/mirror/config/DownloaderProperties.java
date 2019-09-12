package com.hedera.mirror.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.inject.Named;
import javax.validation.constraints.Min;

@Data
@Named
@ConfigurationProperties("hedera.mirror.downloader")
public class DownloaderProperties {
    @Min(0)
    private int maxConnections = 500;

    @Min(1)
    private int coreThreads = 20;
    @Min(0)
    private int maxThreads = 60;
    @Min(1)
    private int taskQueueSize = 500;
}
