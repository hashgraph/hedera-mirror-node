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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
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
import com.hedera.mirror.importer.exception.ParserSQLException;
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

        recordFileParser = new RecordFileParser(parserProperties, new SimpleMeterRegistry(),
                recordItemListener, recordStreamFileListener, mirrorDateRangePropertiesProcessor);

        FileCopier fileCopier = FileCopier
                .create(Path.of(getClass().getClassLoader().getResource("data").getPath()), dataPath)
                .from(StreamType.RECORD.getPath(), versionedPath, "record0.0.3")
                .filterFiles("*.rcd");
        fileCopier.copy();

        streamFileData1 = StreamFileData.from(dataPath.resolve(recordFile1.getName()).toFile());
        streamFileData2 = StreamFileData.from(dataPath.resolve(recordFile2.getName()).toFile());

        verify(recordStreamFileListener, times(2)).onStart();
    }

    @Test
    void parse() {
        // given

        // when
        recordFileParser.parse(recordFile1);
        assertProcessedFile(streamFileData1, recordFile1);

        recordFileParser.parse(recordFile2);

        // then
        verify(recordStreamFileListener, never()).onError();
        assertAllProcessed();
    }

    @Test
    void hashMismatch() {
        // given
        when(applicationStatusRepository.findByStatusCode(LAST_PROCESSED_RECORD_HASH)).thenReturn("123");

        // when
        Assertions.assertThrows(HashMismatchException.class, () -> {
            recordFileParser.parse(recordFile1);
        });

        // then
        verify(recordStreamFileListener).onStart();
        verify(recordStreamFileListener, never()).onEnd(any());
        verify(recordStreamFileListener).onError();
    }

    @Test
    void failureProcessingItemShouldRollback() {
        // given
        doThrow(ParserSQLException.class).when(recordItemListener).onItem(any());

        // when
        Assertions.assertThrows(ImporterException.class, () -> {
            recordFileParser.parse(recordFile1);
        });

        // then
        verify(recordStreamFileListener).onStart();
        verify(recordStreamFileListener, never()).onEnd(recordFile1);
        verify(recordStreamFileListener).onError();
    }

    @Test
    void keepFiles() throws Exception {
        // given
        parserProperties.setKeepFiles(true);

        // when
        recordFileParser.parse(recordFile1);

        // then
        assertAllProcessed();
        assertParsedFiles(streamFileData1.getFilename());
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
        recordFileParser.parse(recordFile1);
        recordFileParser.parse(recordFile2);

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
        recordFileParser.parse(recordFile1);
        recordFileParser.parse(recordFile2);

        // then
        assertAllProcessed(filter);
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
        verify(recordStreamFileListener, times(1)).onStart();
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
        verify(recordStreamFileListener, times(2)).onStart();
        assertOnEnd(recordFile1, recordFile2);
    }

    // Asserts that parsed directory contains exactly the files with given fileNames
    private void assertParsedFiles(String... fileNames) throws Exception {
        assertThat(Files.walk(parserProperties.getParsedPath()))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSize(fileNames.length)
                .extracting(Path::getFileName)
                .extracting(Path::toString)
                .contains(fileNames);
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
