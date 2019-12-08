package com.hedera.mirror.grpc.service;

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
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
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
import com.hedera.mirror.grpc.util.ProtoUtil;

@GrpcService
@Log4j2
@RequiredArgsConstructor
public class ConsensusService extends ReactorConsensusServiceGrpc.ConsensusServiceImplBase {

    private final TopicMessageService topicMessageService;

    @Override
    public Flux<ConsensusTopicResponse> subscribeTopic(Mono<ConsensusTopicQuery> request) {
        try {
            TopicMessageFilter filter = request.map(this::toFilter).block();
            return topicMessageService.subscribeTopic(filter).map(this::toResponse);
        } catch (StatusRuntimeException e) {
            log.warn("Error", e);
            return Flux.error(e);
        } catch (ConstraintViolationException e) {
            log.warn("Invalid request", e);
            return Flux.error(Status.INVALID_ARGUMENT.augmentDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            log.error("Unexpected exception", e);
            return Flux.error(Status.INTERNAL.augmentDescription(e.getMessage()).asRuntimeException());
        }
    }

    private TopicMessageFilter toFilter(ConsensusTopicQuery query) {
        if (!query.hasTopicID()) {
            throw Status.INVALID_ARGUMENT.augmentDescription("Missing required topicID").asRuntimeException();
        }

        TopicMessageFilter.TopicMessageFilterBuilder builder = TopicMessageFilter.builder()
                .limit(query.getLimit())
                .realmNum(query.getTopicID().getRealmNum())
                .topicNum(query.getTopicID().getTopicNum());

        if (query.hasConsensusStartTime()) {
            builder.startTime(ProtoUtil.fromTimestamp(query.getConsensusStartTime()));
        }

        if (query.hasConsensusEndTime()) {
            builder.endTime(ProtoUtil.fromTimestamp(query.getConsensusEndTime()));
        }

        return builder.build();
    }

    private ConsensusTopicResponse toResponse(TopicMessage topicMessage) {
        try {
            return ConsensusTopicResponse.newBuilder()
                    .setConsensusTimestamp(ProtoUtil.toTimestamp(topicMessage.getConsensusTimestamp()))
                    .setMessage(ByteString.copyFrom(topicMessage.getMessage()))
                    .setSequenceNumber(topicMessage.getSequenceNumber())
                    .setRunningHash(ByteString.copyFrom(topicMessage.getRunningHash()))
                    .build();
        } catch (Exception e) {
            throw Status.INTERNAL
                    .augmentDescription("Unable to convert topic message to ConsensusTopicResponse")
                    .asRuntimeException();
        }
    }
}
