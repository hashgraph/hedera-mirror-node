package com.hedera.mirror.importer.parser.record;

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
import static com.hedera.mirror.importer.domain.ApplicationStatusCode.LAST_PROCESSED_RECORD_HASH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.TestRecordFiles;
import com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.exception.HashMismatchException;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;
import com.hedera.mirror.importer.exception.ParserSQLException;
import com.hedera.mirror.importer.reader.record.CompositeRecordFileReader;
import com.hedera.mirror.importer.reader.record.RecordFileReader;
import com.hedera.mirror.importer.reader.record.RecordFileReaderImplV1;
import com.hedera.mirror.importer.reader.record.RecordFileReaderImplV2;
import com.hedera.mirror.importer.reader.record.RecordFileReaderImplV5;
import com.hedera.mirror.importer.repository.ApplicationStatusRepository;

@ExtendWith(MockitoExtension.class)
abstract class AbstractRecordFileParserTest {
    @TempDir
    Path dataPath;
    @Mock
    private ApplicationStatusRepository applicationStatusRepository;
    @Mock
    private RecordItemListener recordItemListener;
    @Mock(lenient = true)
    private RecordStreamFileListener recordStreamFileListener;
    @Mock
    private MirrorDateRangePropertiesProcessor mirrorDateRangePropertiesProcessor;

    private RecordFileParser recordFileParser;
    private RecordParserProperties parserProperties;

    private StreamFileData streamFileData1;
    private StreamFileData streamFileData2;

    private final RecordFile recordFile1;
    private final RecordFile recordFile2;
    private final long[] fileConsensusTimestamps;
    private final String versionedPath;

    AbstractRecordFileParserTest(String filename1, String filename2, long[] fileConsensusTimestamps) {
        Map<String, RecordFile> allRecordFileMap = TestRecordFiles.getAll();
        recordFile1 = allRecordFileMap.get(filename1);
        recordFile2 = allRecordFileMap.get(filename2);
        this.fileConsensusTimestamps = fileConsensusTimestamps;
        versionedPath = "v" + recordFile1.getVersion();
    }

    AbstractRecordFileParserTest(RecordFile recordFile1, RecordFile recordFile2, long[] fileConsensusTimestamps,
            String versionedPath) {
        this.recordFile1 = recordFile1;
        this.recordFile2 = recordFile2;
        this.fileConsensusTimestamps = fileConsensusTimestamps;
        this.versionedPath = versionedPath;
    }

    @BeforeEach
    void before() {
        MirrorProperties mirrorProperties = new MirrorProperties();
        mirrorProperties.setDataPath(dataPath);
        parserProperties = new RecordParserProperties(mirrorProperties);
        parserProperties.setKeepFiles(false);
        parserProperties.init();

        RecordFileReader recordFileReader = new CompositeRecordFileReader(new RecordFileReaderImplV1(),
                new RecordFileReaderImplV2(), new RecordFileReaderImplV5());
        recordFileParser = new RecordFileParser(applicationStatusRepository, parserProperties, new SimpleMeterRegistry(),
                recordFileReader, recordItemListener, recordStreamFileListener, mirrorDateRangePropertiesProcessor);

        FileCopier fileCopier = FileCopier
                .create(Path.of(getClass().getClassLoader().getResource("data").getPath()), dataPath)
                .from(StreamType.RECORD.getPath(), versionedPath, "record0.0.3")
                .filterFiles("*.rcd");
        fileCopier.copy();

        streamFileData1 = StreamFileData.from(dataPath.resolve(recordFile1.getName()).toFile());
        streamFileData2 = StreamFileData.from(dataPath.resolve(recordFile2.getName()).toFile());

        doReturn(recordFile1).when(recordStreamFileListener).onStart(streamFileData1);
        doReturn(recordFile2).when(recordStreamFileListener).onStart(streamFileData2);
    }

    @Test
    void parse() {
        // given

        // when
        recordFileParser.parse(streamFileData1);
        assertProcessedFile(streamFileData1, recordFile1);

        recordFileParser.parse(streamFileData2);

        // then
        verify(recordStreamFileListener, never()).onError();
        assertAllProcessed();
    }

    @Test
    void invalidFile() throws Exception {
        // given
        File file = dataPath.resolve(recordFile1.getName()).toFile();
        FileUtils.writeStringToFile(file, "corrupt", "UTF-8");
        StreamFileData streamFileData = StreamFileData.from(file);
        doReturn(recordFile1).when(recordStreamFileListener).onStart(streamFileData);

        // when
        Assertions.assertThrows(InvalidStreamFileException.class, () -> recordFileParser.parse(streamFileData));

        // then
        verify(recordStreamFileListener).onStart(streamFileData);
        verify(recordStreamFileListener, never()).onEnd(recordFile1);
        verify(recordStreamFileListener).onError();
    }

    @Test
    void hashMismatch() {
        // given
        when(applicationStatusRepository.findByStatusCode(LAST_PROCESSED_RECORD_HASH)).thenReturn("123");

        // when
        Assertions.assertThrows(HashMismatchException.class, () -> {
            recordFileParser.parse(streamFileData1);
        });

        // then
        verify(recordStreamFileListener).onStart(streamFileData1);
        verify(recordStreamFileListener, never()).onEnd(any());
        verify(recordStreamFileListener).onError();
    }

    @Test
    void bypassHashMismatch() {
        // given
        Instant oneNanoAfter = Instant.ofEpochSecond(0, recordFile1.getConsensusStart() + 1L);
        parserProperties.getMirrorProperties().setVerifyHashAfter(oneNanoAfter);
        when(applicationStatusRepository.findByStatusCode(LAST_PROCESSED_RECORD_HASH)).thenReturn("123");

        // when
        recordFileParser.parse(streamFileData1);

        // then
        verify(recordStreamFileListener, never()).onError();
        assertProcessedFile(streamFileData1, recordFile1);
    }

    @Test
    void failureProcessingItemShouldRollback() {
        // given
        doThrow(ParserSQLException.class).when(recordItemListener).onItem(any());

        // when
        Assertions.assertThrows(ImporterException.class, () -> {
            recordFileParser.parse(streamFileData1);
        });

        // then
        verify(recordStreamFileListener).onStart(streamFileData1);
        verify(recordStreamFileListener, never()).onEnd(recordFile1);
        verify(recordStreamFileListener).onError();
    }

    @ParameterizedTest(name = "parse with endDate set to {0}ns after the {1}th transaction")
    @MethodSource("provideTimeOffsetArgument")
    void parseWithEndDate(long nanos, int index) {
        // given
        long end = fileConsensusTimestamps[index] + nanos;
        DateRangeFilter filter = new DateRangeFilter(Instant.EPOCH, Instant.ofEpochSecond(0, end));
        doReturn(filter)
                .when(mirrorDateRangePropertiesProcessor).getDateRangeFilter(parserProperties.getStreamType());

        // when
        recordFileParser.parse(streamFileData1);
        recordFileParser.parse(streamFileData2);

        // then
        assertAllProcessed(filter);
    }

    @ParameterizedTest(name = "parse with startDate set to {0}ns after the {1}th transaction")
    @MethodSource("provideTimeOffsetArgument")
    void parseWithStartDate(long nanos, int index) {
        // given
        long start = fileConsensusTimestamps[index] + nanos;
        DateRangeFilter filter = new DateRangeFilter(Instant.ofEpochSecond(0, start), null);
        doReturn(filter)
                .when(mirrorDateRangePropertiesProcessor).getDateRangeFilter(parserProperties.getStreamType());

        // when
        recordFileParser.parse(streamFileData1);
        recordFileParser.parse(streamFileData2);

        // then
        assertAllProcessed(filter);
    }

    // Asserts that recordStreamFileListener.onStart is called wth exactly the given fileNames.
    private void assertOnStart(String... fileNames) {
        ArgumentCaptor<StreamFileData> captor = ArgumentCaptor.forClass(StreamFileData.class);
        verify(recordStreamFileListener, times(fileNames.length)).onStart(captor.capture());
        List<StreamFileData> actualArgs = captor.getAllValues();
        assertThat(actualArgs)
                .extracting(StreamFileData::getFilename)
                .contains(fileNames);
    }

    // Asserts that recordStreamFileListener.onEnd is called exactly with given recordFiles (ignoring load start and end
    //times), in given order
    private void assertOnEnd(RecordFile... recordFiles) {
        ArgumentCaptor<RecordFile> captor = ArgumentCaptor.forClass(RecordFile.class);
        verify(recordStreamFileListener, times(recordFiles.length)).onEnd(captor.capture());
        List<RecordFile> actualArgs = captor.getAllValues();
        for (int i = 0; i < recordFiles.length; i++) {
            RecordFile actual = actualArgs.get(i);
            RecordFile expected = recordFiles[i];
            assertThat(actual).isEqualToIgnoringGivenFields(expected, "id", "loadEnd", "loadStart");
        }
    }

    private void assertProcessedFile(StreamFileData streamFileData, RecordFile recordFile) {
        // assert mock interactions
        verify(recordItemListener, times(recordFile.getCount().intValue())).onItem(any());
        assertOnStart(streamFileData.getFilename());
        assertOnEnd(recordFile);
    }

    private void assertAllProcessed() {
        assertAllProcessed(null);
    }

    protected void assertAllProcessed(DateRangeFilter dateRangeFilter) {
        // assert mock interactions
        int expectedNumTxns = (int) Arrays.stream(fileConsensusTimestamps)
                .filter(ts -> dateRangeFilter == null || dateRangeFilter.filter(ts)).count();

        verify(recordItemListener, times(expectedNumTxns)).onItem(any());
        assertOnStart(streamFileData1.getFilename(), streamFileData2.getFilename());
        assertOnEnd(recordFile1, recordFile2);
    }

    protected static Stream<Arguments> provideTimeOffsetArgumentFromRecordFile(String filename) {
        RecordFile recordFile = TestRecordFiles.getAll().get(filename);
        return provideTimeOffsetArgumentFromRecordFile(recordFile);
    }

    protected static Stream<Arguments> provideTimeOffsetArgumentFromRecordFile(RecordFile recordFile) {
        int numTransactions = recordFile.getCount().intValue();

        return Stream.of(
                Arguments.of(-1, 0),
                Arguments.of(0, 0),
                Arguments.of(1, 0),
                Arguments.of(-1, numTransactions),
                Arguments.of(0, numTransactions),
                Arguments.of(1, numTransactions)
        );
    }
}
