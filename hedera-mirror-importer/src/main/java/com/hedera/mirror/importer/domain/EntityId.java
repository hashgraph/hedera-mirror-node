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
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.TopicID;
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
    // Ignored so not included in json serialization of PubSubMessage
    @JsonIgnore
    private Long id;
    private Long entityShard;
    private Long entityRealm;
    private Long entityNum;
    private Integer entityTypeId;

    public Entities toEntity() {
        Entities entity = new Entities();
        entity.setId(id);
        entity.setEntityShard(entityShard);
        entity.setEntityRealm(entityRealm);
        entity.setEntityNum(entityNum);
        entity.setEntityTypeId(entityTypeId);
        return entity;
    }

    public String getDisplayId() {
        return String.format("%d.%d.%d", entityShard, entityRealm, entityNum);
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

    private static EntityId of(long entityShard, long entityRealm, long entityNum, EntityTypeEnum type) {
        if (entityNum == 0 && entityRealm == 0 && entityShard == 0) {
            return null;
        }
        return new EntityId(null, entityShard, entityRealm, entityNum, type.getId());
    }
}
