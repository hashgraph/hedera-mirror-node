package com.hedera.mirror.grpc.domain;

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

import com.google.protobuf.UnsafeByteOperations;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusMessageChunkInfo;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.time.Instant;
import java.util.Comparator;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.springframework.data.domain.Persistable;

import com.hedera.mirror.api.proto.ConsensusTopicResponse;
import com.hedera.mirror.grpc.converter.EncodedIdToEntityConverter;
import com.hedera.mirror.grpc.converter.InstantToLongConverter;
import com.hedera.mirror.grpc.converter.LongToInstantConverter;

@AllArgsConstructor
@NoArgsConstructor(force = true)
@Builder
@Entity
@Value
public class TopicMessage implements Comparable<TopicMessage>, Persistable<Long> {

    private static final Comparator<TopicMessage> COMPARATOR = Comparator
            .nullsFirst(Comparator.comparingLong(TopicMessage::getSequenceNumber));

    @Id
    @ToString.Exclude
    private Long consensusTimestamp;

    @Getter(lazy = true)
    @Transient
    private Instant consensusTimestampInstant = LongToInstantConverter.INSTANCE.convert(consensusTimestamp);

    @ToString.Exclude
    private byte[] message;

    private int realmNum;

    @ToString.Exclude
    private byte[] runningHash;

    private int runningHashVersion;

    private long sequenceNumber;

    private int topicNum;

    private Integer chunkNum;

    private Integer chunkTotal;

    @ToString.Exclude
    private Long payerAccountId;

    @Getter(lazy = true)
    @Transient
    private com.hedera.mirror.grpc.domain.Entity payerAccountEntity = EncodedIdToEntityConverter.INSTANCE
            .convert(payerAccountId);

    private Long validStartTimestamp;

    @Getter(lazy = true)
    @Transient
    private Instant validStartInstant = LongToInstantConverter.INSTANCE.convert(validStartTimestamp);

    @NonFinal
    @Transient
    private volatile ConsensusTopicResponse response = null;

    // Cache this to avoid paying the conversion penalty for multiple subscribers to the same topic
    public ConsensusTopicResponse toResponse() {
        if (response == null) {
            var consensusTopicResponseBuilder = ConsensusTopicResponse.newBuilder()
                    .setConsensusTimestamp(Timestamp.newBuilder()
                            .setSeconds(getConsensusTimestampInstant().getEpochSecond())
                            .setNanos(getConsensusTimestampInstant().getNano())
                            .build())
                    .setMessage(UnsafeByteOperations.unsafeWrap(message))
                    .setRunningHash(UnsafeByteOperations.unsafeWrap(runningHash))
                    .setRunningHashVersion(runningHashVersion)
                    .setSequenceNumber(sequenceNumber);

            if (getChunkNum() != null) {
                consensusTopicResponseBuilder
                        .setChunkInfo(ConsensusMessageChunkInfo.newBuilder()
                                .setInitialTransactionID(TransactionID.newBuilder()
                                        .setAccountID(AccountID.newBuilder()
                                                .setShardNum(getPayerAccountEntity().getEntityShard())
                                                .setRealmNum(getPayerAccountEntity().getEntityRealm())
                                                .setAccountNum(getPayerAccountEntity().getEntityNum())
                                                .build())
                                        .setTransactionValidStart(Timestamp.newBuilder()
                                                .setSeconds(getValidStartInstant().getEpochSecond())
                                                .setNanos(getValidStartInstant().getNano())
                                                .build())
                                        .build())
                                .setNumber(getChunkNum())
                                .setTotal(getChunkTotal())
                                .build());
            }

            response = consensusTopicResponseBuilder.build();
        }
        return response;
    }

    @Override
    public int compareTo(TopicMessage other) {
        return COMPARATOR.compare(this, other);
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
        public TopicMessageBuilder consensusTimestamp(Instant consensusTimestamp) {
            this.consensusTimestamp = InstantToLongConverter.INSTANCE.convert(consensusTimestamp);
            return this;
        }

        public TopicMessageBuilder validStartTimestamp(Instant validStartTimestamp) {
            this.validStartTimestamp = InstantToLongConverter.INSTANCE.convert(validStartTimestamp);
            return this;
        }
    }
}
