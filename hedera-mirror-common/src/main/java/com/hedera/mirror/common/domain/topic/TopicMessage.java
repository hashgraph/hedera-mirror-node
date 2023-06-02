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

package com.hedera.mirror.common.domain.topic;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.hedera.mirror.common.converter.AccountIdConverter;
import com.hedera.mirror.common.converter.AccountIdDeserializer;
import com.hedera.mirror.common.converter.EntityIdSerializer;
import com.hedera.mirror.common.converter.TopicIdConverter;
import com.hedera.mirror.common.converter.TopicIdDeserializer;
import com.hedera.mirror.common.domain.entity.EntityId;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.domain.Persistable;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For builder
@Builder(toBuilder = true)
@Data
@Entity
@JsonTypeInfo(use = com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME)
@JsonTypeName("TopicMessage")
@NoArgsConstructor
@ToString(exclude = {"initialTransactionId", "message", "runningHash"})
public class TopicMessage implements Persistable<Long>, StreamMessage {

    private Integer chunkNum;

    private Integer chunkTotal;

    @Id
    private long consensusTimestamp;

    private byte[] initialTransactionId;

    private byte[] message;

    @Convert(converter = AccountIdConverter.class)
    @JsonSerialize(using = EntityIdSerializer.class)
    @JsonDeserialize(using = AccountIdDeserializer.class)
    private EntityId payerAccountId;

    private byte[] runningHash;

    private int runningHashVersion;

    private long sequenceNumber;

    @Convert(converter = TopicIdConverter.class)
    @JsonSerialize(using = EntityIdSerializer.class)
    @JsonDeserialize(using = TopicIdDeserializer.class)
    private EntityId topicId;

    private Long validStartTimestamp;

    @JsonIgnore
    @Override
    public Long getId() {
        return consensusTimestamp;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid Hibernate querying before insert
    }
}
