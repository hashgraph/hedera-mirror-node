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

@ExtendWith(SpringExtension.class)
@WebFluxTest(controllers = ContractController.class)
class ContractControllerTest {
    private static final String CALL_URI = "/api/v1/contracts/call";

    @Resource
    private WebTestClient webClient;

    @Test
    void throwsUnsupportedOperationExceptionWhenCalled() {
        final var errorString = "Operations eth_call and gas_estimate are not supported yet!";

        webClient.post()
                .uri(CALL_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request()))
                .exchange()
                .expectStatus()
                .isEqualTo(NOT_IMPLEMENTED)
                .expectBody(GenericErrorResponse.class)
                .isEqualTo(new GenericErrorResponse(errorString));
    }

    @Test
    void throwsValidationExceptionWhenCalledWithMissingToField() {
        final var request = request();
        request.setTo("");

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
    void throwsValidationExceptionWhenCalledWithWrongFromField() {
        final var errorString = "from field must be 20 bytes hex format";
        final var request = request();
        request.setFrom("");

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
        final var errorString = "value field must be greater than or equal to 0";
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
        final var errorString = "gas field must be greater than or equal to 0";
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
        final var errorString = "gasPrice field must be greater than or equal to 0";
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

    private ContractCallRequest request() {
        final var request = new ContractCallRequest();
        request.setTo("0x00000000000000000000000000000000000004e4");
        request.setFrom("0x00000000000000000000000000000000000004e2");
        request.setGas(200000L);
        request.setGasPrice(78282329L);
        request.setValue(23);

        return request;
    }
}
