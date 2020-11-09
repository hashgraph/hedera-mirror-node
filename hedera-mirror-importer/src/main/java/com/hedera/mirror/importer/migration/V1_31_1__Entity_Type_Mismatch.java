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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Named;
import javax.sql.DataSource;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.Nullable;
import org.springframework.transaction.annotation.Transactional;

import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.exception.MigrationSQLException;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;
import com.hedera.mirror.importer.util.Utility;

@Log4j2
@Named
public class V1_31_1__Entity_Type_Mismatch extends BaseJavaMigration {
    private final EntityRepository entityRepository;
    private final TransactionRepository transactionRepository;
    private final DataSource dataSource;
    private final FlywayMigrationProperties flywayMigrationProperties;
    private JdbcTemplate jdbcTemplate;

    private final String TRANSACTION_MAX_ENTITY_ID_SQL = "select max(entity_id) from transaction where entity_id is " +
            "not null";
    private final String CREATED_ENTITIES_TRANSACTION_SQL = "select  e.id, e.fk_entity_type_id, t.type, " +
            "t.consensus_ns from t_entities e join transaction t on (e.id = t.entity_id and t.result = 22 and " +
            "t.type in (8,11,17,24,29)) where e.id < ? and t.consensus_ns < ? order by id desc, consensus_ns desc " +
            "limit ?";
    private final String ENTITIES_TYPE_UPDATE_SQL = "update t_entities set fk_entity_type_id = ? where id = ?";
    private final String ENTITY_MISMATCH_COUNT_SQL = "select count(*) from t_entities e join transaction t" +
            " on (e.id = t.entity_id and t.result = 22 and t.type = ? and e.fk_entity_type_id <> ?)";
    private final String DROP_TEMP_ENTITIES_SQL = "drop table if exists t_entities_archive";

    AtomicLong entityIdCap;
    AtomicLong timestampCap;
    AtomicLong entityTransactionCount;
    AtomicLong entityTransactionMismatchCount;

    public V1_31_1__Entity_Type_Mismatch(@Lazy EntityRepository entityRepository,
                                         @Lazy TransactionRepository transactionRepository, DataSource dataSource,
                                         FlywayMigrationProperties flywayMigrationProperties) {
        this.entityRepository = entityRepository;
        this.transactionRepository = transactionRepository;
        this.dataSource = dataSource;
        this.flywayMigrationProperties = flywayMigrationProperties;
    }

    @Override
    public void migrate(Context context) throws Exception {
        Stopwatch stopwatch = Stopwatch.createStarted();
        jdbcTemplate = new JdbcTemplate(dataSource);

        // retrieve max entityId value witness by transactions table.
        Long maxEntityId = getMaxEntityId();
        if (maxEntityId == null) {
            log.info("Empty transactions table. Skipping migration.");
            return;
        }

        entityIdCap = new AtomicLong(maxEntityId);
        Instant now = Instant.now();
        timestampCap = new AtomicLong(Utility.convertToNanosMax(now.getEpochSecond(), now.getNano()));
        entityTransactionCount = new AtomicLong(0);
        entityTransactionMismatchCount = new AtomicLong(0);

        // batch retrieve entities whose entity type does not match the type noted in the appropriate create
        // transactions
        // batch update retrieved entities and pull next set until no type mismatched entities are retrieved
        // entity id and transaction timestamp are used to optimally search through tables
        List<EntityIdType> entityIdTypeList = getEntityIdTypes(entityIdCap.get() + 1, timestampCap
                .get() + 1, flywayMigrationProperties.getEntityMismatchReadPageSize());
        while (entityIdTypeList != null) {
            if (!entityIdTypeList.isEmpty()) {
                batchUpdate(entityIdTypeList);
            }

            entityIdTypeList = getEntityIdTypes(entityIdCap.get(), timestampCap.get(), flywayMigrationProperties
                    .getEntityMismatchReadPageSize());
        }

        log.info("Entity mismatch correction completed in {} s. {} total entities, {} mismatches encountered",
                stopwatch.elapsed(TimeUnit.SECONDS), entityTransactionCount
                        .get(), entityTransactionMismatchCount.get());

        verifyNoEntityMismatchesExist();

        // drop temp table
        jdbcTemplate.execute(DROP_TEMP_ENTITIES_SQL);

        log.info("Migration processed in {} s.", stopwatch.elapsed(TimeUnit.SECONDS));
    }

    /**
     * Retrieves max entityId found from all transactions.
     *
     * @return max entity id
     * @throws SQLException
     */
    private Long getMaxEntityId() throws SQLException {
        log.debug("Retrieve max entityId from transaction table");
        Long maxEntityId = jdbcTemplate.queryForObject(TRANSACTION_MAX_ENTITY_ID_SQL, Long.class);

        log.info("Retrieved max EntityId {} from transaction table", maxEntityId);
        return maxEntityId;
    }

    /**
     * Gets the numbers of entity type mismatches found for a specific type of entity
     *
     * @param args
     * @return
     */
    private Long getMismatchCount(@Nullable Object... args) {
        Long mismatchCount = jdbcTemplate.queryForObject(ENTITY_MISMATCH_COUNT_SQL, Long.class, args);

        log.trace("Retrieved {} mismatched entities", mismatchCount);
        return mismatchCount;
    }

    /**
     * Retrieves a list of EntityIdType objects that represent mismatches found between the entity type in t_entities
     * and transactions table
     *
     * @param entityId
     * @param consensusTimestamp
     * @param pageSize
     * @return
     * @throws SQLException
     */
    private List<EntityIdType> getEntityIdTypes(long entityId, long consensusTimestamp, int pageSize) throws SQLException {
        log.info("Retrieve entityIdTypes for create transactions below entityId {} and before timestamp {} with page " +
                "size {}", entityId, consensusTimestamp, pageSize);
        List<EntityIdType> entityIdTypes = jdbcTemplate.query(
                CREATED_ENTITIES_TRANSACTION_SQL,
                new Object[] {entityId, consensusTimestamp, pageSize},
                new RowMapper<>() {
                    @Override
                    public EntityIdType mapRow(ResultSet rs, int rowNum) throws SQLException {
                        return getTypeMismatchedEntity(rs);
                    }
                });

        if (entityIdTypes.isEmpty()) {
            // no more rows to consider, return null
            return null;
        }

        // remove nulls
        entityIdTypes.removeAll(Collections.singleton(null));

        log.debug("Retrieved {} entities with mismatch type from t_entities and transaction join", entityIdTypes
                .size());
        return entityIdTypes;
    }

    /**
     * Batch update entities with correct fk_entity_type_id
     *
     * @param entityIdTypes List of mismatched entities
     * @return
     */
    @Transactional
    public int[][] batchUpdate(List<EntityIdType> entityIdTypes) {
        log.trace("batchUpdate {} entities ", entityIdTypes.size());
        return jdbcTemplate.batchUpdate(
                ENTITIES_TYPE_UPDATE_SQL,
                entityIdTypes,
                flywayMigrationProperties.getEntityMismatchWriteBatchSize(),
                new ParameterizedPreparedStatementSetter<>() {
                    @Override
                    public void setValues(PreparedStatement ps, EntityIdType entityIdType) throws SQLException {
                        long id = entityIdType.entityId;
                        ps.setLong(1, entityIdType.correctedEntityTypeId);
                        ps.setLong(2, id);

                        // update filter counters
                        entityIdCap.set(id);
                        timestampCap.set(entityIdType.consensusTimestamp);
                    }
                }
        );
    }

    /**
     * Retrieve the correct entityType number based on comparison between expected and current values When matched
     * return 0 to signal equality, when mismatched return expectedType
     *
     * @param expectedEntityType
     * @param currentEntityType
     * @return
     */
    private int getCorrectedEntityType(EntityTypeEnum expectedEntityType, int currentEntityType) {
        // check if EntityTypeEnum matches given currentEntityType.
        // Return 0 on match otherwise return expected EntityTypeEnum id
        if (expectedEntityType.getId() == currentEntityType) {
            return 0;
        }

        return expectedEntityType.getId();
    }

    /***
     * Get an EntityIdType object that represents a type mismatch of the result of t_entities and transaction table join
     * If entities object has no mismatch return null.
     * @param rs
     * @return EntityIdType object
     * @throws SQLException
     */
    private EntityIdType getTypeMismatchedEntity(ResultSet rs) throws SQLException {
        int originalEntityType = rs.getInt("fk_entity_type_id");
        int transactionType = rs.getInt("type");
        long entityId = rs.getLong("id");
        long consensusTimestamp = rs.getLong("consensus_ns");
        int correctedEntityType = 0;
        entityTransactionCount.incrementAndGet();

        // update filter counters
        entityIdCap.set(entityId);
        timestampCap.set(consensusTimestamp);

        // for each create transaction, verify expected entity type is matched in entity object.
        // If so exit early, if not create EntityIdType with subset of correct entity properties
        if (transactionType == TransactionTypeEnum.CRYPTOCREATEACCOUNT.getProtoId()) {
            correctedEntityType = getCorrectedEntityType(EntityTypeEnum.ACCOUNT, originalEntityType);
        } else if (transactionType == TransactionTypeEnum.CONTRACTCREATEINSTANCE.getProtoId()) {
            correctedEntityType = getCorrectedEntityType(EntityTypeEnum.CONTRACT, originalEntityType);
        } else if (transactionType == TransactionTypeEnum.FILECREATE.getProtoId()) {
            correctedEntityType = getCorrectedEntityType(EntityTypeEnum.FILE, originalEntityType);
        } else if (transactionType == TransactionTypeEnum.CONSENSUSCREATETOPIC.getProtoId()) {
            correctedEntityType = getCorrectedEntityType(EntityTypeEnum.TOPIC, originalEntityType);
        } else if (transactionType == TransactionTypeEnum.TOKENCREATION.getProtoId()) {
            correctedEntityType = getCorrectedEntityType(EntityTypeEnum.TOKEN, originalEntityType);
        }

        if (correctedEntityType == 0) {
            // no mismatch on entity, return null
            return null;
        }

        EntityIdType entityIdType = new EntityIdType(consensusTimestamp, correctedEntityType, entityId,
                originalEntityType, transactionType);
        entityTransactionMismatchCount.incrementAndGet();
        log.info("Entity type mismatch encountered: {}", transactionType);
        return entityIdType;
    }

    /**
     * Confirm no type mismatches exist on accounts, contracts, files, topics and tokens entities
     *
     * @throws SQLException
     */
    private void verifyNoEntityMismatchesExist() throws MigrationSQLException {
        log.info("Verifying no further entity mismatches exist for accounts, contracts, files, topics and tokens ...");
        Long accountMismatchCount = getMismatchCount(TransactionTypeEnum.CRYPTOCREATEACCOUNT
                .getProtoId(), EntityTypeEnum.ACCOUNT.getId());
        if (accountMismatchCount > 0) {
            throw new MigrationSQLException(accountMismatchCount + " Account type mismatches still remain");
        }

        Long contractMismatchCount = getMismatchCount(TransactionTypeEnum.CONTRACTCREATEINSTANCE
                .getProtoId(), EntityTypeEnum.CONTRACT.getId());
        if (contractMismatchCount > 0) {
            throw new MigrationSQLException(contractMismatchCount + " Contract type mismatches still remain");
        }

        Long fileMismatchCount = getMismatchCount(TransactionTypeEnum.FILECREATE
                .getProtoId(), EntityTypeEnum.FILE.getId());
        if (fileMismatchCount > 0) {
            throw new MigrationSQLException(fileMismatchCount + " Fie type mismatches still remain");
        }

        Long topicMismatchCount = getMismatchCount(TransactionTypeEnum.CONSENSUSCREATETOPIC
                .getProtoId(), EntityTypeEnum.TOPIC.getId());
        if (topicMismatchCount > 0) {
            throw new MigrationSQLException(topicMismatchCount + " Topic type mismatches still remain");
        }

        Long tokenMismatchCount = getMismatchCount(TransactionTypeEnum.TOKENCREATION
                .getProtoId(), EntityTypeEnum.TOKEN.getId());
        if (tokenMismatchCount > 0) {
            throw new MigrationSQLException(tokenMismatchCount + " Token type mismatches still remain");
        }
    }

    @Data
    // Custom Subset of on Entities object with corresponding consensusTimestamp of create transaction
    private class EntityIdType {
        private final long consensusTimestamp;
        private final long correctedEntityTypeId;
        private final long entityId;
        private final long initialEntityTypeId;
        private final long transactionType;
    }
}
