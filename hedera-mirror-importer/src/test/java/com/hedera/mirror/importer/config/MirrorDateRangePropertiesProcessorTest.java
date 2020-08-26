package com.hedera.mirror.importer.config;

import static com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor.DateRangeFilter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
        CommonDownloaderProperties commonDownloaderProperties = new CommonDownloaderProperties(mirrorProperties);
        var balanceDownloaderProperties = new BalanceDownloaderProperties(mirrorProperties, commonDownloaderProperties);
        var eventDownloaderProperties=  new EventDownloaderProperties(mirrorProperties, commonDownloaderProperties);
        var recordDownloaderProperties = new RecordDownloaderProperties(mirrorProperties, commonDownloaderProperties);
        downloaderPropertiesList = List.of(balanceDownloaderProperties, eventDownloaderProperties, recordDownloaderProperties);
        mirrorDateRangePropertiesProcessor = new MirrorDateRangePropertiesProcessor(applicationEventPublisher,
                mirrorProperties, applicationStatusRepository, downloaderPropertiesList);

        balanceDownloaderProperties.setEnabled(true);
        eventDownloaderProperties.setEnabled(true);
        recordDownloaderProperties.setEnabled(true);

        maxAdjustment = downloaderPropertiesList.stream().map(DownloaderProperties::getStartDateAdjustment).max(Duration::compareTo).get();
        minAdjustment = downloaderPropertiesList.stream().map(DownloaderProperties::getStartDateAdjustment).min(Duration::compareTo).get();
    }

    @Test
    void notSetAndApplicationStatusEmpty() {
        mirrorDateRangePropertiesProcessor.process();

        verify(applicationEventPublisher).publishEvent(any(MirrorDateRangePropertiesProcessedEvent.class));
        Instant startUpInstant = MirrorProperties.getStartUpInstant();
        DateRangeFilter expectedFilter = new DateRangeFilter(startUpInstant, null);
        for (var downloaderProperties : downloaderPropertiesList) {
            String expectedFilename = Utility.getStreamFilenameFromInstant(
                    downloaderProperties.getStreamType(), adjustStartDate(startUpInstant, downloaderProperties));
            verify(applicationStatusRepository).updateStatusValue(downloaderProperties.getLastValidDownloadedFileKey(), expectedFilename);
            assertThat(mirrorDateRangePropertiesProcessor.getDateRangeFilter(downloaderProperties.getStreamType())).isEqualTo(expectedFilter);
        }
        assertThat(mirrorProperties.getVerifyHashAfter()).isEqualTo(adjustStartDate(MirrorProperties.getStartUpInstant(), minAdjustment));
    }

    @Test
    void notSetAndApplicationStatusNotEmpty() {
        Instant past = MirrorProperties.getStartUpInstant().minusSeconds(100);
        for (var downloaderProperties : downloaderPropertiesList) {
            doReturn(Utility.getStreamFilenameFromInstant(downloaderProperties.getStreamType(), past))
                    .when(applicationStatusRepository)
                    .findByStatusCode(downloaderProperties.getLastValidDownloadedFileKey());
        }

        mirrorDateRangePropertiesProcessor.process();

        verify(applicationEventPublisher).publishEvent(any(MirrorDateRangePropertiesProcessedEvent.class));
        verify(applicationStatusRepository, never()).updateStatusValue(any(ApplicationStatusCode.class), any(String.class));
        for (var downloaderProperties : downloaderPropertiesList) {
            assertThat(mirrorDateRangePropertiesProcessor.getDateRangeFilter(downloaderProperties.getStreamType())).isNull();
        }
        assertThat(mirrorProperties.getVerifyHashAfter()).isEqualTo(Instant.EPOCH);
    }

    @ParameterizedTest(name = "startDate {0}ns before application status")
    @ValueSource(longs = {0, 1})
    void startDateNotAfterApplicationStatus(long nanos) {
        Instant past = MirrorProperties.getStartUpInstant().minusSeconds(100);
        for (var downloaderProperties : downloaderPropertiesList) {
            doReturn(Utility.getStreamFilenameFromInstant(downloaderProperties.getStreamType(), past))
                    .when(applicationStatusRepository)
                    .findByStatusCode(downloaderProperties.getLastValidDownloadedFileKey());
        }
        mirrorProperties.setStartDate(past.minusNanos(nanos));

        mirrorDateRangePropertiesProcessor.process();

        verify(applicationEventPublisher).publishEvent(any(MirrorDateRangePropertiesProcessedEvent.class));
        verify(applicationStatusRepository, never()).updateStatusValue(any(ApplicationStatusCode.class), any(String.class));
        for (var downloaderProperties : downloaderPropertiesList) {
            assertThat(mirrorDateRangePropertiesProcessor.getDateRangeFilter(downloaderProperties.getStreamType())).isNull();
        }
        assertThat(mirrorProperties.getVerifyHashAfter()).isEqualTo(Instant.EPOCH);
    }

    @Test
    void startDateAfterApplicationStatus() {
        Instant past = MirrorProperties.getStartUpInstant().minusSeconds(100 + maxAdjustment.toSeconds());
        for (var downloaderProperties : downloaderPropertiesList) {
            doReturn(Utility.getStreamFilenameFromInstant(downloaderProperties.getStreamType(), past))
                    .when(applicationStatusRepository)
                    .findByStatusCode(downloaderProperties.getLastValidDownloadedFileKey());
        }
        Instant startDate = past.plusSeconds(maxAdjustment.toSeconds()).plusNanos(1);
        mirrorProperties.setStartDate(startDate);

        mirrorDateRangePropertiesProcessor.process();

        verify(applicationEventPublisher).publishEvent(any(MirrorDateRangePropertiesProcessedEvent.class));
        DateRangeFilter expectedFilter = new DateRangeFilter(startDate, null);
        for (var downloaderProperties : downloaderPropertiesList) {
            String expectedFilename = Utility.getStreamFilenameFromInstant(downloaderProperties.getStreamType(), adjustStartDate(startDate, downloaderProperties));
            verify(applicationStatusRepository).updateStatusValue(downloaderProperties.getLastValidDownloadedFileKey(), expectedFilename);
            assertThat(mirrorDateRangePropertiesProcessor.getDateRangeFilter(downloaderProperties.getStreamType())).isEqualTo(expectedFilter);
        }
        assertThat(mirrorProperties.getVerifyHashAfter()).isEqualTo(adjustStartDate(startDate, minAdjustment));
    }

    @ParameterizedTest(name = "startDate {0} endDate {1} application status {2} violates (effective) start date <= end date constraint")
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
                doReturn(Utility.getStreamFilenameFromInstant(downloaderProperties.getStreamType(), applicationStatusDate))
                        .when(applicationStatusRepository)
                        .findByStatusCode(downloaderProperties.getLastValidDownloadedFileKey());
            }
        }

        assertThatThrownBy(() -> mirrorDateRangePropertiesProcessor.process()).isInstanceOf(InvalidConfigurationException.class);
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
