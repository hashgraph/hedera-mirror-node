package com.hedera.mirror.web3.controller;

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

import static com.hedera.mirror.web3.controller.Web3Controller.METRIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import javax.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import com.hedera.mirror.web3.exception.InvalidParametersException;
import com.hedera.mirror.web3.service.Web3Service;
import com.hedera.mirror.web3.service.Web3ServiceFactory;

@ExtendWith(SpringExtension.class)
@WebFluxTest(controllers = Web3Controller.class)
class Web3ControllerTest {

    private static final String METHOD = "dummy";
    private static final String RESULT = "0x1";

    @Resource
    private WebTestClient webClient;

    @Resource
    private MeterRegistry meterRegistry;

    @MockBean
    private Web3ServiceFactory serviceFactory;

    @Resource
    private Web3Controller web3Controller;

    @BeforeEach
    void setup() {
        meterRegistry.clear();
        web3Controller.timers.clear();
        when(serviceFactory.isValid(METHOD)).thenReturn(true);
    }

    @Test
    void success() {
        JsonRpcRequest jsonRpcRequest = new JsonRpcRequest();
        jsonRpcRequest.setId(1L);
        jsonRpcRequest.setJsonrpc(JsonRpcResponse.VERSION);
        jsonRpcRequest.setMethod(METHOD);

        when(serviceFactory.lookup(METHOD)).thenReturn(new DummyWeb3Service());

        webClient.post()
                .uri("/web3/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(jsonRpcRequest))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.error").doesNotExist()
                .jsonPath("$.id").isEqualTo(jsonRpcRequest.getId())
                .jsonPath("$.jsonrpc").isEqualTo(JsonRpcResponse.VERSION)
                .jsonPath("$.result").isEqualTo(RESULT);

        verify(serviceFactory).lookup(METHOD);
        assertThat(meterRegistry.find(METRIC).timers())
                .hasSize(1)
                .first()
                .returns(METHOD, t -> t.getId().getTag("method"))
                .returns(JsonRpcSuccessResponse.SUCCESS, t -> t.getId().getTag("status"));
    }

    @NullSource
    @ValueSource(longs = {Long.MIN_VALUE, -1L})
    @ParameterizedTest
    void invalidId(Long id) {
        JsonRpcRequest jsonRpcRequest = new JsonRpcRequest();
        jsonRpcRequest.setId(id);
        jsonRpcRequest.setJsonrpc(JsonRpcResponse.VERSION);
        jsonRpcRequest.setMethod(METHOD);

        assertError(jsonRpcRequest, JsonRpcErrorCode.INVALID_REQUEST, "id field must")
                .jsonPath("$.id").isEmpty();
    }

    @NullSource
    @ValueSource(strings = {"", " ", "0", "1.0", "2", "2.1"})
    @ParameterizedTest
    void invalidJsonrpc(String jsonrpc) {
        JsonRpcRequest jsonRpcRequest = new JsonRpcRequest();
        jsonRpcRequest.setId(1L);
        jsonRpcRequest.setJsonrpc(jsonrpc);
        jsonRpcRequest.setMethod(METHOD);

        assertError(jsonRpcRequest, JsonRpcErrorCode.INVALID_REQUEST, "jsonrpc field must")
                .jsonPath("$.id").isEqualTo(jsonRpcRequest.getId());
    }

    @NullSource
    @ValueSource(strings = {"", " "})
    @ParameterizedTest
    void invalidMethod(String method) {
        JsonRpcRequest jsonRpcRequest = new JsonRpcRequest();
        jsonRpcRequest.setId(1L);
        jsonRpcRequest.setJsonrpc(JsonRpcResponse.VERSION);
        jsonRpcRequest.setMethod(method);

        assertError(jsonRpcRequest, JsonRpcErrorCode.INVALID_REQUEST, "method field must")
                .jsonPath("$.id").isEqualTo(jsonRpcRequest.getId());
    }

    @Test
    void methodNotFound() {
        JsonRpcRequest jsonRpcRequest = new JsonRpcRequest();
        jsonRpcRequest.setId(1L);
        jsonRpcRequest.setJsonrpc(JsonRpcResponse.VERSION);
        jsonRpcRequest.setMethod("invalid");

        assertError(jsonRpcRequest, JsonRpcErrorCode.METHOD_NOT_FOUND)
                .jsonPath("$.id").isEqualTo(jsonRpcRequest.getId());
    }

    @Test
    void invalidParamsError() {
        JsonRpcRequest jsonRpcRequest = new JsonRpcRequest();
        jsonRpcRequest.setId(1L);
        jsonRpcRequest.setJsonrpc(JsonRpcResponse.VERSION);
        jsonRpcRequest.setMethod(METHOD);

        when(serviceFactory.lookup(METHOD)).thenThrow(new InvalidParametersException("error"));

        assertError(jsonRpcRequest, JsonRpcErrorCode.INVALID_PARAMS, "error")
                .jsonPath("$.id").isEqualTo(jsonRpcRequest.getId());
    }

    @Test
    void internalError() {
        JsonRpcRequest jsonRpcRequest = new JsonRpcRequest();
        jsonRpcRequest.setId(1L);
        jsonRpcRequest.setJsonrpc(JsonRpcResponse.VERSION);
        jsonRpcRequest.setMethod(METHOD);

        when(serviceFactory.lookup(METHOD)).thenThrow(new IllegalStateException("error"));

        assertError(jsonRpcRequest, JsonRpcErrorCode.INTERNAL_ERROR)
                .jsonPath("$.id").isEqualTo(jsonRpcRequest.getId());
    }

    @ValueSource(strings = {"", "{foo:bar,", "[]"})
    @ParameterizedTest
    void parseError(String payload) {
        assertError(payload, JsonRpcErrorCode.PARSE_ERROR)
                .jsonPath("$.id").isEmpty();
    }

    private WebTestClient.BodyContentSpec assertError(Object payload, JsonRpcErrorCode errorCode, String... messages) {
        List<String> m = ImmutableList.<String>builder().add(errorCode.getMessage()).add(messages).build();
        var bodyContentSpec = webClient.post()
                .uri("/web3/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(payload))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo(errorCode.getCode())
                .jsonPath("$.jsonrpc").isEqualTo(JsonRpcResponse.VERSION)
                .jsonPath("$.result").doesNotExist()
                .jsonPath("$.error.message")
                .value(s -> assertThat(s).contains(m), String.class);

        String method = payload instanceof JsonRpcRequest && METHOD.equals(((JsonRpcRequest) payload).getMethod()) ?
                METHOD : "unknown";
        assertThat(meterRegistry.find(METRIC).timers())
                .hasSize(1)
                .first()
                .returns(method, t -> t.getId().getTag("method"))
                .returns(errorCode.name(), t -> t.getId().getTag("status"));

        return bodyContentSpec;
    }

    @TestConfiguration
    static class Config {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    private class DummyWeb3Service implements Web3Service<Object, Object> {

        @Override
        public String getMethod() {
            return METHOD;
        }

        @Override
        public Object get(Object request) {
            return RESULT;
        }
    }
}
