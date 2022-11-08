package com.hedera.mirror.monitor.subscribe.rest;

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

import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ConnectException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import com.hedera.mirror.monitor.MirrorNodeProperties;
import com.hedera.mirror.monitor.MonitorProperties;
import com.hedera.mirror.rest.model.Links;
import com.hedera.mirror.rest.model.NetworkNode;
import com.hedera.mirror.rest.model.NetworkNodesResponse;
import com.hedera.mirror.rest.model.TransactionByIdResponse;

@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class RestApiClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private ExchangeFunction exchangeFunction;

    private MonitorProperties monitorProperties;
    private RestApiClient restApiClient;

    @BeforeEach
    void setup() {
        monitorProperties = new MonitorProperties();
        monitorProperties.setMirrorNode(new MirrorNodeProperties());
        monitorProperties.getMirrorNode().getRest().setHost("127.0.0.1");

        WebClient.Builder builder = WebClient.builder().exchangeFunction(exchangeFunction);
        restApiClient = new RestApiClient(monitorProperties, builder);
    }

    @Test
    void getNodes() {
        var next = "/network/nodes?limit=25";
        NetworkNode networkNode1 = new NetworkNode();
        NetworkNode networkNode2 = new NetworkNode();
        NetworkNode networkNode3 = new NetworkNode();
        var response1 = new NetworkNodesResponse().links(new Links().next(next + "&node.id=gt:1"))
                .nodes(List.of(networkNode1, networkNode2));
        var response2 = new NetworkNodesResponse().links(new Links()).nodes(List.of(networkNode3));

        when(exchangeFunction.exchange(isA(ClientRequest.class)))
                .thenReturn(response(response1))
                .thenReturn(response(response2));

        restApiClient.getNodes()
                .as(StepVerifier::create)
                .expectNext(networkNode1)
                .expectNext(networkNode2)
                .expectNext(networkNode3)
                .expectComplete()
                .verify(Duration.ofSeconds(2L));

        verify(exchangeFunction, times(2)).exchange(isA(ClientRequest.class));
    }

    @Test
    void getNodesEmpty() {
        var response = new NetworkNodesResponse().links(new Links()).nodes(List.of());
        when(exchangeFunction.exchange(isA(ClientRequest.class)))
                .thenReturn(response(response));

        restApiClient.getNodes()
                .as(StepVerifier::create)
                .expectComplete()
                .verify(Duration.ofSeconds(2L));

        verify(exchangeFunction).exchange(isA(ClientRequest.class));
    }

    @Test
    void retrieve() {
        var response = new TransactionByIdResponse();
        when(exchangeFunction.exchange(isA(ClientRequest.class)))
                .thenReturn(response(response));

        restApiClient.retrieve(TransactionByIdResponse.class, "transactions/{transactionId}", "1.1")
                .as(StepVerifier::create)
                .expectNext(response)
                .expectComplete()
                .verify(Duration.ofSeconds(2L));

        verify(exchangeFunction).exchange(isA(ClientRequest.class));
    }

    @Test
    void retrieveConnectError() {
        restApiClient = new RestApiClient(monitorProperties, WebClient.builder());
        restApiClient.retrieve(TransactionByIdResponse.class, "transactions/{transactionId}", "1.1")
                .as(StepVerifier::create)
                .expectErrorMatches(t -> t.getCause() instanceof ConnectException)
                .verify(Duration.ofSeconds(2L));
    }

    private Mono<ClientResponse> response(Object response) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(response);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(json)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
