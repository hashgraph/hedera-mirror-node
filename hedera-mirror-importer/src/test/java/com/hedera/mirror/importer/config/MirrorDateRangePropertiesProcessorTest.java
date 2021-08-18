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
import static com.hedera.mirror.importer.domain.StreamFilename.FileType.DATA;
import static org.apache.commons.lang3.ObjectUtils.max;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cglib.core.ReflectUtils;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.StreamFile;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;
import com.hedera.mirror.importer.downloader.DownloaderProperties;
import com.hedera.mirror.importer.downloader.balance.BalanceDownloaderProperties;
import com.hedera.mirror.importer.downloader.event.EventDownloaderProperties;
import com.hedera.mirror.importer.downloader.record.RecordDownloaderProperties;
import com.hedera.mirror.importer.exception.InvalidConfigurationException;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import com.hedera.mirror.importer.repository.EventFileRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.util.Utility;

@ExtendWith(MockitoExtension.class)
class MirrorDateRangePropertiesProcessorTest {

    @Mock
    private AccountBalanceFileRepository accountBalanceFileRepository;

    @Mock
    private EventFileRepository eventFileRepository;

    @Mock
    private RecordFileRepository recordFileRepository;

    private MirrorProperties mirrorProperties;
    private List<DownloaderProperties> downloaderPropertiesList;

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
        mirrorDateRangePropertiesProcessor = new MirrorDateRangePropertiesProcessor(mirrorProperties,
                downloaderPropertiesList, accountBalanceFileRepository, eventFileRepository, recordFileRepository);

        balanceDownloaderProperties.setEnabled(true);
        eventDownloaderProperties.setEnabled(true);
        recordDownloaderProperties.setEnabled(true);
    }

    @Test
    void notSetAndDatabaseEmpty() {
        Instant startUpInstant = STARTUP_TIME;
        DateRangeFilter expectedFilter = new DateRangeFilter(startUpInstant, null);
        Instant expectedDate = adjustStartDate(startUpInstant);
        for (var downloaderProperties : downloaderPropertiesList) {
            StreamType streamType = downloaderProperties.getStreamType();
            assertThat(mirrorDateRangePropertiesProcessor.getLastStreamFile(streamType))
                    .isEqualTo(streamFile(streamType, expectedDate));
            assertThat(mirrorDateRangePropertiesProcessor.getDateRangeFilter(streamType)).isEqualTo(expectedFilter);
        }
        assertThat(mirrorProperties.getVerifyHashAfter()).isEqualTo(adjustStartDate(STARTUP_TIME));
    }

    @Test
    void notSetAndDemoNetworkAndDatabaseNotEmpty() {
        mirrorProperties.setNetwork(MirrorProperties.HederaNetwork.DEMO);
        Instant past = STARTUP_TIME.minusSeconds(100);

        doReturn(streamFile(StreamType.BALANCE, past)).when(accountBalanceFileRepository).findLatest();
        doReturn(streamFile(StreamType.EVENT, past)).when(eventFileRepository).findLatest();
        doReturn(streamFile(StreamType.RECORD, past)).when(recordFileRepository).findLatest();

        for (var downloaderProperties : downloaderPropertiesList) {
            StreamType streamType = downloaderProperties.getStreamType();
            assertThat(mirrorDateRangePropertiesProcessor.getLastStreamFile(streamType)).matches(s -> matches(s, past));
            assertThat(mirrorDateRangePropertiesProcessor.getDateRangeFilter(streamType))
                    .isEqualTo(new DateRangeFilter(past, Utility.MAX_INSTANT_LONG));
        }
        assertThat(mirrorProperties.getVerifyHashAfter()).isEqualTo(Instant.EPOCH);
    }

    @Test
    void notSetAndDatabaseNotEmpty() {
        Instant past = STARTUP_TIME.minusSeconds(100);

        doReturn(streamFile(StreamType.BALANCE, past)).when(accountBalanceFileRepository).findLatest();
        doReturn(streamFile(StreamType.EVENT, past)).when(eventFileRepository).findLatest();
        doReturn(streamFile(StreamType.RECORD, past)).when(recordFileRepository).findLatest();

        for (var downloaderProperties : downloaderPropertiesList) {
            StreamType streamType = downloaderProperties.getStreamType();
            assertThat(mirrorDateRangePropertiesProcessor.getLastStreamFile(streamType)).matches(s -> matches(s, past));
            assertThat(mirrorDateRangePropertiesProcessor.getDateRangeFilter(streamType))
                    .isEqualTo(new DateRangeFilter(past, Utility.MAX_INSTANT_LONG));
        }
        assertThat(mirrorProperties.getVerifyHashAfter()).isEqualTo(Instant.EPOCH);
    }

    @Test
    void startDateNotSetAndEndDateAfterLongMaxAndDatabaseNotEmpty() {
        Instant past = STARTUP_TIME.minusSeconds(100);
        mirrorProperties.setEndDate(Utility.MAX_INSTANT_LONG.plusNanos(1));

        doReturn(streamFile(StreamType.BALANCE, past)).when(accountBalanceFileRepository).findLatest();
        doReturn(streamFile(StreamType.EVENT, past)).when(eventFileRepository).findLatest();
        doReturn(streamFile(StreamType.RECORD, past)).when(recordFileRepository).findLatest();

        for (var downloaderProperties : downloaderPropertiesList) {
            StreamType streamType = downloaderProperties.getStreamType();
            assertThat(mirrorDateRangePropertiesProcessor.getLastStreamFile(streamType)).matches(s -> matches(s, past));
            assertThat(mirrorDateRangePropertiesProcessor.getDateRangeFilter(streamType))
                    .isEqualTo(new DateRangeFilter(past, Utility.MAX_INSTANT_LONG));
        }
        assertThat(mirrorProperties.getVerifyHashAfter()).isEqualTo(Instant.EPOCH);
    }

    @Test
    void startDateSetAndDatabaseEmpty() {
        mirrorProperties.setStartDate(STARTUP_TIME);
        DateRangeFilter expectedFilter = new DateRangeFilter(mirrorProperties.getStartDate(), null);
        Instant expectedDate = adjustStartDate(mirrorProperties.getStartDate());
        for (var downloaderProperties : downloaderPropertiesList) {
            StreamType streamType = downloaderProperties.getStreamType();
            assertThat(mirrorDateRangePropertiesProcessor.getLastStreamFile(streamType))
                    .isEqualTo(streamFile(streamType, expectedDate));
            assertThat(mirrorDateRangePropertiesProcessor.getDateRangeFilter(streamType)).isEqualTo(expectedFilter);
        }
        assertThat(mirrorProperties.getVerifyHashAfter()).isEqualTo(adjustStartDate(STARTUP_TIME));
    }

    @ParameterizedTest(name = "startDate {0}ns before application status, endDate")
    @ValueSource(longs = {0, 1})
    void startDateNotAfterDatabase(long nanos) {
        Instant past = STARTUP_TIME.minusSeconds(100);
        mirrorProperties.setStartDate(past.minusNanos(nanos));

        doReturn(streamFile(StreamType.BALANCE, past)).when(accountBalanceFileRepository).findLatest();
        doReturn(streamFile(StreamType.EVENT, past)).when(eventFileRepository).findLatest();
        doReturn(streamFile(StreamType.RECORD, past)).when(recordFileRepository).findLatest();

        for (var downloaderProperties : downloaderPropertiesList) {
            StreamType streamType = downloaderProperties.getStreamType();
            assertThat(mirrorDateRangePropertiesProcessor.getLastStreamFile(streamType)).matches(s -> matches(s, past));
            assertThat(mirrorDateRangePropertiesProcessor.getDateRangeFilter(streamType))
                    .isEqualTo(new DateRangeFilter(past, null));
        }
        assertThat(mirrorProperties.getVerifyHashAfter()).isEqualTo(Instant.EPOCH);
    }

    @ParameterizedTest(name = "startDate is {0}ns after application status")
    @ValueSource(longs = {1, 2_000_000_000L, 200_000_000_000L})
    void startDateAfterDatabase(long diffNanos) {
        Instant lastFileInstant = Instant.now().minusSeconds(200);

        doReturn(streamFile(StreamType.BALANCE, lastFileInstant)).when(accountBalanceFileRepository).findLatest();
        doReturn(streamFile(StreamType.EVENT, lastFileInstant)).when(eventFileRepository).findLatest();
        doReturn(streamFile(StreamType.RECORD, lastFileInstant)).when(recordFileRepository).findLatest();

        Instant startDate = lastFileInstant.plusNanos(diffNanos);
        mirrorProperties.setStartDate(startDate);

        DateRangeFilter expectedFilter = new DateRangeFilter(startDate, null);
        for (var downloaderProperties : downloaderPropertiesList) {
            StreamType streamType = downloaderProperties.getStreamType();
            Instant effectiveStartDate = max(adjustStartDate(startDate), lastFileInstant);
            Optional<StreamFile> streamFile = streamFile(streamType, effectiveStartDate);
            assertThat(mirrorDateRangePropertiesProcessor.getLastStreamFile(streamType)).isEqualTo(streamFile);
            assertThat(mirrorDateRangePropertiesProcessor.getDateRangeFilter(downloaderProperties.getStreamType()))
                    .isEqualTo(expectedFilter);
        }

        Instant expectedVerifyHashAfter;
        if (diffNanos > mirrorProperties.getStartDateAdjustment().toNanos()) {
            expectedVerifyHashAfter = adjustStartDate(startDate);
        } else {
            expectedVerifyHashAfter = mirrorProperties.getVerifyHashAfter();
        }
        assertThat(mirrorProperties.getVerifyHashAfter()).isEqualTo(expectedVerifyHashAfter);
    }

    @ParameterizedTest(name = "startDate {0} endDate {1} database {2} violates (effective) start date <= " +
            "end date constraint")
    @CsvSource(value = {
            "2020-08-18T09:00:05.124Z, 2020-08-18T09:00:05.123Z,",
            "2020-08-18T09:00:04.123Z, 2020-08-18T09:00:05.123Z, 2020-08-18T09:00:05.124Z",
            "2020-08-18T09:00:04.123Z, 2020-08-18T09:00:05.123Z, 2020-08-18T09:00:06.123Z",
            ", 2020-08-18T09:00:05.123Z, 2020-08-19T09:00:05.111Z",
            ", 2020-08-18T09:00:05.123Z,"
    })
    void startDateNotBeforeEndDate(Instant startDate, Instant endDate, Instant lastFileDate) {
        mirrorProperties.setStartDate(startDate);
        mirrorProperties.setEndDate(endDate);

        if (lastFileDate != null) {
            doReturn(streamFile(StreamType.BALANCE, lastFileDate)).when(accountBalanceFileRepository).findLatest();
            doReturn(streamFile(StreamType.EVENT, lastFileDate)).when(eventFileRepository).findLatest();
            doReturn(streamFile(StreamType.RECORD, lastFileDate)).when(recordFileRepository).findLatest();
        }

        for (var downloaderProperties : downloaderPropertiesList) {
            StreamType streamType = downloaderProperties.getStreamType();
            assertThatThrownBy(() -> mirrorDateRangePropertiesProcessor.getLastStreamFile(streamType))
                    .isInstanceOf(InvalidConfigurationException.class);
        }
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

    private Instant adjustStartDate(Instant startDate) {
        return startDate.minus(mirrorProperties.getStartDateAdjustment());
    }

    private boolean matches(Optional<StreamFile> streamFile, Instant instant) {
        return instant.equals(streamFile
                .map(StreamFile::getConsensusStart)
                .map(nanos -> Instant.ofEpochSecond(0, nanos))
                .orElse(null));
    }

    private Optional<StreamFile> streamFile(StreamType streamType, Instant instant) {
        StreamFile streamFile = (StreamFile) ReflectUtils.newInstance(streamType.getStreamFileClass());
        streamFile.setConsensusStart(Utility.convertToNanosMax(instant));
        streamFile.setName(StreamFilename.getFilename(streamType, DATA, instant));
        return Optional.of(streamFile);
    }
}
