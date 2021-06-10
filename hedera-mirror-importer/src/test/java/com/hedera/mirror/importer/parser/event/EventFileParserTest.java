package com.hedera.mirror.importer.parser.event;

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

import static com.hedera.mirror.importer.domain.StreamFilename.FileType.DATA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.DigestAlgorithm;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.EventFile;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.parser.domain.EventItem;
import com.hedera.mirror.importer.repository.EventFileRepository;

@ExtendWith(MockitoExtension.class)
class EventFileParserTest {

    @TempDir
    Path dataDir;

    @Mock
    private EventFileRepository eventFileRepository;

    private EventFileParser eventFileParser;
    private EventParserProperties parserProperties;
    private long count = 0;

    @BeforeEach
    void before() {
        MirrorProperties mirrorProperties = new MirrorProperties();
        mirrorProperties.setDataPath(dataDir);
        parserProperties = new EventParserProperties(mirrorProperties);
        parserProperties.setEnabled(true);
        eventFileParser = new EventFileParser(eventFileRepository, parserProperties);
    }

    @Test
    void parse() throws Exception {
        // given
        EventFile eventFile = eventFile();

        // when
        eventFileParser.parse(eventFile);

        // then
        verify(eventFileRepository).save(eventFile);
        assertPostParseState(eventFile, true);
    }

    @Test
    void disabled() throws Exception {
        // given
        parserProperties.setEnabled(false);
        parserProperties.setKeepFiles(true);
        EventFile eventFile = eventFile();

        // when
        eventFileParser.parse(eventFile);

        // then
        verify(eventFileRepository, never()).save(any());
        assertPostParseState(eventFile, true);
    }

    @Test
    void keepFiles() throws Exception {
        // given
        parserProperties.setKeepFiles(true);
        EventFile eventFile = eventFile();

        // when
        eventFileParser.parse(eventFile);

        // then
        verify(eventFileRepository).save(eventFile);
        assertPostParseState(eventFile, true, eventFile.getName());
    }

    @Test
    void failureShouldRollback() throws Exception {
        // given
        EventFile eventFile = eventFile();
        doThrow(ParserException.class).when(eventFileRepository).save(any());

        // when
        Assertions.assertThrows(ParserException.class, () -> {
            eventFileParser.parse(eventFile);
        });

        // then
        assertPostParseState(eventFile, false);
    }

    // Asserts that parsed directory contains exactly the files with given fileNames
    private void assertFilesArchived(String... fileNames) throws Exception {
        if (fileNames == null || fileNames.length == 0) {
            assertThat(parserProperties.getParsedPath()).doesNotExist();
            return;
        }
        assertThat(Files.walk(parserProperties.getParsedPath()))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSize(fileNames.length)
                .extracting(Path::getFileName)
                .extracting(Path::toString)
                .contains(fileNames);
    }

    private void assertPostParseState(EventFile eventFile, boolean success,
                                      String... archivedFileNames) throws Exception {
        if (success) {
            assertThat(eventFile.getBytes()).isNull();
            assertThat(eventFile.getItems()).isNull();
        } else {
            assertThat(eventFile.getBytes()).isNotNull();
            assertThat(eventFile.getItems()).isNotNull();
        }

        assertFilesArchived(archivedFileNames);
    }

    private EventFile eventFile() {
        long id = ++count;
        Instant instant = Instant.ofEpochSecond(0L, id);
        String filename = StreamFilename.getFilename(parserProperties.getStreamType(), DATA, instant);
        EventFile eventFile = new EventFile();
        eventFile.setBytes(new byte[] {0, 1, 2});
        eventFile.setConsensusEnd(id);
        eventFile.setConsensusStart(id);
        eventFile.setConsensusEnd(id);
        eventFile.setCount(id);
        eventFile.setDigestAlgorithm(DigestAlgorithm.SHA384);
        eventFile.setFileHash("fileHash" + id);
        eventFile.setHash("hash" + id);
        eventFile.setLoadEnd(id);
        eventFile.setLoadStart(id);
        eventFile.setName(filename);
        eventFile.setNodeAccountId(EntityId.of("0.0.3", EntityTypeEnum.ACCOUNT));
        eventFile.setPreviousHash("previousHash" + (id - 1));
        eventFile.setVersion(1);
        eventFile.getItems().add(new EventItem());
        return eventFile;
    }
}
