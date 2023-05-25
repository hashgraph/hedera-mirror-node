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

package com.hedera.mirror.importer.config;

import static com.hedera.mirror.importer.domain.StreamFilename.FileType.DATA;
import static org.apache.commons.lang3.ObjectUtils.max;

import com.hedera.mirror.common.domain.StreamFile;
import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.downloader.DownloaderProperties;
import com.hedera.mirror.importer.exception.InvalidConfigurationException;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import com.hedera.mirror.importer.repository.EventFileRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.StreamFileRepository;
import com.hedera.mirror.importer.util.Utility;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Named
@RequiredArgsConstructor
public class MirrorDateRangePropertiesProcessor {

    static final Instant STARTUP_TIME = Instant.now();

    private final MirrorProperties mirrorProperties;
    private final List<DownloaderProperties> downloaderPropertiesList;
    private final AccountBalanceFileRepository accountBalanceFileRepository;
    private final EventFileRepository eventFileRepository;
    private final RecordFileRepository recordFileRepository;

    private final Map<StreamType, DateRangeFilter> filters = new ConcurrentHashMap<>();

    // Clear cache between test runs
    public void clear() {
        filters.clear();
    }

    /**
     * Gets the DateRangeFilter for the downloader (record, balance, event).
     *
     * @param type - downloader type
     * @return the DateRangeFilter
     */
    public DateRangeFilter getDateRangeFilter(StreamType type) {
        return filters.computeIfAbsent(type, this::newDateRangeFilter);
    }

    private DateRangeFilter newDateRangeFilter(StreamType streamType) {
        DownloaderProperties downloaderProperties = getDownloaderProperties(streamType);

        if (!downloaderProperties.isEnabled()) {
            return DateRangeFilter.empty();
        }

        Instant startDate = mirrorProperties.getStartDate();
        Instant endDate = mirrorProperties.getEndDate();
        Instant lastFileInstant = findLatest(streamType)
                .map(StreamFile::getConsensusStart)
                .map(nanos -> Instant.ofEpochSecond(0, nanos))
                .orElse(null);
        Instant filterStartDate = lastFileInstant;

        if (startDate != null && startDate.compareTo(endDate) > 0) {
            throw new InvalidConfigurationException(String.format(
                    "Date range constraint violation: " + "startDate (%s) > endDate (%s)", startDate, endDate));
        }

        if (startDate != null) {
            filterStartDate = max(startDate, lastFileInstant);
        } else {
            if (mirrorProperties.getNetwork() != MirrorProperties.HederaNetwork.DEMO && lastFileInstant == null) {
                filterStartDate = STARTUP_TIME;
            }
        }

        DateRangeFilter filter = new DateRangeFilter(filterStartDate, endDate);

        log.info(
                "{}: parser will parse items in the range [{}, {}]",
                downloaderProperties.getStreamType(),
                filter.getStartAsInstant(),
                filter.getEndAsInstant());
        return filter;
    }

    /**
     * Gets the latest stream file for downloader based on startDate in MirrorProperties, the startDateAdjustment and
     * last valid downloaded stream file.
     *
     * @param streamType What type of stream to retrieve
     * @return The latest stream file from the database or a dummy stream file if it calculated a different effective
     * start date
     */
    public <T extends StreamFile<?>> Optional<T> getLastStreamFile(StreamType streamType) {
        Instant startDate = mirrorProperties.getStartDate();
        Optional<T> streamFile = findLatest(streamType);
        Instant lastFileInstant = streamFile
                .map(StreamFile::getConsensusStart)
                .map(nanos -> Instant.ofEpochSecond(0, nanos))
                .orElse(null);

        Instant effectiveStartDate = STARTUP_TIME;
        boolean hasStreamFile = lastFileInstant != null;

        if (startDate != null) {
            effectiveStartDate = max(startDate, hasStreamFile ? lastFileInstant : Instant.EPOCH);
        } else if (hasStreamFile) {
            effectiveStartDate = lastFileInstant;
        } else if (mirrorProperties.getNetwork() == MirrorProperties.HederaNetwork.DEMO) {
            effectiveStartDate = Instant.EPOCH; // Demo network contains only data in the past, so don't default to now
        }

        Instant endDate = mirrorProperties.getEndDate();
        if (startDate != null && startDate.compareTo(endDate) > 0) {
            throw new InvalidConfigurationException(String.format(
                    "Date range constraint violation: " + "startDate (%s) > endDate (%s)", startDate, endDate));
        }

        if (effectiveStartDate.compareTo(endDate) > 0) {
            throw new InvalidConfigurationException(String.format(
                    "Date range constraint violation for %s downloader: effective startDate (%s) > endDate (%s)",
                    streamType, effectiveStartDate, endDate));
        }

        if (!effectiveStartDate.equals(lastFileInstant)) {
            Instant verifyHashAfter = mirrorProperties.getVerifyHashAfter();

            if (verifyHashAfter.isBefore(effectiveStartDate)) {
                mirrorProperties.setVerifyHashAfter(effectiveStartDate);
                log.debug("Set verifyHashAfter to {}", effectiveStartDate);
            }

            String filename = StreamFilename.getFilename(streamType, DATA, effectiveStartDate);
            T effectiveStreamFile = streamType.newStreamFile();
            effectiveStreamFile.setConsensusStart(DomainUtils.convertToNanosMax(effectiveStartDate));
            effectiveStreamFile.setName(filename);
            effectiveStreamFile.setIndex(streamFile.map(StreamFile::getIndex).orElse(null));
            streamFile = Optional.of(effectiveStreamFile);
        }

        log.info(
                "{}: downloader will download files in time range ({}, {}]",
                streamType,
                effectiveStartDate,
                mirrorProperties.getEndDate());
        return streamFile;
    }

    @SuppressWarnings("unchecked")
    private <T extends StreamFile<?>> Optional<T> findLatest(StreamType streamType) {
        return (Optional<T>) getStreamFileRepository(streamType).findLatest();
    }

    private StreamFileRepository<?, ?> getStreamFileRepository(StreamType streamType) {
        return switch (streamType) {
            case BALANCE -> accountBalanceFileRepository;
            case EVENT -> eventFileRepository;
            case RECORD -> recordFileRepository;
        };
    }

    private DownloaderProperties getDownloaderProperties(StreamType streamType) {
        return downloaderPropertiesList.stream()
                .filter(p -> p.getStreamType() == streamType)
                .findFirst()
                .orElseThrow(() -> new UnsupportedOperationException("Unsupported stream type " + streamType));
    }

    @Value
    public static class DateRangeFilter {
        long start;
        long end;

        public DateRangeFilter(Instant startDate, Instant endDate) {
            if (startDate == null) {
                startDate = Instant.EPOCH;
            }
            start = DomainUtils.convertToNanosMax(startDate);

            if (endDate == null) {
                end = Long.MAX_VALUE;
            } else {
                end = DomainUtils.convertToNanosMax(endDate);
            }
        }

        public static DateRangeFilter empty() {
            return new DateRangeFilter(Instant.EPOCH.plusNanos(1), Instant.EPOCH);
        }

        public static DateRangeFilter all() {
            return new DateRangeFilter(Instant.EPOCH, Utility.MAX_INSTANT_LONG);
        }

        public boolean filter(long timestamp) {
            return timestamp >= start && timestamp <= end;
        }

        public Instant getStartAsInstant() {
            return Instant.ofEpochSecond(0, start);
        }

        public Instant getEndAsInstant() {
            return Instant.ofEpochSecond(0, end);
        }

        @Override
        public String toString() {
            return String.format("DateRangeFilter([%s, %s])", getStartAsInstant(), getEndAsInstant());
        }
    }
}
