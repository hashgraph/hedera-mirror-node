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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
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
    private static final int NUM_TXNS_FILE_1 = 19;
    private static final int NUM_TXNS_FILE_2 = 15;
    private static RecordFile recordFile1;
    private static RecordFile recordFile2;

    @BeforeEach
    void before() {
        var mirrorProperties = new MirrorProperties();
        mirrorProperties.setDataPath(dataPath);
        parserProperties = new RecordParserProperties(mirrorProperties);
        parserProperties.setKeepFiles(false);
        parserProperties.init();
        recordFileParser = new RecordFileParser(applicationStatusRepository, parserProperties,
                new SimpleMeterRegistry(), recordItemListener, recordStreamFileListener);
        StreamType streamType = StreamType.RECORD;
        fileCopier = FileCopier
                .create(Path.of(this.getClass().getClassLoader().getResource("data").getPath()), dataPath)
                .from(streamType.getPath(), "v2", "record0.0.3")
                .filterFiles("*.rcd")
                .to(streamType.getPath(), streamType.getValid());
        file1 = parserProperties.getValidPath().resolve("2019-08-30T18_10_00.419072Z.rcd").toFile();
        file2 = parserProperties.getValidPath().resolve("2019-08-30T18_10_05.249678Z.rcd").toFile();
        recordFile1 = new RecordFile(1567188600419072000L, 1567188604906443001L, null, file1.getPath(), 0L, 0L,
                "591558e059bd1629ee386c4e35a6875b4c67a096718f5d225772a651042715189414df7db5588495efb2a85dc4a0ffda",
                "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000", 2);

        recordFile2 = new RecordFile(1567188605249678000L, 1567188609705382001L, null, file2.getPath(), 0L, 0L,
                "5ed51baeff204eb6a2a68b76bbaadcb9b6e7074676c1746b99681d075bef009e8d57699baaa6342feec4e83726582d36",
                recordFile1.getFileHash(), 2);
    }

    @Test
    void parse() throws Exception {
        // given
        fileCopier.copy();

        // when
        when(applicationStatusRepository.findByStatusCode(LAST_PROCESSED_RECORD_HASH)).thenReturn("")
                .thenReturn(recordFile1.getFileHash());
        recordFileParser.parse();

        // then
        assertAllProcessed();
    }

    @Test
    void parseAndKeepFiles() throws Exception {
        // given
        parserProperties.setKeepFiles(true);
        fileCopier.copy();

        // when
        when(applicationStatusRepository.findByStatusCode(LAST_PROCESSED_RECORD_HASH)).thenReturn("")
                .thenReturn(recordFile1.getFileHash());
        recordFileParser.parse();

        // then
        assertAllProcessed();
    }

    @Test
    void noFiles() throws Exception {
        // when
        recordFileParser.parse();

        // then
        assertParsedFiles();
        verifyNoInteractions(recordItemListener);
        verifyNoInteractions(recordStreamFileListener);
    }

    @Test
    void invalidFile() throws Exception {
        // given
        fileCopier.copy();
        FileUtils.writeStringToFile(file1, "corrupt", "UTF-8");

        // when
        recordFileParser.parse();

        // then
        assertValidFiles();
        verifyNoInteractions(recordItemListener);
        verify(recordStreamFileListener).onError();
    }

    @Test
    void hashMismatch() throws Exception {
        // given
        when(applicationStatusRepository.findByStatusCode(LAST_PROCESSED_RECORD_HASH)).thenReturn("123");
        fileCopier.copy();

        // when
        recordFileParser.parse();

        // then
        assertValidFiles();
        verifyNoInteractions(recordItemListener);
        verify(recordStreamFileListener).onError();
    }

    @Test
    void bypassHashMismatch() throws Exception {
        // given
        parserProperties.getMirrorProperties().setVerifyHashAfter(Instant.parse("2019-09-01T00:00:00.000000Z"));
        when(applicationStatusRepository.findByStatusCode(LAST_PROCESSED_RECORD_HASH)).thenReturn("123");
        fileCopier.copy();

        // when
        recordFileParser.parse();

        // then
        assertAllProcessed();
    }

    @Test
    void failureProcessingItemShouldRollback() throws Exception {
        // given
        fileCopier.copy();
        doThrow(ParserSQLException.class).when(recordItemListener).onItem(any());

        // when
        recordFileParser.parse();

        // then
        assertValidFiles();
        verify(recordStreamFileListener).onError();
    }

    @Test
    void skipFileOnDuplicateFileException() throws Exception {
        // given
        fileCopier.copy();
        String fileName = file1.toString();
        recordFileParser.loadRecordFile(new StreamFileData(fileName, new FileInputStream(file1)));
        doThrow(DuplicateFileException.class).when(recordStreamFileListener).onStart(any());

        // when: load same file again
        // then: throws exception
        Assertions.assertThrows(DuplicateFileException.class, () -> {
            recordFileParser.loadRecordFile(new StreamFileData(fileName, new FileInputStream(file1)));
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

    // Asserts that parsed directory contains exactly the files with given fileNames
    private void assertParsedFiles(String... fileNames) throws Exception {
        assertThat(Files.walk(parserProperties.getParsedPath()))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSize(fileNames.length)
                .extracting(Path::getFileName)
                .extracting(Path::toString)
                .contains(fileNames);
    }

    // Asserts that valid files are untouched i.e. neither deleted, nor moved
    private void assertValidFiles() {
        assertTrue(Files.exists(file1.toPath()));
        assertTrue(Files.exists(file2.toPath()));
    }

    private void assertAllProcessed() throws Exception {
        // assert no valid files when processing completes successfully
        assertFalse(Files.exists(file1.toPath()));
        assertFalse(Files.exists(file2.toPath()));

        // assert parsed files are moved/deleted.
        if (parserProperties.isKeepFiles()) {
            assertParsedFiles(file1.getName(), file2.getName());
        } else {
            assertParsedFiles(); // assert no files in parsed directory
        }

        // assert mock interactions
        verify(recordItemListener, times(NUM_TXNS_FILE_1 + NUM_TXNS_FILE_2)).onItem(any());
        assertOnStart(file1.getPath(), file2.getPath());
        assertOnEnd(recordFile1, recordFile2);
    }
}
