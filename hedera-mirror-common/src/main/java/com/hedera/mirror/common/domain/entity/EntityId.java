/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.common.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Splitter;
import com.google.common.collect.Range;
import com.hedera.mirror.common.converter.EntityTypeSerializer;
import com.hedera.mirror.common.exception.InvalidEntityException;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import java.io.Serializable;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;

/**
 * Common encapsulation for accountID, fileID, contractID, topicID and tokenID.
 * <p>
 * There is no valid entity in Hedera network with an id '0.0.0'. When AccountID/FileID/ContractID/TopicID/TokenID are
 * not set, their values default to '0.0.0'. If such an unset (default) instance is used to create EntityId using one of
 * the of(..) functions, null is returned.
 */
@Value
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class EntityId implements Serializable, Comparable<EntityId> {

    public static final EntityId EMPTY = new EntityId(0L, 0L, 0L, EntityType.ACCOUNT);

    static final int SHARD_BITS = 15;
    static final int REALM_BITS = 16;
    static final int NUM_BITS = 32;

    private static final long SHARD_MASK = (1L << SHARD_BITS) - 1;
    private static final long REALM_MASK = (1L << REALM_BITS) - 1;
    private static final long NUM_MASK = (1L << NUM_BITS) - 1;
    private static final Comparator<EntityId> COMPARATOR =
            Comparator.nullsFirst(Comparator.comparingLong(EntityId::getId));
    private static final Range<Long> DEFAULT_RANGE = Range.atLeast(0L);
    private static final Splitter SPLITTER = Splitter.on('.').omitEmptyStrings().trimResults();
    private static final long serialVersionUID = 1427649605832330197L;

    // Ignored so not included in json serialization of PubSubMessage
    @JsonIgnore
    @EqualsAndHashCode.Include
    private final Long id;

    private final Long shardNum;
    private final Long realmNum;
    private final Long entityNum;

    @JsonSerialize(using = EntityTypeSerializer.class)
    private final EntityType type;

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private final String cachedString = String.format("%d.%d.%d", shardNum, realmNum, entityNum);

    private EntityId(long shardNum, long realmNum, long entityNum, EntityType type) {
        id = encode(shardNum, realmNum, entityNum);
        this.shardNum = shardNum;
        this.realmNum = realmNum;
        this.entityNum = entityNum;
        this.type = type;
    }

    private EntityId(long id, EntityType type) {
        if (id < 0) {
            throw new InvalidEntityException("Entity ID can not be negative: " + id);
        }

        this.id = id;
        this.shardNum = id >> (REALM_BITS + NUM_BITS);
        this.realmNum = (id >> NUM_BITS) & REALM_MASK;
        this.entityNum = id & NUM_MASK;
        this.type = type;
    }

    /**
     * Encodes given shard, realm, num into an 8 bytes long.
     * <p/>
     * Only 63 bits (excluding signed bit) are used for encoding to make it easy to encode/decode using mathematical
     * operations too. That's because JavaScript's support for bitwise operations is very limited (truncates numbers to
     * 32 bits internally before bitwise operation).
     * <p/>
     * Format: <br/> First bit (sign bit) is left 0. <br/> Next 15 bits are for shard, followed by 16 bits for realm,
     * and then 32 bits for entity num. <br/> This encoding will support following ranges: <br/> shard: 0 - 32767 <br/>
     * realm: 0 - 65535 <br/> num: 0 - 4294967295 <br/> Placing entity num in the end has the advantage that encoded ids
     * <= 4294967295 will also be human-readable.
     */
    private static Long encode(long shardNum, long realmNum, long entityNum) {
        if (shardNum > SHARD_MASK
                || shardNum < 0
                || realmNum > REALM_MASK
                || realmNum < 0
                || entityNum > NUM_MASK
                || entityNum < 0) {
            throw new InvalidEntityException("Invalid entity ID: " + shardNum + "." + realmNum + "." + entityNum);
        }

        return (entityNum & NUM_MASK)
                | (realmNum & REALM_MASK) << NUM_BITS
                | (shardNum & SHARD_MASK) << (REALM_BITS + NUM_BITS);
    }

    public static EntityId of(AccountID accountID) {
        return of(accountID.getShardNum(), accountID.getRealmNum(), accountID.getAccountNum(), EntityType.ACCOUNT);
    }

    public static EntityId of(ContractID contractID) {
        return of(contractID.getShardNum(), contractID.getRealmNum(), contractID.getContractNum(), EntityType.CONTRACT);
    }

    public static EntityId of(FileID fileID) {
        return of(fileID.getShardNum(), fileID.getRealmNum(), fileID.getFileNum(), EntityType.FILE);
    }

    public static EntityId of(TopicID topicID) {
        return of(topicID.getShardNum(), topicID.getRealmNum(), topicID.getTopicNum(), EntityType.TOPIC);
    }

    public static EntityId of(TokenID tokenID) {
        return of(tokenID.getShardNum(), tokenID.getRealmNum(), tokenID.getTokenNum(), EntityType.TOKEN);
    }

    public static EntityId of(ScheduleID scheduleID) {
        return of(scheduleID.getShardNum(), scheduleID.getRealmNum(), scheduleID.getScheduleNum(), EntityType.SCHEDULE);
    }

    public static EntityId of(String entityId, EntityType type) {
        List<Long> parts = SPLITTER.splitToStream(Objects.requireNonNullElse(entityId, ""))
                .map(Long::valueOf)
                .filter(n -> n >= 0)
                .toList();

        if (parts.size() != 3) {
            throw new IllegalArgumentException("Invalid entity ID: " + entityId);
        }

        return of(parts.get(0), parts.get(1), parts.get(2), type);
    }

    public static EntityId of(long entityShard, long entityRealm, long entityNum, EntityType type) {
        if (entityNum == 0 && entityRealm == 0 && entityShard == 0) {
            return EMPTY;
        }
        return new EntityId(entityShard, entityRealm, entityNum, type);
    }

    public static EntityId of(long encodedEntityId, EntityType type) {
        return new EntityId(encodedEntityId, type);
    }

    public static boolean isEmpty(EntityId entityId) {
        return entityId == null || EMPTY.equals(entityId);
    }

    public Entity toEntity() {
        Entity entity = new Entity();
        entity.setId(id);
        entity.setShard(shardNum);
        entity.setRealm(realmNum);
        entity.setNum(entityNum);
        entity.setTimestampRange(DEFAULT_RANGE);
        entity.setType(type);
        return entity;
    }

    @Override
    public int compareTo(EntityId other) {
        return COMPARATOR.compare(this, other);
    }

    @Override
    public String toString() {
        return getCachedString();
    }
}
