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

import static com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor.DateRangeFilter;
import static com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor.STARTUP_TIME;
import static org.apache.commons.lang3.ObjectUtils.max;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.config.event.MirrorDateRangePropertiesProcessedEvent;
import com.hedera.mirror.importer.domain.ApplicationStatusCode;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;
import com.hedera.mirror.importer.downloader.DownloaderProperties;
import com.hedera.mirror.importer.downloader.balance.BalanceDownloaderProperties;
import com.hedera.mirror.importer.downloader.event.EventDownloaderProperties;
import com.hedera.mirror.importer.downloader.record.RecordDownloaderProperties;
import com.hedera.mirror.importer.exception.InvalidConfigurationException;
import com.hedera.mirror.importer.repository.ApplicationStatusRepository;
import com.hedera.mirror.importer.util.Utility;

@ExtendWith(MockitoExtension.class)
public class MirrorDateRangePropertiesProcessorTest {

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    private MirrorProperties mirrorProperties;
    @Mock(lenient = true)
    private ApplicationStatusRepository applicationStatusRepository;
    private List<DownloaderProperties> downloaderPropertiesList;
    private Duration maxAdjustment;
    private Duration minAdjustment;

    private MirrorDateRangePropertiesProcessor mirrorDateRangePropertiesProcessor;

    @BeforeEach
    void setUp() {
        mirrorProperties = new MirrorProperties();
        mirrorProperties.setNetwork(MirrorProperties.HederaNetwork.TESTNET);
        CommonDownloaderProperties commonDownloaderProperties = new CommonDownloaderProperties(mirrorProperties);
        var balanceDownloaderProperties = new BalanceDownloaderProperties(mirrorProperties, commonDownloaderProperties);
        var eventDownloaderProperties = new EventDownloaderProperties(mirrorProperties, commonDownloaderProperties);
        var recordDownloaderProperties = new RecordDownloaderProperties(mirrorProperties, commonDownloaderProperties);
        downloaderPropertiesList = List
                .of(balanceDownloaderProperties, eventDownloaderProperties, recordDownloaderProperties);
        mirrorDateRangePropertiesProcessor = new MirrorDateRangePropertiesProcessor(applicationEventPublisher,
                mirrorProperties, applicationStatusRepository, downloaderPropertiesList);

        balanceDownloaderProperties.setEnabled(true);
        eventDownloaderProperties.setEnabled(true);
        recordDownloaderProperties.setEnabled(true);

        maxAdjustment = downloaderPropertiesList.stream().map(DownloaderProperties::getStartDateAdjustment)
                .max(Duration::compareTo).get();
        minAdjustment = downloaderPropertiesList.stream().map(DownloaderProperties::getStartDateAdjustment)
                .min(Duration::compareTo).get();
    }

    @Test
    void notSetAndApplicationStatusEmpty() {
        mirrorDateRangePropertiesProcessor.process();

        verify(applicationEventPublisher).publishEvent(any(MirrorDateRangePropertiesProcessedEvent.class));
        Instant startUpInstant = STARTUP_TIME;
        DateRangeFilter expectedFilter = new DateRangeFilter(startUpInstant, null);
        for (var downloaderProperties : downloaderPropertiesList) {
            String expectedFilename = Utility.getStreamFilenameFromInstant(
                    downloaderProperties.getStreamType(), adjustStartDate(startUpInstant, downloaderProperties));
            verify(applicationStatusRepository)
                    .updateStatusValue(downloaderProperties.getLastValidDownloadedFileKey(), expectedFilename);
            assertThat(mirrorDateRangePropertiesProcessor.getDateRangeFilter(downloaderProperties.getStreamType()))
                    .isEqualTo(expectedFilter);
        }
        assertThat(mirrorProperties.getVerifyHashAfter()).isEqualTo(adjustStartDate(STARTUP_TIME, minAdjustment));
    }

    @Test
    void notSetAndDemoNetworkAndApplicationStatusNotEmpty() {
        mirrorProperties.setNetwork(MirrorProperties.HederaNetwork.DEMO);
        Instant past = STARTUP_TIME.minusSeconds(100);
        for (var downloaderProperties : downloaderPropertiesList) {
            doReturn(Utility.getStreamFilenameFromInstant(downloaderProperties.getStreamType(), past))
                    .when(applicationStatusRepository)
                    .findByStatusCode(downloaderProperties.getLastValidDownloadedFileKey());
        }

        mirrorDateRangePropertiesProcessor.process();

        verify(applicationEventPublisher).publishEvent(any(MirrorDateRangePropertiesProcessedEvent.class));
        verify(applicationStatusRepository, never())
                .updateStatusValue(any(ApplicationStatusCode.class), any(String.class));
        for (var downloaderProperties : downloaderPropertiesList) {
            assertThat(mirrorDateRangePropertiesProcessor.getDateRangeFilter(downloaderProperties.getStreamType()))
                    .isEqualTo(new DateRangeFilter(past, Utility.MAX_INSTANT_LONG));
        }
        assertThat(mirrorProperties.getVerifyHashAfter()).isEqualTo(Instant.EPOCH);
    }

    @Test
    void notSetAndApplicationStatusNotEmpty() {
        Instant past = STARTUP_TIME.minusSeconds(100);
        for (var downloaderProperties : downloaderPropertiesList) {
            doReturn(Utility.getStreamFilenameFromInstant(downloaderProperties.getStreamType(), past))
                    .when(applicationStatusRepository)
                    .findByStatusCode(downloaderProperties.getLastValidDownloadedFileKey());
        }

        mirrorDateRangePropertiesProcessor.process();

        verify(applicationEventPublisher).publishEvent(any(MirrorDateRangePropertiesProcessedEvent.class));
        verify(applicationStatusRepository, never())
                .updateStatusValue(any(ApplicationStatusCode.class), any(String.class));
        for (var downloaderProperties : downloaderPropertiesList) {
            assertThat(mirrorDateRangePropertiesProcessor.getDateRangeFilter(downloaderProperties.getStreamType()))
                    .isEqualTo(new DateRangeFilter(past, Utility.MAX_INSTANT_LONG));
        }
        assertThat(mirrorProperties.getVerifyHashAfter()).isEqualTo(Instant.EPOCH);
    }

    @Test
    void startDateNotSetAndEndDateAfterLongMaxAndApplicationStatusNotEmpty() {
        Instant past = STARTUP_TIME.minusSeconds(100);
        for (var downloaderProperties : downloaderPropertiesList) {
            doReturn(Utility.getStreamFilenameFromInstant(downloaderProperties.getStreamType(), past))
                    .when(applicationStatusRepository)
                    .findByStatusCode(downloaderProperties.getLastValidDownloadedFileKey());
        }
        mirrorProperties.setEndDate(Utility.MAX_INSTANT_LONG.plusNanos(1));

        mirrorDateRangePropertiesProcessor.process();

        verify(applicationEventPublisher).publishEvent(any(MirrorDateRangePropertiesProcessedEvent.class));
        verify(applicationStatusRepository, never())
                .updateStatusValue(any(ApplicationStatusCode.class), any(String.class));
        for (var downloaderProperties : downloaderPropertiesList) {
            assertThat(mirrorDateRangePropertiesProcessor.getDateRangeFilter(downloaderProperties.getStreamType()))
                    .isEqualTo(new DateRangeFilter(past, Utility.MAX_INSTANT_LONG));
        }
        assertThat(mirrorProperties.getVerifyHashAfter()).isEqualTo(Instant.EPOCH);
    }

    @ParameterizedTest(name = "startDate {0}ns before application status, endDate")
    @ValueSource(longs = {0, 1})
    void startDateNotAfterApplicationStatus(long nanos) {
        Instant past = STARTUP_TIME.minusSeconds(100);
        for (var downloaderProperties : downloaderPropertiesList) {
            doReturn(Utility.getStreamFilenameFromInstant(downloaderProperties.getStreamType(), past))
                    .when(applicationStatusRepository)
                    .findByStatusCode(downloaderProperties.getLastValidDownloadedFileKey());
        }
        mirrorProperties.setStartDate(past.minusNanos(nanos));

        mirrorDateRangePropertiesProcessor.process();

        verify(applicationEventPublisher).publishEvent(any(MirrorDateRangePropertiesProcessedEvent.class));
        verify(applicationStatusRepository, never())
                .updateStatusValue(any(ApplicationStatusCode.class), any(String.class));
        for (var downloaderProperties : downloaderPropertiesList) {
            assertThat(mirrorDateRangePropertiesProcessor.getDateRangeFilter(downloaderProperties.getStreamType()))
                    .isEqualTo(new DateRangeFilter(past, null));
        }
        assertThat(mirrorProperties.getVerifyHashAfter()).isEqualTo(Instant.EPOCH);
    }

    @ParameterizedTest(name = "startDate is {0}ns after application status")
    @ValueSource(longs = {1, 2_000_000_000L, 200_000_000_000L})
    void startDateAfterApplicationStatus(long diffNanos) {
        Instant lastFileInstant = Instant.now().minusSeconds(200);
        for (var downloaderProperties : downloaderPropertiesList) {
            doReturn(Utility.getStreamFilenameFromInstant(downloaderProperties.getStreamType(), lastFileInstant))
                    .when(applicationStatusRepository)
                    .findByStatusCode(downloaderProperties.getLastValidDownloadedFileKey());
        }
        Instant startDate = lastFileInstant.plusNanos(diffNanos);
        mirrorProperties.setStartDate(startDate);

        mirrorDateRangePropertiesProcessor.process();

        verify(applicationEventPublisher).publishEvent(any(MirrorDateRangePropertiesProcessedEvent.class));
        DateRangeFilter expectedFilter = new DateRangeFilter(startDate, null);
        for (var downloaderProperties : downloaderPropertiesList) {
            Instant effectiveStartDate = max(adjustStartDate(startDate, downloaderProperties), lastFileInstant);
            if (effectiveStartDate.equals(lastFileInstant)) {
                verify(applicationStatusRepository, never())
                        .updateStatusValue(eq(downloaderProperties.getLastValidDownloadedFileKey()), any(String.class));
            } else {
                String expectedFilename = Utility
                        .getStreamFilenameFromInstant(downloaderProperties.getStreamType(), effectiveStartDate);
                verify(applicationStatusRepository)
                        .updateStatusValue(downloaderProperties.getLastValidDownloadedFileKey(), expectedFilename);
            }
            assertThat(mirrorDateRangePropertiesProcessor.getDateRangeFilter(downloaderProperties.getStreamType()))
                    .isEqualTo(expectedFilter);
        }

        Instant expectedVerifyHashAfter;
        if (diffNanos > maxAdjustment.toNanos()) {
            expectedVerifyHashAfter = adjustStartDate(startDate, minAdjustment);
        } else {
            expectedVerifyHashAfter = mirrorProperties.getVerifyHashAfter();
        }
        assertThat(mirrorProperties.getVerifyHashAfter()).isEqualTo(expectedVerifyHashAfter);
    }

    @ParameterizedTest(name = "startDate {0} endDate {1} application status {2} violates (effective) start date <= " +
            "end date constraint")
    @CsvSource(value = {
            "2020-08-18T09:00:05.124Z, 2020-08-18T09:00:05.123Z,",
            "2020-08-18T09:00:04.123Z, 2020-08-18T09:00:05.123Z, 2020-08-18T09:00:05.124Z",
            "2020-08-18T09:00:04.123Z, 2020-08-18T09:00:05.123Z, 2020-08-18T09:00:06.123Z",
            ", 2020-08-18T09:00:05.123Z, 2020-08-19T09:00:05.111Z",
            ", 2020-08-18T09:00:05.123Z,"
    })
    void startDateNotBeforeEndDate(Instant startDate, Instant endDate, Instant applicationStatusDate) {
        mirrorProperties.setStartDate(startDate);
        mirrorProperties.setEndDate(endDate);
        if (applicationStatusDate != null) {
            for (var downloaderProperties : downloaderPropertiesList) {
                doReturn(Utility
                        .getStreamFilenameFromInstant(downloaderProperties.getStreamType(), applicationStatusDate))
                        .when(applicationStatusRepository)
                        .findByStatusCode(downloaderProperties.getLastValidDownloadedFileKey());
            }
        }

        assertThatThrownBy(() -> mirrorDateRangePropertiesProcessor.process())
                .isInstanceOf(InvalidConfigurationException.class);
        verify(applicationEventPublisher, never()).publishEvent(any(MirrorDateRangePropertiesProcessedEvent.class));
    }

    @ParameterizedTest(name = "timestamp {0} does not pass empty filter")
    @ValueSource(longs = {-10L, 0L, 1L, 10L, 8L, 100L})
    void emptyFilter(long timestamp) {
        DateRangeFilter filter = DateRangeFilter.empty();
        assertThat(filter.filter(timestamp)).isFalse();
    }

    @ParameterizedTest(name = "filter [{0}, {1}], timestamp {2}, pass: {3}")
    @CsvSource(value = {
            "1, 1, 1, true",
            "1, 10, 1, true",
            "1, 10, 10, true",
            "1, 10, 6, true",
            "1, 10, 0, false",
            "1, 10, 11, false",
            "1, 10, -1, false",
    })
    void filter(long start, long end, long timestamp, boolean expected) {
        DateRangeFilter filter = new DateRangeFilter(Instant.ofEpochSecond(0, start), Instant.ofEpochSecond(0, end));
        assertThat(filter.filter(timestamp)).isEqualTo(expected);
    }

    private Instant adjustStartDate(Instant startDate, DownloaderProperties downloaderProperties) {
        return adjustStartDate(startDate, downloaderProperties.getStartDateAdjustment());
    }

    private Instant adjustStartDate(Instant startDate, Duration adjustment) {
        return startDate.minus(adjustment);
    }
}
