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

import static org.apache.commons.lang3.ObjectUtils.max;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.StreamFile;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.downloader.DownloaderProperties;
import com.hedera.mirror.importer.exception.InvalidConfigurationException;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import com.hedera.mirror.importer.repository.EventFileRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.StreamFileRepository;
import com.hedera.mirror.importer.util.Utility;

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

    /**
     * Gets the DateRangeFilter for the downloader (record, balance, event).
     *
     * @param type - downloader type
     * @return the DateRangeFilter
     */
    public DateRangeFilter getDateRangeFilter(StreamType type) {
        return filters.computeIfAbsent(type, t -> newDateRangeFilter(t));
    }

    private DateRangeFilter newDateRangeFilter(StreamType streamType) {
        DownloaderProperties downloaderProperties = getDownloaderProperties(streamType);

        if (!downloaderProperties.isEnabled()) {
            return DateRangeFilter.empty();
        }

        Instant startDate = mirrorProperties.getStartDate();
        Instant endDate = mirrorProperties.getEndDate();
        Instant lastFileInstant = getLastValidDownloadedFileInstant(downloaderProperties);
        Instant filterStartDate = lastFileInstant;

        if (startDate != null && startDate.compareTo(endDate) > 0) {
            throw new InvalidConfigurationException(String.format("Date range constraint violation: " +
                    "startDate (%s) > endDate (%s)", startDate, endDate));
        }

        if (startDate != null) {
            if (lastFileInstant != null && startDate.isAfter(lastFileInstant)) {
                filterStartDate = startDate;
            }
        } else {
            if (mirrorProperties.getNetwork() != MirrorProperties.HederaNetwork.DEMO && lastFileInstant == null) {
                filterStartDate = STARTUP_TIME;
            }
        }

        DateRangeFilter filter = new DateRangeFilter(filterStartDate, endDate);

        log.info("{}: parser will parse items in the range [{}, {}]",
                downloaderProperties.getStreamType(), filter.getStartAsInstant(), filter.getEndAsInstant());
        return filter;
    }

    /**
     * Gets the effective startDate for downloader based on startDate in MirrorProperties, the startDateAdjustment and
     * last valid downloaded file.
     *
     * @param downloaderProperties The downloader's properties
     * @return The effective startDate: null if downloader is disabled; if startDate is set, the effective startDate is
     * startDate if the database is empty or max(startDate, timestamp of last valid downloaded file); if startDate is
     * not set, the effective startDate is now if the database is empty, or the timestamp of last valid downloaded file
     */
    public Instant getEffectiveStartDate(DownloaderProperties downloaderProperties) {
        Instant startDate = mirrorProperties.getStartDate();
        Instant lastFileInstant = getLastValidDownloadedFileInstant(downloaderProperties);
        Duration adjustment = mirrorProperties.getStartDateAdjustment();
        Instant effectiveStartDate = STARTUP_TIME.minus(adjustment);

        if (startDate != null) {
            effectiveStartDate = max(startDate
                    .minus(adjustment), lastFileInstant != null ? lastFileInstant : Instant.EPOCH);
        } else if (lastFileInstant != null) {
            effectiveStartDate = lastFileInstant;
        } else if (mirrorProperties.getNetwork() == MirrorProperties.HederaNetwork.DEMO) {
            effectiveStartDate = Instant.EPOCH; // Demo network contains only data in the past, so don't default to now
        }

        Instant endDate = mirrorProperties.getEndDate();
        if (startDate != null && startDate.compareTo(endDate) > 0) {
            throw new InvalidConfigurationException(String.format("Date range constraint violation: " +
                    "startDate (%s) > endDate (%s)", startDate, endDate));
        }

        if (effectiveStartDate.compareTo(endDate) > 0) {
            throw new InvalidConfigurationException(String
                    .format("Date range constraint violation for %s downloader: " +
                                    "effective startDate (%s) > endDate (%s)", downloaderProperties.getStreamType(),
                            effectiveStartDate, endDate));
        }

        if (!effectiveStartDate.equals(lastFileInstant)) {
            Instant verifyHashAfter = mirrorProperties.getVerifyHashAfter();

            if (verifyHashAfter == null || verifyHashAfter.isBefore(effectiveStartDate)) {
                mirrorProperties.setVerifyHashAfter(effectiveStartDate);
                log.debug("Set verifyHashAfter to {}", effectiveStartDate);
            }
        }

        log.info("{}: downloader will download files in time range ({}, {}]",
                downloaderProperties.getStreamType(), effectiveStartDate, mirrorProperties.getEndDate());
        return effectiveStartDate;
    }

    private Instant getLastValidDownloadedFileInstant(DownloaderProperties downloaderProperties) {
        StreamFileRepository<?, ?> streamFileRepository = getStreamFileRepository(downloaderProperties.getStreamType());
        return streamFileRepository.findLatest()
                .map(StreamFile::getName)
                .map(Utility::getInstantFromFilename)
                .orElse(null);
    }

    private StreamFileRepository<?, ?> getStreamFileRepository(StreamType streamType) {
        switch (streamType) {
            case BALANCE:
                return accountBalanceFileRepository;
            case EVENT:
                return eventFileRepository;
            case RECORD:
                return recordFileRepository;
            default:
                throw new UnsupportedOperationException("Unsupported stream type " + streamType);
        }
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
            start = Utility.convertToNanosMax(startDate.getEpochSecond(), startDate.getNano());

            if (endDate == null) {
                end = Long.MAX_VALUE;
            } else {
                end = Utility.convertToNanosMax(endDate.getEpochSecond(), endDate.getNano());
            }
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

        public static DateRangeFilter empty() {
            return new DateRangeFilter(Instant.EPOCH.plusNanos(1), Instant.EPOCH);
        }

        public static DateRangeFilter all() {
            return new DateRangeFilter(Instant.EPOCH, Utility.MAX_INSTANT_LONG);
        }
    }
}
