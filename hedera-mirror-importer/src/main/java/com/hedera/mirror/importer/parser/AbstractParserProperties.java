package com.hedera.mirror.importer.parser;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import java.nio.file.Path;
import java.time.Duration;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.validation.annotation.Validated;

import com.hedera.mirror.importer.MirrorProperties;

@Data
@Validated
public abstract class AbstractParserProperties implements ParserProperties {

    @Min(8192)
    private int bufferSize = 32768; // tested max byte size of buffer used by PGCopyOutputStream

    protected boolean enabled = true;

    @DurationMin(millis = 10L)
    @NotNull
    protected Duration frequency = Duration.ofMillis(100L);

    protected boolean keepFiles = false;

    protected boolean persistBytes = false;

    @Min(1)
    protected int queueCapacity = 10;

    @DurationMax(minutes = 35L)
    @DurationMin(seconds = 0)
    protected Duration thresholdWindow;

    @NotNull
    protected RetryProperties retry = new RetryProperties();

    @Override
    public Path getParsedPath() {
        return getMirrorProperties().getDataPath()
                .resolve(getStreamType().getPath())
                .resolve(getStreamType().getParsed());
    }

    // Due to Lombok requiring default constructor in base class, we have to keep this in sub-classes.
    protected abstract MirrorProperties getMirrorProperties();

    @Data
    @Validated
    public static class RetryProperties {

        @Min(0)
        private int maxAttempts = Integer.MAX_VALUE;

        @NotNull
        @DurationMin(millis = 500L)
        private Duration maxBackoff = Duration.ofSeconds(10L);

        @NotNull
        @DurationMin(millis = 100L)
        private Duration minBackoff = Duration.ofMillis(250L);

        @Min(1)
        private int multiplier = 2;
    }
}
