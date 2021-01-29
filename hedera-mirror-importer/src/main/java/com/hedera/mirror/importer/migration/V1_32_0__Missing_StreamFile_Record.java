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

import com.google.common.base.Stopwatch;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import javax.inject.Named;
import javax.sql.DataSource;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;

import com.hedera.mirror.importer.converter.AccountIdConverter;
import com.hedera.mirror.importer.domain.AccountBalanceFile;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.StreamFile;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.downloader.DownloaderProperties;
import com.hedera.mirror.importer.downloader.balance.BalanceDownloaderProperties;
import com.hedera.mirror.importer.downloader.record.RecordDownloaderProperties;
import com.hedera.mirror.importer.reader.balance.BalanceFileReader;
import com.hedera.mirror.importer.reader.record.RecordFileReader;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import com.hedera.mirror.importer.util.Utility;

@Log4j2
@Named
public class V1_32_0__Missing_StreamFile_Record extends MirrorBaseJavaMigration {

    public static final EntityId DEFAULT_NODE_ACCOUNT_ID = EntityId.of(0, 0, 3, EntityTypeEnum.ACCOUNT);

    private static final String COUNT_RECORD_FILE_STATEMENT = "select count(*) from record_file where name = ?";
    private static final String INSERT_RECORD_FILE_STATEMENT = "insert into record_file " +
            "(consensus_start, consensus_end, count, file_hash, name, node_account_id, prev_hash) " +
            "values (?, ?, ?, ?, ?, ?, ?)";

    private final AccountIdConverter accountIdConverter;
    private final AccountBalanceFileRepository accountBalanceFileRepository;
    private final BalanceDownloaderProperties balanceDownloaderProperties;
    private final JdbcTemplate jdbcTemplate;
    private final RecordDownloaderProperties recordDownloaderProperties;
    private final RecordFileReader recordFileReader;
    private final BalanceFileReader balanceFileReader;

    public V1_32_0__Missing_StreamFile_Record(AccountIdConverter accountIdConverter,
                                              AccountBalanceFileRepository accountBalanceFileRepository,
                                              BalanceDownloaderProperties balanceDownloaderProperties,
                                              DataSource dataSource,
                                              RecordDownloaderProperties recordDownloaderProperties,
                                              RecordFileReader recordFileReader, BalanceFileReader balanceFileReader) {
        this.accountIdConverter = accountIdConverter;
        this.accountBalanceFileRepository = accountBalanceFileRepository;
        this.balanceDownloaderProperties = balanceDownloaderProperties;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.recordDownloaderProperties = recordDownloaderProperties;
        this.recordFileReader = recordFileReader;
        this.balanceFileReader = balanceFileReader;
    }

    @Override
    protected void doMigrate() {
        addStreamFileRecords(balanceDownloaderProperties);
        addStreamFileRecords(recordDownloaderProperties);
    }

    private void addStreamFileRecords(DownloaderProperties downloaderProperties) {
        int count = 0;
        StreamType streamType = downloaderProperties.getStreamType();
        Path validPath = downloaderProperties.getValidPath();
        Stopwatch stopwatch = Stopwatch.createStarted();

        try {
            File file = validPath.toFile();
            if (!file.isDirectory()) {
                log.error("ValidPath {} for {} downloader is not a directory", validPath, streamType);
                return;
            }

            String[] files = file.list();
            if (files == null || files.length == 0) {
                log.info("No files to parse in directory {} for {}", file.getPath(), streamType);
                return;
            }

            Arrays.sort(files);
            for (String filename : files) {
                if (isStreamFileRecordPresent(filename, streamType)) {
                    log.info("Skip file {} since it's already in db", filename);
                    continue;
                }

                StreamFile streamFile = readStreamFile(validPath.resolve(filename).toFile(), streamType);
                saveStreamFile(streamFile, streamType);
                count++;
            }
        } catch (Exception e) {
            log.error("Unexpected error adding stream file records for {}", streamType, e);
            throw e;
        } finally {
            log.info("Added {} stream file records for {} in {}", count, streamType, stopwatch);
        }
    }

    private StreamFile readStreamFile(File file, StreamType streamType) {
        StreamFile streamFile;
        if (streamType == StreamType.BALANCE) {
            streamFile = balanceFileReader.read(StreamFileData.from(file));
        } else if (streamType == StreamType.RECORD) {
            streamFile = recordFileReader.read(StreamFileData.from(file));
        } else {
            throw new IllegalArgumentException("StreamType " + streamType + " is not supported");
        }

        streamFile.setNodeAccountId(DEFAULT_NODE_ACCOUNT_ID);
        return streamFile;
    }

    private void saveStreamFile(StreamFile streamFile, StreamType streamType) {
        if (streamType == StreamType.BALANCE) {
            accountBalanceFileRepository.save((AccountBalanceFile) streamFile);
        } else if (streamType == StreamType.RECORD) {
            RecordFile recordFile = (RecordFile) streamFile;
            jdbcTemplate.update(INSERT_RECORD_FILE_STATEMENT,
                    recordFile.getConsensusStart(),
                    recordFile.getConsensusEnd(),
                    recordFile.getCount(),
                    recordFile.getFileHash(),
                    recordFile.getName(),
                    accountIdConverter.convertToDatabaseColumn(recordFile.getNodeAccountId()),
                    recordFile.getPreviousHash()
            );
        }
    }

    private boolean isStreamFileRecordPresent(String filename, StreamType streamType) {
        if (streamType == StreamType.BALANCE) {
            long timestamp = Utility.getTimestampFromFilename(filename);
            return accountBalanceFileRepository.findById(timestamp).isPresent();
        } else if (streamType == StreamType.RECORD) {
            int count = jdbcTemplate.queryForObject(COUNT_RECORD_FILE_STATEMENT, Integer.class, filename);
            return count == 1;
        }

        return false;
    }
}
