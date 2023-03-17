package com.hedera.mirror.importer.config;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.importer.downloader.Downloader.STREAM_CLOSE_LATENCY_METRIC_NAME;
import static com.hedera.mirror.importer.parser.AbstractStreamFileParser.STREAM_PARSE_DURATION_METRIC_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.doReturn;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.search.Search;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.leader.LeaderService;
import com.hedera.mirror.importer.parser.AbstractParserProperties;

@ExtendWith(MockitoExtension.class)
abstract class AbstractStreamFileHealthIndicatorTest {

    private static final String REASON_KEY = "reason";

    @Mock(lenient = true)
    private LeaderService leaderService;

    @Mock(lenient = true)
    private Timer streamFileParseDurationTimer;

    @Mock(lenient = true)
    private Timer streamCloseLatencyDurationTimer;

    @Mock(lenient = true)
    private Search streamParseDurationSearch;

    @Mock(lenient = true)
    private Search streamCloseLatencySearch;

    @Mock(lenient = true)
    private MeterRegistry meterRegistry;

    private StreamFileHealthIndicator streamFileHealthIndicator;

    private AbstractParserProperties parserProperties;

    protected MirrorProperties mirrorProperties;

    abstract AbstractParserProperties getParserProperties();

    @BeforeEach
    void setup() {
        doReturn(true).when(leaderService).isLeader();
        doReturn(0.0).when(streamCloseLatencyDurationTimer).mean(any());
        doReturn(0L).when(streamFileParseDurationTimer).count();

        doReturn(streamCloseLatencyDurationTimer).when(streamCloseLatencySearch).timer();
        doReturn(streamFileParseDurationTimer).when(streamParseDurationSearch).timer();

        doReturn(streamCloseLatencySearch).when(streamCloseLatencySearch).tags(anyIterable());
        doReturn(streamParseDurationSearch).when(streamParseDurationSearch).tags(anyIterable());

        doReturn(streamCloseLatencySearch).when(meterRegistry).find(STREAM_CLOSE_LATENCY_METRIC_NAME);
        doReturn(streamParseDurationSearch).when(meterRegistry).find(STREAM_PARSE_DURATION_METRIC_NAME);

        mirrorProperties = new MirrorProperties();
        mirrorProperties.setEndDate(Instant.MAX);
        parserProperties = getParserProperties();

        streamFileHealthIndicator = new StreamFileHealthIndicator(leaderService, meterRegistry, mirrorProperties,
                parserProperties);
    }

    @Test
    void startupParsingDisabled() {
        parserProperties.setEnabled(false);

        Health health = streamFileHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat((String) health.getDetails().get(REASON_KEY)).contains("Parsing is disabled");
    }

    @Test
    void notLeader() {
        doReturn(false).when(leaderService).isLeader();

        Health health = streamFileHealthIndicator.health();
        assertThat(health).isNotNull()
                .returns(Status.UNKNOWN, Health::getStatus)
                .returns(Map.of(REASON_KEY, "Not currently leader"), Health::getDetails);
    }

    @Test
    void missingParserDurationTimer() {
        doReturn(null).when(streamParseDurationSearch).timer();

        Health health = streamFileHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat((String) health.getDetails().get(REASON_KEY))
                .contains(STREAM_PARSE_DURATION_METRIC_NAME + " timer is missing");
    }

    @Test
    void missingStreamCloseLatencyTimer() {
        doReturn(null).when(streamCloseLatencySearch).timer();

        Health health = streamFileHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat((String) health.getDetails().get(REASON_KEY))
                .contains(STREAM_CLOSE_LATENCY_METRIC_NAME + " timer is missing");
    }

    @Test
    void startupNoStreamFilesBeforeWindow() {
        Health health = streamFileHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat((String) health.getDetails().get(REASON_KEY)).contains("Starting up, no files parsed yet");
        assertThat((Long) health.getDetails().get("count")).isZero();
    }

    @Test
    void startupNoStreamFilesAfterWindowBeforeFirstFileClose() {
        // set window time to smaller value
        parserProperties.setProcessingTimeout(Duration.ofSeconds(-10L));
        mirrorProperties.setStartDate(Instant.now().plus(1, ChronoUnit.DAYS));

        Health health = streamFileHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat((String) health.getDetails().get(REASON_KEY))
                .contains("Starting up, no files parsed yet");
        assertThat((Long) health.getDetails().get("count")).isZero();
    }

    @Test
    void startupNoNewDownloadedStreamFilesAfterWindowBeforeFileClose() {
        // file close mean metric will be zero and default fileCLose duration will be used
        // set window time to smaller value
        parserProperties.setProcessingTimeout(Duration.ofMillis(1L));

        Health health = streamFileHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat((String) health.getDetails().get(REASON_KEY))
                .contains("Starting up, no files parsed yet");
        assertThat((Long) health.getDetails().get("count")).isZero();
    }

    @Test
    void startupNoStreamFilesAfterFirstFileClose() {
        // set window time to smaller value
        parserProperties.setProcessingTimeout(Duration.ofSeconds(-10L));
        mirrorProperties.setStartDate(Instant.now().minus(1, ChronoUnit.DAYS));

        // update fileClose mean otherwise larger default value is used
        doReturn(1.0).when(streamCloseLatencyDurationTimer).mean(any());

        Health health = streamFileHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat((String) health.getDetails().get(REASON_KEY))
                .contains("No new stream stream files have been parsed since:");
        assertThat((Long) health.getDetails().get("count")).isZero();
    }

    @Test
    void newStreamFiles() {
        Health health = streamFileHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);

        // update counter
        doReturn(1L).when(streamFileParseDurationTimer).count();
        doReturn((double) parserProperties.getStreamType().getFileCloseInterval().toMillis())
                .when(streamFileParseDurationTimer).mean(any());

        health = streamFileHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void noNewStreamFilesInWindow() {
        Health health = streamFileHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);

        // update counter
        doReturn(1L).when(streamFileParseDurationTimer).count();
        doReturn((double) parserProperties.getStreamType().getFileCloseInterval().toMillis())
                .when(streamCloseLatencyDurationTimer).mean(any());

        streamFileHealthIndicator.health();

        health = streamFileHealthIndicator.health(); // count unchanged
        assertThat(health.getStatus()).isEqualTo(Status.UP); // cache should be returned
    }

    @Test
    void noNewStreamFilesAfterWindow() {
//        parserProperties.setProcessingTimeout(Duration.ofMinutes(-10L)); // force end of timeout to before now
        mirrorProperties.setStartDate(Instant.EPOCH);

        Health health = streamFileHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);

        // update counter
        doReturn(1L).when(streamFileParseDurationTimer).count();

        health = streamFileHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);

        // update fileClose mean otherwise larger default value is used
        doReturn(1.0).when(streamCloseLatencyDurationTimer).mean(any());
        parserProperties.setProcessingTimeout(Duration.ofMinutes(-10L)); // force end of timeout to before now

        health = streamFileHealthIndicator.health(); // count unchanged
        assertThat(health.getStatus()).isEqualTo(Status.DOWN); // cache should not be returned
        assertThat((String) health.getDetails().get(REASON_KEY))
                .contains("No new stream stream files have been parsed since:");
        assertThat((Long) health.getDetails().get("count")).isEqualTo(1);
    }

    @Test
    void noNewStreamFilesAfterWindowAndEndTime() {
        mirrorProperties.setEndDate(Instant.now().minusSeconds(60));  // force endDate to before now
        parserProperties = getParserProperties();

        Health health = streamFileHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP); // cache should not be returned
        assertThat((String) health.getDetails().get(REASON_KEY)).contains("stream files are no longer expected");
    }

    @Test
    void recoverWhenNewStreamFiles() {
        mirrorProperties.setStartDate(Instant.now().minus(1, ChronoUnit.DAYS));

        Health health = streamFileHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);

        // update counter
        doReturn(1L).when(streamFileParseDurationTimer).count();

        health = streamFileHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);

        // update fileClose mean otherwise larger default value is used
        doReturn(1.0).when(streamCloseLatencyDurationTimer).mean(any());
        parserProperties.setProcessingTimeout(Duration.ofSeconds(-10L)); // force end of timeout to before now

        health = streamFileHealthIndicator.health(); // count unchanged
        assertThat(health.getStatus()).isEqualTo(Status.DOWN); // cache should not be returned
        assertThat((String) health.getDetails().get(REASON_KEY))
                .contains("No new stream stream files have been parsed since:");
        assertThat((Long) health.getDetails().get("count")).isEqualTo(1);

        doReturn(2L).when(streamFileParseDurationTimer).count();

        health = streamFileHealthIndicator.health(); // count incremented
        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }
}
