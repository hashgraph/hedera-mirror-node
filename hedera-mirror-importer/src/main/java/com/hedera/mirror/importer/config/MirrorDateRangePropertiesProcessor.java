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

import java.time.Instant;
import java.util.List;
import javax.inject.Named;
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

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void process() {
        for (DownloaderProperties downloaderProperties : downloaderPropertiesList) {
            validateDateRange(downloaderProperties);
        }

        for (DownloaderProperties downloaderProperties : downloaderPropertiesList) {
            updateApplicationStatus(downloaderProperties);
        }

        applicationEventPublisher.publishEvent(new MirrorDateRangePropertiesProcessedEvent(this));
        log.info("Mirror date range properties processed successfully, MirrorDateRangePropertiesProcessedEvent fired");
    }

    /**
     * Validates the configured (startDate, endDate] range for downloader.
     * @param downloaderProperties The properties of the downloader to validate the (startDate, endDate] range for
     * @throws InvalidConfigurationException if the constraint effective startDate < endDate is violated
     */
    private void validateDateRange(DownloaderProperties downloaderProperties) {
        Instant effectiveStartDate = getEffectiveStartDate(downloaderProperties);
        Instant endDate = mirrorProperties.getEndDate();
        if (effectiveStartDate != null && endDate != null && !endDate.isAfter(effectiveStartDate)) {
            throw new InvalidConfigurationException(String.format("Date range constraint violation for %s downloader: " +
                            "startDate (%s) >= endDate (%s)", downloaderProperties.getStreamType(),
                    effectiveStartDate, endDate));
        }
    }

    /**
     * Updates the application status for downloader based on the validated (startDate, endDate] range.
     * If effective startDate is not null, the application status is set so the downloader will pull data from files
     * after the effective startDate. In addition, verifyHashAfter is set to effective startDate if not set or before
     * effective startDate.
     * @param downloaderProperties The properties of the downloader to update application status for
     */
    private void updateApplicationStatus(DownloaderProperties downloaderProperties) {
        Instant effectiveStartDate = getEffectiveStartDate(downloaderProperties);
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
     * Gets the effective startDate for downloader based on startDate in MirrorProperties and application status.
     * @param downloaderProperties The downloader's properties
     * @return The effective startDate: null if downloader is disabled; the configured startDate if the last valid
     * downloaded file in application status repository is empty or the timestamp is not before startDate; now if startDate
     * is not set and application status is empty; null for all other cases
     */
    private Instant getEffectiveStartDate(DownloaderProperties downloaderProperties) {
        if (!downloaderProperties.isEnabled()) {
            return null;
        }

        Instant startDate = mirrorProperties.getStartDate();
        Instant lastFileInstant = getLastValidDownloadedFileInstant(downloaderProperties);
        if (startDate != null) {
            return lastFileInstant.isBefore(startDate) ? startDate : lastFileInstant;
        } else {
            return lastFileInstant.equals(Instant.EPOCH) ? MirrorProperties.getStartDateNow() : null;
        }
    }

    private Instant getLastValidDownloadedFileInstant(DownloaderProperties downloaderProperties) {
        String filename = applicationStatusRepository.findByStatusCode(downloaderProperties.getLastValidDownloadedFileKey());
        return Utility.getInstantFromFilename(filename);
    }
}
