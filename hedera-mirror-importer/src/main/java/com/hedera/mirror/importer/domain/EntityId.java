package com.hedera.mirror.importer.domain;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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
import lombok.EqualsAndHashCode;
import lombok.Value;

import com.hedera.mirror.importer.util.EntityIdEndec;

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

    public static final EntityId EMPTY = new EntityId(0L, 0L, 0L, EntityTypeEnum.ACCOUNT.getId());
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
    private final Integer type;

    public EntityId(Long shardNum, Long realmNum, Long entityNum, Integer type) {
        id = EntityIdEndec.encode(shardNum, realmNum, entityNum);
        this.shardNum = shardNum;
        this.realmNum = realmNum;
        this.entityNum = entityNum;
        this.type = type;
    }

    public static EntityId of(AccountID accountID) {
        return of(accountID.getShardNum(), accountID.getRealmNum(), accountID.getAccountNum(), EntityTypeEnum.ACCOUNT);
    }

    public static EntityId of(ContractID contractID) {
        return of(contractID.getShardNum(), contractID.getRealmNum(), contractID.getContractNum(),
                EntityTypeEnum.CONTRACT);
    }

    public static EntityId of(FileID fileID) {
        return of(fileID.getShardNum(), fileID.getRealmNum(), fileID.getFileNum(), EntityTypeEnum.FILE);
    }

    public static EntityId of(TopicID topicID) {
        return of(topicID.getShardNum(), topicID.getRealmNum(), topicID.getTopicNum(), EntityTypeEnum.TOPIC);
    }

    public static EntityId of(TokenID tokenID) {
        return of(tokenID.getShardNum(), tokenID.getRealmNum(), tokenID.getTokenNum(), EntityTypeEnum.TOKEN);
    }

    public static EntityId of(ScheduleID scheduleID) {
        return of(scheduleID.getShardNum(), scheduleID.getRealmNum(), scheduleID
                .getScheduleNum(), EntityTypeEnum.SCHEDULE);
    }

    public static EntityId of(String entityId, EntityTypeEnum type) {
        List<Long> parts = SPLITTER.splitToStream(Objects.requireNonNullElse(entityId, ""))
                .map(Long::valueOf)
                .filter(n -> n >= 0)
                .collect(Collectors.toList());

        if (parts.size() != 3) {
            throw new IllegalArgumentException("Invalid entity ID: " + entityId);
        }

        return of(parts.get(0), parts.get(1), parts.get(2), type);
    }

    public static EntityId of(long entityShard, long entityRealm, long entityNum, EntityTypeEnum type) {
        if (entityNum == 0 && entityRealm == 0 && entityShard == 0) {
            return EMPTY;
        }
        return new EntityId(entityShard, entityRealm, entityNum, type.getId());
    }

    public static boolean isEmpty(EntityId entityId) {
        return entityId == null || EMPTY.equals(entityId);
    }

    public String entityIdToString() {
        return String.format("%d.%d.%d", getShardNum(),
                getRealmNum(), getEntityNum());
    }

    public <T extends AbstractEntity> T toEntity() {
        T entity = createEntity();
        entity.setId(id);
        entity.setShard(shardNum);
        entity.setRealm(realmNum);
        entity.setNum(entityNum);
        entity.setTimestampRange(DEFAULT_RANGE);
        entity.setType(type);
        return entity;
    }

    private <T extends AbstractEntity> T createEntity() {
        switch (type) {
            case 2:
                return (T) new Contract();
            default:
                return (T) new Entity();
        }
    }

    @Override
    public int compareTo(EntityId other) {
        return COMPARATOR.compare(this, other);
    }
}
