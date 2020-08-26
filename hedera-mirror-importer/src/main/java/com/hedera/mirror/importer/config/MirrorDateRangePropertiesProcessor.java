package com.hedera.mirror.importer.config;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Named;
import javax.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.config.event.MirrorDateRangePropertiesProcessedEvent;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.downloader.DownloaderProperties;
import com.hedera.mirror.importer.exception.InvalidConfigurationException;
import com.hedera.mirror.importer.repository.ApplicationStatusRepository;
import com.hedera.mirror.importer.util.Utility;

@Log4j2
@Named
@RequiredArgsConstructor
public class MirrorDateRangePropertiesProcessor {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final MirrorProperties mirrorProperties;
    private final ApplicationStatusRepository applicationStatusRepository;
    private final List<DownloaderProperties> downloaderPropertiesList;

    private Map<StreamType, DateRangeFilter> filters = new HashMap<>();

    @Transactional
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void process() {
        Instant startDate = mirrorProperties.getStartDate();
        Instant endDate = mirrorProperties.getEndDate();
        if (startDate != null && startDate.compareTo(endDate) > 0) {
            throw new InvalidConfigurationException(String.format("Date range constraint violation: " +
                    "startDate (%s) > endDate (%s)", startDate, endDate));
        }

        for (DownloaderProperties downloaderProperties : downloaderPropertiesList) {
            processDateRange(downloaderProperties);
        }

        applicationEventPublisher.publishEvent(new MirrorDateRangePropertiesProcessedEvent(this));
        log.debug("Mirror date range properties processed successfully, MirrorDateRangePropertiesProcessedEvent fired");
    }

    /**
     * Gets the DateRangeFilter for the downloader (record, balance, event).
     * @param type - downloader type
     * @return the DateRangeFilter
     */
    public DateRangeFilter getDateRangeFilter(StreamType type) {
        return filters.get(type);
    }

    /**
     * Validates the configured [startDate, endDate] range for downloader and updates its application status if needed.
     * @param downloaderProperties the properties of the downloader to validate the [startDate, endDate] range for
     * @throws InvalidConfigurationException if the constraint effective startDate <= endDate is violated
     */
    private void processDateRange(DownloaderProperties downloaderProperties) {
        StreamType streamType = downloaderProperties.getStreamType();
        if (!downloaderProperties.isEnabled()) {
            filters.put(streamType, DateRangeFilter.empty());
            return;
        }

        Instant effectiveStartDate = validateDateRange(downloaderProperties);
        setDateRangeFilter(downloaderProperties);
        updateApplicationStatus(downloaderProperties, effectiveStartDate);
    }

    /**
     * Validates that effective startDate <= endDate for the downloader.
     * @param downloaderProperties the properties of the downloader
     * @return effective startDate
     * @throws InvalidConfigurationException if effective startDate > endDate
     */
    private Instant validateDateRange(DownloaderProperties downloaderProperties) {
        Instant effectiveStartDate = getEffectiveStartDate(downloaderProperties);
        Instant endDate = mirrorProperties.getEndDate();
        if (effectiveStartDate.compareTo(endDate) > 0) {
            throw new InvalidConfigurationException(String.format("Date range constraint violation for %s downloader: " +
                            "effective startDate (%s) > endDate (%s)", downloaderProperties.getStreamType(),
                    effectiveStartDate, endDate));
        }

        return effectiveStartDate;
    }

    /**
     * Sets DateRangeFilter for downloader
     * @param downloaderProperties the properties of the downloader
     */
    private void setDateRangeFilter(DownloaderProperties downloaderProperties) {
        Instant startDate = mirrorProperties.getStartDate();
        Instant endDate = mirrorProperties.getEndDate();
        Instant lastFileInstant = getLastValidDownloadedFileInstant(downloaderProperties);

        Instant filterStartDate = null;
        if (startDate != null) {
            if (startDate.isAfter(lastFileInstant)) {
                filterStartDate = startDate;
            } else if (!endDate.equals(Instant.MAX)) {
                filterStartDate = lastFileInstant;
            }
        } else {
            if (lastFileInstant.equals(Instant.EPOCH)) {
                filterStartDate = MirrorProperties.getStartUpInstant();;
            } else if (!endDate.equals(Instant.MAX)) {
                filterStartDate = lastFileInstant;
            }
        }

        if (filterStartDate != null) {
            filters.put(downloaderProperties.getStreamType(), new DateRangeFilter(filterStartDate, endDate));
        }
    }

    /**
     * Updates application status for the downloader. The last valid downloaded file in application status is set to
     * the file at effectiveStartDate of the downloader's format if effectiveStartDate is different than the timestamp
     * of the file in application status.
     * @param downloaderProperties the properties of the downloader
     * @param effectiveStartDate the effective startDate
     */
    private void updateApplicationStatus(DownloaderProperties downloaderProperties, Instant effectiveStartDate) {
        // update application status
        Instant lastFileInstant = getLastValidDownloadedFileInstant(downloaderProperties);
        StreamType streamType = downloaderProperties.getStreamType();
        if (!effectiveStartDate.equals(lastFileInstant)) {
            String filename = Utility.getStreamFilenameFromInstant(streamType, effectiveStartDate);
            applicationStatusRepository.updateStatusValue(downloaderProperties.getLastValidDownloadedFileKey(), filename);
            log.debug("Set last valid downloaded file to {} for {} downloader", filename, streamType);

            Instant verifyHashAfter = mirrorProperties.getVerifyHashAfter();
            if (verifyHashAfter == null || verifyHashAfter.isBefore(effectiveStartDate)) {
                mirrorProperties.setVerifyHashAfter(effectiveStartDate);
                log.debug("Set verifyHashAfter to {}", effectiveStartDate);
            }

            if (log.isInfoEnabled()) {
                DateRangeFilter filter = filters.get(streamType);
                log.info("{}: downloader will download files in time range ({}, {}], parser will parse items " +
                                "in time range [{}, {}]", streamType, effectiveStartDate, mirrorProperties.getEndDate(),
                        filter.getStartAsInstant(), filter.getEndAsInstant());
            }
        }
    }

    /**
     * Gets the effective startDate for downloader based on startDate in MirrorProperties, the startDateAdjustment
     * and last valid downloaded file.
     * @param downloaderProperties The downloader's properties
     * @return The effective startDate: null if downloader is disabled; if startDate is set, the effective startDate is
     * startDate if the database is empty or max(startDate, timestamp of last valid downloaded file); if startDate is
     * not set, the effective startDate is now if the database is empty, or the timestamp of last valid downloaded file
     */
    private Instant getEffectiveStartDate(DownloaderProperties downloaderProperties) {
        Instant startDate = mirrorProperties.getStartDate();
        Instant startUpInstant = MirrorProperties.getStartUpInstant();
        Instant lastFileInstant = getLastValidDownloadedFileInstant(downloaderProperties);
        Duration adjustment = downloaderProperties.getStartDateAdjustment();

        if (startDate != null) {
            return max(startDate.minus(adjustment), lastFileInstant);
        } else {
            return lastFileInstant.equals(Instant.EPOCH) ? startUpInstant.minus(adjustment) : lastFileInstant;
        }
    }

    private Instant getLastValidDownloadedFileInstant(DownloaderProperties downloaderProperties) {
        String filename = applicationStatusRepository.findByStatusCode(downloaderProperties.getLastValidDownloadedFileKey());
        return Utility.getInstantFromFilename(filename);
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

        public String toString() {
            return String.format("DateRangeFilter([%s, %s])", getStartAsInstant(), getEndAsInstant());
        }

        public static DateRangeFilter empty() {
            return new DateRangeFilter(Instant.EPOCH.plusNanos(1), Instant.EPOCH);
        }
    }
}
