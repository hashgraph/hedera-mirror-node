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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Named;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;

import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.exception.MigrationSQLException;
import com.hedera.mirror.importer.util.Utility;

@Log4j2
@Named
public class V1_31_2__Entity_Type_Mismatch extends BaseJavaMigration {
    private final FlywayMigrationProperties flywayMigrationProperties;
    private final JdbcTemplate jdbcTemplate;

    private final String TRANSACTION_MAX_ENTITY_ID_SQL = "select max(entity_id) from transaction where entity_id is " +
            "not null";
    // where clause used by count that captures correct entityType to transactionType mapping
    private final String ENTITY_MISMATCH_WHERE_CLAUSE_SQL = "t.result = 22 and ((t.type = 11 and  e.fk_entity_type_id" +
            " <> 1) or (t.type = 8 and e.fk_entity_type_id <> 2) or (t.type = 17 and e.fk_entity_type_id <> 3) or (t" +
            ".type = 24 and e.fk_entity_type_id <> 4) or (t.type = 29 and e.fk_entity_type_id <> 5))";
    private final String ENTITY_TYPE_MISMATCH_COUNT_SQL = "select e.fk_entity_type_id, t.type, count(*) from " +
            "t_entities e join transaction t on e.id = t.entity_id where " + ENTITY_MISMATCH_WHERE_CLAUSE_SQL +
            " group by e.fk_entity_type_id, t.type having count(*) > 0";
    private final String ENTITY_TYPE_MISMATCH_SEARCH_SQL = "select  e.id, e.fk_entity_type_id, t.type, " +
            "t.consensus_ns from t_entities e join transaction t on e.id = t.entity_id  where e.id < ? and t" +
            ".consensus_ns < ? and t.result = 22 and t.type in (8,11,17,24,29) order by id desc, consensus_ns desc " +
            "limit ?";
    private final String ENTITY_TYPE_UPDATE_SQL = "update t_entities set fk_entity_type_id = ? where id = ?";

    AtomicLong entityIdCap;
    AtomicLong timestampCap;
    AtomicLong entityTransactionCount;
    AtomicLong entityTransactionMismatchCount;

    public V1_31_2__Entity_Type_Mismatch(@Lazy JdbcTemplate jdbcTemplate,
                                         FlywayMigrationProperties flywayMigrationProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.flywayMigrationProperties = flywayMigrationProperties;
    }

    @Override
    public void migrate(Context context) throws Exception {
        Stopwatch stopwatch = Stopwatch.createStarted();

        // retrieve max entityId value witness by transactions table.
        Long maxEntityId = getMaxEntityId();
        if (maxEntityId == null) {
            log.info("Empty transactions table. Skipping migration.");
            return;
        }

        int entityMismatch = getMismatchCount();
        if (entityMismatch == 0) {
            log.info("No entity mismatches. Skipping migration.");
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
        List<TypeMismatchedEntity> typeMismatchedEntityList = getEntityIdTypes(entityIdCap.get() + 1, timestampCap
                .get(), flywayMigrationProperties.getEntityMismatchReadPageSize());
        while (typeMismatchedEntityList != null) {
            if (!typeMismatchedEntityList.isEmpty()) {
                batchUpdate(typeMismatchedEntityList);
            }

            typeMismatchedEntityList = getEntityIdTypes(entityIdCap.get(), timestampCap.get(), flywayMigrationProperties
                    .getEntityMismatchReadPageSize());
        }

        log.info("Entity mismatch correction completed in {}. {} total entities, {} mismatches encountered",
                stopwatch, entityTransactionCount
                        .get(), entityTransactionMismatchCount.get());

        verifyNoEntityMismatchesExist();

        log.info("Migration processed in {}.", stopwatch);
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
     * @return
     */
    private int getMismatchCount() {
        AtomicInteger mismatchCount = new AtomicInteger(0);
        jdbcTemplate.query(
                ENTITY_TYPE_MISMATCH_COUNT_SQL,
                new RowMapper<>() {
                    @Override
                    public Object mapRow(ResultSet rs, int rowNum) throws SQLException {
                        int count = rs.getInt("count");
                        if (count > 0) {
                            log.info("{} mismatched entities found of entity type {}, with transactionType {}",
                                    count, rs.getInt("fk_entity_type_id"), rs.getInt("type"));
                        }

                        mismatchCount.addAndGet(count);
                        return null;
                    }
                });

        log.debug("Retrieved {} mismatched entities", mismatchCount);
        return mismatchCount.get();
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
    private List<TypeMismatchedEntity> getEntityIdTypes(long entityId, long consensusTimestamp, int pageSize) throws SQLException {
        log.info("Retrieve entityIdTypes for create transactions below entityId {} and before timestamp {} with page " +
                "size {}", entityId, consensusTimestamp, pageSize);
        List<TypeMismatchedEntity> typeMismatchedEntities = jdbcTemplate.query(
                ENTITY_TYPE_MISMATCH_SEARCH_SQL,
                new Object[] {entityId, consensusTimestamp, pageSize},
                new RowMapper<>() {
                    @Override
                    public TypeMismatchedEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
                        return getTypeMismatchedEntity(rs);
                    }
                });

        if (typeMismatchedEntities.isEmpty()) {
            // no more rows to consider, return null
            log.debug("Retrieved {} entities with mismatch type from t_entities and transaction join",
                    typeMismatchedEntities.size());
            return null;
        }

        // remove nulls
        typeMismatchedEntities.removeAll(Collections.singleton(null));

        log.debug("Retrieved {} entities with mismatch type from t_entities and transaction join",
                typeMismatchedEntities.size());
        return typeMismatchedEntities;
    }

    /**
     * Batch update entities with correct fk_entity_type_id
     *
     * @param typeMismatchedEntities List of mismatched entities
     * @return
     */
    public int[][] batchUpdate(List<TypeMismatchedEntity> typeMismatchedEntities) {
        log.trace("batchUpdate {} entities ", typeMismatchedEntities.size());
        return jdbcTemplate.batchUpdate(
                ENTITY_TYPE_UPDATE_SQL,
                typeMismatchedEntities,
                flywayMigrationProperties.getEntityMismatchWriteBatchSize(),
                new ParameterizedPreparedStatementSetter<>() {
                    @Override
                    public void setValues(PreparedStatement ps, TypeMismatchedEntity typeMismatchedEntity) throws SQLException {
                        long id = typeMismatchedEntity.entityId;
                        ps.setInt(1, typeMismatchedEntity.correctedEntityTypeId);
                        ps.setLong(2, id);
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
        return expectedEntityType.getId() == currentEntityType ? 0 : expectedEntityType.getId();
    }

    /***
     * Get an EntityIdType object that represents a type mismatch of the result of t_entities and transaction table join
     * If entities object has no mismatch return null.
     * @param rs
     * @return EntityIdType object
     * @throws SQLException
     */
    private TypeMismatchedEntity getTypeMismatchedEntity(ResultSet rs) throws SQLException {
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

        TypeMismatchedEntity typeMismatchedEntity = new TypeMismatchedEntity(consensusTimestamp, correctedEntityType,
                entityId,
                originalEntityType, transactionType);
        entityTransactionMismatchCount.incrementAndGet();
        log.info("Entity type mismatch encountered: {}", typeMismatchedEntity);
        return typeMismatchedEntity;
    }

    /**
     * Confirm no type mismatches exist on accounts, contracts, files, topics and tokens entities
     *
     * @throws SQLException
     */
    private void verifyNoEntityMismatchesExist() throws MigrationSQLException {
        log.info("Verifying no further entity mismatches exist for accounts, contracts, files, topics and tokens ...");
        int entityMismatchCount = getMismatchCount();
        if (entityMismatchCount > 0) {
            throw new MigrationSQLException(entityMismatchCount + " Entity type mismatches still remain");
        }
    }

    @Data
    // Custom Subset of a type mismatched Entities object with corresponding consensusTimestamp of create transaction
    private class TypeMismatchedEntity {
        private final long consensusTimestamp;
        private final int correctedEntityTypeId;
        private final long entityId;
        private final int initialEntityTypeId;
        private final int transactionType;
    }
}
