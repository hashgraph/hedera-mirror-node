/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.Node;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.ImporterProperties;
import com.hedera.mirror.importer.parser.record.transactionhandler.NodeTransactionHandler;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import io.hypersistence.utils.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import jakarta.inject.Named;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Named
public class FixNodeTransactionsMigration extends ConfigurableJavaMigration {
    // Version transactions became available
    static final int HAPI_VERSION_56 = 56;

    // Earliest consensus timestamp to consider
    static final long RECORD_FILE_LOWER_BOUND = 1733961600000000000L;

    private static final String DROP_DATA_SQL =
            """
            truncate node;
            truncate node_history;
            """;

    private static final String BOUNDS_SQL =
            """
            select consensus_start
            from record_file
            where hapi_version_minor = :hapiMinorVersion
            and consensus_end >= :recordFileLowerBound
            order by consensus_end asc limit 1;
            """;

    private static final String NODE_TRANSACTIONS_SQL =
            """
            select consensus_timestamp, transaction_bytes, transaction_record_bytes
            from transaction
            where consensus_timestamp >= :startTimestamp and type in (54, 55, 56)
            order by consensus_timestamp asc;
            """;

    private static final String INSERT_SQL =
            """
            insert into %s (node_id, created_timestamp, deleted, admin_key, timestamp_range)
            values (?, ?, ?, ?, ?::int8range);
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectProvider<NodeTransactionHandler> nodeTransactionHandlers;
    private final Map<TransactionType, NodeTransactionHandler> nodeTransactionHandlerMap =
            new EnumMap<>(TransactionType.class);
    private final boolean v2;

    @Lazy
    FixNodeTransactionsMigration(
            Environment environment,
            ObjectProvider<NodeTransactionHandler> nodeTransactionHandlers,
            ImporterProperties importerProperties,
            NamedParameterJdbcTemplate jdbcTemplate) {
        super(importerProperties.getMigration());
        this.jdbcTemplate = jdbcTemplate;
        this.nodeTransactionHandlers = nodeTransactionHandlers;
        this.v2 = environment.acceptsProfiles(Profiles.of("v2"));
    }

    @Override
    protected void doMigrate() throws IOException {
        var nodeRecordItems = getRecordItems();
        if (nodeRecordItems.isEmpty()) {
            log.info("No node transactions to fix. Skipping migration.");

            return;
        }
        var entitiesMap = new HashMap<Long, List<Node>>();

        for (var recordItem : nodeRecordItems) {
            var nodeEntity = recordItemToNode(recordItem);
            var nodeEntities = entitiesMap.computeIfAbsent(nodeEntity.getNodeId(), k -> new ArrayList<>());
            var previous = nodeEntities.isEmpty() ? null : nodeEntities.getLast();
            if (previous != null) {
                previous.setTimestampRange(Range.closedOpen(
                        previous.getTimestampRange().lowerEndpoint(),
                        nodeEntity.getTimestampRange().lowerEndpoint()));

                if (nodeEntity.getAdminKey() == null) {
                    nodeEntity.setAdminKey(previous.getAdminKey());
                }

                if (nodeEntity.getCreatedTimestamp() == null) {
                    nodeEntity.setCreatedTimestamp(previous.getCreatedTimestamp());
                }
            }

            nodeEntities.add(nodeEntity);
        }

        var nodeEntities = entitiesMap.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.groupingBy(node -> node.getTimestampUpper() == null));

        var nonHistoryEntities = nodeEntities.get(true);
        var historyEntities = nodeEntities.getOrDefault(false, List.of());

        var statementSetter = new ParameterizedPreparedStatementSetter<Node>() {

            @Override
            public void setValues(PreparedStatement ps, Node node) throws SQLException {
                ps.setLong(1, node.getNodeId());
                ps.setLong(2, node.getCreatedTimestamp());
                ps.setBoolean(3, node.isDeleted());
                ps.setBytes(4, node.getAdminKey());
                ps.setString(5, PostgreSQLGuavaRangeType.INSTANCE.asString(node.getTimestampRange()));
            }
        };

        jdbcTemplate.getJdbcTemplate().execute(DROP_DATA_SQL);
        jdbcTemplate
                .getJdbcTemplate()
                .batchUpdate(
                        INSERT_SQL.formatted("node"), nonHistoryEntities, nonHistoryEntities.size(), statementSetter);
        jdbcTemplate
                .getJdbcTemplate()
                .batchUpdate(
                        INSERT_SQL.formatted("node_history"), historyEntities, historyEntities.size(), statementSetter);
    }

    private Node recordItemToNode(RecordItem recordItem) {
        var type = TransactionType.of(recordItem.getTransactionType());
        var handler = nodeTransactionHandlerMap.computeIfAbsent(type, t -> nodeTransactionHandlers.stream()
                .filter(h -> h.getType().equals(type))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No handler found for transaction type: " + t)));

        return handler.parseNode(recordItem);
    }

    @SneakyThrows
    private RecordItem toRecordItem(Transaction transaction) {
        var protoTransaction =
                com.hederahashgraph.api.proto.java.Transaction.parseFrom(transaction.getTransactionBytes());
        var protoRecord = TransactionRecord.parseFrom(transaction.getTransactionRecordBytes());
        return RecordItem.builder()
                .transaction(protoTransaction)
                .transactionRecord(protoRecord)
                .build();
    }

    @Override
    public MigrationVersion getVersion() {
        return v2 ? MigrationVersion.fromVersion("2.8.0") : MigrationVersion.fromVersion("1.103.0");
    }

    @Override
    public String getDescription() {
        return "Add missing node information from node transactions";
    }

    private List<RecordItem> getRecordItems() {
        var lowerBound = jdbcTemplate.query(
                BOUNDS_SQL,
                Map.of("hapiMinorVersion", HAPI_VERSION_56, "recordFileLowerBound", RECORD_FILE_LOWER_BOUND),
                new SingleColumnRowMapper<>(Long.class));

        if (lowerBound.isEmpty()) {
            return List.of();
        }

        var params = Map.of("startTimestamp", lowerBound.getFirst());

        return jdbcTemplate.query(NODE_TRANSACTIONS_SQL, params, new DataClassRowMapper<>(Transaction.class)).stream()
                .map(this::toRecordItem)
                .toList();
    }
}
