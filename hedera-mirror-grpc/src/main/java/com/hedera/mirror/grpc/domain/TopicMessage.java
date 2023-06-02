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

package com.hedera.mirror.grpc.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.mirror.api.proto.ConsensusTopicResponse;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.grpc.converter.InstantToLongConverter;
import com.hedera.mirror.grpc.converter.LongToInstantConverter;
import com.hedera.mirror.grpc.util.ProtoUtil;
import com.hederahashgraph.api.proto.java.ConsensusMessageChunkInfo;
import com.hederahashgraph.api.proto.java.TransactionID;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.Comparator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.Value;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Persistable;

@AllArgsConstructor
@Builder
@Entity
@JsonIgnoreProperties(
        ignoreUnknown = true,
        value = {"consensusTimestampInstant", "response"})
@JsonTypeInfo(use = com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME)
@JsonTypeName("TopicMessage")
@Log4j2
@NoArgsConstructor(force = true)
@Value
public class TopicMessage implements Comparable<TopicMessage>, Persistable<Long>, StreamMessage {

    private static final Comparator<TopicMessage> COMPARATOR =
            Comparator.nullsFirst(Comparator.comparingLong(TopicMessage::getSequenceNumber));

    @Id
    @ToString.Exclude
    private Long consensusTimestamp;

    @JsonIgnore
    @Getter(lazy = true)
    @Transient
    private Instant consensusTimestampInstant = LongToInstantConverter.INSTANCE.convert(consensusTimestamp);

    @ToString.Exclude
    private byte[] initialTransactionId;

    @ToString.Exclude
    private byte[] message;

    @ToString.Exclude
    private byte[] runningHash;

    private int runningHashVersion;

    private long sequenceNumber;

    private long topicId;

    private Integer chunkNum;

    private Integer chunkTotal;

    @ToString.Exclude
    private Long payerAccountId;

    private Long validStartTimestamp;

    // Cache this to avoid paying the conversion penalty for multiple subscribers to the same topic
    @EqualsAndHashCode.Exclude
    @Getter(lazy = true)
    @ToString.Exclude
    @Transient
    private final ConsensusTopicResponse response = toResponse();

    private ConsensusTopicResponse toResponse() {
        var consensusTopicResponseBuilder = ConsensusTopicResponse.newBuilder()
                .setConsensusTimestamp(ProtoUtil.toTimestamp(getConsensusTimestampInstant()))
                .setMessage(ProtoUtil.toByteString(message))
                .setRunningHash(ProtoUtil.toByteString(runningHash))
                .setRunningHashVersion(runningHashVersion)
                .setSequenceNumber(sequenceNumber);

        if (getChunkNum() != null) {
            ConsensusMessageChunkInfo.Builder chunkBuilder = ConsensusMessageChunkInfo.newBuilder()
                    .setNumber(getChunkNum())
                    .setTotal(getChunkTotal());

            TransactionID transactionID = parseTransactionID(initialTransactionId);
            EntityId payerAccountEntity = EntityId.of(payerAccountId, EntityType.ACCOUNT);
            Instant validStartInstant = LongToInstantConverter.INSTANCE.convert(validStartTimestamp);

            if (transactionID != null) {
                chunkBuilder.setInitialTransactionID(transactionID);
            } else if (payerAccountEntity != null && validStartInstant != null) {
                chunkBuilder.setInitialTransactionID(TransactionID.newBuilder()
                        .setAccountID(ProtoUtil.toAccountID(payerAccountEntity))
                        .setTransactionValidStart(ProtoUtil.toTimestamp(validStartInstant))
                        .build());
            }

            consensusTopicResponseBuilder.setChunkInfo(chunkBuilder.build());
        }

        return consensusTopicResponseBuilder.build();
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

    private TransactionID parseTransactionID(byte[] transactionIdBytes) {
        if (transactionIdBytes == null) {
            return null;
        }
        try {
            return TransactionID.parseFrom(transactionIdBytes);
        } catch (InvalidProtocolBufferException e) {
            log.error("Failed to parse TransactionID for topic {} sequence number {}", topicId, sequenceNumber);
            return null;
        }
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
