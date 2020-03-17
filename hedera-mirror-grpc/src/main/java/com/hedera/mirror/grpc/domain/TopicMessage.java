package com.hedera.mirror.grpc.domain;

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

import java.time.Instant;
import java.util.Comparator;
import javax.persistence.Entity;
import javax.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.domain.Persistable;

import com.hedera.mirror.grpc.converter.InstantToLongConverter;
import com.hedera.mirror.grpc.converter.LongToInstantConverter;

@Data
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TopicMessage implements Comparable<TopicMessage>, Persistable<Long> {

    private static final InstantToLongConverter instantToLongConverter = new InstantToLongConverter();
    private static final LongToInstantConverter longToInstantConverter = new LongToInstantConverter();

    @Id
    private Long consensusTimestamp;

    @ToString.Exclude
    private byte[] message;

    private int realmNum;

    @ToString.Exclude
    private byte[] runningHash;

    private long sequenceNumber;

    private int topicNum;

    @Override
    public int compareTo(TopicMessage other) {
        return Comparator.nullsFirst(Comparator.comparingLong(TopicMessage::getSequenceNumber)).compare(this, other);
    }

    public Instant getConsensusTimestampInstant() {
        return longToInstantConverter.convert(consensusTimestamp);
    }

    @Override
    public Long getId() {
        return consensusTimestamp;
    }

    @Override
    public boolean isNew() {
        return true;
    }

    public static class TopicMessageBuilder {
        private long consensusTimestamp;

        public TopicMessageBuilder consensusTimestamp(Instant consensusTimestamp) {
            this.consensusTimestamp = instantToLongConverter.convert(consensusTimestamp);
            return this;
        }

        public TopicMessage build() {
            return new TopicMessage(consensusTimestamp, message, realmNum, runningHash, sequenceNumber, topicNum);
        }
    }
}
