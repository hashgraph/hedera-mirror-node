package com.hedera.mirror.importer.config;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;

@ExtendWith(MockitoExtension.class)
public class StreamFileHealthIndicatorTest {
    public static final Health HEALTH_DOWN = Health.down().build();
    public static final Health HEALTH_UP = Health.up().build();
    public static final Health HEALTH_UNKNOWN = Health.unknown().build();

    @Mock
    private Timer streamFileParseDurationTimer;

    private StreamFileHealthIndicator streamFileHealthIndicator;

    private RecordParserProperties recordParserProperties;

    @BeforeEach
    void setUp() {
        doReturn(0L).when(streamFileParseDurationTimer).count();
        recordParserProperties = new RecordParserProperties(new MirrorProperties());
        streamFileHealthIndicator = new StreamFileHealthIndicator(
                streamFileParseDurationTimer,
                recordParserProperties.getStreamFileStatusCheckWindow(),
                Instant.MAX);
    }

    @Test
    void startUpNoStreamFilesBeforeWindow() {
        Health health = streamFileHealthIndicator.health();
        assertThat(health).isEqualTo(HEALTH_UNKNOWN);
    }

    @Test
    void startUpNoStreamFilesAfterWindow() {
        // set window time to smaller value
        streamFileHealthIndicator = new StreamFileHealthIndicator(
                streamFileParseDurationTimer,
                Duration.ofSeconds(-10L), // force end of window to before now
                Instant.MAX);

        Health health = streamFileHealthIndicator.health();
        assertThat(health).isEqualTo(HEALTH_DOWN);
    }

    @Test
    void newStreamFiles() {
        streamFileHealthIndicator.health();

        // update counter
        doReturn(1L).when(streamFileParseDurationTimer).count();

        Health health = streamFileHealthIndicator.health();
        assertThat(health).isEqualTo(HEALTH_UP);
    }

    @Test
    void noNewStreamFilesInWindow() {
        streamFileHealthIndicator.health(); // unknown

        // update counter
        doReturn(1L).when(streamFileParseDurationTimer).count();

        streamFileHealthIndicator.health(); // up

        Health health = streamFileHealthIndicator.health(); // count unchanged
        assertThat(health).isEqualTo(HEALTH_UP); // cache should be returned
    }

    @Test
    void noNewStreamFilesAfterWindow() {
        streamFileHealthIndicator = new StreamFileHealthIndicator(
                streamFileParseDurationTimer,
                Duration.ofSeconds(-10L), // force end of window to before now
                Instant.MAX);

        streamFileHealthIndicator.health(); // unknown

        // update counter
        doReturn(1L).when(streamFileParseDurationTimer).count();

        streamFileHealthIndicator.health(); // up

        Health health = streamFileHealthIndicator.health(); // count unchanged
        assertThat(health).isEqualTo(HEALTH_DOWN); // cache should not be returned
    }

    @Test
    void noNewStreamFilesAfterWindowAndEndTime() {
        streamFileHealthIndicator = new StreamFileHealthIndicator(
                streamFileParseDurationTimer,
                Duration.ofSeconds(10L), // force end of window to before now
                Instant.now());

        streamFileHealthIndicator.health(); // unknown

        // update counter
        doReturn(1L).when(streamFileParseDurationTimer).count();

        streamFileHealthIndicator.health(); // up

        Health health = streamFileHealthIndicator.health(); // count unchanged
        assertThat(health).isEqualTo(HEALTH_UP); // cache should not be returned
    }

    @Test
    void recoverWhenNewStreamFiles() {
        streamFileHealthIndicator = new StreamFileHealthIndicator(
                streamFileParseDurationTimer,
                Duration.ofSeconds(-10L), // force end of window to before now
                Instant.MAX);

        streamFileHealthIndicator.health(); // unknown

        // update counter
        doReturn(1L).when(streamFileParseDurationTimer).count();

        streamFileHealthIndicator.health(); // up

        Health health = streamFileHealthIndicator.health(); // count unchanged
        assertThat(health).isEqualTo(HEALTH_DOWN); // cache should not be returned

        doReturn(2L).when(streamFileParseDurationTimer).count();

        health = streamFileHealthIndicator.health(); // count incremented
        assertThat(health).isEqualTo(HEALTH_UP);
    }
}
