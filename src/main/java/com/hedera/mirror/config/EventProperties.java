package com.hedera.mirror.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.inject.Named;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.time.Duration;

@Data
@Named
@ConfigurationProperties("hedera.mirror.event")
public class EventProperties {

    private boolean enabled = false;

    @NotNull
    private EventDownloaderProperties downloader = new EventDownloaderProperties();

    @NotNull
    private EventParserProperties parser = new EventParserProperties();

    @Data
    public class EventDownloaderProperties implements CommonDownloaderProperties {
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
        private int maxDownloadItems = 10;
    }

    @Data
    public class EventParserProperties {
        private Duration frequency = Duration.ofMillis(100L);
    }
}
