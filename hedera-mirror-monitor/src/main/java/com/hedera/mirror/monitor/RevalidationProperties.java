package com.hedera.mirror.monitor;

import java.time.Duration;
import javax.validation.constraints.Min;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
public class RevalidationProperties {

    @DurationMin(seconds = 30)
    private Duration frequency = Duration.ofMinutes(5);

    @DurationMin(seconds = 10)
    private Duration maxBackoff = Duration.ofMillis(1000);

    @Min(1)
    private int maxAttempts = 15;

    @DurationMin(millis = 250)
    private Duration minBackoff = Duration.ofMillis(500);
}
