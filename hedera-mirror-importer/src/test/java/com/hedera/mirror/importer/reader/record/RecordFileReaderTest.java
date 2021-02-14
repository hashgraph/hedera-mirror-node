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

import java.nio.file.Path;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

import com.hedera.mirror.importer.TestRecordFiles;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;
import com.hedera.mirror.importer.parser.domain.RecordItem;

@ExtendWith(MockitoExtension.class)
abstract class RecordFileReaderTest {

    private static final Collection<RecordFile> ALL_RECORD_FILES = TestRecordFiles.getAll().values();

    protected RecordFileReader recordFileReader;
    protected Path testPath;

    @BeforeEach
    void setup() throws Exception {
        testPath = new ClassPathResource("data/recordstreams").getFile().toPath();
        recordFileReader = getRecordFileReader();
    }

    @TestFactory
    Stream<DynamicTest> readValidFileWithConsumer() {
        String template = "read valid version %d file %s";

        return DynamicTest.stream(
                getFilteredFiles(false),
                (recordFile) -> String.format(template, recordFile.getVersion(), recordFile.getName()),
                (recordFile) -> {
                    Consumer<RecordItem> itemConsumer = mock(Consumer.class);

                    // given
                    Path testFile = getTestFile(recordFile);
                    StreamFileData streamFileData = StreamFileData.from(testFile.toFile());

                    // when
                    RecordFile actual = recordFileReader.read(streamFileData, itemConsumer);

                    // then
                    assertThat(actual).isEqualToIgnoringGivenFields(recordFile, "bytes", "items", "loadStart");
                    assertThat(actual.getBytes()).isNotEmpty().isEqualTo(streamFileData.getBytes());
                    assertThat(actual.getLoadStart()).isNotNull().isPositive();
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
                    // given
                    Path testFile = getTestFile(recordFile);
                    StreamFileData streamFileData = StreamFileData.from(testFile.toFile());

                    // when
                    RecordFile actual = recordFileReader.read(streamFileData);

                    // then
                    assertThat(actual).isEqualToIgnoringGivenFields(recordFile, "bytes", "items", "loadStart");
                    assertThat(actual.getBytes()).isNotEmpty().isEqualTo(streamFileData.getBytes());
                    assertThat(actual.getLoadStart()).isNotNull().isPositive();
                });
    }

    @TestFactory
    Stream<DynamicTest> readInvalidFileWithGarbageAppended() {
        String template = "read corrupted version %d file %s";

        return DynamicTest.stream(
                getFilteredFiles(false),
                (recordFile) -> String.format(template, recordFile.getVersion(), recordFile.getName()),
                (recordFile) -> {
                    // given
                    Path testFile = getTestFile(recordFile);
                    byte[] bytes = FileUtils.readFileToByteArray(testFile.toFile());
                    byte[] bytesCorrupted = ArrayUtils.addAll(bytes, new byte[] {0, 1, 2, 3});
                    StreamFileData streamFileData = new StreamFileData(recordFile.getName(), bytesCorrupted);

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
                    // given
                    Path testFile = getTestFile(recordFile);
                    byte[] bytes = FileUtils.readFileToByteArray(testFile.toFile());
                    byte[] bytesTruncated = ArrayUtils.subarray(bytes, 0, bytes.length - 48);
                    StreamFileData streamFileData = new StreamFileData(recordFile.getName(), bytesTruncated);

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

    protected Path getTestFile(RecordFile recordFile) {
        return testPath.resolve("v" + recordFile.getVersion()).resolve("record0.0.3").resolve(recordFile.getName());
    }

    protected abstract RecordFileReader getRecordFileReader();

    protected abstract boolean filterFile(int version);
}
