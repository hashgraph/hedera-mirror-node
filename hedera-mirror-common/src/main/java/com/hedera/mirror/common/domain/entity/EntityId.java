package com.hedera.mirror.common.domain.entity;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Splitter;
import com.google.common.collect.Range;
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
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;

import com.hedera.mirror.common.converter.EntityTypeSerializer;

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
    private static final Comparator<EntityId> COMPARATOR = Comparator
            .nullsFirst(Comparator.comparingLong(EntityId::getId));
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

    public EntityId(Long shardNum, Long realmNum, Long entityNum, EntityType type) {
        id = EntityIdEndec.encode(shardNum, realmNum, entityNum);
        this.shardNum = shardNum;
        this.realmNum = realmNum;
        this.entityNum = entityNum;
        this.type = type;
    }

    public static EntityId of(AccountID accountID) {
        return of(accountID.getShardNum(), accountID.getRealmNum(), accountID.getAccountNum(), EntityType.ACCOUNT);
    }

    public static EntityId of(ContractID contractID) {
        return of(contractID.getShardNum(), contractID.getRealmNum(), contractID.getContractNum(),
                EntityType.CONTRACT);
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
        return of(scheduleID.getShardNum(), scheduleID.getRealmNum(), scheduleID
                .getScheduleNum(), EntityType.SCHEDULE);
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
        return EntityIdEndec.decode(encodedEntityId, type);
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
