package com.hedera.mirror.importer.domain;

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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Splitter;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.TopicID;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.Value;

/**
 * Common encapsulation for accountID, fileID, contractID, and topicID.
 * <p>
 * There is no valid entity in Hedera network with an id '0.0.0'. When AccountID/FileID/ContractID/TopicID are not set,
 * their values default to '0.0.0'. If such an unset (default) instance is used to create EntityId using one of the
 * of(..) functions, null is returned.
 */
@Value
public class EntityId {

    private static final Splitter SPLITTER = Splitter.on('.').omitEmptyStrings().trimResults();

    // Ignored so not included in json serialization of PubSubMessage
    @JsonIgnore
    private Long id;
    private Long shardNum;
    private Long realmNum;
    private Long entityNum;
    private Integer type;

    public Entities toEntity() {
        Entities entity = new Entities();
        entity.setId(id);
        entity.setEntityShard(shardNum);
        entity.setEntityRealm(realmNum);
        entity.setEntityNum(entityNum);
        entity.setEntityTypeId(type);
        return entity;
    }

    @JsonIgnore
    public String getDisplayId() {
        return String.format("%d.%d.%d", shardNum, realmNum, entityNum);
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
            return null;
        }
        return new EntityId(null, entityShard, entityRealm, entityNum, type.getId());
    }
}
