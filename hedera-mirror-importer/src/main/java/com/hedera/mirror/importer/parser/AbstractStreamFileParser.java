/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.importer.parser;

import com.google.common.base.Stopwatch;
import com.hedera.mirror.common.domain.StreamFile;
import com.hedera.mirror.importer.exception.HashMismatchException;
import com.hedera.mirror.importer.repository.StreamFileRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class AbstractStreamFileParser<T extends StreamFile<?>> implements StreamFileParser<T> {

    public static final String STREAM_PARSE_DURATION_METRIC_NAME = "hedera.mirror.parse.duration";

    protected final AtomicReference<T> last;
    protected final Logger log = LogManager.getLogger(getClass());
    protected final MeterRegistry meterRegistry;
    protected final ParserProperties parserProperties;
    protected final StreamFileRepository<T, Long> streamFileRepository;

    private final Timer parseDurationMetricFailure;
    private final Timer parseDurationMetricSuccess;
    private final Timer parseLatencyMetric;

    protected AbstractStreamFileParser(
            MeterRegistry meterRegistry,
            ParserProperties parserProperties,
            StreamFileRepository<T, Long> streamFileRepository) {

        this.last = new AtomicReference<>();
        this.meterRegistry = meterRegistry;
        this.parserProperties = parserProperties;
        this.streamFileRepository = streamFileRepository;

        // Metrics
        Timer.Builder parseDurationTimerBuilder = Timer.builder(STREAM_PARSE_DURATION_METRIC_NAME)
                .description("The duration in seconds it took to parse the file and store it in the database")
                .tag("type", parserProperties.getStreamType().toString());
        parseDurationMetricFailure =
                parseDurationTimerBuilder.tag("success", "false").register(meterRegistry);
        parseDurationMetricSuccess =
                parseDurationTimerBuilder.tag("success", "true").register(meterRegistry);

        parseLatencyMetric = Timer.builder("hedera.mirror.parse.latency")
                .description("The difference in ms between the consensus time of the last transaction in the file "
                        + "and the time at which the file was processed successfully")
                .tag("type", parserProperties.getStreamType().toString())
                .register(meterRegistry);
    }

    @Override
    public ParserProperties getProperties() {
        return parserProperties;
    }

    @Override
    public void parse(T streamFile) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        boolean success = false;

        if (shouldParse(streamFile)) {
            try {
                doParse(streamFile);

                log.info(
                        "Successfully processed {} items from {} in {}",
                        streamFile.getCount(),
                        streamFile.getName(),
                        stopwatch);
                success = true;
                Instant consensusInstant = Instant.ofEpochSecond(0L, streamFile.getConsensusEnd());
                parseLatencyMetric.record(Duration.between(consensusInstant, Instant.now()));
            } catch (Exception e) {
                log.error("Error parsing file {} after {}", streamFile.getName(), stopwatch, e);
                throw e;
            } finally {
                Timer timer = success ? parseDurationMetricSuccess : parseDurationMetricFailure;
                timer.record(stopwatch.elapsed());
            }
        }
    }

    protected abstract void doParse(T streamFile);

    private boolean shouldParse(T streamFile) {
        if (!parserProperties.isEnabled()) {
            return false;
        }
        var lastStreamFileFromDB = streamFileRepository.findLatest();

        if (lastStreamFileFromDB.isEmpty()) {
            return true;
        }

        if (last.get() == null) {
            last.set(lastStreamFileFromDB.get());
        }

        var lastStreamFile = lastStreamFileFromDB.get();
        var name = streamFile.getName();

        if (lastStreamFile.getConsensusEnd() >= streamFile.getConsensusStart()) {
            log.warn("Skipping existing stream file {}", name);
            return false;
        }

        var actualHash = streamFile.getPreviousHash();
        var expectedHash = lastStreamFile.getHash();

        // Verify hash chain
        if (streamFile.getType().isChained() && !expectedHash.contentEquals(actualHash)) {
            throw new HashMismatchException(
                    name, expectedHash, actualHash, getClass().getSimpleName());
        }

        return true;
    }
}
