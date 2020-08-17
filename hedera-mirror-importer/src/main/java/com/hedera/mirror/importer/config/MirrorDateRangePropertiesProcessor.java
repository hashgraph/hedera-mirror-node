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
import javax.annotation.PostConstruct;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.downloader.Downloader;
import com.hedera.mirror.importer.downloader.DownloaderProperties;
import com.hedera.mirror.importer.exception.InvalidConfigurationException;
import com.hedera.mirror.importer.repository.ApplicationStatusRepository;
import com.hedera.mirror.importer.util.Utility;

@Named
@RequiredArgsConstructor
public class MirrorDateRangePropertiesProcessor {

    private final MirrorProperties mirrorProperties;

    private final ApplicationStatusRepository applicationStatusRepository;

    private final List<Downloader> downloaders;

    @PostConstruct
    private void init() {
        for (Downloader downloader : downloaders) {
            validateDateRange(downloader);
        }

        for (Downloader downloader : downloaders) {
            updateStartDate(downloader);
        }
    }

    private void validateDateRange(Downloader downloader) {
        Instant effectiveStartDate = getEffectiveStartDate(downloader);
        Instant endDate = mirrorProperties.getEndDate();
        if (effectiveStartDate != null && endDate != null && !endDate.isAfter(effectiveStartDate)) {
            throw new InvalidConfigurationException(String.format("Date range constraint violation for %s downloader: " +
                            "startDate (%s) >= endDate (%s)", downloader.getDownloaderProperties().getStreamType(),
                    effectiveStartDate, endDate));
        }
    }

    private void updateStartDate(Downloader downloader) {
        Instant effectiveStartDate = getEffectiveStartDate(downloader);
        if (effectiveStartDate != null) {
            applicationStatusRepository.updateStatusValue(downloader.getLastValidDownloadedFileKey(),
                    Utility.getStreamFilenameFromInstant(downloader.getDownloaderProperties().getStreamType(), effectiveStartDate));
        }

        Instant startDate = mirrorProperties.getStartDate();
        Instant verifyHashAfter = mirrorProperties.getVerifyHashAfter();
        if (startDate != null && (verifyHashAfter == null || verifyHashAfter.isBefore(startDate))) {
            mirrorProperties.setVerifyHashAfter(startDate);
        }
    }

    private Instant getEffectiveStartDate(Downloader downloader) {
        DownloaderProperties downloaderProperties = downloader.getDownloaderProperties();
        if (!downloaderProperties.isEnabled()) {
            return null;
        }

        Instant startDate = mirrorProperties.getStartDate();
        String filename = applicationStatusRepository.findByStatusCode(downloader.getLastValidDownloadedFileKey());
        Instant timestamp = Utility.getInstantFromFilename(filename);
        if (startDate != null) {
            return timestamp.isBefore(startDate) ? startDate : timestamp;
        } else {
            return timestamp.equals(Instant.EPOCH) ? MirrorProperties.getStartDateNow() : timestamp;
        }
    }
}
