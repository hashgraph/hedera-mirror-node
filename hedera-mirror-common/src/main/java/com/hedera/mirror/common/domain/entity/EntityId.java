/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.base.Splitter;
import com.google.common.collect.Range;
import com.hedera.mirror.common.exception.InvalidEntityException;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import jakarta.persistence.Transient;
import java.io.Serial;
import java.io.Serializable;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.Value;

/**
 * Common encapsulation for a Hedera entity identifier.
 */
@Value
public final class EntityId implements Serializable, Comparable<EntityId> {

    public static final EntityId EMPTY = new EntityId(0L);

    static final int NUM_BITS = 32;
    static final int REALM_BITS = 16;
    static final int SHARD_BITS = 15;

    private static final long NUM_MASK = (1L << NUM_BITS) - 1;
    private static final long REALM_MASK = (1L << REALM_BITS) - 1;
    private static final long SHARD_MASK = (1L << SHARD_BITS) - 1;

    private static final String CACHE_DEFAULT = "expireAfterAccess=60m,maximumSize=500000,recordStats";
    private static final String CACHE_PROPERTY = "HEDERA_MIRROR_COMMON_CACHE_ENTITYID";
    private static final String CACHE_SPEC = System.getProperty(CACHE_PROPERTY, CACHE_DEFAULT);
    private static final Cache<Long, EntityId> CACHE = Caffeine.from(CACHE_SPEC).build();

    private static final Comparator<EntityId> COMPARATOR =
            Comparator.nullsFirst(Comparator.comparingLong(EntityId::getId));
    private static final Range<Long> DEFAULT_RANGE = Range.atLeast(0L);
    private static final String DOT = ".";
    private static final Splitter SPLITTER = Splitter.on('.').omitEmptyStrings().trimResults();

    @Serial
    private static final long serialVersionUID = 1427649605832330197L;

    @JsonValue
    private final long id;

    private EntityId(long id) {
        if (id < 0) {
            throw new InvalidEntityException("Entity ID can not be negative: " + id);
        }

        this.id = id;
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
    private static long encode(long shard, long realm, long num) {
        if (shard > SHARD_MASK || shard < 0 || realm > REALM_MASK || realm < 0 || num > NUM_MASK || num < 0) {
            throw new InvalidEntityException("Invalid entity ID: " + shard + "." + realm + "." + num);
        }

        if (shard == 0 && realm == 0) {
            return num;
        }

        return (num & NUM_MASK) | (realm & REALM_MASK) << NUM_BITS | (shard & SHARD_MASK) << (REALM_BITS + NUM_BITS);
    }

    public static EntityId of(AccountID accountID) {
        return of(accountID.getShardNum(), accountID.getRealmNum(), accountID.getAccountNum());
    }

    public static EntityId of(ContractID contractID) {
        return of(contractID.getShardNum(), contractID.getRealmNum(), contractID.getContractNum());
    }

    public static EntityId of(FileID fileID) {
        return of(fileID.getShardNum(), fileID.getRealmNum(), fileID.getFileNum());
    }

    public static EntityId of(TopicID topicID) {
        return of(topicID.getShardNum(), topicID.getRealmNum(), topicID.getTopicNum());
    }

    public static EntityId of(TokenID tokenID) {
        return of(tokenID.getShardNum(), tokenID.getRealmNum(), tokenID.getTokenNum());
    }

    public static EntityId of(ScheduleID scheduleID) {
        return of(scheduleID.getShardNum(), scheduleID.getRealmNum(), scheduleID.getScheduleNum());
    }

    public static EntityId of(String entityId) {
        List<Long> parts = SPLITTER.splitToStream(Objects.requireNonNullElse(entityId, ""))
                .map(Long::valueOf)
                .filter(n -> n >= 0)
                .toList();

        if (parts.size() != 3) {
            throw new IllegalArgumentException("Invalid entity ID: " + entityId);
        }

        return of(parts.get(0), parts.get(1), parts.get(2));
    }

    public static EntityId of(long shard, long realm, long num) {
        long id = encode(shard, realm, num);
        return of(id);
    }

    public static EntityId of(long id) {
        if (id == 0) {
            return EMPTY;
        }

        return CACHE.get(id, k -> new EntityId(id));
    }

    public static boolean isEmpty(EntityId entityId) {
        return entityId == null || EMPTY.equals(entityId);
    }

    @Transient
    public long getNum() {
        return id & NUM_MASK;
    }

    @Transient
    public long getRealm() {
        return (id >> NUM_BITS) & REALM_MASK;
    }

    @Transient
    public long getShard() {
        return id >> (NUM_BITS + REALM_BITS);
    }

    public Entity toEntity() {
        Entity entity = new Entity();
        entity.setId(id);
        entity.setNum(getNum());
        entity.setRealm(getRealm());
        entity.setShard(getShard());
        entity.setTimestampRange(DEFAULT_RANGE);
        return entity;
    }

    @Override
    public int compareTo(EntityId other) {
        return COMPARATOR.compare(this, other);
    }

    @Override
    public String toString() {
        return getShard() + DOT + getRealm() + DOT + getNum();
    }
}
