package com.hedera.mirror.grpc.service;

import static org.assertj.core.api.Assertions.*;

import com.google.common.primitives.Longs;
import com.google.protobuf.ByteString;

import com.hedera.mirror.api.proto.ReactorConsensusServiceGrpc;

import com.hederahashgraph.api.proto.java.Timestamp;
import io.grpc.StatusRuntimeException;


import net.devh.boot.grpc.client.inject.GrpcClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import com.hedera.mirror.api.proto.ConsensusServiceGrpc;
import com.hedera.mirror.api.proto.ConsensusTopicQuery;
import com.hedera.mirror.api.proto.ConsensusTopicResponse;

@SpringBootTest
public class ConsensusServiceTest {

    @GrpcClient("local")
    private ReactorConsensusServiceGrpc.ReactorConsensusServiceStub grpcConsensusService;

    @GrpcClient("local")
    private ConsensusServiceGrpc.ConsensusServiceBlockingStub blockingService;

    @Test
    void subscribeTopicReactive() throws Exception {
        ConsensusTopicQuery query = ConsensusTopicQuery.newBuilder().setLimit(3L).build();
        grpcConsensusService.subscribeTopic(Mono.just(query))
                .as(StepVerifier::create)
                .expectNext(response(1L))
                .expectNext(response(2L))
                .expectNext(response(3L))
                .verifyComplete();
    }

    @Test
    void subscribeTopicReactiveInfinite() throws Exception {
        ConsensusTopicQuery query = ConsensusTopicQuery.newBuilder().build();
        grpcConsensusService.subscribeTopic(Mono.just(query))
                .as(StepVerifier::create)
                .expectNext(response(1L))
                .expectNext(response(2L))
                .expectNext(response(3L))
                .expectNext(response(4L))
                .thenCancel()
                .verify();
    }

    @Test
    void subscribeTopicReactiveInvalidLimit() throws Exception {
        ConsensusTopicQuery query = ConsensusTopicQuery.newBuilder().setLimit(-1L).build();
        grpcConsensusService.subscribeTopic(Mono.just(query))
                .as(StepVerifier::create)
                .expectError(StatusRuntimeException.class);
    }

    @Test
    void subscribeTopicBlocking() throws Exception {
        ConsensusTopicQuery query = ConsensusTopicQuery.newBuilder().setLimit(3L).build();
        assertThat(blockingService.subscribeTopic(query))
                .toIterable()
                .hasSize(3)
                .containsExactly(
                        response(1L),
                        response(2L),
                        response(3L)
                );
    }

    @Test
    void subscribeTopicBlockingInvalidLimit() throws Exception {
        ConsensusTopicQuery query = ConsensusTopicQuery.newBuilder().setLimit(-1L).build();
        assertThatThrownBy(() -> blockingService.subscribeTopic(query).hasNext())
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("Cannot be negative");
    }

    private ConsensusTopicResponse response(long sequenceNumber) throws Exception {
        return ConsensusTopicResponse.newBuilder()
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(sequenceNumber).build())
                .setMessage(ByteString.copyFrom("Message #" + sequenceNumber, "UTF-8"))
                .setSequenceNumber(sequenceNumber)
                .setRunningHash(ByteString.copyFrom(Longs.toByteArray(sequenceNumber)))
                .build();
    }
}
