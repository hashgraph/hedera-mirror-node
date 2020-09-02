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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.parser.domain.StreamFileData;

@ExtendWith(MockitoExtension.class)
public class RecordFilePollerTest {

    @TempDir
    Path dataPath;
    @Mock
    RecordFileParser recordFileParser;
    private FileCopier fileCopier;
    private RecordFilePoller recordFilePoller;
    private RecordParserProperties parserProperties;
    private MirrorProperties mirrorProperties;

    private File file1;
    private File file2;
    private static final long FILE1_CONSENSUS_START = 1567188600419072000L;
    private static final int NUM_TXNS_FILE_1 = 19;
    private static final int NUM_TXNS_FILE_2 = 15;
    private static RecordFile recordFile1;
    private static RecordFile recordFile2;

    @BeforeEach
    void before() {
        mirrorProperties = new MirrorProperties();
        mirrorProperties.setDataPath(dataPath);
        parserProperties = new RecordParserProperties(mirrorProperties);
        parserProperties.setKeepFiles(false);
        parserProperties.init();
        recordFilePoller = new RecordFilePoller(parserProperties, recordFileParser);
        StreamType streamType = StreamType.RECORD;
        fileCopier = FileCopier
                .create(Path.of(getClass().getClassLoader().getResource("data").getPath()), dataPath)
                .from(streamType.getPath(), "v2", "record0.0.3")
                .filterFiles("*.rcd")
                .to(streamType.getPath(), streamType.getValid());
        file1 = parserProperties.getValidPath().resolve("2019-08-30T18_10_00.419072Z.rcd").toFile();
        file2 = parserProperties.getValidPath().resolve("2019-08-30T18_10_05.249678Z.rcd").toFile();
        EntityId nodeAccountId = EntityId.of(TestUtils.toAccountId("0.0.3"));
        recordFile1 = new RecordFile(1567188600419072000L, 1567188604906443001L, null, file1.getName(), 0L, 0L,
                "591558e059bd1629ee386c4e35a6875b4c67a096718f5d225772a651042715189414df7db5588495efb2a85dc4a0ffda",
                "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000", nodeAccountId, 0L, 2);

        recordFile2 = new RecordFile(1567188605249678000L, 1567188609705382001L, null, file2.getName(), 0L, 0L,
                "5ed51baeff204eb6a2a68b76bbaadcb9b6e7074676c1746b99681d075bef009e8d57699baaa6342feec4e83726582d36",
                recordFile1.getFileHash(), nodeAccountId, 0L, 2);
    }

    @Test
    void poll() throws Exception {
        // given
        fileCopier.copy();

        // when
        recordFilePoller.poll();

        // then
        assertAllProcessed();
    }

    @Test
    void pollAndKeepFiles() throws Exception {
        // given
        parserProperties.setKeepFiles(true);
        fileCopier.copy();

        // when
        recordFilePoller.poll();

        // then
        assertAllProcessed();
    }

    @Test
    void noFiles() throws Exception {
        // when
        recordFilePoller.poll();

        // then
        verifyNoInteractions(recordFileParser);
    }

    @Test
    void pathNotDirectory() throws Exception {
        // when
        fileCopier.copy();
        parserProperties.getMirrorProperties().setDataPath(dataPath.resolve("file.txt"));
        recordFilePoller.poll();

        // then
        assertValidFiles();
        verifyNoInteractions(recordFileParser);
    }

    @Test
    void fileNotFoundFromRecordFileParser() {
        // given
        fileCopier.copy();
        RecordParserProperties mockParserProperties = Mockito.mock(RecordParserProperties.class);
        doReturn(Path.of("/var/folders/tmp")).when(mockParserProperties).getValidPath();
        recordFilePoller = new RecordFilePoller(mockParserProperties, recordFileParser);

        // when
        recordFilePoller.poll();

        // then
        assertValidFiles();
    }

    @Test
    void errorFromRecordFileParser() {
        // when
        fileCopier.copy();
        doThrow(ParserException.class).when(recordFileParser).parse(any());

        // when
        recordFilePoller.poll();

        // then
        assertValidFiles();
    }

    // Asserts that recordFileParser.parse is called wth exactly the given fileNames.
    private void assertParse(String... fileNames) {
        ArgumentCaptor<StreamFileData> captor = ArgumentCaptor.forClass(StreamFileData.class);
        verify(recordFileParser, times(fileNames.length)).parse(captor.capture());
        List<StreamFileData> actualArgs = captor.getAllValues();
        assertThat(actualArgs)
                .extracting(StreamFileData::getFilename)
                .isEqualTo(Arrays.asList(fileNames));
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
        assertParse(file1.getPath(), file2.getPath());
    }
}
