/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.hedera.mirror.common.domain.StreamFile;
import com.hedera.mirror.importer.exception.HashMismatchException;
import com.hedera.mirror.importer.repository.StreamFileRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractStreamFileParser<T extends StreamFile<?>> implements StreamFileParser<T> {

    public static final String STREAM_PARSE_DURATION_METRIC_NAME = "hedera.mirror.parse.duration";

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final MeterRegistry meterRegistry;
    protected final ParserProperties parserProperties;
    protected final StreamFileListener<T> streamFileListener;
    protected final StreamFileRepository<T, Long> streamFileRepository;

    private final AtomicReference<T> last;
    private final Timer parseDurationMetricFailure;
    private final Timer parseDurationMetricSuccess;
    private final Timer parseLatencyMetric;

    protected AbstractStreamFileParser(
            MeterRegistry meterRegistry,
            ParserProperties parserProperties,
            StreamFileListener<T> streamFileListener,
            StreamFileRepository<T, Long> streamFileRepository) {
        this.last = new AtomicReference<>();
        this.meterRegistry = meterRegistry;
        this.parserProperties = parserProperties;
        this.streamFileListener = streamFileListener;
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

    @VisibleForTesting
    public void clear() {
        last.set(null);
    }

    @Override
    public ParserProperties getProperties() {
        return parserProperties;
    }

    @Override
    public void parse(T streamFile) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        boolean success = true;

        try {
            if (!shouldParse(getLast(), streamFile)) {
                streamFile.clear();
                return;
            }

            doParse(streamFile);

            streamFileListener.onEnd(streamFile);
            last.set(streamFile);
            streamFile.clear();

            log.info(
                    "Successfully processed {} items from {} in {}",
                    streamFile.getCount(),
                    streamFile.getName(),
                    stopwatch);
            Instant consensusInstant = Instant.ofEpochSecond(0L, streamFile.getConsensusEnd());
            parseLatencyMetric.record(Duration.between(consensusInstant, Instant.now()));
        } catch (Throwable e) {
            success = false;
            log.error("Error parsing file {} after {}", streamFile.getName(), stopwatch, e);
            throw e;
        } finally {
            Timer timer = success ? parseDurationMetricSuccess : parseDurationMetricFailure;
            timer.record(stopwatch.elapsed());
        }
    }

    @Override
    public void parse(List<T> streamFiles) {
        long count = 0L;
        var previous = getLast();
        int size = streamFiles.size();
        var stopwatch = Stopwatch.createStarted();
        boolean success = true;
        var filenames = new ArrayList<String>(size);

        try {
            for (int i = 0; i < size; ++i) {
                var streamFile = streamFiles.get(i);

                if (!shouldParse(previous, streamFile)) {
                    streamFile.clear();
                    return;
                }

                doParse(streamFile);

                count += streamFile.getCount();
                previous = streamFile;
                filenames.add(streamFile.getName());
            }

            streamFileListener.onEnd(previous);
            last.set(previous);
            previous.clear();
            log.info(
                    "Successfully batch processed {} items from {} files in {}: {}", count, size, stopwatch, filenames);

            Instant consensusInstant = Instant.ofEpochSecond(0L, previous.getConsensusEnd());
            parseLatencyMetric.record(Duration.between(consensusInstant, Instant.now()));
        } catch (Throwable e) {
            success = false;
            log.error("Error parsing file {} after {}", previous.getName(), stopwatch, e);
            throw e;
        } finally {
            Timer timer = success ? parseDurationMetricSuccess : parseDurationMetricFailure;
            timer.record(stopwatch.elapsed());
        }
    }

    protected abstract void doParse(T streamFile);

    protected final T getLast() {
        var latest = last.get();

        if (latest != null) {
            return latest;
        }

        return streamFileRepository.findLatest().orElse(null);
    }

    private boolean shouldParse(T previous, T current) {
        if (!parserProperties.isEnabled()) {
            return false;
        }

        if (previous == null) {
            return true;
        }

        var name = current.getName();

        if (previous.getConsensusEnd() >= current.getConsensusStart()) {
            log.warn("Skipping existing stream file {}", name);
            return false;
        }

        var actualHash = current.getPreviousHash();
        var expectedHash = previous.getHash();

        // Verify hash chain
        if (previous.getType().isChained() && !expectedHash.contentEquals(actualHash)) {
            throw new HashMismatchException(
                    name, expectedHash, actualHash, getClass().getSimpleName());
        }

        return true;
    }
}
