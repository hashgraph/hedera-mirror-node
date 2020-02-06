package com.hedera.mirror.grpc.controller;

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

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Timestamp;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.Instant;
import javax.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.devh.boot.grpc.server.service.GrpcService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.hedera.mirror.api.proto.ConsensusTopicQuery;
import com.hedera.mirror.api.proto.ConsensusTopicResponse;
import com.hedera.mirror.api.proto.ReactorConsensusServiceGrpc;
import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;
import com.hedera.mirror.grpc.service.TopicMessageService;
import com.hedera.mirror.grpc.util.ProtoUtil;

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

    private static final long LONG_MAX_SECONDS = 9_223_372_036_000_000_000L;
    private static final int LONG_MAX_NANOSECONDS = 854_775_807;
    private final TopicMessageService topicMessageService;

    @Override
    public Flux<ConsensusTopicResponse> subscribeTopic(Mono<ConsensusTopicQuery> request) {
        return request.map(this::toFilter)
                .flatMapMany(topicMessageService::subscribeTopic)
                .map(this::toResponse)
                .onErrorMap(ConstraintViolationException.class, t -> invalidRequest(t))
                .onErrorMap(t -> unknownError(t));
    }

    private TopicMessageFilter toFilter(ConsensusTopicQuery query) {
        if (!query.hasTopicID()) {
            log.warn("Missing required topicID");
            throw Status.INVALID_ARGUMENT.augmentDescription("Missing required topicID").asRuntimeException();
        }

        TopicMessageFilter.TopicMessageFilterBuilder builder = TopicMessageFilter.builder()
                .limit(query.getLimit())
                .realmNum((int) query.getTopicID().getRealmNum())
                .topicNum((int) query.getTopicID().getTopicNum());

        if (query.hasConsensusStartTime()) {
            Timestamp startTimeStamp = query.getConsensusStartTime();

            // scope pre epoch timestamps to epoch instant
            if (startTimeStamp.getSeconds() < 0 || startTimeStamp.getNanos() < 0) {
                builder.startTime(Instant.EPOCH);
            } else {
                builder.startTime(ProtoUtil.fromTimestamp(startTimeStamp));
            }
        }

        if (query.hasConsensusEndTime()) {
            Timestamp endTimeStamp = query.getConsensusEndTime();

            // only set endTime if it's smaller than the seconds and nanoseconds in the Long.MAX value
            if (endTimeStamp.getSeconds() <= LONG_MAX_SECONDS && endTimeStamp.getNanos() <= LONG_MAX_NANOSECONDS) {
                builder.endTime(ProtoUtil.fromTimestamp(endTimeStamp));
            }
        }

        return builder.build();
    }

    private ConsensusTopicResponse toResponse(TopicMessage topicMessage) {
        return ConsensusTopicResponse.newBuilder()
                .setConsensusTimestamp(ProtoUtil.toTimestamp(topicMessage.getConsensusTimestamp()))
                .setMessage(ByteString.copyFrom(topicMessage.getMessage()))
                .setSequenceNumber(topicMessage.getSequenceNumber())
                .setRunningHash(ByteString.copyFrom(topicMessage.getRunningHash()))
                .build();
    }

    private Throwable invalidRequest(Throwable t) {
        log.warn("Invalid ConsensusTopicQuery: {}", t.getMessage());
        return Status.INVALID_ARGUMENT.augmentDescription(t.getMessage()).asRuntimeException();
    }

    private Throwable unknownError(Throwable t) {
        if (t instanceof StatusRuntimeException) {
            return t;
        }

        log.error("Unknown error subscribing to topic", t);
        return Status.INTERNAL.augmentDescription(t.getMessage()).asRuntimeException();
    }
}
