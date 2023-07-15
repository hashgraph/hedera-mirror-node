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

package com.hedera.mirror.grpc.controller;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.mirror.api.proto.ConsensusTopicQuery;
import com.hedera.mirror.api.proto.ConsensusTopicResponse;
import com.hedera.mirror.api.proto.ReactorConsensusServiceGrpc;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.topic.TopicMessage;
import com.hedera.mirror.grpc.converter.InstantToLongConverter;
import com.hedera.mirror.grpc.converter.LongToInstantConverter;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;
import com.hedera.mirror.grpc.service.TopicMessageService;
import com.hedera.mirror.grpc.util.ProtoUtil;
import com.hederahashgraph.api.proto.java.ConsensusMessageChunkInfo;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.devh.boot.grpc.server.service.GrpcService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * GRPC calls their protocol adapter layer a service, but most of the industry calls this layer the controller layer.
 * See the Front Controller pattern or Model-View-Controller (MVC) pattern. The service layer is generally reserved for
 * non-protocol specific business logic so to avoid confusion with our TopicMessageService we'll name this GRPC layer as
 * controller.
 */
@GrpcService
@Log4j2
@RequiredArgsConstructor
public class ConsensusController extends ReactorConsensusServiceGrpc.ConsensusServiceImplBase {

    private final TopicMessageService topicMessageService;

    @Override
    public Flux<ConsensusTopicResponse> subscribeTopic(Mono<ConsensusTopicQuery> request) {
        return request.map(this::toFilter)
                .flatMapMany(topicMessageService::subscribeTopic)
                .map(this::toResponse)
                .onErrorMap(ProtoUtil::toStatusRuntimeException);
    }

    private TopicMessageFilter toFilter(ConsensusTopicQuery query) {
        var filter = TopicMessageFilter.builder().limit(query.getLimit());

        if (query.hasTopicID()) {
            filter.topicId(EntityId.of(query.getTopicID()));
        }

        if (query.hasConsensusStartTime()) {
            Timestamp startTimeStamp = query.getConsensusStartTime();
            Instant startInstant = ProtoUtil.fromTimestamp(startTimeStamp);
            filter.startTime(startInstant.isBefore(Instant.EPOCH) ? Instant.EPOCH : startInstant);
        }

        if (query.hasConsensusEndTime()) {
            Timestamp endTimeStamp = query.getConsensusEndTime();
            Instant endInstant = ProtoUtil.fromTimestamp(endTimeStamp);
            filter.endTime(
                    endInstant.isAfter(InstantToLongConverter.LONG_MAX_INSTANT)
                            ? InstantToLongConverter.LONG_MAX_INSTANT
                            : endInstant);
        }

        return filter.build();
    }

    // Consider caching this conversion for multiple subscribers to the same topic if the need arises.
    private ConsensusTopicResponse toResponse(TopicMessage t) {
        var consensusTopicResponseBuilder = ConsensusTopicResponse.newBuilder()
                .setConsensusTimestamp(
                        ProtoUtil.toTimestamp(LongToInstantConverter.INSTANCE.convert(t.getConsensusTimestamp())))
                .setMessage(ProtoUtil.toByteString(t.getMessage()))
                .setRunningHash(ProtoUtil.toByteString(t.getRunningHash()))
                .setRunningHashVersion(t.getRunningHashVersion())
                .setSequenceNumber(t.getSequenceNumber());

        if (t.getChunkNum() != null) {
            ConsensusMessageChunkInfo.Builder chunkBuilder = ConsensusMessageChunkInfo.newBuilder()
                    .setNumber(t.getChunkNum())
                    .setTotal(t.getChunkTotal());

            TransactionID transactionID = parseTransactionID(
                    t.getInitialTransactionId(), t.getTopicId().getEntityNum(), t.getSequenceNumber());
            EntityId payerAccountEntity = t.getPayerAccountId();
            Instant validStartInstant = LongToInstantConverter.INSTANCE.convert(t.getValidStartTimestamp());

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

    private TransactionID parseTransactionID(byte[] transactionIdBytes, long topicId, long sequenceNumber) {
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
}
