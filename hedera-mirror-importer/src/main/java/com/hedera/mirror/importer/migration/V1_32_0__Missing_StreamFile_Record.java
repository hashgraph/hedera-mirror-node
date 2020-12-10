package com.hedera.mirror.importer.migration;

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

import com.google.common.base.Stopwatch;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.data.repository.CrudRepository;

import com.hedera.mirror.importer.domain.AccountBalanceFile;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.StreamFile;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.downloader.DownloaderProperties;
import com.hedera.mirror.importer.downloader.balance.BalanceDownloaderProperties;
import com.hedera.mirror.importer.downloader.record.RecordDownloaderProperties;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.util.Utility;

@Log4j2
@Named
@RequiredArgsConstructor
public class V1_32_0__Missing_StreamFile_Record extends BaseJavaMigration {

    public final static EntityId DEFAULT_NODE_ACCOUNT_ID = EntityId.of(0, 0, 3, EntityTypeEnum.ACCOUNT);

    private final BalanceDownloaderProperties balanceDownloaderProperties;
    private final AccountBalanceFileRepository accountBalanceFileRepository;
    private final RecordDownloaderProperties recordDownloaderProperties;
    private final RecordFileRepository recordFileRepository;

    @Override
    public void migrate(final Context context) {
        addStreamFileRecords(balanceDownloaderProperties, accountBalanceFileRepository);
        addStreamFileRecords(recordDownloaderProperties, recordFileRepository);
    }

    private void addStreamFileRecords(DownloaderProperties downloaderProperties, CrudRepository repository) {
        AtomicInteger count = new AtomicInteger(0);
        StreamType streamType = downloaderProperties.getStreamType();
        Path validPath = downloaderProperties.getValidPath();
        Stopwatch stopwatch = Stopwatch.createStarted();

        try {
            File file = validPath.toFile();
            if (file.isDirectory()) {
                String[] files = file.list();
                if (files == null || files.length == 0) {
                    log.info("No files to parse in directory {} for {}", file.getPath(), streamType);
                    return;
                }

                Arrays.sort(files);
                for (String filename : files) {
                    if (isStreamFileRecordPresent(filename, streamType)) {
                        continue;
                    }

                    StreamFile streamFile = readStreamFile(validPath.resolve(filename).toFile(), streamType);
                    repository.save(streamFile);
                    count.getAndIncrement();
                }
            } else {
                log.error("ValidPath {} for {} downloader is not a directory", validPath, streamType);
            }
        } catch (Exception e) {
            log.error("Unexpected error adding stream file records for {}", streamType, e);
        } finally {
            log.info("Added {} stream file records for {} in {}", count.get(), streamType, stopwatch);
        }
    }

    private StreamFile readStreamFile(File file, StreamType streamType) {
        StreamFile streamFile = null;
        if (streamType == StreamType.BALANCE) {
            streamFile = AccountBalanceFile.builder()
                    .consensusTimestamp(Utility.getTimestampFromFilename(file.getName()))
                    .count(0L)
                    .fileHash(Utility.getBalanceFileHash(file.getPath()))
                    .name(file.getName())
                    .build();
        } else if (streamType == StreamType.RECORD) {
            streamFile = Utility.parseRecordFile(file.getPath(), null);
        }

        if (streamFile != null) {
            streamFile.setNodeAccountId(DEFAULT_NODE_ACCOUNT_ID);
        }

        return streamFile;
    }

    private boolean isStreamFileRecordPresent(String filename, StreamType streamType) {
        Optional<? extends StreamFile> streamFile = Optional.empty();
        if (streamType == StreamType.BALANCE) {
            long timestamp = Utility.getTimestampFromFilename(filename);
            streamFile = accountBalanceFileRepository.findById(timestamp);
        } else if (streamType == StreamType.RECORD) {
            streamFile = recordFileRepository.findByName(filename);
        }

        return streamFile.isPresent();
    }
}
