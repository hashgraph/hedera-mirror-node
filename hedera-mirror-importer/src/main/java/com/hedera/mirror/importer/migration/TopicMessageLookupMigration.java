/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.importer.ImporterProperties;
import com.hedera.mirror.importer.db.DBProperties;
import com.hedera.mirror.importer.db.TimePartition;
import com.hedera.mirror.importer.db.TimePartitionService;
import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import jakarta.inject.Named;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import org.apache.commons.lang3.BooleanUtils;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionOperations;

@Named
public class TopicMessageLookupMigration extends AsyncJavaMigration<String> {

    private static final String TOPIC_MESSAGE_TABLE_NAME = "topic_message";

    private static final String UPDATE_PARTITION_SQL =
            """
                    insert into topic_message_lookup (partition, sequence_number_range, timestamp_range, topic_id)
                    select
                      '%1$s',
                      int8range(min(sequence_number), max(sequence_number), '[]'),
                      int8range(min(consensus_timestamp), max(consensus_timestamp), '[]'),
                      topic_id
                    from %1$s
                    group by topic_id;
                    """;
    private static final String PARTITION_NEEDS_MIGRATION_SQL =
            """
                    select exists (select 1 from topic_message_lookup where partition = ?)
                    """;

    private final EntityProperties entityProperties;
    private final JdbcTemplate jdbcTemplate;
    private final List<String> partitions = new LinkedList<>();

    private final RecordFileRepository recordFileRepository;
    private final TimePartitionService timePartitionService;

    @Getter
    private final TransactionOperations transactionOperations;

    @Lazy
    protected TopicMessageLookupMigration(
            EntityProperties entityProperties,
            JdbcTemplate jdbcTemplate,
            ImporterProperties importerProperties,
            RecordFileRepository recordFileRepository,
            DBProperties dbProperties,
            TimePartitionService timePartitionService,
            TransactionOperations transactionOperations) {
        super(
                importerProperties.getMigration(),
                new NamedParameterJdbcTemplate(jdbcTemplate),
                dbProperties.getSchema());
        this.entityProperties = entityProperties;
        this.jdbcTemplate = jdbcTemplate;
        this.recordFileRepository = recordFileRepository;
        this.timePartitionService = timePartitionService;
        this.transactionOperations = transactionOperations;
    }

    @Override
    protected boolean performSynchronousSteps() {
        if (!entityProperties.getPersist().isTopicMessageLookups()) {
            // don't skip migration when persist.topics is false since we should still create topic message lookups
            // for topic messages ingested when topics is enabled
            log.info("Skip the migration since topicMessageLookups persist is disabled");
            return false;
        }

        var lastRecordFile = recordFileRepository.findLatest().map(RecordFile::getConsensusEnd);
        if (lastRecordFile.isEmpty()) {
            log.info("Skip the migration since there's no record file parsed");
            return false;
        }

        var timePartitions = timePartitionService.getTimePartitions(TOPIC_MESSAGE_TABLE_NAME);

        if (timePartitions.isEmpty()) {
            log.info("Skip the migration since topic_message doesn't contain any partitions");
            return false;
        }

        var activePartition = timePartitions.stream()
                .filter(p -> p.getTimestampRange().contains(lastRecordFile.get()))
                .map(TimePartition::getName)
                .findFirst()
                .orElseThrow(() -> new ParserException("No active partition found"));

        partitions.addAll(timePartitions.stream()
                .filter(p -> p.getTimestampRange().lowerEndpoint() <= lastRecordFile.get()
                        && !p.getName().equals(activePartition))
                .map(TimePartition::getName)
                .toList());

        log.info("Migrating topic_message_lookup for partition {} synchronously", activePartition);
        getTransactionOperations().executeWithoutResult(status -> migratePartition(activePartition));

        return !partitions.isEmpty();
    }

    @Override
    protected String getInitial() {
        return partitions.isEmpty() ? null : partitions.removeLast();
    }

    @Override
    protected Optional<String> migratePartial(String partitionName) {
        migratePartition(partitionName);
        return Optional.ofNullable(getInitial());
    }

    private void migratePartition(String partitionName) {
        var alreadyMigrated = jdbcTemplate.queryForObject(PARTITION_NEEDS_MIGRATION_SQL, Boolean.class, partitionName);
        if (BooleanUtils.isFalse(alreadyMigrated)) {
            var sql = String.format(UPDATE_PARTITION_SQL, partitionName);
            jdbcTemplate.update(sql);
            log.info("Migrated topic_message_lookup for partition {}", partitionName);
        } else {
            log.info("Partition {} already migrated", partitionName);
        }
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
