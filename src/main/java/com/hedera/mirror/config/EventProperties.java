package com.hedera.mirror.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.inject.Named;
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
    public class EventDownloaderProperties {
        private Duration frequency = Duration.ofMillis(100L);
    }

    @Data
    public class EventParserProperties {
        private Duration frequency = Duration.ofMillis(100L);
    }
}
