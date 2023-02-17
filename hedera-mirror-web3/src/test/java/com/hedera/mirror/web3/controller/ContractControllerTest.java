package com.hedera.mirror.web3.controller;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.web3.controller.ContractController.NOT_IMPLEMENTED_ERROR;
import static com.hedera.mirror.web3.validation.HexValidator.MESSAGE;
import static org.mockito.BDDMockito.given;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_IMPLEMENTED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

import com.hedera.mirror.web3.exception.InvalidTransactionException;

import io.github.bucket4j.Bucket;
import javax.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import com.hedera.mirror.web3.service.ContractCallService;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.mirror.web3.viewmodel.ContractCallRequest;
import com.hedera.mirror.web3.viewmodel.GenericErrorResponse;

@ExtendWith(SpringExtension.class)
@WebFluxTest(controllers = ContractController.class)
class ContractControllerTest {

    private static final String CALL_URI = "/api/v1/contracts/call";
    private static final String NEGATIVE_NUMBER_ERROR = "{} field must be greater than or equal to 0";

    @Resource
    private WebTestClient webClient;

    @MockBean
    private ContractCallService service;

    @MockBean
    private Bucket bucket;

    @BeforeEach
    void setUp(){
        given(bucket.tryConsume(1)).willReturn(true);
    }

    @Test
    void estimateGas() {
        final var request = request();
        request.setEstimate(true);
        webClient.post()
                .uri(CALL_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .exchange()
                .expectStatus()
                .isEqualTo(NOT_IMPLEMENTED)
                .expectBody(GenericErrorResponse.class)
                .isEqualTo(new GenericErrorResponse(NOT_IMPLEMENTED_ERROR));
    }

    @Test
    void exceedingRateLimit() {
        for (var i = 0; i < 3; i++) {
            webClient.post()
                    .uri(CALL_URI)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(request()))
                    .exchange()
                    .expectStatus()
                    .isEqualTo(OK);
        }
        given(bucket.tryConsume(1)).willReturn(false);

        webClient.post()
                .uri(CALL_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request()))
                .exchange()
                .expectStatus()
                .isEqualTo(TOO_MANY_REQUESTS);
    }

    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "0x",
            "0xghijklmno",
            "0x00000000000000000000000000000000000004e",
            "0x00000000000000000000000000000000000004e2a",
            "0x000000000000000000000000000000Z0000007e7",
            "00000000001239847e"
    })
    @ParameterizedTest
    void callInvalidTo(String to) {
        final var request = request();
        request.setTo(to);

        webClient.post()
                .uri(CALL_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .exchange()
                .expectStatus()
                .isEqualTo(BAD_REQUEST)
                .expectBody(GenericErrorResponse.class);
    }

    @EmptySource
    @ValueSource(strings = {
            " ",
            "0x",
            "0xghijklmno",
            "0x00000000000000000000000000000000000004e",
            "0x00000000000000000000000000000000000004e2a",
            "0x000000000000000000000000000000Z0000007e7",
            "00000000001239847e"
    })
    @ParameterizedTest
    void callInvalidFrom(String from) {
        final var errorString = "from field ".concat(MESSAGE);
        final var request = request();
        request.setFrom(from);

        webClient.post()
                .uri(CALL_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .exchange()
                .expectStatus()
                .isEqualTo(BAD_REQUEST)
                .expectBody(GenericErrorResponse.class)
                .isEqualTo(new GenericErrorResponse(errorString));
    }

    @Test
    void callInvalidValue() {
        final var request = request();
        request.setValue(-1L);

        webClient.post()
                .uri(CALL_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .exchange()
                .expectStatus()
                .isEqualTo(BAD_REQUEST)
                .expectBody(GenericErrorResponse.class);
    }

    @Test
    void callRevertMethodAndExpectDetailMessage() {
        final var detailedErrorMessage = "Custom revert message";
        final var request = request();
        request.setData("0xa26388bb");

        given(service.processCall(any())).willThrow(new InvalidTransactionException(CONTRACT_REVERT_EXECUTED, detailedErrorMessage));

        webClient.post()
                .uri(CALL_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .exchange()
                .expectStatus()
                .isEqualTo(BAD_REQUEST)
                .expectBody(GenericErrorResponse.class)
                .isEqualTo(new GenericErrorResponse(CONTRACT_REVERT_EXECUTED.name(), detailedErrorMessage));
    }

    @Test
    void callInvalidGas() {
        final var errorString = negativeNumberErrorFrom("gas");
        final var request = request();
        request.setGas(-1L);

        webClient.post()
                .uri(CALL_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .exchange()
                .expectStatus()
                .isEqualTo(BAD_REQUEST)
                .expectBody(GenericErrorResponse.class)
                .isEqualTo(new GenericErrorResponse(errorString));
    }

    @Test
    void callInvalidGasPrice() {
        final var errorString = negativeNumberErrorFrom("gasPrice");
        final var request = request();
        request.setGasPrice(-1L);

        webClient.post()
                .uri(CALL_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .exchange()
                .expectStatus()
                .isEqualTo(BAD_REQUEST)
                .expectBody(GenericErrorResponse.class)
                .isEqualTo(new GenericErrorResponse(errorString));
    }

    @Test
    void transferWithoutSender() {
        final var errorString = "from field must not be null";
        final var request = request();
        request.setFrom(null);

        webClient.post()
                .uri(CALL_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .exchange()
                .expectStatus()
                .isEqualTo(BAD_REQUEST)
                .expectBody(GenericErrorResponse.class)
                .isEqualTo(new GenericErrorResponse(errorString));
    }

    @NullAndEmptySource
    @ParameterizedTest
    @ValueSource(strings = {"earliest", "pending", "latest", "0", "0x1a"})
    void callValidBlockType(String value) {
        final var request = request();
        request.setBlock(BlockType.of(value));

        webClient.post()
                .uri(CALL_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .exchange()
                .expectStatus()
                .isEqualTo(OK);
    }

    @Test
    void callSuccess() {
        final var request = request();
        request.setData("0x1079023a0000000000000000000000000000000000000000000000000000000000000156");
        request.setValue(0);

        webClient.post()
                .uri(CALL_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .exchange()
                .expectStatus()
                .isEqualTo(OK);
    }

    @Test
    void transferSuccess() {
        webClient.post()
                .uri(CALL_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request()))
                .exchange()
                .expectStatus()
                .isEqualTo(OK);
    }

    private ContractCallRequest request() {
        final var request = new ContractCallRequest();
        request.setFrom("0x00000000000000000000000000000000000004e2");
        request.setGas(200000L);
        request.setGasPrice(78282329L);
        request.setTo("0x00000000000000000000000000000000000004e4");
        request.setValue(23);
        return request;
    }

    private String negativeNumberErrorFrom(String field) {
        return NEGATIVE_NUMBER_ERROR.replace("{}", field);
    }
}
