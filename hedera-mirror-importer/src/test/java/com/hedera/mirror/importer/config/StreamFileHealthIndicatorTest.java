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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.search.Search;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;

@ExtendWith(MockitoExtension.class)
class StreamFileHealthIndicatorTest {
    private static final String REASON_KEY = "reason";

    @Mock(lenient = true)
    private Timer streamFileParseDurationTimer;

    @Mock(lenient = true)
    private MeterRegistry meterRegistry;

    private StreamFileHealthIndicator streamFileHealthIndicator;

    private RecordParserProperties recordParserProperties;

    private MirrorProperties mirrorProperties;

    @BeforeEach
    void setUp() {

        doReturn(0L).when(streamFileParseDurationTimer).count();

        Search searchTimer = mock(Search.class, withSettings().lenient());
        doReturn(searchTimer).when(searchTimer).tag(anyString(), anyString());
        doReturn(streamFileParseDurationTimer).when(searchTimer).timer();

        doReturn(searchTimer).when(meterRegistry).find(anyString());

        mirrorProperties = new MirrorProperties();
        mirrorProperties.setEndDate(Instant.MAX);
        recordParserProperties = new RecordParserProperties(mirrorProperties);

        streamFileHealthIndicator = new StreamFileHealthIndicator(
                recordParserProperties,
                meterRegistry);
    }

    @Test
    void startUpParsingDisabled() {
        recordParserProperties.setEnabled(false);
        streamFileHealthIndicator = new StreamFileHealthIndicator(
                recordParserProperties,
                meterRegistry);

        Health health = streamFileHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat((String) health.getDetails().get(REASON_KEY)).contains("parsing is disabled");
    }

    @Test
    void startUpNoStreamFilesBeforeWindow() {
        Health health = streamFileHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat((String) health.getDetails().get(REASON_KEY)).contains("Starting up, no files parsed yet");
    }

    @Test
    void startUpNoStreamFilesAfterWindow() {
        // set window time to smaller value
        recordParserProperties.setStreamFileStatusCheckBuffer(Duration.ofSeconds(-10L));
        streamFileHealthIndicator = new StreamFileHealthIndicator(
                recordParserProperties, // force end of window to before now
                meterRegistry);

        Health health = streamFileHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat((String) health.getDetails().get(REASON_KEY))
                .contains("No new stream stream files have been parsed since:");
        assertThat((Long) health.getDetails().get("count")).isZero();
    }

    @Test
    void newStreamFiles() {
        streamFileHealthIndicator.health();

        // update counter
        doReturn(1L).when(streamFileParseDurationTimer).count();

        Health health = streamFileHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void noNewStreamFilesInWindow() {
        streamFileHealthIndicator.health(); // unknown

        // update counter
        doReturn(1L).when(streamFileParseDurationTimer).count();

        streamFileHealthIndicator.health(); // up

        Health health = streamFileHealthIndicator.health(); // count unchanged
        assertThat(health.getStatus()).isEqualTo(Status.UP); // cache should be returned
    }

    @Test
    void noNewStreamFilesAfterWindow() {
        recordParserProperties.setStreamFileStatusCheckBuffer(Duration.ofSeconds(-10L));
        streamFileHealthIndicator = new StreamFileHealthIndicator(
                recordParserProperties, // force end of window to before now
                meterRegistry);

        streamFileHealthIndicator.health(); // unknown

        // update counter
        doReturn(1L).when(streamFileParseDurationTimer).count();

        streamFileHealthIndicator.health(); // up

        Health health = streamFileHealthIndicator.health(); // count unchanged
        assertThat(health.getStatus()).isEqualTo(Status.DOWN); // cache should not be returned
        assertThat((String) health.getDetails().get(REASON_KEY))
                .contains("No new stream stream files have been parsed since:");
        assertThat((Long) health.getDetails().get("count")).isEqualTo(1);
    }

    @Test
    void noNewStreamFilesAfterWindowAndEndTime() {
        mirrorProperties.setEndDate(Instant.now().minusSeconds(60));
        recordParserProperties = new RecordParserProperties(mirrorProperties);
        streamFileHealthIndicator = new StreamFileHealthIndicator(
                recordParserProperties, // force end of window to before now
                meterRegistry);

        streamFileHealthIndicator.health(); // unknown

        // update counter
        doReturn(1L).when(streamFileParseDurationTimer).count();

        streamFileHealthIndicator.health(); // up

        Health health = streamFileHealthIndicator.health(); // count unchanged
        assertThat(health.getStatus()).isEqualTo(Status.UP); // cache should not be returned
        assertThat((String) health.getDetails().get(REASON_KEY)).contains("stream files are no longer expected");
    }

    @Test
    void recoverWhenNewStreamFiles() {
        recordParserProperties.setStreamFileStatusCheckBuffer(Duration.ofSeconds(-10L));
        streamFileHealthIndicator = new StreamFileHealthIndicator(
                recordParserProperties, // force end of window to before now
                meterRegistry);

        streamFileHealthIndicator.health(); // unknown

        // update counter
        doReturn(1L).when(streamFileParseDurationTimer).count();

        streamFileHealthIndicator.health(); // up

        Health health = streamFileHealthIndicator.health(); // count unchanged
        assertThat(health.getStatus()).isEqualTo(Status.DOWN); // cache should not be returned
        assertThat((String) health.getDetails().get(REASON_KEY))
                .contains("No new stream stream files have been parsed since:");
        assertThat((Long) health.getDetails().get("count")).isEqualTo(1);

        doReturn(2L).when(streamFileParseDurationTimer).count();

        health = streamFileHealthIndicator.health(); // count incremented
        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }
}
