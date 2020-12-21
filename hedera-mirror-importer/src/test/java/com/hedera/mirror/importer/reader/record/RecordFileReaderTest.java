package com.hedera.mirror.importer.reader.record;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.exception.InvalidRecordFileException;
import com.hedera.mirror.importer.parser.domain.RecordItem;

@ExtendWith(MockitoExtension.class)
abstract class RecordFileReaderTest {
    private final static String pathPrefix = "data/recordstreams";

    protected FileCopier fileCopier;
    protected RecordFileReader recordFileReader;
    protected List<RecordFile> allRecordFiles;

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
                .create(Path.of(getClass().getClassLoader().getResource(pathPrefix).getPath()), dataPath);
        recordFileReader = getRecordFileReader();

        RecordFile recordFileV1_1 = new RecordFile(1561990380317763000L, 1561990399074934000L, null,
                "2019-07-01T14:13:00.317763Z.rcd", null, null,
                "333d6940254659533fd6b939033e59c57fe8f4ff78375d1e687c032918aa0b7b8179c7fd403754274a8c91e0b6c0195a",
                "f423447a3d5a531a07426070e511555283daae063706242590949116f717a0524e4dd18f9d64e66c73982d475401db04",
                null, 15L, 1);
        RecordFile recordFileV1_2 = new RecordFile(1561991340302068000L, 1561991353226225001L, null,
                "2019-07-01T14:29:00.302068Z.rcd", null, null,
                "1faf198f8fdbefa59bde191f214d73acdc4f5c0f434677a7edf9591b129e21aea90a5b3119d2802cee522e7be6bc8830",
                recordFileV1_1.getFileHash(), null, 69L, 1);
        RecordFile recordFileV2_1 = new RecordFile(1567188600419072000L, 1567188604906443001L, null,
                "2019-08-30T18_10_00.419072Z.rcd", null, null,
                "591558e059bd1629ee386c4e35a6875b4c67a096718f5d225772a651042715189414df7db5588495efb2a85dc4a0ffda",
                "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
                null, 19L, 2);
        RecordFile recordFileV2_2 = new RecordFile(1567188605249678000L, 1567188609705382001L, null,
                "2019-08-30T18_10_05.249678Z.rcd", null, null,
                "5ed51baeff204eb6a2a68b76bbaadcb9b6e7074676c1746b99681d075bef009e8d57699baaa6342feec4e83726582d36",
                recordFileV2_1.getFileHash(), null, 15L, 2);
        allRecordFiles = List.of(recordFileV1_1, recordFileV1_2, recordFileV2_1, recordFileV2_2);
    }

    @TestFactory
    Stream<DynamicTest> readValidFileWithConsumer() {
        String template = "read valid version %d file %s";

        return DynamicTest.stream(
                getFilteredFiles(false),
                (recordFile) -> String.format(template, recordFile.getRecordFormatVersion(), recordFile.getName()),
                (recordFile) -> {
                    String filename = recordFile.getName();
                    Consumer<RecordItem> itemConsumer = mock(Consumer.class);

                    // given
                    fileCopier.from(getSubPath(recordFile.getRecordFormatVersion())).filterFiles(filename).copy();
                    File inputFile = fileCopier.getTo().resolve(filename).toFile();

                    // when
                    RecordFile actual;
                    try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(inputFile))) {
                        StreamFileData streamFileData = new StreamFileData(inputFile.getAbsolutePath(), bis);
                        actual = recordFileReader.read(streamFileData, itemConsumer);
                    }

                    // then
                    assertThat(actual).isEqualTo(recordFile);
                    ArgumentCaptor<RecordItem> captor = ArgumentCaptor.forClass(RecordItem.class);
                    verify(itemConsumer, times(recordFile.getCount().intValue())).accept(captor.capture());
                    RecordItem[] recordItems = captor.getAllValues().toArray(new RecordItem[0]);
                    assertThat(recordItems[0].getConsensusTimestamp()).isEqualTo(recordFile.getConsensusStart());
                    assertThat(recordItems[recordItems.length - 1].getConsensusTimestamp()).isEqualTo(recordFile.getConsensusEnd());
                });
    }

    @TestFactory
    Stream<DynamicTest> readValidFileWithOutConsumer() {
        String template = "read valid version %d file %s";

        return DynamicTest.stream(
                getFilteredFiles(false),
                (recordFile) -> String.format(template, recordFile.getRecordFormatVersion(), recordFile.getName()),
                (recordFile) -> {
                    String filename = recordFile.getName();

                    // given
                    fileCopier.from(getSubPath(recordFile.getRecordFormatVersion())).filterFiles(filename).copy();
                    File inputFile = fileCopier.getTo().resolve(filename).toFile();

                    // when
                    RecordFile actual;
                    try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(inputFile))) {
                        StreamFileData streamFileData = new StreamFileData(inputFile.getAbsolutePath(), bis);
                        actual = recordFileReader.read(streamFileData, null);
                    }

                    // then
                    assertThat(actual).isEqualTo(recordFile);
                });
    }

    @TestFactory
    Stream<DynamicTest> readInvalidFileWithGarbageAppended() {
        String template = "read corrupted version %d file %s";

        return DynamicTest.stream(
                getFilteredFiles(false),
                (recordFile) -> String.format(template, recordFile.getRecordFormatVersion(), recordFile.getName()),
                (recordFile) -> {
                    String filename = recordFile.getName();

                    // given
                    fileCopier.from(getSubPath(recordFile.getRecordFormatVersion())).filterFiles(filename).copy();
                    File inputFile = fileCopier.getTo().resolve(filename).toFile();
                    Files.walk(dataPath).forEach(RecordFileReaderTest::corruptFile);

                    // when
                    try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(inputFile))) {
                        StreamFileData streamFileData = new StreamFileData(inputFile.getAbsolutePath(), bis);
                        Assertions.assertThrows(InvalidRecordFileException.class,
                                () -> recordFileReader.read(streamFileData, null));
                    }
                });
    }

    @TestFactory
    Stream<DynamicTest> readInvalidFileWithDataTruncated() {
        String template = "read incomplete version %d file %s";

        return DynamicTest.stream(
                getFilteredFiles(false),
                (recordFile) -> String.format(template, recordFile.getRecordFormatVersion(), recordFile.getName()),
                (recordFile) -> {
                    String filename = recordFile.getName();

                    // given
                    fileCopier.from(getSubPath(recordFile.getRecordFormatVersion())).filterFiles(filename).copy();
                    File inputFile = fileCopier.getTo().resolve(filename).toFile();
                    Files.walk(dataPath).forEach(RecordFileReaderTest::truncateFile);

                    // when
                    try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(inputFile))) {
                        StreamFileData streamFileData = new StreamFileData(inputFile.getAbsolutePath(), bis);
                        Assertions.assertThrows(InvalidRecordFileException.class,
                                () -> recordFileReader.read(streamFileData, null));
                    }
                });
    }

    protected Iterator<RecordFile> getFilteredFiles(boolean negate) {
        return allRecordFiles.stream()
                .filter((recordFile) -> negate ^ filterFile(recordFile.getRecordFormatVersion()))
                .collect(Collectors.toList())
                .iterator();
    }

    protected String getSubPath(int version) {
        return String.format("v%d/record0.0.3", version);
    }

    protected abstract RecordFileReader getRecordFileReader();

    protected abstract boolean filterFile(int version);
}
