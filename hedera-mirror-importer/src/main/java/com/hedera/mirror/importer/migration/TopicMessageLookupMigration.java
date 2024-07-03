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

import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.importer.ImporterProperties;
import com.hedera.mirror.importer.db.DBProperties;
import com.hedera.mirror.importer.db.TimePartition;
import com.hedera.mirror.importer.db.TimePartitionService;
import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import jakarta.inject.Named;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import org.apache.commons.lang3.BooleanUtils;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionOperations;

@Named
@Profile("v2")
public class TopicMessageLookupMigration extends AsyncJavaMigration<String> {

    private static final int BATCH_SIZE = 10_000;
    private static final String GET_SHARD_COUNT_SQL =
            """
            select shardid as shard_id, result as count
            from run_command_on_shards(
              '%s',
              $cmd$
              select reltuples::bigint
              from pg_class
              where relname::text = '%%s'
              $cmd$)
            """;
    private static final String GET_TOPIC_SHARD_SQL =
            """
            select id as topic_id, get_shard_id_for_distribution_column('topic_message', id) as shard_id
            from entity
            where type = 'TOPIC'
            """;
    private static final String GET_TOPIC_STAT_SQL =
            """
            select jsonb_object_agg(shardid, result::jsonb)
            from run_command_on_shards(
              '%s',
              $cmd$
              with most_common_topic as (
                select
                  unnest(most_common_vals::text::bigint[]) as topic_id,
                  unnest(most_common_freqs) as freq
                from pg_stats
                where tablename::text = '%%s' and attname = 'topic_id'
              )
              select coalesce(jsonb_agg(jsonb_build_object('topicId', topic_id, 'freq', freq)), '[]'::jsonb)
              from most_common_topic
              $cmd$)
            """;
    private static final String INSERT_BATCH_TOPIC_MESSAGE_LOOKUP_SQL =
            """
            with sequence_number as (
              select
                int8range(min(sequence_number), max(sequence_number), '[]') as sequence_number_range,
                topic_id
              from %1$s
              where topic_id = any(?)
              group by topic_id
            ), timestamp as (
              select
                int8range(min(consensus_timestamp), max(consensus_timestamp), '[]') as timestamp_range,
                topic_id
              from %1$s
              where topic_id = any(?)
              group by topic_id
            )
            insert into topic_message_lookup (partition, sequence_number_range, timestamp_range, topic_id)
            select
              '%1$s',
              sn.sequence_number_range,
              ts.timestamp_range,
              sn.topic_id
            from sequence_number as sn
            join timestamp as ts using (topic_id)
            """;
    private static final String INSERT_SINGLE_TOPIC_MESSAGE_LOOKUP_SQL =
            """
            with lookup as (
              select
                int8range(min(sequence_number), max(sequence_number), '[]') as sequence_number_range,
                int8range(min(consensus_timestamp), max(consensus_timestamp), '[]') as timestamp_range
              from %1$s
              where topic_id = :id
            )
            insert into topic_message_lookup (partition, sequence_number_range, timestamp_range, topic_id)
            select
              '%1$s',
              sequence_number_range,
              timestamp_range,
              :id
            from lookup
            where sequence_number_range <> '(,)'::int8range
            """;
    private static final String PARTITION_NEEDS_MIGRATION_SQL =
            """
                    select
                      not exists(select 1 from topic_message_lookup where partition = ? limit 1) and
                      exists(select 1 from %s limit 1)
                    """;
    private static final RowMapper<ShardCount> SHARD_COUNT_ROW_MAPPER = new DataClassRowMapper<>(ShardCount.class);
    private static final String TOPIC_MESSAGE_TABLE_NAME = "topic_message";
    private static final long TOPIC_MESSAGE_THRESHOLD = 1_000_000L;
    private static final RowMapper<TopicShard> TOPIC_SHARD_ROW_MAPPER = new DataClassRowMapper<>(TopicShard.class);
    private static final TypeReference<Map<Long, List<TopicStat>>> TOPIC_STAT_MAP_TYPE = new TypeReference<>() {};

    private final EntityProperties entityProperties;
    private final JdbcTemplate jdbcTemplate;
    private final List<String> partitions = new LinkedList<>();
    private final ObjectMapper objectMapper;
    private final RecordFileRepository recordFileRepository;
    private final TimePartitionService timePartitionService;

    @Getter
    private final TransactionOperations transactionOperations;

    private Collection<Set<Long>> topicByShard;

    @Lazy
    @SuppressWarnings("java:S107")
    protected TopicMessageLookupMigration(
            EntityProperties entityProperties,
            JdbcTemplate jdbcTemplate,
            ImporterProperties importerProperties,
            ObjectMapper objectMapper,
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
        this.objectMapper = objectMapper;
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

        topicByShard = jdbcTemplate.query(GET_TOPIC_SHARD_SQL, TOPIC_SHARD_ROW_MAPPER).stream()
                .collect(Collectors.groupingBy(TopicShard::shardId, mapping(TopicShard::topicId, toSet())))
                .values();

        log.info("Migrating topic_message_lookup for partition {} synchronously", activePartition);
        getTransactionOperations().executeWithoutResult(status -> migratePartition(activePartition));

        return !partitions.isEmpty();
    }

    @Override
    public String getDescription() {
        return "Backfill topic_message_lookup table";
    }

    @Override
    protected String getInitial() {
        return partitions.isEmpty() ? null : partitions.removeLast();
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        // The version where topic_message_lookup table was added
        return MigrationVersion.fromVersion("1.80.1");
    }

    @Override
    protected Optional<String> migratePartial(String partitionName) {
        migratePartition(partitionName);
        return Optional.ofNullable(getInitial());
    }

    private Set<Long> getTopTopics(String partitionName) {
        var sql = String.format(GET_SHARD_COUNT_SQL, partitionName);
        var shardCount = jdbcTemplate.query(sql, SHARD_COUNT_ROW_MAPPER).stream()
                .collect(Collectors.toMap(ShardCount::shardId, ShardCount::count));

        sql = String.format(GET_TOPIC_STAT_SQL, partitionName);
        var topicStats = Objects.requireNonNull(jdbcTemplate.query(sql, rs -> {
            try {
                rs.next();
                return objectMapper.readValue(rs.getString(1), TOPIC_STAT_MAP_TYPE);
            } catch (JsonProcessingException e) {
                throw new ParserException(e);
            }
        }));

        var topTopics = new HashSet<Long>();
        for (var entry : topicStats.entrySet()) {
            long count = shardCount.getOrDefault(entry.getKey(), 0L);
            if (count < TOPIC_MESSAGE_THRESHOLD) {
                continue;
            }

            for (var topicStat : entry.getValue()) {
                if (count * topicStat.freq >= TOPIC_MESSAGE_THRESHOLD) {
                    topTopics.add(topicStat.topicId());
                }
            }
        }

        return topTopics;
    }

    private void migratePartition(String partitionName) {
        var stopwatch = Stopwatch.createStarted();

        var sql = String.format(PARTITION_NEEDS_MIGRATION_SQL, partitionName);
        var needsMigration = jdbcTemplate.queryForObject(sql, Boolean.class, partitionName);
        if (BooleanUtils.isFalse(needsMigration)) {
            log.info("Partition {} doesn't need migration", partitionName);
            return;
        }

        var topTopics = getTopTopics(partitionName);
        int count = migrateBatch(partitionName, topTopics) + migrateSingle(partitionName, topTopics);

        log.info("Migrated topic_message_lookup with {} rows for partition {} in {}", count, partitionName, stopwatch);
    }

    private int migrateBatch(String partitionName, Set<Long> topTopics) {
        int count = 0;
        var sql = String.format(INSERT_BATCH_TOPIC_MESSAGE_LOOKUP_SQL, partitionName);

        for (var topicShard : topicByShard) {
            if (topicShard.isEmpty()) {
                continue;
            }

            var batches = Lists.partition(Lists.newArrayList(Sets.difference(topicShard, topTopics)), BATCH_SIZE);
            for (var batch : batches) {
                count += jdbcTemplate.update(sql, ps -> {
                    var topicArray = ps.getConnection().createArrayOf("BIGINT", batch.toArray());
                    ps.setArray(1, topicArray);
                    ps.setArray(2, topicArray);
                });
            }
        }

        return count;
    }

    private int migrateSingle(String partitionName, Set<Long> topTopics) {
        int count = 0;
        var sql = String.format(INSERT_SINGLE_TOPIC_MESSAGE_LOOKUP_SQL, partitionName);

        for (var topicId : topTopics) {
            var paramSource = new MapSqlParameterSource("id", topicId);
            count += namedParameterJdbcTemplate.update(sql, paramSource);
        }

        return count;
    }

    private record ShardCount(long shardId, long count) {}

    private record TopicShard(long shardId, long topicId) {}

    private record TopicStat(long topicId, float freq) {}
}
