package com.hedera.mirror.importer.reader.record;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.TestRecordFiles;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;
import com.hedera.mirror.importer.parser.domain.RecordItem;

@ExtendWith(MockitoExtension.class)
abstract class RecordFileReaderTest {

    private static final Collection<RecordFile> ALL_RECORD_FILES = TestRecordFiles.getAll().values();

    protected FileCopier fileCopier;
    protected RecordFileReader recordFileReader;

    @TempDir
    Path dataPath;

    private static void corruptFile(Path p) {
        try {
            File file = p.toFile();
            if (file.isFile()) {
                FileUtils.writeStringToFile(file, "corrupt", "UTF-8", true);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void truncateFile(Path p) {
        try {
            File file = p.toFile();
            if (file.isFile()) {
                FileChannel outChan = new FileOutputStream(file, true).getChannel();
                if (outChan.size() <= 48) {
                    outChan.truncate(outChan.size() / 2);
                } else {
                    outChan.truncate(48);
                }
                outChan.close();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setup() {
        fileCopier = FileCopier
                .create(Path.of(getClass().getClassLoader().getResource("data").getPath()), dataPath)
                .from("recordstreams");
        recordFileReader = getRecordFileReader();
    }

    @TestFactory
    Stream<DynamicTest> readValidFileWithConsumer() {
        String template = "read valid version %d file %s";

        return DynamicTest.stream(
                getFilteredFiles(false),
                (recordFile) -> String.format(template, recordFile.getVersion(), recordFile.getName()),
                (recordFile) -> {
                    String filename = recordFile.getName();
                    Consumer<RecordItem> itemConsumer = mock(Consumer.class);

                    // given
                    fileCopier.from(getSubPath(recordFile.getVersion())).filterFiles(filename).copy();
                    File file = fileCopier.getTo().resolve(filename).toFile();
                    StreamFileData streamFileData = StreamFileData.from(file);

                    // when
                    RecordFile actual = recordFileReader.read(streamFileData, itemConsumer);

                    // then
                    assertThat(actual).isEqualTo(recordFile);
                    ArgumentCaptor<RecordItem> captor = ArgumentCaptor.forClass(RecordItem.class);
                    verify(itemConsumer, times(recordFile.getCount().intValue())).accept(captor.capture());
                    List<Long> timestamps = captor.getAllValues().stream()
                            .map(RecordItem::getConsensusTimestamp)
                            .collect(Collectors.toList());
                    assertThat(timestamps).first().isEqualTo(recordFile.getConsensusStart());
                    assertThat(timestamps).last().isEqualTo(recordFile.getConsensusEnd());
                    assertThat(timestamps).doesNotHaveDuplicates().isSorted();
                });
    }

    @TestFactory
    Stream<DynamicTest> readValidFileWithoutConsumer() {
        String template = "read valid version %d file %s";

        return DynamicTest.stream(
                getFilteredFiles(false),
                (recordFile) -> String.format(template, recordFile.getVersion(), recordFile.getName()),
                (recordFile) -> {
                    String filename = recordFile.getName();

                    // given
                    fileCopier.from(getSubPath(recordFile.getVersion())).filterFiles(filename).copy();
                    File file = fileCopier.getTo().resolve(filename).toFile();
                    StreamFileData streamFileData = StreamFileData.from(file);

                    // when
                    RecordFile actual = recordFileReader.read(streamFileData);

                    // then
                    assertThat(actual).isEqualTo(recordFile);
                });
    }

    @TestFactory
    Stream<DynamicTest> readInvalidFileWithGarbageAppended() {
        String template = "read corrupted version %d file %s";

        return DynamicTest.stream(
                getFilteredFiles(false),
                (recordFile) -> String.format(template, recordFile.getVersion(), recordFile.getName()),
                (recordFile) -> {
                    String filename = recordFile.getName();

                    // given
                    fileCopier.from(getSubPath(recordFile.getVersion())).filterFiles(filename).copy();
                    File file = fileCopier.getTo().resolve(filename).toFile();
                    Files.walk(dataPath).forEach(RecordFileReaderTest::corruptFile);
                    StreamFileData streamFileData = StreamFileData.from(file);

                    // when
                    assertThrows(InvalidStreamFileException.class, () -> recordFileReader.read(streamFileData));
                });
    }

    @TestFactory
    Stream<DynamicTest> readInvalidFileWithDataTruncated() {
        String template = "read incomplete version %d file %s";

        return DynamicTest.stream(
                getFilteredFiles(false),
                (recordFile) -> String.format(template, recordFile.getVersion(), recordFile.getName()),
                (recordFile) -> {
                    String filename = recordFile.getName();

                    // given
                    fileCopier.from(getSubPath(recordFile.getVersion())).filterFiles(filename).copy();
                    File file = fileCopier.getTo().resolve(filename).toFile();
                    Files.walk(dataPath).forEach(RecordFileReaderTest::truncateFile);
                    StreamFileData streamFileData = StreamFileData.from(file);

                    // when
                    assertThrows(InvalidStreamFileException.class, () -> recordFileReader.read(streamFileData));
                });
    }

    protected Iterator<RecordFile> getFilteredFiles(boolean negate) {
        return ALL_RECORD_FILES.stream()
                .filter((recordFile) -> negate ^ filterFile(recordFile.getVersion()))
                .collect(Collectors.toList())
                .iterator();
    }

    protected Path getSubPath(int version) {
        return Path.of("v" + version, "record0.0.3");
    }

    protected abstract RecordFileReader getRecordFileReader();

    protected abstract boolean filterFile(int version);
}
