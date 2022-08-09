package com.hedera.mirror.web3.controller;

import static com.hedera.mirror.web3.controller.ApiContractController.METRIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import javax.annotation.Resource;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import com.hedera.mirror.web3.service.ApiContractService;
import com.hedera.mirror.web3.service.ApiContractServiceFactory;

@ExtendWith(SpringExtension.class)
@WebFluxTest(controllers = ApiContractController.class)
class ApiContractControllerTest {

    private static final String ETH_CALL_METHOD = "eth_call";
    private static final String ETH_GAS_ESTIMATE_METHOD = "eth_gasEstimate";

    @Resource
    private WebTestClient webClient;

    @Resource
    private MeterRegistry meterRegistry;

    @MockBean
    private ApiContractServiceFactory serviceFactory;

    @Resource
    private ApiContractController apiContractController;

    @BeforeEach
    void setup() {
        meterRegistry.clear();
        apiContractController.timers.clear();
        when(serviceFactory.isValid(ETH_GAS_ESTIMATE_METHOD)).thenReturn(true);
    }

    @Test
    void successForEthGasEstimate() {
        final var ethParams = new HashMap<>();
        ethParams.put("from", "0x00000000000000000000000000000000000004e2");
        ethParams.put("to", "0x00000000000000000000000000000000000004e3");
        ethParams.put("gas", "0x76c0");
        ethParams.put("gasPrice", "0x76c0");
        ethParams.put("value", "0x76c0");
        ethParams.put("data", "0xd46e8dd67c5d32be8d46e8dd67c5d32be8058bb8eb970870f072445675058bb8eb970870f072445675");
        final var params = new ArrayList<>();
        params.add(ethParams);
        params.add("latest");

        JsonRpcRequest jsonRpcRequest = new JsonRpcRequest();
        jsonRpcRequest.setId(1L);
        jsonRpcRequest.setJsonrpc(JsonRpcResponse.VERSION);
        jsonRpcRequest.setMethod(ETH_GAS_ESTIMATE_METHOD);
        jsonRpcRequest.setParams(params);

        when(serviceFactory.lookup(ETH_GAS_ESTIMATE_METHOD)).thenReturn(new DummyEthGasEstimateService());

        webClient.post()
                .uri("/api/v1/contracts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(jsonRpcRequest))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.error").doesNotExist()
                .jsonPath("$.id").isEqualTo(jsonRpcRequest.getId())
                .jsonPath("$.jsonrpc").isEqualTo(JsonRpcResponse.VERSION)
                .jsonPath("$.result").isEqualTo(Bytes.wrap(String.valueOf(100L).getBytes()).toHexString());

        verify(serviceFactory).lookup(ETH_GAS_ESTIMATE_METHOD);
        assertThat(meterRegistry.find(METRIC).timers())
                .hasSize(1)
                .first()
                .returns(ETH_GAS_ESTIMATE_METHOD, t -> t.getId().getTag("method"))
                .returns(JsonRpcSuccessResponse.SUCCESS, t -> t.getId().getTag("status"));
    }

    @Test
    void successForEthCallForPureFunction() {
        final var ethParams = new HashMap<>();
        ethParams.put("from", "0x00000000000000000000000000000000000004e2");
        ethParams.put("to", "0x00000000000000000000000000000000000004e4");
        ethParams.put("gas", "0x76c0");
        ethParams.put("gasPrice", "0x76c0");
        ethParams.put("value", "0x76c0");
        ethParams.put("data", "0x8070450f");
        final var params = new ArrayList<>();
        params.add(ethParams);
        params.add("latest");

        JsonRpcRequest jsonRpcRequest = new JsonRpcRequest();
        jsonRpcRequest.setId(1L);
        jsonRpcRequest.setJsonrpc(JsonRpcResponse.VERSION);
        jsonRpcRequest.setMethod(ETH_CALL_METHOD);
        jsonRpcRequest.setParams(params);

        when(serviceFactory.lookup(ETH_CALL_METHOD)).thenReturn(new DummyEthCallService());

        webClient.post()
                .uri("/api/v1/contracts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(jsonRpcRequest))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.error").doesNotExist()
                .jsonPath("$.id").isEqualTo(jsonRpcRequest.getId())
                .jsonPath("$.jsonrpc").isEqualTo(JsonRpcResponse.VERSION)
                .jsonPath("$.result").isEqualTo(Bytes.wrap(String.valueOf(4L).getBytes()).toHexString());

        verify(serviceFactory).lookup(ETH_CALL_METHOD);
        assertThat(meterRegistry.find(METRIC).timers())
                .hasSize(1)
                .first()
                .returns(ETH_CALL_METHOD, t -> t.getId().getTag("method"))
                .returns(JsonRpcSuccessResponse.SUCCESS, t -> t.getId().getTag("status"));
    }

    @TestConfiguration
    static class Config {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    private class DummyEthGasEstimateService implements ApiContractService<Object, Object> {

        @Override
        public String getMethod() {
            return ETH_GAS_ESTIMATE_METHOD;
        }

        @Override
        public Object get(Object request) {
            return Bytes.wrap(String.valueOf(100L).getBytes()).toHexString();
        }
    }

    private class DummyEthCallService implements ApiContractService<Object, Object> {

        @Override
        public String getMethod() {
            return ETH_CALL_METHOD;
        }

        @Override
        public Object get(Object request) {
            return Bytes.wrap(String.valueOf(4L).getBytes()).toHexString();
        }
    }
}
