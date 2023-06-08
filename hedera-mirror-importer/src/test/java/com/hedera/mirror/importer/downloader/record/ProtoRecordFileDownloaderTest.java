/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.downloader.record;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.TestRecordFiles;
import com.hedera.mirror.importer.downloader.AbstractDownloaderTest;
import com.hedera.services.stream.proto.SidecarFile;
import com.hedera.services.stream.proto.SidecarType;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class ProtoRecordFileDownloaderTest extends AbstractRecordFileDownloaderTest {

    private static final String RECORD_FILE_WITH_SIDECAR = "2022-07-13T08_46_11.304284003Z.rcd.gz";
    private static final String SIDECAR_FILENAME = "2022-07-13T08_46_11.304284003Z_01.rcd.gz";

    @BeforeEach
    void setup() {
        loadAddressBook("test-v6-sidecar-4n.bin");
    }

    @Test
    void sidecarDisabled() {
        sidecarProperties.setEnabled(false);
        var recordFile = recordFileMap.get(RECORD_FILE_WITH_SIDECAR);
        recordFile.getSidecars().forEach(sidecar -> {
            sidecar.setActualHash(null);
            sidecar.setBytes(null);
            sidecar.setCount(null);
            sidecar.setRecords(Collections.emptyList());
            sidecar.setSize(null);
        });
        fileCopier.copy();
        expectLastStreamFile(Instant.EPOCH);
        downloader.download();

        super.verifyStreamFiles(List.of(file1, file2), s -> {});
        assertThat(downloaderProperties.getStreamPath()).isEmptyDirectory();
    }

    @Test
    void sidecarTypesFilter() {
        // The test sidecar file has CONTRACT_BYTECODE and CONTRACT_STATE_CHANGE
        sidecarProperties.setTypes(Set.of(SidecarType.CONTRACT_ACTION));
        var recordFile = recordFileMap.get(RECORD_FILE_WITH_SIDECAR);
        recordFile.getSidecars().forEach(sidecar -> {
            sidecar.setActualHash(null);
            sidecar.setBytes(null);
            sidecar.setCount(null);
            sidecar.setRecords(Collections.emptyList());
            sidecar.setSize(null);
        });
        fileCopier.copy();
        expectLastStreamFile(Instant.EPOCH);
        downloader.download();

        super.verifyStreamFiles(List.of(file1, file2), actual -> {
            var transactionSidecarRecords = actual.getItems()
                    .flatMap(recordItem -> Flux.fromIterable(recordItem.getSidecarRecords()))
                    .collectList()
                    .block();
            assertThat(transactionSidecarRecords).isEmpty();
        });
        assertThat(downloaderProperties.getStreamPath()).isEmptyDirectory();
    }

    @Test
    void sidecarTypesFilterSome() {
        sidecarProperties.setPersistBytes(true);
        sidecarProperties.setTypes(Set.of(SidecarType.CONTRACT_BYTECODE));
        fileCopier.copy();
        expectLastStreamFile(Instant.EPOCH);
        downloader.download();

        verifyStreamFiles(List.of(file1, file2), recordFile -> {
            var sidecarTypes = recordFile
                    .getItems()
                    .flatMap(recordItem -> Flux.fromIterable(recordItem.getSidecarRecords()))
                    .map(TransactionSidecarRecord::getSidecarRecordsCase)
                    .collectList()
                    .block();
            if (Objects.equals(recordFile.getName(), RECORD_FILE_WITH_SIDECAR)) {
                assertThat(sidecarTypes).containsExactly(TransactionSidecarRecord.SidecarRecordsCase.BYTECODE);
            } else {
                assertThat(sidecarTypes).isEmpty();
            }
        });
        assertThat(downloaderProperties.getStreamPath()).isEmptyDirectory();
    }

    @Test
    void sidecarFileCorrupted() throws IOException {
        fileCopier.copy();
        Files.walk(s3Path).filter(p -> p.endsWith(SIDECAR_FILENAME)).forEach(AbstractDownloaderTest::corruptFile);
        expectLastStreamFile(Instant.EPOCH);
        downloader.download();

        verifyForSuccess(List.of(file1));
        assertThat(downloaderProperties.getStreamPath()).isEmptyDirectory();
    }

    @Test
    void sidecarFileHashMismatch() throws IOException {
        try (var byteArrayOutputStream = new ByteArrayOutputStream();
                var gzipCompressorOutputStream = new GzipCompressorOutputStream(byteArrayOutputStream)) {
            gzipCompressorOutputStream.write(SidecarFile.getDefaultInstance().toByteArray());
            gzipCompressorOutputStream.finish();
            var fileData = byteArrayOutputStream.toByteArray();

            fileCopier.copy();
            Files.walk(s3Path).filter(p -> p.endsWith(SIDECAR_FILENAME)).forEach(p -> {
                try {
                    FileUtils.writeByteArrayToFile(p.toFile(), fileData);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            expectLastStreamFile(Instant.EPOCH);
            downloader.download();

            verifyForSuccess(List.of(file1));
            assertThat(downloaderProperties.getStreamPath()).isEmptyDirectory();
        }
    }

    @Override
    protected Path getTestDataDir() {
        return Paths.get("recordstreams", "v6");
    }

    @Override
    protected Duration getCloseInterval() {
        return Duration.ofSeconds(3L);
    }

    @Override
    protected Map<String, RecordFile> getRecordFileMap() {
        var allRecordFileMap = TestRecordFiles.getAll();
        var recordFile1 = allRecordFileMap.get("2022-07-13T08_46_08.041986003Z.rcd.gz").toBuilder()
                .build();
        var recordFile2 = allRecordFileMap.get("2022-07-13T08_46_11.304284003Z.rcd.gz").toBuilder()
                .build();
        return Map.of(recordFile1.getName(), recordFile1, recordFile2.getName(), recordFile2);
    }

    @Override
    protected Map<String, Long> getExpectedFileIndexMap() {
        return getRecordFileMap().values().stream()
                .collect(Collectors.toMap(RecordFile::getName, RecordFile::getIndex));
    }

    @Override
    protected void verifyStreamFiles(List<String> files, Consumer<RecordFile> extraAssert) {
        Consumer<RecordFile> recordAssert = recordFile -> {
            var recordItems = recordFile.getItems().collectList().block();
            if (Objects.equals(recordFile.getName(), RECORD_FILE_WITH_SIDECAR)) {
                assertThat(recordItems)
                        // The record item either has empty transaction sidecar records or all such records consensus
                        // timestamp is the same as that of the recordItem
                        .allSatisfy(recordItem -> {
                            var consensusTimestamp = recordItem.getConsensusTimestamp();
                            assertThat(recordItem.getSidecarRecords())
                                    .satisfiesAnyOf(
                                            sidecarRecords ->
                                                    assertThat(sidecarRecords).isEmpty(),
                                            sidecarRecords -> assertThat(sidecarRecords)
                                                    .map(TransactionSidecarRecord::getConsensusTimestamp)
                                                    .map(DomainUtils::timestampInNanosMax)
                                                    .containsOnly(consensusTimestamp));
                        })
                        // Also verify there are transaction sidecar records
                        .flatMap(RecordItem::getSidecarRecords)
                        .isNotEmpty();
            } else {
                assertThat(recordItems).flatMap(RecordItem::getSidecarRecords).isEmpty();
            }
        };
        super.verifyStreamFiles(files, recordAssert.andThen(extraAssert));
    }
}
