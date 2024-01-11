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

package com.hedera.mirror.common.domain.topic;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.hedera.mirror.common.domain.entity.EntityId;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.Comparator;
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
public class TopicMessage implements Comparable<TopicMessage>, Persistable<Long>, StreamMessage {

    private static final Comparator<TopicMessage> COMPARATOR = Comparator.nullsFirst(
            Comparator.comparing(TopicMessage::getTopicId).thenComparing(TopicMessage::getSequenceNumber));

    private Integer chunkNum;

    private Integer chunkTotal;

    @Id
    private long consensusTimestamp;

    @ToString.Exclude
    private byte[] initialTransactionId;

    @ToString.Exclude
    private byte[] message;

    private EntityId payerAccountId;

    @ToString.Exclude
    private byte[] runningHash;

    private int runningHashVersion;

    private long sequenceNumber;

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

    @Override
    public int compareTo(TopicMessage other) {
        return COMPARATOR.compare(this, other);
    }
}
