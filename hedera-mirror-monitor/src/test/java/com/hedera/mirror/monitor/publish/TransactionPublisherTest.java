package com.hedera.mirror.monitor.publish;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import static com.hedera.hashgraph.sdk.proto.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hedera.hashgraph.sdk.proto.ResponseCodeEnum.OK;
import static com.hedera.hashgraph.sdk.proto.ResponseCodeEnum.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.util.concurrent.Uninterruptibles;
import io.grpc.Server;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
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
import com.hedera.mirror.monitor.OperatorProperties;
import com.hedera.mirror.monitor.publish.transaction.TransactionType;

@Log4j2
class TransactionPublisherTest {

    private CryptoServiceStub cryptoServiceStub;
    private MonitorProperties monitorProperties;
    private PublishProperties publishProperties;
    private PublishScenarioProperties publishScenarioProperties;
    private Server server;
    private TransactionPublisher transactionPublisher;

    @BeforeEach
    void setup() throws IOException {
        publishScenarioProperties = new PublishScenarioProperties();
        publishScenarioProperties.setName("test");
        publishScenarioProperties.setType(TransactionType.CRYPTO_TRANSFER);
        monitorProperties = new MonitorProperties();
        monitorProperties.setNodes(Set.of(new NodeProperties("0.0.3", "in-process:test")));
        monitorProperties.getNodeValidation().setEnabled(false);
        OperatorProperties operatorProperties = monitorProperties.getOperator();
        operatorProperties.setAccountId("0.0.100");
        operatorProperties.setPrivateKey(PrivateKey.generate().toString());
        publishProperties = new PublishProperties();
        transactionPublisher = new TransactionPublisher(monitorProperties, publishProperties);
        cryptoServiceStub = new CryptoServiceStub();
        server = InProcessServerBuilder.forName("test")
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
    }

    @Test
    @Timeout(3)
    void publish() {
        PublishRequest request = request().build();
        cryptoServiceStub.addTransactions(Mono.just(response(OK)));

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
        cryptoServiceStub.addTransactions(Mono.just(response(OK)));

        transactionPublisher.publish(request().build())
                .as(StepVerifier::create)
                .expectNextCount(1L)
                .expectComplete()
                .verify(Duration.ofSeconds(1L));
    }

    @Test
    @Timeout(3)
    void publishWithReceipt() {
        cryptoServiceStub.addQueries(Mono.just(receipt(SUCCESS)));
        cryptoServiceStub.addTransactions(Mono.just(response(OK)));

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
        cryptoServiceStub.addQueries(Mono.just(record(SUCCESS)));
        cryptoServiceStub.addTransactions(Mono.just(response(OK)));

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
        cryptoServiceStub.addTransactions(Mono.just(response(errorResponseCode)));

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
        cryptoServiceStub.addTransactions(Mono.just(response(ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED)),
                Mono.just(response(ResponseCodeEnum.OK)));

        transactionPublisher.publish(request().build())
                .as(StepVerifier::create)
                .expectNextCount(1L)
                .expectComplete()
                .verify(Duration.ofSeconds(1L));
    }

    @Test
    @Timeout(3)
    void validationRecovers() {
        // Initialize publisher internals with first transaction
        var request = request().build();
        cryptoServiceStub.addTransactions(Mono.just(response(OK)));
        transactionPublisher.publish(request)
                .as(StepVerifier::create)
                .expectNextCount(1L)
                .expectComplete()
                .verify(Duration.ofSeconds(1L));
        var scenario = request.getScenario();
        assertThat(scenario.getCount()).isEqualTo(1);
        assertThat(scenario.getErrors()).isEmpty();

        // Validate node as down manually
        NodeProperties nodeProperties = monitorProperties.getNodes().iterator().next();
        cryptoServiceStub.addQueries(Mono.just(receipt(ACCOUNT_DELETED)));
        cryptoServiceStub.addTransactions(Mono.just(response(OK)));
        assertThat(transactionPublisher.validateNode(nodeProperties)).isFalse();

        request = request().build();
        transactionPublisher.publish(request)
                .as(StepVerifier::create)
                .expectErrorSatisfies(t -> assertThat(t)
                        .isInstanceOf(PublishException.class)
                        .hasMessageContaining("No valid nodes available")
                        .hasCauseInstanceOf(IllegalArgumentException.class))
                .verify(Duration.ofSeconds(1L));
        scenario = request.getScenario();
        assertThat(scenario.getCount()).isZero();
        assertThat(scenario.getErrors()).containsOnly(Map.entry(IllegalArgumentException.class.getSimpleName(), 1));

        // Node recovers
        cryptoServiceStub.addQueries(Mono.just(receipt(SUCCESS)));
        cryptoServiceStub.addTransactions(Mono.just(response(OK)));
        assertThat(transactionPublisher.validateNode(nodeProperties)).isTrue();

        request = request().build();
        cryptoServiceStub.addTransactions(Mono.just(response(OK)));
        transactionPublisher.publish(request)
                .as(StepVerifier::create)
                .expectNextCount(1L)
                .expectComplete()
                .verify(Duration.ofSeconds(1L));
        scenario = request.getScenario();
        assertThat(scenario.getCount()).isEqualTo(1);
        assertThat(scenario.getErrors()).isEmpty();
    }

    @Test
    @Timeout(3)
    void validationSucceeds() {
        monitorProperties.getNodeValidation().setEnabled(true);
        cryptoServiceStub.addQueries(Mono.just(receipt(SUCCESS)));
        cryptoServiceStub.addTransactions(Mono.just(response(OK)), Mono.just(response(OK)));
        transactionPublisher.publish(request().build())
                .as(StepVerifier::create)
                .expectNextCount(1L)
                .expectComplete()
                .verify(Duration.ofSeconds(1L));

        // Wait for validation thread to succeed
        Uninterruptibles.sleepUninterruptibly(Duration.ofMillis(500L));

        cryptoServiceStub.addTransactions(Mono.just(response(OK)));
        transactionPublisher.publish(request().build())
                .as(StepVerifier::create)
                .expectNextCount(1L)
                .expectComplete()
                .verify(Duration.ofSeconds(1L));
    }

    @Test
    @Timeout(3)
    void publishRetryError() {
        ResponseCodeEnum errorResponseCode = ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
        cryptoServiceStub.addTransactions(Mono.just(response(errorResponseCode)),
                Mono.just(response(errorResponseCode)));

        transactionPublisher.publish(request().build())
                .as(StepVerifier::create)
                .expectErrorSatisfies(t -> assertThat(t)
                        .isInstanceOf(PublishException.class)
                        .hasMessageContaining("exceeded maximum attempts for request with last exception being")
                        .getRootCause()
                        .hasMessageContaining(errorResponseCode.toString()))
                .verify(Duration.ofSeconds(2L));
    }

    @Test
    @Timeout(3)
    void publishRetrySameRequest() {
        ResponseCodeEnum errorResponseCode = ResponseCodeEnum.PLATFORM_NOT_ACTIVE;
        cryptoServiceStub.addTransactions(Mono.just(response(errorResponseCode)),
                Mono.just(response(errorResponseCode)));

        var request = request().build();
        transactionPublisher.publish(request)
                .as(StepVerifier::create)
                .expectErrorSatisfies(t -> assertThat(t)
                        .isInstanceOf(PublishException.class)
                        .hasMessageContaining("exceeded maximum attempts for request with last exception being")
                        .getRootCause()
                        .hasMessageContaining(errorResponseCode.toString()))
                .verify(Duration.ofSeconds(2L));

        cryptoServiceStub.addTransactions(Mono.just(response(OK)));
        transactionPublisher.publish(request)
                .as(StepVerifier::create)
                .expectNextCount(1L)
                .expectComplete()
                .verify(Duration.ofSeconds(1L));
    }

    @Test
    @Timeout(3)
    void publishTimeout() {
        publishScenarioProperties.setTimeout(Duration.ofMillis(100L));
        cryptoServiceStub.addTransactions(Mono.delay(Duration.ofMillis(500L)).thenReturn(response(OK)));

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
    void someValidNodes() {
        NodeProperties node1 = new NodeProperties("0.0.3", "in-process:test");
        NodeProperties node2 = new NodeProperties("0.0.4", "invalid:test");
        monitorProperties.setNodes(Set.of(node1, node2));

        // Initialize publisher internals with first transaction
        cryptoServiceStub.addTransactions(Mono.just(response(OK)));
        PublishRequest publishRequest = request().build();
        publishRequest.getTransaction().setNodeAccountIds(node1.getAccountIds());
        transactionPublisher.publish(publishRequest)
                .as(StepVerifier::create)
                .expectNextCount(1L)
                .expectComplete()
                .verify(Duration.ofSeconds(1L));

        // Validate one of the nodes as down manually
        assertThat(transactionPublisher.validateNode(node2)).isFalse();

        cryptoServiceStub.addTransactions(Mono.just(response(OK)));
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
