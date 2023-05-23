/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.migration;

import com.google.common.base.Stopwatch;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.config.Owner;
import com.hedera.mirror.importer.db.TimePartitionService;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import jakarta.inject.Named;
import java.io.IOException;
import java.util.Objects;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Named
public class TopicMessageLookupMigration extends RepeatableMigration {

    private static final String TOPIC_MESSAGE_TABLE_NAME = "topic_message";
    private static final String TRUNCATE_TABLE_SQL = "truncate topic_message_lookup";
    private static final String UPDATE_PARTITION_SQL =
            """
            insert into topic_message_lookup (partition, sequence_number_range, timestamp_range, topic_id)
            select
              '%1$s',
              int8range(min(sequence_number), max(sequence_number), '[]'),
              int8range(min(consensus_timestamp), max(consensus_timestamp), '[]'),
              topic_id
            from %1$s
            group by topic_id
            """;

    private final EntityProperties entityProperties;
    private final JdbcTemplate jdbcTemplate;
    private final RecordFileRepository recordFileRepository;
    private final TimePartitionService timePartitionService;

    @Lazy
    protected TopicMessageLookupMigration(
            EntityProperties entityProperties,
            @Owner JdbcTemplate jdbcTemplate,
            MirrorProperties mirrorProperties,
            RecordFileRepository recordFileRepository,
            TimePartitionService timePartitionService) {
        super(mirrorProperties.getMigration());
        this.entityProperties = entityProperties;
        this.jdbcTemplate = jdbcTemplate;
        this.recordFileRepository = recordFileRepository;
        this.timePartitionService = timePartitionService;
    }

    @Override
    protected void doMigrate() throws IOException {
        if (!entityProperties.getPersist().isTopicMessageLookups()) {
            // don't skip migration when persist.topics is false since we should still create topic message lookups
            // for topic messages ingested when topics is enabled
            log.info("Skip the migration since topicMessageLookups persist is disabled");
            return;
        }

        var stopwatch = Stopwatch.createStarted();
        var lastRecordFile = recordFileRepository.findLatest();
        if (lastRecordFile.isEmpty()) {
            log.info("Skip the migration since there's no record file parsed");
            return;
        }

        var partitions = timePartitionService.getTimePartitions(TOPIC_MESSAGE_TABLE_NAME);
        if (partitions.isEmpty()) {
            log.info("Skip the migration since topic_message is either not partitioned or without attached partitions");
            return;
        }

        var transactionManager = new DataSourceTransactionManager(Objects.requireNonNull(jdbcTemplate.getDataSource()));
        var transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(s -> {
            jdbcTemplate.update(TRUNCATE_TABLE_SQL);

            for (var partition : partitions) {
                var sql = String.format(UPDATE_PARTITION_SQL, partition.getName());
                jdbcTemplate.update(sql);
            }
        });

        log.info(
                "Successfully backfilled topic_message_lookup table for {} time partitions in {}",
                partitions.size(),
                stopwatch);
    }

    @Override
    public String getDescription() {
        return "Backfill topic_message_lookup table";
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        // The version where topic_message_lookup table was added
        return MigrationVersion.fromVersion("1.80.1");
    }
}
