package com.hedera.mirror.monitor;

import java.time.Duration;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
public class NodeValidationProperties {

    private boolean enabled = true;

    @DurationMin(seconds = 30)
    @NotNull
    private Duration frequency = Duration.ofMinutes(5);

    @DurationMin(millis = 250)
    @DurationMax(seconds = 10)
    @NotNull
    private Duration maxBackoff = Duration.ofSeconds(2);

    @Min(1)
    private int maxAttempts = 20;

    @DurationMin(millis = 250)
    @DurationMax(seconds = 10)
    @NotNull
    private Duration minBackoff = Duration.ofMillis(500);
}
