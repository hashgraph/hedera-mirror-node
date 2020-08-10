package com.hedera.mirror.importer.parser.record;

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

import static com.hedera.mirror.importer.domain.ApplicationStatusCode.LAST_PROCESSED_RECORD_HASH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.exception.DuplicateFileException;
import com.hedera.mirror.importer.exception.ParserSQLException;
import com.hedera.mirror.importer.parser.domain.StreamFileData;
import com.hedera.mirror.importer.repository.ApplicationStatusRepository;

@ExtendWith(MockitoExtension.class)
public class RecordFileParserTest {

    @TempDir
    Path dataPath;
    @Mock
    private ApplicationStatusRepository applicationStatusRepository;
    @Mock
    private RecordItemListener recordItemListener;
    @Mock
    private RecordStreamFileListener recordStreamFileListener;
    private FileCopier fileCopier;
    private RecordFileParser recordFileParser;
    private RecordParserProperties parserProperties;

    private File file1;
    private File file2;
    private static final long FILE1_CONSENSUS_START = 1567188600419072000L;
    private static final int NUM_TXNS_FILE_1 = 19;
    private static final int NUM_TXNS_FILE_2 = 15;
    private static RecordFile recordFile1;
    private static RecordFile recordFile2;
    private static StreamFileData streamFileData1;
    private static StreamFileData streamFileData2;

    @BeforeEach
    void before() throws FileNotFoundException {
        var mirrorProperties = new MirrorProperties();
        mirrorProperties.setDataPath(dataPath);
        parserProperties = new RecordParserProperties(mirrorProperties);
        parserProperties.setKeepFiles(false);
        parserProperties.init();
        recordFileParser = new RecordFileParser(applicationStatusRepository, parserProperties,
                new SimpleMeterRegistry(), recordItemListener, recordStreamFileListener);
        StreamType streamType = StreamType.RECORD;
        fileCopier = FileCopier
                .create(Path.of(getClass().getClassLoader().getResource("data").getPath()), dataPath)
                .from(streamType.getPath(), "v2", "record0.0.3")
                .filterFiles("*.rcd");

        fileCopier.copy();
        file1 = dataPath.resolve("2019-08-30T18_10_00.419072Z.rcd").toFile();
        file2 = dataPath.resolve("2019-08-30T18_10_05.249678Z.rcd").toFile();
        recordFile1 = new RecordFile(1567188600419072000L, 1567188604906443001L, null, file1.getName(), 0L, 0L,
                "591558e059bd1629ee386c4e35a6875b4c67a096718f5d225772a651042715189414df7db5588495efb2a85dc4a0ffda",
                "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000", 2);

        recordFile2 = new RecordFile(1567188605249678000L, 1567188609705382001L, null, file2.getName(), 0L, 0L,
                "5ed51baeff204eb6a2a68b76bbaadcb9b6e7074676c1746b99681d075bef009e8d57699baaa6342feec4e83726582d36",
                recordFile1.getFileHash(), 2);

        streamFileData1 = new StreamFileData(file1.toString(), new FileInputStream(file1));
        streamFileData2 = new StreamFileData(file2.toString(), new FileInputStream(file2));
    }

    @Test
    void parse() throws Exception {
        // given
        parserProperties.getMirrorProperties().setVerifyHashAfter(Instant.parse("2019-09-01T00:00:00.000000Z"));
        when(applicationStatusRepository.findByStatusCode(LAST_PROCESSED_RECORD_HASH)).thenReturn("")
                .thenReturn(recordFile1.getFileHash(), recordFile2.getFileHash());

        // when
        recordFileParser.parse(streamFileData1);
        assertProcessedFile(streamFileData1, recordFile1, NUM_TXNS_FILE_1);

        recordFileParser.parse(streamFileData2);

        // then
        verify(recordStreamFileListener, never()).onError();
        assertAllProcessed();
    }

    @Test
    void invalidFile() throws Exception {
        // given
        FileUtils.writeStringToFile(file1, "corrupt", "UTF-8");
        streamFileData1 = new StreamFileData(file1.toString(), new FileInputStream(file1));

        // when
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            recordFileParser.parse(streamFileData1);
        });

        // then
        verify(recordStreamFileListener).onStart(streamFileData1);
        verify(recordStreamFileListener, never()).onEnd(recordFile1);
        verify(recordStreamFileListener).onError();
    }

    @Test
    void hashMismatch() {
        // given
        when(applicationStatusRepository.findByStatusCode(LAST_PROCESSED_RECORD_HASH)).thenReturn("123");

        // when
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            recordFileParser.parse(streamFileData1);
        });

        // then
        verify(recordStreamFileListener).onStart(streamFileData1);
        verify(recordStreamFileListener, never()).onEnd(any());
        verify(recordStreamFileListener).onError();
    }

    @Test
    void bypassHashMismatch() throws Exception {
        // given
        parserProperties.getMirrorProperties().setVerifyHashAfter(Instant.parse("2019-09-01T00:00:00.000000Z"));
        when(applicationStatusRepository.findByStatusCode(LAST_PROCESSED_RECORD_HASH)).thenReturn("123");

        // when
        recordFileParser.parse(streamFileData1);

        // then
        verify(recordStreamFileListener, never()).onError();
        assertProcessedFile(streamFileData1, recordFile1, NUM_TXNS_FILE_1);
    }

    @Test
    void failureProcessingItemShouldRollback() {
        // given
        doThrow(ParserSQLException.class).when(recordItemListener).onItem(any());

        // when
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            recordFileParser.parse(streamFileData1);
        });

        // then
        verify(recordStreamFileListener).onStart(streamFileData1);
        verify(recordStreamFileListener, never()).onEnd(recordFile1);
        verify(recordStreamFileListener).onError();
    }

    @Test
    void skipFileOnDuplicateFileException() {
        // given
        recordFileParser.parse(streamFileData1);
        doThrow(DuplicateFileException.class).when(recordStreamFileListener).onStart(any());

        // when: load same file again
        // then: throws exception
        Assertions.assertThrows(DuplicateFileException.class, () -> {
            recordFileParser.parse(streamFileData1);
        });
        verify(recordItemListener, times(NUM_TXNS_FILE_1)).onItem(any());
        verify(recordStreamFileListener, times(2)).onStart(any());
        verify(recordStreamFileListener, times(1)).onEnd(any());
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
            assertThat(actual).isEqualToIgnoringGivenFields(expected, "id", "loadEnd", "loadStart", "recordItems");
        }
    }

    private void assertProcessedFile(StreamFileData streamFileData, RecordFile recordFile, int numTransactions) throws Exception {
        // assert mock interactions
        verify(recordItemListener, times(numTransactions)).onItem(any());
        assertOnStart(streamFileData.getFilename());
        assertOnEnd(recordFile);
    }

    private void assertAllProcessed() throws Exception {
        // assert mock interactions
        verify(recordItemListener, times(NUM_TXNS_FILE_1 + NUM_TXNS_FILE_2)).onItem(any());
        assertOnStart(streamFileData1.getFilename(), streamFileData2.getFilename());
        assertOnEnd(recordFile1, recordFile2);
    }
}
