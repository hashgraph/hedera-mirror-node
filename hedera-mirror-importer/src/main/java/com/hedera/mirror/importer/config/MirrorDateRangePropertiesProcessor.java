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
import lombok.Data;
import lombok.RequiredArgsConstructor;
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
        if (startDate != null && endDate != null && startDate.compareTo(endDate) > 0) {
            throw new InvalidConfigurationException(String.format("Date range constraint violation: " +
                    "startDate (%s) > endDate (%s)", startDate, endDate));
        }

        for (DownloaderProperties downloaderProperties : downloaderPropertiesList) {
            processDateRange(downloaderProperties);
        }

        applicationEventPublisher.publishEvent(new MirrorDateRangePropertiesProcessedEvent(this));
        log.info("Mirror date range properties processed successfully, MirrorDateRangePropertiesProcessedEvent fired");
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
     * @throws InvalidConfigurationException if the constraint effective startDate < endDate is violated
     */
    private void processDateRange(DownloaderProperties downloaderProperties) {
        // validate date range
        Instant effectiveStartDate = getEffectiveStartDate(downloaderProperties);
        Instant endDate = mirrorProperties.getEndDate();
        if (effectiveStartDate != null && endDate != null && effectiveStartDate.compareTo(endDate) > 0) {
            throw new InvalidConfigurationException(String.format("Date range constraint violation for %s downloader: " +
                            "effective startDate (%s) > endDate (%s)", downloaderProperties.getStreamType(),
                    effectiveStartDate, endDate));
        }

        // update application status
        Instant lastFileInstant = getLastValidDownloadedFileInstant(downloaderProperties);
        if (effectiveStartDate != null && !effectiveStartDate.equals(lastFileInstant)) {
            StreamType streamType = downloaderProperties.getStreamType();
            String filename = Utility.getStreamFilenameFromInstant(streamType, effectiveStartDate);
            applicationStatusRepository.updateStatusValue(downloaderProperties.getLastValidDownloadedFileKey(), filename);
            log.debug("Set last valid downloaded file to {} for {} downloader", filename, streamType);

            Instant verifyHashAfter = mirrorProperties.getVerifyHashAfter();
            if (verifyHashAfter == null || verifyHashAfter.isBefore(effectiveStartDate)) {
                mirrorProperties.setVerifyHashAfter(effectiveStartDate);
                log.debug("Set verifyHashAfter to {}", effectiveStartDate);
            }
        }
    }

    /**
     * Gets the effective startDate for downloader based on startDate in MirrorProperties, the startDateAdjustment
     * and last valid downloaded file. Also sets the effective inclusive start date of the items to accept.
     * @param downloaderProperties The downloader's properties
     * @return The effective startDate: null if downloader is disabled; if startDate is set, the effective startDate is
     * startDate if the database is empty or max(startDate, timestamp of last valid downloaded file); if startDate is
     * not set, the effective startDate is now if the database is empty, or the timestamp of last valid downloaded file
     */
    private Instant getEffectiveStartDate(DownloaderProperties downloaderProperties) {
        if (!downloaderProperties.isEnabled()) {
            filters.put(downloaderProperties.getStreamType(), DateRangeFilter.empty());
            return null;
        }

        Instant startDate = mirrorProperties.getStartDate();
        Instant startDateNow = MirrorProperties.getStartDateNow();
        Instant endDate = mirrorProperties.getEndDate();
        Instant lastFileInstant = getLastValidDownloadedFileInstant(downloaderProperties);
        Duration adjustment = downloaderProperties.getStartDateAdjustment();
        if (startDate != null) {
            if (startDate.isAfter(lastFileInstant) || endDate != null) {
                filters.put(downloaderProperties.getStreamType(), new DateRangeFilter(startDate, endDate));
            }
            return max(startDate.minus(adjustment), lastFileInstant);
        } else {
            if (lastFileInstant.equals(Instant.EPOCH)) {
                filters.put(downloaderProperties.getStreamType(), new DateRangeFilter(startDateNow, endDate));
                return startDateNow.minus(adjustment);
            } else {
                if (endDate != null) {
                    filters.put(downloaderProperties.getStreamType(), new DateRangeFilter(lastFileInstant, endDate));
                }
                return lastFileInstant;
            }
        }
    }

    private Instant getLastValidDownloadedFileInstant(DownloaderProperties downloaderProperties) {
        String filename = applicationStatusRepository.findByStatusCode(downloaderProperties.getLastValidDownloadedFileKey());
        return Utility.getInstantFromFilename(filename);
    }

    @Data
    public static class DateRangeFilter {
        private final long start;
        private final long end;

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

        public static DateRangeFilter empty() {
            return new DateRangeFilter(Instant.EPOCH.plusNanos(1), Instant.EPOCH);
        }
    }
}
