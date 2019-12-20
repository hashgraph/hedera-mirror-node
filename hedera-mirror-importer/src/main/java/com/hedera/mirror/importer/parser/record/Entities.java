package com.hedera.mirror.importer.parser.record;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.DateTimeException;
import java.util.HashMap;

import com.hederahashgraph.api.proto.java.TopicID;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.importer.util.Utility;

import static java.sql.Types.*;

@Log4j2
public class Entities {
    private static int FK_ACCOUNT = 0;
    private static int FK_CONTRACT = 0;
    private static int FK_FILE = 0;
    private static int FK_TOPIC = 0;
    private static Connection connect = null;
    HashMap<String, Long> entities = new HashMap<>();

    public Entities(Connection connect) throws SQLException {
        Entities.connect = connect;
        if (Entities.connect != null) {
            if (FK_ACCOUNT + FK_CONTRACT + FK_FILE + FK_TOPIC == 0) {
                try (Statement statement = Entities.connect.createStatement()) {
                    try (ResultSet resultSet = statement
                            .executeQuery("SELECT id, name FROM t_entity_types ORDER BY id")) {
                        while (resultSet.next()) {
                            if (resultSet.getString("name").contentEquals("account")) {
                                FK_ACCOUNT = resultSet.getInt("id");
                            } else if (resultSet.getString("name").contentEquals("contract")) {
                                FK_CONTRACT = resultSet.getInt("id");
                            } else if (resultSet.getString("name").contentEquals("file")) {
                                FK_FILE = resultSet.getInt("id");
                            } else if (resultSet.getString("name").contentEquals("topic")) {
                                FK_TOPIC = resultSet.getInt("id");
                            }
                        }
                    }
                }
            }
        }
    }

    private long updateEntity(int fk_entity_type, long shard, long realm, long num, long exp_time_seconds,
                              long exp_time_nanos, long auto_renew_period, byte[] key, long fk_proxy_account_id,
                              byte[] submitKey, long topicValidStartTime, String memo) throws SQLException {
        long entityId = 0;

        if (shard + realm + num == 0) {
            return 0;
        }

        entityId = createOrGetEntity(shard, realm, num, fk_entity_type);

        if ((exp_time_nanos == 0) && (exp_time_seconds == 0) && (auto_renew_period == 0) && (fk_proxy_account_id == 0)
                && (key == null) && (submitKey == null) && (topicValidStartTime == 0) && (memo == null)) {
            // nothing to update
            return entityId;
        }

        // build the SQL to prepare a statement
        String sqlUpdate = "UPDATE t_entities SET ";
        int fieldCount = 0;
        boolean bDoComma = false;

        if ((exp_time_seconds != 0) || (exp_time_nanos != 0)) {
            sqlUpdate += " exp_time_seconds = ?";
            sqlUpdate += ",exp_time_nanos = ?";
            sqlUpdate += ",exp_time_ns = ?";
            bDoComma = true;
        }
        if (auto_renew_period != 0) {
            if (bDoComma) {
                sqlUpdate += ",";
            }
            sqlUpdate += "auto_renew_period = ?";
            bDoComma = true;
        }

        if (key != null) {
            // The key has been specified, thus update this field either to null for non-ED25519 key or the hex value.
            if (bDoComma) {
                sqlUpdate += ",";
            }
            sqlUpdate += "ed25519_public_key_hex = ?";
            bDoComma = true;

            if (bDoComma) {
                sqlUpdate += ",";
            }
            sqlUpdate += "key = ?";
            bDoComma = true;
        }

        if (fk_proxy_account_id != 0) {
            if (bDoComma) {
                sqlUpdate += ",";
            }
            sqlUpdate += "fk_prox_acc_id = ?";
            bDoComma = true;
        }

        if (submitKey != null) {
            if (bDoComma) {
                sqlUpdate += ",";
            }
            sqlUpdate += "submit_key = ?";
            bDoComma = true;
        }

        if (topicValidStartTime != 0) {
            if (bDoComma) {
                sqlUpdate += ",";
            }
            sqlUpdate += "topic_valid_start_time = ?";
        }

        if (memo != null) {
            if (bDoComma) {
                sqlUpdate += ",";
            }
            sqlUpdate += "memo = ?";
            bDoComma = true;
        }

        sqlUpdate += " WHERE entity_shard = ?";
        sqlUpdate += " AND entity_realm = ?";
        sqlUpdate += " AND entity_num = ?";
        sqlUpdate += " RETURNING id";

        // inserts or returns an existing entity
        try (PreparedStatement updateEntity = Entities.connect.prepareStatement(sqlUpdate)) {

            if ((exp_time_seconds != 0) || (exp_time_nanos != 0)) {
                updateEntity.setLong(1, exp_time_seconds);
                updateEntity.setLong(2, exp_time_nanos);
                updateEntity.setLong(3, Utility.convertToNanosMax(exp_time_seconds, exp_time_nanos));
                fieldCount = 3;
            }

            if (auto_renew_period != 0) {
                fieldCount += 1;
                updateEntity.setLong(fieldCount, auto_renew_period);
            }

            if (key != null) {
                fieldCount += 1;
                String ed25519PublicKeyHex = null;
                try {
                    ed25519PublicKeyHex = Utility.protobufKeyToHexIfEd25519OrNull(key);
                } catch (InvalidProtocolBufferException e) {
                    log.error("Invalid ED25519 key could not be translated to hex text for entity {}.{}.{}. Column " +
                                    "will be nulled. {}",
                            shard, realm, num, e);
                }
                if (null != ed25519PublicKeyHex) {
                    updateEntity.setString(fieldCount, ed25519PublicKeyHex);
                } else {
                    updateEntity.setNull(fieldCount, VARCHAR);
                }

                fieldCount += 1;
                updateEntity.setBytes(fieldCount, key);
            }

            if (fk_proxy_account_id != 0) {
                fieldCount += 1;
                updateEntity.setLong(fieldCount, fk_proxy_account_id);
            }

            if (submitKey != null) {
                fieldCount += 1;
                updateEntity.setBytes(fieldCount, submitKey);
            }

            if (topicValidStartTime != 0) {
                fieldCount += 1;
                updateEntity.setLong(fieldCount, topicValidStartTime);
            }

            if (memo != null) {
                fieldCount += 1;
                updateEntity.setString(fieldCount, memo);
            }

            fieldCount += 1;
            updateEntity.setLong(fieldCount, shard);
            fieldCount += 1;
            updateEntity.setLong(fieldCount, realm);
            fieldCount += 1;
            updateEntity.setLong(fieldCount, num);

            updateEntity.execute();

            try (ResultSet newId = updateEntity.getResultSet()) {
                if (newId.next()) {
                    entityId = newId.getLong(1);
                } else {
                    throw new IllegalStateException("Expected entity not found, shard " + shard + ", realm " + realm + ", num " + num);
                }
            }
        }

        return entityId;
    }

    public long updateEntity(FileID fileId, long exp_time_seconds, long exp_time_nanos, long auto_renew_period,
                             byte[] key, long fk_proxy_account_id) throws SQLException {
        return updateEntity(FK_FILE, fileId.getShardNum(), fileId.getRealmNum(), fileId.getFileNum(), exp_time_seconds,
                exp_time_nanos, auto_renew_period, key, fk_proxy_account_id, null, 0, null);
    }

    public long updateEntity(ContractID contractId, long exp_time_seconds, long exp_time_nanos,
                             long auto_renew_period, byte[] key, long fk_proxy_account_id, String memo) throws SQLException {
        // Can't clear memo on contracts. 0 length indicates no change.
        if (memo != null && memo.length() == 0) {
            memo = null;
        }
        return updateEntity(FK_CONTRACT, contractId.getShardNum(), contractId.getRealmNum(), contractId
                        .getContractNum(), exp_time_seconds, exp_time_nanos, auto_renew_period, key,
                fk_proxy_account_id,
                null, 0, memo);
    }

    public long updateEntity(AccountID accountId, long exp_time_seconds, long exp_time_nanos, long auto_renew_period,
                             byte[] key, long fk_proxy_account_id) throws SQLException {
        return updateEntity(FK_ACCOUNT, accountId.getShardNum(), accountId.getRealmNum(), accountId
                        .getAccountNum(), exp_time_seconds, exp_time_nanos, auto_renew_period, key,
                fk_proxy_account_id, null
                , 0, null);
    }

    public long updateEntity(TopicID topicId, long expirationTimeSeconds, long expirationTimeNanos, byte[] adminKey,
                             byte[] submitKey, long validStartTime, String memo) throws SQLException {
        return updateEntity(FK_TOPIC, topicId.getShardNum(), topicId.getRealmNum(), topicId
                        .getTopicNum(), expirationTimeSeconds, expirationTimeNanos, 0, adminKey, 0, submitKey,
                validStartTime
                , memo);
    }

    private long deleteEntity(int fk_entity_type, long shard, long realm, long num) throws SQLException {

        long entityId = 0;

        if (shard + realm + num == 0) {
            return 0;
        }

        entityId = createOrGetEntity(shard, realm, num, fk_entity_type);

        // build the SQL to prepare a statement
        String sqlDelete = "UPDATE t_entities SET deleted = true";
        sqlDelete += " WHERE entity_shard = ?";
        sqlDelete += " AND entity_realm = ?";
        sqlDelete += " AND entity_num = ?";
        sqlDelete += " RETURNING id";

        // inserts or returns an existing entity
        try (PreparedStatement deleteEntity = Entities.connect.prepareStatement(sqlDelete)) {

            deleteEntity.setLong(1, shard);
            deleteEntity.setLong(2, realm);
            deleteEntity.setLong(3, num);

            deleteEntity.execute();

            try (ResultSet newId = deleteEntity.getResultSet()) {
                if (newId.next()) {
                    entityId = newId.getLong(1);
                } else {
                    throw new IllegalStateException("Expected entity not found, shard " + shard + ", realm " + realm + ", num " + num);
                }
            }
        }

        return entityId;
    }

    public long deleteEntity(FileID fileId) throws SQLException {
        return deleteEntity(FK_FILE, fileId.getShardNum(), fileId.getRealmNum(), fileId.getFileNum());
    }

    public long deleteEntity(ContractID contractId) throws SQLException {
        return deleteEntity(FK_CONTRACT, contractId.getShardNum(), contractId.getRealmNum(), contractId
                .getContractNum());
    }

    public long deleteEntity(AccountID accountId) throws SQLException {
        return deleteEntity(FK_ACCOUNT, accountId.getShardNum(), accountId.getRealmNum(), accountId.getAccountNum());
    }

    public long deleteEntity(TopicID topicId) throws SQLException {
        return deleteEntity(FK_TOPIC, topicId.getShardNum(), topicId.getRealmNum(), topicId.getTopicNum());
    }

    private long unDeleteEntity(int fk_entity_type, long shard, long realm, long num) throws SQLException {

        long entityId = 0;

        if (shard + realm + num == 0) {
            return 0;
        }

        entityId = createOrGetEntity(shard, realm, num, fk_entity_type);

        // build the SQL to prepare a statement
        String sqlDelete = "UPDATE t_entities SET deleted = false";
        sqlDelete += " WHERE entity_shard = ?";
        sqlDelete += " AND entity_realm = ?";
        sqlDelete += " AND entity_num = ?";
        sqlDelete += " RETURNING id";

        // inserts or returns an existing entity
        try (PreparedStatement deleteEntity = Entities.connect.prepareStatement(sqlDelete)) {

            deleteEntity.setLong(1, shard);
            deleteEntity.setLong(2, realm);
            deleteEntity.setLong(3, num);

            deleteEntity.execute();

            try (ResultSet newId = deleteEntity.getResultSet()) {
                if (newId.next()) {
                    entityId = newId.getLong(1);
                } else {
                    throw new IllegalStateException("Expected entity not found, shard " + shard + ", realm " + realm + ", num " + num);
                }
            }
        }

        return entityId;
    }

    public long unDeleteEntity(FileID fileId) throws SQLException {
        return unDeleteEntity(FK_FILE, fileId.getShardNum(), fileId.getRealmNum(), fileId.getFileNum());
    }

    public long unDeleteEntity(ContractID contractId) throws SQLException {
        return unDeleteEntity(FK_CONTRACT, contractId.getShardNum(), contractId.getRealmNum(), contractId
                .getContractNum());
    }

    public long unDeleteEntity(AccountID accountId) throws SQLException {
        return unDeleteEntity(FK_ACCOUNT, accountId.getShardNum(), accountId.getRealmNum(), accountId.getAccountNum());
    }

    private long createEntity(long shard, long realm, long num, long exp_time_seconds, long exp_time_nanos,
                              long auto_renew_period, byte[] key, long fk_proxy_account_id, int fk_entity_type,
                              byte[] submitKey, long topicValidStartTime, String memo)
            throws SQLException {

        long entityId = getCachedEntityId(shard, realm, num);
        if (entityId != -1) {
            return entityId;
        }

        try (CallableStatement entityCreate = connect
                .prepareCall("{? = call f_entity_create ( ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? ) }")) {
            entityCreate.registerOutParameter(1, Types.BIGINT);
            entityCreate.setLong(2, shard);
            entityCreate.setLong(3, realm);
            entityCreate.setLong(4, num);
            entityCreate.setInt(5, fk_entity_type);
            entityCreate.setLong(6, exp_time_seconds);
            entityCreate.setLong(7, exp_time_nanos);
            entityCreate.setLong(8, Utility.convertToNanosMax(exp_time_seconds, exp_time_nanos));
            entityCreate.setLong(9, auto_renew_period);

            if (key == null) {
                entityCreate.setNull(10, VARCHAR);
                entityCreate.setNull(11, VARBINARY);
            } else {
                String ed25519PublicKeyHex = null;
                try {
                    ed25519PublicKeyHex = Utility.protobufKeyToHexIfEd25519OrNull(key);
                } catch (InvalidProtocolBufferException e) {
                    log.error("Invalid ED25519 key could not be translated to hex text for entity {}.{}.{}. Column " +
                                    "will be nulled. {}",
                            shard, realm, num, e);
                }
                if (null != ed25519PublicKeyHex) {
                    entityCreate.setString(10, ed25519PublicKeyHex);
                } else {
                    entityCreate.setNull(10, VARCHAR);
                }
                entityCreate.setBytes(11, key);
            }

            entityCreate.setLong(12, fk_proxy_account_id);

            if (null == submitKey) {
                entityCreate.setNull(13, VARBINARY);
            } else {
                entityCreate.setBytes(13, submitKey);
            }

            entityCreate.setLong(14, topicValidStartTime);
            if (null == memo) {
                entityCreate.setNull(15, VARCHAR);
            } else {
                entityCreate.setString(15, memo);
            }

            entityCreate.execute();
            entityId = entityCreate.getLong(1);
        }

        return entityId;
    }

    public long createEntity(FileID fileId, long exp_time_seconds, long exp_time_nanos, long auto_renew_period,
                             byte[] key, long fk_proxy_account_id) throws SQLException {
        return createEntity(fileId.getShardNum(), fileId.getRealmNum(), fileId.getFileNum(), exp_time_seconds,
                exp_time_nanos, auto_renew_period, key, fk_proxy_account_id, FK_FILE, null, 0, null);
    }

    public long createEntity(ContractID contractId, long exp_time_seconds, long exp_time_nanos,
                             long auto_renew_period, byte[] key, long fk_proxy_account_id, String memo)
            throws SQLException {
        return createEntity(contractId.getShardNum(), contractId.getRealmNum(), contractId.getContractNum(),
                exp_time_seconds, exp_time_nanos, auto_renew_period, key, fk_proxy_account_id, FK_CONTRACT, null, 0,
                memo);
    }

    public long createEntity(AccountID accountId, long exp_time_seconds, long exp_time_nanos, long auto_renew_period,
                             byte[] key, long fk_proxy_account_id) throws SQLException {
        return createEntity(accountId.getShardNum(), accountId.getRealmNum(), accountId.getAccountNum(),
                exp_time_seconds, exp_time_nanos, auto_renew_period, key, fk_proxy_account_id, FK_ACCOUNT, null, 0,
                null);
    }

    public long createEntity(TopicID topicId, long expTimeSeconds, long expTimeNanos, byte[] adminKey, byte[] submitKey,
                             long topicValidStartTime, String memo) throws SQLException {
        return createEntity(topicId.getShardNum(), topicId.getRealmNum(), topicId.getTopicNum(), expTimeSeconds,
                expTimeNanos, 0, adminKey, 0, FK_TOPIC, submitKey, topicValidStartTime, memo);
    }

    private long createOrGetEntity(long shard, long realm, long num, int fk_entity_type) throws SQLException {

        long entityId = getCachedEntityId(shard, realm, num);
        if (entityId != -1) {
            return entityId;
        }

        try (CallableStatement entityCreate = connect
                .prepareCall("{? = call f_entity_create ( ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) }")) {
            entityCreate.registerOutParameter(1, Types.BIGINT);
            entityCreate.setLong(2, shard);
            entityCreate.setLong(3, realm);
            entityCreate.setLong(4, num);
            entityCreate.setInt(5, fk_entity_type);
            entityCreate.setLong(6, 0);
            entityCreate.setLong(7, 0);
            entityCreate.setLong(8, 0);
            entityCreate.setLong(9, 0);
            entityCreate.setNull(10, VARCHAR);
            entityCreate.setNull(11, VARBINARY);
            entityCreate.setLong(12, 0);
            entityCreate.setNull(13, VARBINARY);
            entityCreate.setLong(14, 0);
            entityCreate.setNull(15, VARCHAR);

            entityCreate.execute();
            entityId = entityCreate.getLong(1);
        }

        String entity = shard + "-" + realm + "-" + num;
        entities.put(entity, entityId);
        return entityId;
    }

    public long createOrGetEntity(FileID fileId) throws SQLException {
        return createOrGetEntity(fileId.getShardNum(), fileId.getRealmNum(), fileId.getFileNum(), FK_FILE);
    }

    public long createOrGetEntity(ContractID contractId) throws SQLException {
        return createOrGetEntity(contractId.getShardNum(), contractId.getRealmNum(), contractId
                .getContractNum(), FK_CONTRACT);
    }

    public long createOrGetEntity(AccountID accountId) throws SQLException {
        return createOrGetEntity(accountId.getShardNum(), accountId.getRealmNum(), accountId
                .getAccountNum(), FK_ACCOUNT);
    }

    public long createOrGetEntity(TopicID topicId) throws SQLException {
        return createOrGetEntity(topicId.getShardNum(), topicId.getRealmNum(), topicId.getTopicNum(), FK_TOPIC);
    }

    private long getCachedEntityId(long shard, long realm, long num) {
        String entity = shard + "-" + realm + "-" + num;

        if (shard + realm + num == 0) {
            return 0;
        } else if (entities.containsKey(entity)) {
            return entities.get(entity);
        } else {
            return -1;
        }
    }
}
