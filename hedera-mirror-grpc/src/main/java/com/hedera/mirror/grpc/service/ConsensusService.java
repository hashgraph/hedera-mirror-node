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

import com.google.common.primitives.Longs;
import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Timestamp;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.devh.boot.grpc.server.service.GrpcService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.hedera.mirror.api.proto.ConsensusTopicQuery;
import com.hedera.mirror.api.proto.ConsensusTopicResponse;
import com.hedera.mirror.api.proto.ReactorConsensusServiceGrpc;

@GrpcService
@Log4j2
@RequiredArgsConstructor
public class ConsensusService extends ReactorConsensusServiceGrpc.ConsensusServiceImplBase {

    @Override
    public Flux<ConsensusTopicResponse> subscribeTopic(Mono<ConsensusTopicQuery> request) {
        long limit = request.block().getLimit();
        if (limit < 0) {
            throw Status.INVALID_ARGUMENT.augmentDescription("Cannot be negative").asRuntimeException();
        }

        return Flux.<ConsensusTopicResponse, Long>generate(() -> 1L, (state, sink) -> {
            if (state <= limit || limit == 0) {
                sink.next(response(state));
            } else {
                sink.complete();
            }
            return state + 1;
        });
    }

    private ConsensusTopicResponse response(long sequenceNumber) {
        try {
            return ConsensusTopicResponse.newBuilder()
                    .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(sequenceNumber).build())
                    .setMessage(ByteString.copyFrom("Message #" + sequenceNumber, "UTF-8"))
                    .setSequenceNumber(sequenceNumber)
                    .setRunningHash(ByteString.copyFrom(Longs.toByteArray(sequenceNumber)))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
