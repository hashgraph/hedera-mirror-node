package com.hedera.mirror.web3.controller;

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

import static com.hedera.mirror.web3.controller.BlockType.EARLIEST;
import static com.hedera.mirror.web3.controller.BlockType.LATEST;
import static com.hedera.mirror.web3.controller.BlockType.PENDING;
import static com.hedera.mirror.web3.controller.validation.AddressValidator.MESSAGE;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_IMPLEMENTED;

import javax.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import java.time.Duration;

@ExtendWith(SpringExtension.class)
@WebFluxTest(controllers = ContractController.class)
class ContractControllerTest {
    private static final String CALL_URI = "/api/v1/contracts/call";
    private static final String NOT_IMPLEMENTED_ERROR = "Operation not supported yet!";
    private static final String NEGATIVE_NUMBER_ERROR = "{} field must be greater than or equal to 0";

    @Resource
    private WebTestClient webClient;

    @Test
    void throwsUnsupportedOperationExceptionWhenCalled() {

        webClient.post()
                .uri(CALL_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request()))
                .exchange()
                .expectStatus()
                .isEqualTo(NOT_IMPLEMENTED)
                .expectBody(GenericErrorResponse.class)
                .isEqualTo(new GenericErrorResponse(NOT_IMPLEMENTED_ERROR));
    }

    @NullAndEmptySource
    @ValueSource(strings = {" ", "0x", "0xghijklmno", "0x000000000000000000000000000000Z0000007e7",
            "00000000001239847e"})
    @ParameterizedTest
    void throwsValidationExceptionWhenCalledWithMissingToField(String to) {
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
    @ValueSource(strings = {"", " ", "0x", "0xghijklmno", "0x000000000000000000000000000000Z0000007e7",
            "00000000001239847e"})
    @ParameterizedTest
    void throwsValidationExceptionWhenCalledWithWrongFromField(String from) {
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
    void throwsValidationExceptionWhenCalledWithWrongValueField() {
        final var errorString = negativeNumberErrorFrom("value");
        final var request = request();
        request.setValue(-1L);

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
    void throwsValidationExceptionWhenCalledWithWrongGasField() {
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
    void throwsValidationExceptionWhenCalledWithWrongGasPriceField() {
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
    void throwsValidationExceptionWhenCalledWithWrongBlockTypeField() {
        final var request = request();
        final var errorResponse = "block field must not be null";
        request.setBlock(null);

        webClient.post()
                .uri(CALL_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .exchange()
                .expectStatus()
                .isEqualTo(BAD_REQUEST)
                .expectBody(GenericErrorResponse.class)
                .isEqualTo(new GenericErrorResponse(errorResponse));
    }

    @ParameterizedTest
    @ValueSource(strings = {"earliest", "pending", "latest"})
    void throwsUnsupportedOperationExceptionWhenCalledWithCorrectBlockTypes(String value) {
        //throwing NOT_IMPLEMENTED_ERROR is desired behavior atm
        final var request = request();
        if (value.equals("earliest")) {
            request.setBlock(EARLIEST);
        } else if (value.equals("pending")) {
            request.setBlock(PENDING);
        } else {
            request.setBlock(LATEST);
        }

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

    private ContractCallRequest request() {
        final var request = new ContractCallRequest();
        request.setTo("0x00000000000000000000000000000000000004e4");
        request.setFrom("0x00000000000000000000000000000000000004e2");
        request.setGas(200000L);
        request.setGasPrice(78282329L);
        request.setValue(23);

        return request;
    }

    private String negativeNumberErrorFrom(String field) {
        return NEGATIVE_NUMBER_ERROR.replace("{}", field);
    }
}
