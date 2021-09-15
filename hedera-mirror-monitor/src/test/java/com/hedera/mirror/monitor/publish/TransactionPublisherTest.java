package com.hedera.mirror.monitor.publish;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import static com.hedera.hashgraph.sdk.proto.ResponseCodeEnum.OK;
import static com.hedera.hashgraph.sdk.proto.ResponseCodeEnum.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.TransferTransaction;
import com.hedera.hashgraph.sdk.proto.CryptoServiceGrpc;
import com.hedera.hashgraph.sdk.proto.Query;
import com.hedera.hashgraph.sdk.proto.Response;
import com.hedera.hashgraph.sdk.proto.ResponseCodeEnum;
import com.hedera.hashgraph.sdk.proto.ResponseHeader;
import com.hedera.hashgraph.sdk.proto.Transaction;
import com.hedera.hashgraph.sdk.proto.TransactionGetReceiptResponse;
import com.hedera.hashgraph.sdk.proto.TransactionGetRecordResponse;
import com.hedera.hashgraph.sdk.proto.TransactionReceipt;
import com.hedera.hashgraph.sdk.proto.TransactionRecord;
import com.hedera.hashgraph.sdk.proto.TransactionResponse;
import com.hedera.mirror.monitor.MonitorProperties;
import com.hedera.mirror.monitor.NodeProperties;
import com.hedera.mirror.monitor.NodeValidationProperties;
import com.hedera.mirror.monitor.OperatorProperties;
import com.hedera.mirror.monitor.publish.transaction.TransactionType;

@Log4j2
class TransactionPublisherTest {

    private CryptoServiceStub cryptoServiceStub;
    private MonitorProperties monitorProperties;
    private NodeValidationProperties nodeValidationProperties;
    private PublishProperties publishProperties;
    private PublishScenarioProperties publishScenarioProperties;
    private Server server;
    private Server server2;
    private TransactionPublisher transactionPublisher;

    @BeforeEach
    void setup() throws IOException {
        OperatorProperties operatorProperties = new OperatorProperties();
        operatorProperties.setAccountId("0.0.100");
        operatorProperties.setPrivateKey(PrivateKey.generate().toString());
        publishScenarioProperties = new PublishScenarioProperties();
        publishScenarioProperties.setName("test");
        publishScenarioProperties.setType(TransactionType.CRYPTO_TRANSFER);
        monitorProperties = new MonitorProperties();
        monitorProperties.setNodes(Set.of(new NodeProperties("0.0.3", "in-process:test")));
        monitorProperties.setOperator(operatorProperties);
        nodeValidationProperties = new NodeValidationProperties();
        monitorProperties.setNodeValidation(nodeValidationProperties);
        publishProperties = new PublishProperties();
        transactionPublisher = new TransactionPublisher(monitorProperties, publishProperties);
        cryptoServiceStub = new CryptoServiceStub();
        server = InProcessServerBuilder.forName("test")
                .addService(cryptoServiceStub)
                .directExecutor()
                .build()
                .start();
        server2 = InProcessServerBuilder.forName("test2")
                .addService(cryptoServiceStub)
                .directExecutor()
                .build()
                .start();
    }

    @AfterEach
    void teardown() throws InterruptedException {
        cryptoServiceStub.verify();
        transactionPublisher.close();
        if (server != null) {
            server.shutdown();
            server.awaitTermination();
        }
        if (server2 != null) {
            server2.shutdown();
            server2.awaitTermination();
        }
    }

    @Test
    @Timeout(3)
    void publish() {
        PublishRequest request = request().build();
        cryptoServiceStub.addQueries(Mono.just(receipt(SUCCESS)));
        cryptoServiceStub.addTransactions(Mono.just(response(OK)), Mono.just(response(OK)));

        transactionPublisher.publish(request)
                .as(StepVerifier::create)
                .expectNextMatches(r -> {
                    assertThat(r)
                            .isNotNull()
                            .returns(request, PublishResponse::getRequest)
                            .returns(request.getTransaction().getTransactionId(), PublishResponse::getTransactionId)
                            .extracting(PublishResponse::getTimestamp)
                            .isNotNull();
                    return true;
                })
                .expectComplete()
                .verify(Duration.ofSeconds(1L));

        assertThat(request.getTransaction().getTransactionMemo())
                .containsPattern(Pattern.compile("\\d+ Monitor test on \\w+"));
    }

    @Test
    @Timeout(3)
    void publishWithLogResponse() {
        publishScenarioProperties.setLogResponse(true);
        cryptoServiceStub.addQueries(Mono.just(receipt(SUCCESS)));
        cryptoServiceStub.addTransactions(Mono.just(response(OK)), Mono.just(response(OK)));

        transactionPublisher.publish(request().build())
                .as(StepVerifier::create)
                .expectNextCount(1L)
                .expectComplete()
                .verify(Duration.ofSeconds(1L));
    }

    @Test
    @Timeout(3)
    void publishWithReceipt() {
        cryptoServiceStub.addQueries(Mono.just(receipt(SUCCESS)), Mono.just(receipt(SUCCESS)));
        cryptoServiceStub.addTransactions(Mono.just(response(OK)), Mono.just(response(OK)));

        transactionPublisher.publish(request().receipt(true).build())
                .as(StepVerifier::create)
                .expectNextMatches(r -> {
                    assertThat(r).extracting(PublishResponse::getReceipt).isNotNull();
                    assertThat(r).extracting(PublishResponse::getRecord).isNull();
                    return true;
                })
                .expectComplete()
                .verify(Duration.ofSeconds(1L));
    }

    @Test
    @Timeout(3)
    void publishWithRecord() {
        cryptoServiceStub.addQueries(Mono.just(receipt(SUCCESS)), Mono.just(record(SUCCESS)));
        cryptoServiceStub.addTransactions(Mono.just(response(OK)), Mono.just(response(OK)));

        transactionPublisher.publish(request().record(true).build())
                .as(StepVerifier::create)
                .expectNextMatches(r -> {
                    assertThat(r).extracting(PublishResponse::getReceipt).isNotNull();
                    assertThat(r).extracting(PublishResponse::getRecord).isNotNull();
                    return true;
                })
                .expectComplete()
                .verify(Duration.ofSeconds(1L));
    }

    @Test
    @Timeout(3)
    void publishPreCheckError() {
        ResponseCodeEnum errorResponseCode = ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
        cryptoServiceStub.addQueries(Mono.just(receipt(SUCCESS)));
        cryptoServiceStub.addTransactions(Mono.just(response(OK)), Mono.just(response(errorResponseCode)));

        transactionPublisher.publish(request().build())
                .as(StepVerifier::create)
                .expectErrorSatisfies(t -> assertThat(t)
                        .isInstanceOf(PublishException.class)
                        .hasMessageContaining(errorResponseCode.toString()))
                .verify(Duration.ofSeconds(1L));
    }

    @Test
    @Timeout(3)
    void publishRetrySuccessful() {
        cryptoServiceStub.addQueries(Mono.just(receipt(SUCCESS)));
        cryptoServiceStub.addTransactions(Mono.just(response(OK)),
                Mono.just(response(ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED)),
                Mono.just(response(ResponseCodeEnum.OK)));

        transactionPublisher.publish(request().build())
                .as(StepVerifier::create)
                .expectNextCount(1L)
                .expectComplete()
                .verify(Duration.ofSeconds(1L));
    }

//    @Test
//    @Timeout(20)
//    void publishWithRevalidate2() {
//        nodeValidationProperties.setFrequency(Duration.ofSeconds(1));
//        monitorProperties.setNodes(Set.of(new NodeProperties("0.0.3", "in-process:test"),
//                new NodeProperties("0.0.4", "in-process:test2"))); // Illegal DNS to avoid SDK retry
//        nodeValidationProperties.setFrequency(Duration.ofSeconds(5));
//        cryptoServiceStub.addQueries(Mono.just(receipt(SUCCESS)), Mono.just(receipt(SUCCESS)));
//        cryptoServiceStub.addTransactions(Mono.just(response(OK)), Mono.just(response(OK)), Mono.just(response(OK)));
//
//        log.info("Executing first step for revalidate test");
//        transactionPublisher.publish(request().build())
//                .as(StepVerifier::create)
//                .expectNextCount(1L)
//                .expectComplete()
//                .verify(Duration.ofSeconds(1L));
//
//        // Force the only node to be unhealthy, verify error occurs
//        cryptoServiceStub.addQueries(Mono.just(receipt(SUCCESS)));
//        cryptoServiceStub.addTransactions(Mono.just(response(OK)), Mono.just(response(OK)));
//        monitorProperties.setNodes(Set.of(new NodeProperties("0.0.3", "invalid:1"),
//                new NodeProperties("0.0.4", "in-process:test"))); // Illegal DNS to avoid SDK retry
//
//        log.info("Executing second validate for revalidate test");
//        await().atMost(20, TimeUnit.SECONDS).until(() -> transactionPublisher.getNodeAccountIds()
//                .get() != null && transactionPublisher.getNodeAccountIds().get().size() == 1);
//        log.info("Executing second step for revalidate test");
//        transactionPublisher.publish(request().build())
//                .as(StepVerifier::create)
//                .expectNextCount(1L)
//                .expectComplete()
//                .verify(Duration.ofSeconds(1L));
}

    @Test
    @Timeout(20)
    void publishWithRevalidate() {
        monitorProperties.setNodes(Set.of(new NodeProperties("0.0.3", "in-process:test"),
                new NodeProperties("0.0.4", "in-process:test2"))); // Illegal DNS to avoid SDK retry
        nodeValidationProperties.setFrequency(Duration.ofSeconds(2));
        cryptoServiceStub.addQueries(Mono.just(receipt(SUCCESS)), Mono.just(receipt(SUCCESS)));
        cryptoServiceStub.addTransactions(Mono.just(response(OK)), Mono.just(response(OK)), Mono.just(response(OK)));

        log.info("Executing first step for revalidate test");
        transactionPublisher.publish(request().build())
                .as(StepVerifier::create)
                .expectNextCount(1L)
                .expectComplete()
                .verify(Duration.ofSeconds(1L));

        // Force the only node to be unhealthy, verify error occurs
        monitorProperties.setNodes(Set.of(new NodeProperties("0.0.3", "invalid:test"),
                new NodeProperties("0.0.4", "invalid:test2"))); // Illegal DNS to avoid SDK retry

        log.info("Executing second validate for revalidate test");
        await().atMost(5, TimeUnit.SECONDS).until(() -> transactionPublisher.getNodeAccountIds().get().isEmpty());
        log.info("Executing second step for revalidate test");

        cryptoServiceStub.addQueries(Mono.just(receipt(SUCCESS)), Mono.just(receipt(SUCCESS)));
        cryptoServiceStub.addTransactions(Mono.just(response(OK)), Mono.just(response(OK)));
        monitorProperties.setNodes(Set.of(new NodeProperties("0.0.3", "in-process:test"),
                new NodeProperties("0.0.4", "in-process:test2"))); // Illegal DNS to avoid SDK retry
        await().atMost(5, TimeUnit.SECONDS).until(() -> !transactionPublisher.getNodeAccountIds().get().isEmpty());
    }

    @Test
    @Timeout(3)
    void publishRetryError() {
        ResponseCodeEnum errorResponseCode = ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
        cryptoServiceStub.addQueries(Mono.just(receipt(SUCCESS)));
        cryptoServiceStub.addTransactions(Mono.just(response(OK)),
                Mono.just(response(errorResponseCode)),
                Mono.just(response(errorResponseCode)));

        transactionPublisher.publish(request().build())
                .as(StepVerifier::create)
                .expectErrorSatisfies(t -> assertThat(t)
                        .isInstanceOf(PublishException.class)
                        .hasMessageContaining("Failed to get gRPC response within maximum retry count")
                        .getRootCause()
                        .hasMessageContaining(errorResponseCode.toString()))
                .verify(Duration.ofSeconds(1L));
    }

    @Test
    @Timeout(3)
    void publishTimeout() {
        publishScenarioProperties.setTimeout(Duration.ofMillis(100L));
        cryptoServiceStub.addQueries(Mono.just(receipt(SUCCESS)));
        cryptoServiceStub.addTransactions(Mono.just(response(OK)),
                Mono.delay(Duration.ofMillis(500L)).thenReturn(response(OK)));

        transactionPublisher.publish(request().build())
                .as(StepVerifier::create)
                .expectErrorSatisfies(t -> assertThat(t)
                        .isInstanceOf(PublishException.class)
                        .hasMessageContaining("Did not observe any item or terminal signal within 100ms")
                        .hasCauseInstanceOf(TimeoutException.class))
                .verify(Duration.ofSeconds(1L));
    }

    @Test
    @Timeout(3)
    void noValidNodes() {
        cryptoServiceStub.addTransactions(Mono.error(Status.INTERNAL.asRuntimeException()));
        transactionPublisher.publish(request().build())
                .as(StepVerifier::create)
                .expectError(IllegalArgumentException.class)
                .verify(Duration.ofSeconds(1L));
    }

    @Test
    @Timeout(3)
    void skipNodeValidation() {
        nodeValidationProperties.setEnabled(false);
        cryptoServiceStub.addTransactions(Mono.just(response(OK)));

        transactionPublisher.publish(request().build())
                .as(StepVerifier::create)
                .expectNextCount(1L)
                .expectComplete()
                .verify(Duration.ofSeconds(1L));
    }

    @Test
    @Timeout(3)
    void nodeValidationFailsReceipt() {
        cryptoServiceStub.addQueries(Mono.just(receipt(ResponseCodeEnum.ACCOUNT_DELETED)));
        cryptoServiceStub.addTransactions(Mono.just(response(OK)));

        transactionPublisher.publish(request().build())
                .as(StepVerifier::create)
                .expectError(IllegalArgumentException.class)
                .verify(Duration.ofSeconds(1L));
    }

    @Test
    @Timeout(3)
    void someValidNodes() {
        monitorProperties.setNodes(Set.of(new NodeProperties("0.0.3", "in-process:test"),
                new NodeProperties("0.0.4", "invalid:1"))); // Illegal DNS to avoid SDK retry
        cryptoServiceStub.addQueries(Mono.just(receipt(SUCCESS)));
        cryptoServiceStub.addTransactions(Mono.just(response(OK)), Mono.just(response(OK)));

        transactionPublisher.publish(request().build())
                .as(StepVerifier::create)
                .expectNextCount(1L)
                .expectComplete()
                .verify(Duration.ofSeconds(1L));
    }

    @Test
    @Timeout(3)
    void closeWhenDisabled() {
        publishProperties.setEnabled(false);
        transactionPublisher.close();
        transactionPublisher.publish(request().build())
                .as(StepVerifier::create)
                .expectNextCount(0L)
                .expectComplete()
                .verify(Duration.ofSeconds(1L));
    }

    private PublishRequest.PublishRequestBuilder request() {
        return PublishRequest.builder()
                .scenario(new PublishScenario(publishScenarioProperties))
                .timestamp(Instant.now())
                .transaction(new TransferTransaction().setMaxAttempts(2));
    }

    private TransactionResponse response(ResponseCodeEnum responseCode) {
        return TransactionResponse.newBuilder()
                .setNodeTransactionPrecheckCode(responseCode)
                .build();
    }

    private Response receipt(ResponseCodeEnum responseCode) {
        ResponseHeader responseHeader = ResponseHeader.newBuilder()
                .setNodeTransactionPrecheckCode(OK)
                .build();
        return Response.newBuilder()
                .setTransactionGetReceipt(TransactionGetReceiptResponse.newBuilder()
                        .setHeader(responseHeader)
                        .setReceipt(TransactionReceipt.newBuilder().setStatus(responseCode).build())
                        .build())
                .build();
    }

    private Response record(ResponseCodeEnum responseCode) {
        ResponseHeader.Builder responseHeader = ResponseHeader.newBuilder()
                .setNodeTransactionPrecheckCode(OK);
        TransactionReceipt.Builder transactionReceipt = TransactionReceipt.newBuilder().setStatus(responseCode);
        return Response.newBuilder()
                .setTransactionGetRecord(TransactionGetRecordResponse.newBuilder()
                        .setHeader(responseHeader)
                        .setTransactionRecord(TransactionRecord.newBuilder().setReceipt(transactionReceipt)))
                .build();
    }

@Data
private class CryptoServiceStub extends CryptoServiceGrpc.CryptoServiceImplBase {

    private Queue<Mono<TransactionResponse>> transactions = new LinkedList<>();
    private Queue<Mono<Response>> queries = new LinkedList<>();

    void addQueries(Mono<Response>... query) {
        queries.addAll(Arrays.asList(query));
    }

    void addTransactions(Mono<TransactionResponse>... transaction) {
        transactions.addAll(Arrays.asList(transaction));
    }

    @Override
    public void cryptoTransfer(Transaction request, StreamObserver<TransactionResponse> responseObserver) {
        log.debug("cryptoTransfer: {}", request);
        send(responseObserver, transactions.poll());
    }

    @Override
    public void getTransactionReceipts(Query request, StreamObserver<Response> responseObserver) {
        log.debug("getTransactionReceipts: {}", request);
        send(responseObserver, queries.poll());
    }

    @Override
    public void getTxRecordByTxID(Query request, StreamObserver<Response> responseObserver) {
        log.debug("getTxRecordByTxID: {}", request);
        send(responseObserver, queries.poll());
    }

    private <T> void send(StreamObserver<T> responseObserver, Mono<T> response) {
        assertThat(response).isNotNull();
        response.delayElement(Duration.ofMillis(100L))
                .doOnError(responseObserver::onError)
                .doOnNext(responseObserver::onNext)
                .doOnNext(t -> log.trace("Next: {}", t))
                .doOnSuccess(r -> responseObserver.onCompleted())
                .subscribe();
    }

    void verify() {
        assertThat(queries).isEmpty();
        assertThat(transactions).isEmpty();
    }
}
}
