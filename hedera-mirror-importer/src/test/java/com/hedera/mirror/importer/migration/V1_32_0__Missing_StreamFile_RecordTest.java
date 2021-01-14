package com.hedera.mirror.importer.migration;

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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import javax.sql.DataSource;
import lombok.Data;
import org.apache.commons.io.FileUtils;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.repository.CrudRepository;
import org.springframework.test.context.TestPropertySource;

import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.AccountBalanceFile;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.StreamFile;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.downloader.DownloaderProperties;
import com.hedera.mirror.importer.downloader.balance.BalanceDownloaderProperties;
import com.hedera.mirror.importer.downloader.record.RecordDownloaderProperties;
import com.hedera.mirror.importer.migration.domain.RecordFileV1_33_0;
import com.hedera.mirror.importer.migration.repository.RecordFileRepositoryV1_33_0;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import com.hedera.mirror.importer.util.Utility;

@Tag("failincci")
@TestPropertySource(properties = "spring.flyway.target=1.31.3")
class V1_32_0__Missing_StreamFile_RecordTest extends IntegrationTest {

    @Resource
    private V1_32_0__Missing_StreamFile_Record migration;
    @Resource
    private DataSource dataSource;
    @Resource
    private AccountBalanceFileRepository accountBalanceFileRepository;
    @Resource
    private BalanceDownloaderProperties balanceDownloaderProperties;
    @Resource
    private RecordFileRepositoryV1_33_0 recordFileRepositoryCompat;
    @Resource
    private RecordDownloaderProperties recordDownloaderProperties;
    @Resource
    private MirrorProperties mirrorProperties;

    @TempDir
    Path dataPath;
    private final Map<String, StreamFileMetadata> allFilesWithMeta = new HashMap<>();
    private List<String> allFiles;
    private List<String> accountBalanceFiles;
    private List<String> recordFiles;

    @BeforeEach
    void setup() {
        mirrorProperties.setDataPath(dataPath);
        balanceDownloaderProperties.init();
        recordDownloaderProperties.init();

        FileCopier accountBalanceFileCopier = FileCopier.create(Utility.getResource("data").toPath(), balanceDownloaderProperties.getValidPath())
                .from(balanceDownloaderProperties.getStreamType().getPath(), "v1", "balance0.0.3");
        FileCopier recordFileCopier = FileCopier.create(Utility.getResource("data").toPath(), recordDownloaderProperties.getValidPath())
                .from(recordDownloaderProperties.getStreamType().getPath(), "v2", "record0.0.3");

        EntityId nodeAccountId = V1_32_0__Missing_StreamFile_Record.DEFAULT_NODE_ACCOUNT_ID;

        // account balance file "2019-08-30T18_15_00.016002001Z_Balances.csv"
        AccountBalanceFile accountBalanceFile = new AccountBalanceFile(1567188900016002001L, 0L,
                "c1a6ffb5df216a1e8331f949f45cb9400fc474150d57d977c77f21318687eb18d407c780147d0435791a02743a0f7bfc",
                null, null, "2019-08-30T18_15_00.016002001Z_Balances.csv", nodeAccountId);
        allFilesWithMeta.put(accountBalanceFile.getName(), new StreamFileMetadata(
                accountBalanceFileCopier, accountBalanceFile, accountBalanceFileRepository
        ));

        // account balance file "2019-08-30T18_30_00.010147001Z_Balances.csv"
        accountBalanceFile = new AccountBalanceFile(1567189800010147001L, 0L,
                "c197898e485e92a85752d475b536e6dc09879a18d358b1e72a9a1160bb24c8bb7a4c58610383ac80fd1c7659214eccd4",
                null, null, "2019-08-30T18_30_00.010147001Z_Balances.csv", nodeAccountId);
        allFilesWithMeta.put(accountBalanceFile.getName(), new StreamFileMetadata(
                accountBalanceFileCopier, accountBalanceFile, accountBalanceFileRepository
        ));

        // record file "2019-08-30T18_10_00.419072Z.rcd"
        RecordFileV1_33_0 recordFile = RecordFileV1_33_0.builder()
                .consensusStart(1567188600419072000L)
                .consensusEnd(1567188604906443001L)
                .count(0L)
                .fileHash("591558e059bd1629ee386c4e35a6875b4c67a096718f5d225772a651042715189414df7db5588495efb2a85dc4a0ffda")
                .name("2019-08-30T18_10_00.419072Z.rcd")
                .nodeAccountId(nodeAccountId)
                .previousHash(Utility.EMPTY_SHA_384_HASH)
                .build();
        allFilesWithMeta.put(recordFile.getName(), new StreamFileMetadata(
                recordFileCopier, recordFile, recordFileRepositoryCompat
        ));

        // record file "2019-08-30T18_10_05.249678Z.rcd"
        recordFile = RecordFileV1_33_0.builder()
                .consensusStart(1567188605249678000L)
                .consensusEnd(1567188609705382001L)
                .count(0L)
                .fileHash("5ed51baeff204eb6a2a68b76bbaadcb9b6e7074676c1746b99681d075bef009e8d57699baaa6342feec4e83726582d36")
                .name("2019-08-30T18_10_05.249678Z.rcd")
                .nodeAccountId(nodeAccountId)
                .previousHash(recordFile.getFileHash())
                .build();
        allFilesWithMeta.put(recordFile.getName(), new StreamFileMetadata(
                recordFileCopier, recordFile, recordFileRepositoryCompat
        ));

        allFiles = List.copyOf(allFilesWithMeta.keySet());
        accountBalanceFiles = allFilesWithMeta.keySet().stream()
                .filter(filename -> StreamType.fromFilename(filename) == StreamType.BALANCE)
                .collect(Collectors.toList());
        recordFiles = allFilesWithMeta.keySet().stream()
                .filter(filename -> StreamType.fromFilename(filename) == StreamType.RECORD)
                .collect(Collectors.toList());
    }

    @Test
    void noFiles() throws IOException {
        testMigration(Collections.emptyList(), Collections.emptyList());
    }

    @Test
    void allFilesHaveRecord() throws IOException {
        testMigration(allFiles, allFiles);
    }

    @Test
    void allFilesWithoutRecord() throws IOException {
        testMigration(allFiles, Collections.emptyList());
    }

    @Test
    void someFilesHaveRecord() throws IOException {
        List<String> filesWithRecord = List.of(accountBalanceFiles.get(0), recordFiles.get(0));
        testMigration(allFiles, filesWithRecord);
    }

    @Test
    void validPathIsNotDirectory() throws IOException {
        // replace valid path with a file
        for (DownloaderProperties properties : List.of(balanceDownloaderProperties, recordDownloaderProperties)) {
            Path validPath = properties.getValidPath();
            FileUtils.deleteDirectory(validPath.toFile());
            FileUtils.write(validPath.toFile(), "", StandardCharsets.UTF_8);
        }

        testMigration(Collections.emptyList(), Collections.emptyList());
    }

    void testMigration(List<String> validFiles, List<String> filesWithRecord) throws IOException {
        // given
        for (String filename : validFiles) {
            allFilesWithMeta.get(filename).getFileCopier().filterFiles(filename).copy();
        }

        for (String filename : filesWithRecord) {
            StreamFileMetadata meta = allFilesWithMeta.get(filename);
            meta.getRepository().save(meta.getStreamFile());
        }

        // when
        migration.migrate(new FlywayContext());

        // then
        List<String> expectedAddedStreamFiles = new ArrayList<>(validFiles);
        expectedAddedStreamFiles.removeAll(filesWithRecord);
        List<String> actualAddedStreamFiles = new ArrayList<>();
        accountBalanceFileRepository.findAll().forEach(streamFile -> actualAddedStreamFiles.add(streamFile.getName()));
        recordFileRepositoryCompat.findAll().forEach(streamFile -> actualAddedStreamFiles.add(streamFile.getName()));
        actualAddedStreamFiles.removeAll(filesWithRecord);
        assertThat(actualAddedStreamFiles).hasSameElementsAs(expectedAddedStreamFiles);
    }

    @Data
    private static class StreamFileMetadata {
        private final FileCopier fileCopier;
        private final StreamFile streamFile;
        private final CrudRepository repository;
    }

    private class FlywayContext implements Context {

        @Override
        public Configuration getConfiguration() {
            return new FluentConfiguration().target("1.32.0");
        }

        @Override
        public Connection getConnection() {
            try {
                return dataSource.getConnection();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
