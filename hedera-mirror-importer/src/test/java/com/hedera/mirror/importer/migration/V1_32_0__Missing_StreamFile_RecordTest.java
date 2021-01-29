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
import java.util.function.Consumer;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.TestRecordFiles;
import com.hedera.mirror.importer.converter.AccountIdConverter;
import com.hedera.mirror.importer.domain.AccountBalanceFile;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.StreamFile;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.downloader.DownloaderProperties;
import com.hedera.mirror.importer.downloader.balance.BalanceDownloaderProperties;
import com.hedera.mirror.importer.downloader.record.RecordDownloaderProperties;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import com.hedera.mirror.importer.util.Utility;

@Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "classpath:db/scripts/cleanup_v1.31.2.sql")
@Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "classpath:db/scripts/cleanup_v1.31.2.sql")
@Tag("failincci")
@Tag("migration")
@Tag("v1")
@TestPropertySource(properties = "spring.flyway.target=1.31.3")
class V1_32_0__Missing_StreamFile_RecordTest extends IntegrationTest {

    private final static String INSERT_RECORD_FILE_STATEMENT = "insert into record_file " +
            "(consensus_start, consensus_end, count, file_hash, name, node_account_id, prev_hash) " +
            "values (?, ?, ?, ?, ?, ?, ?)";
    private final static String SELECT_ALL_RECORD_FILES_STATEMENT = "select * from record_file";

    @Resource
    private V1_32_0__Missing_StreamFile_Record migration;
    @Resource
    private DataSource dataSource;
    @Resource
    private AccountIdConverter accountIdConverter;
    @Resource
    private AccountBalanceFileRepository accountBalanceFileRepository;
    @Resource
    private BalanceDownloaderProperties balanceDownloaderProperties;
    private JdbcTemplate jdbcTemplate;
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
        jdbcTemplate = new JdbcTemplate(dataSource);
        mirrorProperties.setDataPath(dataPath);
        balanceDownloaderProperties.init();
        recordDownloaderProperties.init();

        FileCopier accountBalanceFileCopier = FileCopier
                .create(Utility.getResource("data").toPath(), balanceDownloaderProperties.getValidPath())
                .from(balanceDownloaderProperties.getStreamType().getPath(), "v1", "balance0.0.3");
        FileCopier recordFileCopier = FileCopier
                .create(Utility.getResource("data").toPath(), recordDownloaderProperties.getValidPath())
                .from(recordDownloaderProperties.getStreamType().getPath(), "v2", "record0.0.3");

        EntityId nodeAccountId = V1_32_0__Missing_StreamFile_Record.DEFAULT_NODE_ACCOUNT_ID;

        Consumer<StreamFile> saveAccountBalanceFile = (streamFile) -> {
            accountBalanceFileRepository.save((AccountBalanceFile) streamFile);
        };
        AccountBalanceFile accountBalanceFile = new AccountBalanceFile(1567188900016002001L, 0L,
                "c1a6ffb5df216a1e8331f949f45cb9400fc474150d57d977c77f21318687eb18d407c780147d0435791a02743a0f7bfc",
                null, null, "2019-08-30T18_15_00.016002001Z_Balances.csv", nodeAccountId);
        allFilesWithMeta.put(accountBalanceFile.getName(), new StreamFileMetadata(
                accountBalanceFileCopier, accountBalanceFile, saveAccountBalanceFile
        ));

        accountBalanceFile = new AccountBalanceFile(1567189800010147001L, 0L,
                "c197898e485e92a85752d475b536e6dc09879a18d358b1e72a9a1160bb24c8bb7a4c58610383ac80fd1c7659214eccd4",
                null, null, "2019-08-30T18_30_00.010147001Z_Balances.csv", nodeAccountId);
        allFilesWithMeta.put(accountBalanceFile.getName(), new StreamFileMetadata(
                accountBalanceFileCopier, accountBalanceFile, saveAccountBalanceFile
        ));

        Map<String, RecordFile> allRecordFileMap = TestRecordFiles.getAll();
        RecordFile recordFile = allRecordFileMap.get("2019-08-30T18_10_00.419072Z.rcd");
        recordFile.setNodeAccountId(nodeAccountId);
        allFilesWithMeta.put(recordFile
                .getName(), new StreamFileMetadata(recordFileCopier, recordFile, this::insertRecordFile));

        recordFile = allRecordFileMap.get("2019-08-30T18_10_05.249678Z.rcd");
        recordFile.setNodeAccountId(nodeAccountId);
        allFilesWithMeta.put(recordFile
                .getName(), new StreamFileMetadata(recordFileCopier, recordFile, this::insertRecordFile));

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
            StreamFileMetadata metadata = allFilesWithMeta.get(filename);
            metadata.getSave().accept(metadata.getStreamFile());
        }

        // when
        migration.migrate(new FlywayContext());

        // then
        List<String> expectedAddedStreamFiles = new ArrayList<>(validFiles);
        expectedAddedStreamFiles.removeAll(filesWithRecord);
        List<String> actualAddedStreamFiles = new ArrayList<>();
        accountBalanceFileRepository.findAll().forEach(streamFile -> actualAddedStreamFiles.add(streamFile.getName()));
        loadAllRecordFiles().forEach(streamFile -> actualAddedStreamFiles.add(streamFile.getName()));
        actualAddedStreamFiles.removeAll(filesWithRecord);
        assertThat(actualAddedStreamFiles).hasSameElementsAs(expectedAddedStreamFiles);
    }

    private void insertRecordFile(StreamFile streamFile) {
        RecordFile recordFile = (RecordFile) streamFile;
        jdbcTemplate.update(
                INSERT_RECORD_FILE_STATEMENT,
                recordFile.getConsensusStart(),
                recordFile.getConsensusEnd(),
                recordFile.getCount(),
                recordFile.getFileHash(),
                recordFile.getName(),
                accountIdConverter.convertToDatabaseColumn(recordFile.getNodeAccountId()),
                recordFile.getPreviousHash()
        );
    }

    private List<RecordFile> loadAllRecordFiles() {
        return jdbcTemplate.query(
                SELECT_ALL_RECORD_FILES_STATEMENT,
                (resultSet, rowNum) -> RecordFile.builder()
                        .consensusStart(resultSet.getLong("consensus_start"))
                        .consensusEnd(resultSet.getLong("consensus_end"))
                        .count(resultSet.getLong("count"))
                        .fileHash(resultSet.getString("file_hash"))
                        .name(resultSet.getString("name"))
                        .nodeAccountId(accountIdConverter
                                .convertToEntityAttribute(resultSet.getLong("node_account_id")))
                        .previousHash(resultSet.getString("prev_hash"))
                        .build());
    }

    @Data
    private static class StreamFileMetadata {
        private final FileCopier fileCopier;
        private final StreamFile streamFile;
        private final Consumer<StreamFile> save;
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
