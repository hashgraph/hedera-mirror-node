package com.hedera.mirror.importer.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
    }

    @Test
    void notSetAndApplicationStatusEmpty() {
        mirrorDateRangePropertiesProcessor.process();

        verify(applicationEventPublisher).publishEvent(any(MirrorDateRangePropertiesProcessedEvent.class));
        for (var downloaderProperties : downloaderPropertiesList) {
            String expectedFilename = Utility.getStreamFilenameFromInstant(downloaderProperties.getStreamType(), MirrorProperties.getStartDateNow());
            verify(applicationStatusRepository).updateStatusValue(downloaderProperties.getLastValidDownloadedFileKey(), expectedFilename);
        }
        assertThat(mirrorProperties.getVerifyHashAfter()).isEqualTo(MirrorProperties.getStartDateNow());
    }

    @Test
    void notSetAndApplicationStatusNotEmpty() {
        Instant past = MirrorProperties.getStartDateNow().minusSeconds(100);
        for (var downloaderProperties : downloaderPropertiesList) {
            doReturn(Utility.getStreamFilenameFromInstant(downloaderProperties.getStreamType(), past))
                    .when(applicationStatusRepository)
                    .findByStatusCode(downloaderProperties.getLastValidDownloadedFileKey());
        }

        mirrorDateRangePropertiesProcessor.process();

        verify(applicationEventPublisher).publishEvent(any(MirrorDateRangePropertiesProcessedEvent.class));
        verify(applicationStatusRepository, never()).updateStatusValue(any(ApplicationStatusCode.class), any(String.class));
        assertThat(mirrorProperties.getVerifyHashAfter()).isEqualTo(Instant.EPOCH);
    }

    @ParameterizedTest(name = "startDate {0}ns before application status")
    @ValueSource(longs = {0, 1})
    void startDateNotAfterApplicationStatus(long nanos) {
        Instant past = MirrorProperties.getStartDateNow().minusSeconds(100);
        for (var downloaderProperties : downloaderPropertiesList) {
            doReturn(Utility.getStreamFilenameFromInstant(downloaderProperties.getStreamType(), past))
                    .when(applicationStatusRepository)
                    .findByStatusCode(downloaderProperties.getLastValidDownloadedFileKey());
        }
        mirrorProperties.setStartDate(past.minusNanos(nanos));

        mirrorDateRangePropertiesProcessor.process();

        verify(applicationEventPublisher).publishEvent(any(MirrorDateRangePropertiesProcessedEvent.class));
        verify(applicationStatusRepository, never()).updateStatusValue(any(ApplicationStatusCode.class), any(String.class));
        assertThat(mirrorProperties.getVerifyHashAfter()).isEqualTo(Instant.EPOCH);
    }

    @Test
    void startDateAfterApplicationStatus() {
        Instant past = MirrorProperties.getStartDateNow().minusSeconds(100);
        for (var downloaderProperties : downloaderPropertiesList) {
            doReturn(Utility.getStreamFilenameFromInstant(downloaderProperties.getStreamType(), past))
                    .when(applicationStatusRepository)
                    .findByStatusCode(downloaderProperties.getLastValidDownloadedFileKey());
        }
        Instant startDate = past.plusNanos(1);
        mirrorProperties.setStartDate(startDate);

        mirrorDateRangePropertiesProcessor.process();

        verify(applicationEventPublisher).publishEvent(any(MirrorDateRangePropertiesProcessedEvent.class));
        for (var downloaderProperties : downloaderPropertiesList) {
            String expectedFilename = Utility.getStreamFilenameFromInstant(downloaderProperties.getStreamType(), startDate);
            verify(applicationStatusRepository).updateStatusValue(downloaderProperties.getLastValidDownloadedFileKey(), expectedFilename);
        }
        assertThat(mirrorProperties.getVerifyHashAfter()).isEqualTo(startDate);
    }

    @ParameterizedTest(name = "startDate {0} endDate {1} application status {2} violates effective start date < end date constraint")
    @CsvSource(value = {
            "2020-08-18T09:00:05.123Z, 2020-08-18T09:00:05.123Z,",
            "2020-08-18T09:00:06.123Z, 2020-08-18T09:00:05.123Z,",
            "2020-08-18T09:00:04.123Z, 2020-08-18T09:00:05.123Z, 2020-08-18T09:00:05.123Z", //
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
}
