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

import static com.hedera.mirror.common.util.DomainUtils.convertToNanosMax;
import static com.hedera.mirror.importer.TestUtils.plus;
import static com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor.DateRangeFilter;
import static com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor.STARTUP_TIME;
import static com.hedera.mirror.importer.domain.StreamFilename.FileType.DATA;
import static org.apache.commons.lang3.ObjectUtils.max;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

import com.hedera.mirror.common.domain.StreamFile;
import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;
import com.hedera.mirror.importer.downloader.DownloaderProperties;
import com.hedera.mirror.importer.downloader.balance.BalanceDownloaderProperties;
import com.hedera.mirror.importer.downloader.event.EventDownloaderProperties;
import com.hedera.mirror.importer.downloader.record.RecordDownloaderProperties;
import com.hedera.mirror.importer.exception.InvalidConfigurationException;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import com.hedera.mirror.importer.repository.EventFileRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.StreamFileRepository;
import com.hedera.mirror.importer.util.Utility;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private final Map<StreamType, StreamFileRepository<?, ?>> streamFileRepositories = new HashMap<>();

    @BeforeEach
    void setUp() {
        mirrorProperties = new MirrorProperties();
        mirrorProperties.setNetwork(MirrorProperties.HederaNetwork.TESTNET);
        var commonDownloaderProperties = new CommonDownloaderProperties(mirrorProperties);
        var balanceDownloaderProperties = new BalanceDownloaderProperties(mirrorProperties, commonDownloaderProperties);
        var eventDownloaderProperties = new EventDownloaderProperties(mirrorProperties, commonDownloaderProperties);
        var recordDownloaderProperties = new RecordDownloaderProperties(mirrorProperties, commonDownloaderProperties);
        downloaderPropertiesList =
                List.of(balanceDownloaderProperties, eventDownloaderProperties, recordDownloaderProperties);
        mirrorDateRangePropertiesProcessor = new MirrorDateRangePropertiesProcessor(
                mirrorProperties,
                downloaderPropertiesList,
                accountBalanceFileRepository,
                eventFileRepository,
                recordFileRepository);

        balanceDownloaderProperties.setEnabled(true);
        eventDownloaderProperties.setEnabled(true);
        recordDownloaderProperties.setEnabled(true);

        streamFileRepositories.putIfAbsent(StreamType.BALANCE, accountBalanceFileRepository);
        streamFileRepositories.putIfAbsent(StreamType.EVENT, eventFileRepository);
        streamFileRepositories.putIfAbsent(StreamType.RECORD, recordFileRepository);
    }

    @Test
    void notSetAndDatabaseEmpty() {
        var expectedDate = STARTUP_TIME;
        var expectedFilter = new DateRangeFilter(expectedDate, null);
        for (var downloaderProperties : downloaderPropertiesList) {
            var streamType = downloaderProperties.getStreamType();
            assertThat(mirrorDateRangePropertiesProcessor.getLastStreamFile(streamType))
                    .isEqualTo(streamFile(streamType, expectedDate, true));
            assertThat(mirrorDateRangePropertiesProcessor.getDateRangeFilter(streamType))
                    .isEqualTo(expectedFilter);
        }
        assertThat(mirrorProperties.getVerifyHashAfter()).isEqualTo(expectedDate);
    }

    @Test
    void notSetAndDatabaseNotEmpty() {
        var past = STARTUP_TIME.minusSeconds(100);
        streamFileRepositories.forEach((streamType, repository) ->
                doReturn(streamFile(streamType, past, false)).when(repository).findLatest());
        verifyWhenLastStreamFileFromDatabase(past);
    }

    @Test
    void startDateNotSetAndEndDateAfterLongMaxAndDatabaseNotEmpty() {
        var past = STARTUP_TIME.minusSeconds(100);
        mirrorProperties.setEndDate(Utility.MAX_INSTANT_LONG.plusNanos(1));
        streamFileRepositories.forEach((streamType, repository) ->
                doReturn(streamFile(streamType, past, false)).when(repository).findLatest());
        verifyWhenLastStreamFileFromDatabase(past);
    }

    @Test
    void startDateSetAndDatabaseEmpty() {
        var startDate = STARTUP_TIME.plusSeconds(10L);
        mirrorProperties.setStartDate(startDate);
        var expectedFilter = new DateRangeFilter(mirrorProperties.getStartDate(), null);
        var expectedDate = mirrorProperties.getStartDate();
        for (var downloaderProperties : downloaderPropertiesList) {
            StreamType streamType = downloaderProperties.getStreamType();
            assertThat(mirrorDateRangePropertiesProcessor.getLastStreamFile(streamType))
                    .isEqualTo(streamFile(streamType, expectedDate, true));
            assertThat(mirrorDateRangePropertiesProcessor.getDateRangeFilter(streamType))
                    .isEqualTo(expectedFilter);
        }
        assertThat(mirrorProperties.getVerifyHashAfter()).isEqualTo(startDate);
    }

    @ParameterizedTest(name = "startDate {0}ns before application status, endDate")
    @ValueSource(longs = {0, 1})
    void startDateNotAfterDatabase(long nanos) {
        var past = STARTUP_TIME.minusSeconds(100);
        mirrorProperties.setStartDate(past.minusNanos(nanos));
        streamFileRepositories.forEach((streamType, repository) ->
                doReturn(streamFile(streamType, past, false)).when(repository).findLatest());
        verifyWhenLastStreamFileFromDatabase(past);
    }

    @ParameterizedTest(name = "startDate is {0}ns after application status")
    @ValueSource(longs = {1, 2_000_000_000L, 200_000_000_000L})
    void startDateAfterDatabase(long diffNanos) {
        var lastFileInstant = Instant.now().minusSeconds(200);
        streamFileRepositories.forEach(
                (streamType, repository) -> doReturn(streamFile(streamType, lastFileInstant, false))
                        .when(repository)
                        .findLatest());

        var startDate = lastFileInstant.plusNanos(diffNanos);
        mirrorProperties.setStartDate(startDate);
        var effectiveStartDate = max(startDate, lastFileInstant);

        var expectedFilter = new DateRangeFilter(startDate, null);
        for (var downloaderProperties : downloaderPropertiesList) {
            var streamType = downloaderProperties.getStreamType();

            assertThat(mirrorDateRangePropertiesProcessor.getLastStreamFile(streamType))
                    .isEqualTo(streamFile(streamType, effectiveStartDate, true));
            assertThat(mirrorDateRangePropertiesProcessor.getDateRangeFilter(downloaderProperties.getStreamType()))
                    .isEqualTo(expectedFilter);
        }
    }

    @ParameterizedTest(
            name = "startDate {0} endDate {1} database {2} violates (effective) start date <= " + "end date constraint")
    @CsvSource(
            value = {
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
            streamFileRepositories.forEach(
                    (streamType, repository) -> doReturn(streamFile(streamType, lastFileDate, false))
                            .when(repository)
                            .findLatest());
        }

        for (var downloaderProperties : downloaderPropertiesList) {
            var streamType = downloaderProperties.getStreamType();
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
    @CsvSource(
            value = {
                "1, 1, 1, true",
                "1, 10, 1, true",
                "1, 10, 10, true",
                "1, 10, 6, true",
                "1, 10, 0, false",
                "1, 10, 11, false",
                "1, 10, -1, false",
            })
    void filter(long start, long end, long timestamp, boolean expected) {
        var filter = new DateRangeFilter(Instant.ofEpochSecond(0, start), Instant.ofEpochSecond(0, end));
        assertThat(filter.filter(timestamp)).isEqualTo(expected);
    }

    private Optional<StreamFile<?>> streamFile(StreamType streamType, Instant instant, boolean nullConsensusEnd) {
        var streamFile = streamType.newStreamFile();
        long consensusStart = convertToNanosMax(instant);
        streamFile.setConsensusStart(consensusStart);
        if (!nullConsensusEnd) {
            streamFile.setConsensusEnd(plus(consensusStart, streamType.getFileCloseInterval()));
        }
        streamFile.setName(StreamFilename.getFilename(streamType, DATA, instant));
        return Optional.of(streamFile);
    }

    private void verifyWhenLastStreamFileFromDatabase(Instant fileInstant) {
        long expectedConsensusStart = convertToNanosMax(fileInstant);
        var expectedDateRangeFilter = new DateRangeFilter(fileInstant, null);
        for (var downloaderProperties : downloaderPropertiesList) {
            var streamType = downloaderProperties.getStreamType();
            long expectedConsensusEnd = streamType != StreamType.BALANCE
                    ? plus(expectedConsensusStart, streamType.getFileCloseInterval())
                    : expectedConsensusStart;
            assertThat(mirrorDateRangePropertiesProcessor.getLastStreamFile(streamType))
                    .get()
                    .returns(expectedConsensusStart, StreamFile::getConsensusStart)
                    .returns(expectedConsensusEnd, StreamFile::getConsensusEnd);
            assertThat(mirrorDateRangePropertiesProcessor.getDateRangeFilter(streamType))
                    .isEqualTo(expectedDateRangeFilter);
        }
        assertThat(mirrorProperties.getVerifyHashAfter()).isEqualTo(Instant.EPOCH);
    }
}
