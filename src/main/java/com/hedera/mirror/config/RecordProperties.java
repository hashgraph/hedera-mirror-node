package com.hedera.mirror.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.inject.Named;
import javax.validation.constraints.NotNull;
import java.time.Duration;

@Data
@Named
@ConfigurationProperties("hedera.mirror.record")
public class RecordProperties {

    private boolean enabled = true;

    @NotNull
    private RecordDownloaderProperties downloader = new RecordDownloaderProperties();

    @NotNull
    private RecordParserProperties parser = new RecordParserProperties();

    @Data
    public class RecordDownloaderProperties {
        private Duration frequency = Duration.ofMillis(100L);
    }

    @Data
    public class RecordParserProperties {
        private Duration frequency = Duration.ofMillis(100L);
    }
}
