package com.hedera.mirror.web3.controller;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_IMPLEMENTED;

import javax.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import java.math.BigInteger;

@ExtendWith(SpringExtension.class)
@WebFluxTest(controllers = ContractController.class)
class ContractControllerTest {
    private static final String CALL_URI = "/api/v1/contracts/call";
    private static final String ERROR_JSON_UNSUPPORTED_OP = "{\"_status\":{\"messages\":[{\"message\":\"Operations " +
            "eth_call and gas_estimate are not supported yet!\"}]}}";
    private static final String VALIDATION_JSON_ERROR = "{\"_status\":{\"messages\":[{\"message\":\"to field must not" +
            " be empty\"}]}}";
    @Resource
    private WebTestClient webClient;

    @Test
    void throwsUnsupportedOperationExceptionWhenCalled() {

        webClient.post()
                .uri(CALL_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request(true)))
                .exchange()
                .expectStatus()
                .isEqualTo(NOT_IMPLEMENTED)
                .expectBody()
                .json(ERROR_JSON_UNSUPPORTED_OP);
    }

    @Test
    void throwsValidationExceptionWhenCalledWithWrongRequestData() {

        webClient.post()
                .uri(CALL_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request(false)))
                .exchange()
                .expectStatus()
                .isEqualTo(BAD_REQUEST)
                .expectBody()
                .json(VALIDATION_JSON_ERROR);
    }

    private ContractCallRequest request(boolean isValidRequest) {
        final var to =
                isValidRequest ? "0x00000000000000000000000000000000000004e4"
                        : "";
        return
                new ContractCallRequest(
                        "0x76c0",
                        "0x6f0fccab00000000000000000000000000000000000000000000000000000000000004e5",
                        "0x00000000000000000000000000000000000004e2",
                        1232321L,
                        1232322133134214211L,
                        to,
                        BigInteger.valueOf(100000000000000000L),
                        false);
    }
}
