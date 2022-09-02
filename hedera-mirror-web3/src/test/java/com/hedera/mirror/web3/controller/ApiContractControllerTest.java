package com.hedera.mirror.web3.controller;

import static com.hedera.mirror.web3.controller.ApiContractController.METRIC;
import static com.hedera.mirror.web3.utils.TestConstants.contractHexAddress;
import static com.hedera.mirror.web3.utils.TestConstants.data;
import static com.hedera.mirror.web3.utils.TestConstants.from;
import static com.hedera.mirror.web3.utils.TestConstants.gas;
import static com.hedera.mirror.web3.utils.TestConstants.gasHexValue;
import static com.hedera.mirror.web3.utils.TestConstants.gasPrice;
import static com.hedera.mirror.web3.utils.TestConstants.gasPriceHexValue;
import static com.hedera.mirror.web3.utils.TestConstants.getSenderBalanceInputData;
import static com.hedera.mirror.web3.utils.TestConstants.latestTag;
import static com.hedera.mirror.web3.utils.TestConstants.multiplySimpleNumbersSelector;
import static com.hedera.mirror.web3.utils.TestConstants.receiverHexAddress;
import static com.hedera.mirror.web3.utils.TestConstants.senderHexAddress;
import static com.hedera.mirror.web3.utils.TestConstants.to;
import static com.hedera.mirror.web3.utils.TestConstants.transferHbarsToReceiverInputData;
import static com.hedera.mirror.web3.utils.TestConstants.value;
import static com.hedera.mirror.web3.utils.TestConstants.valueHexValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    void successForEthGasEstimateTransferHbarsDirectly() {
        JsonRpcRequest jsonRpcRequest = new JsonRpcRequest();
        jsonRpcRequest.setId(1L);
        jsonRpcRequest.setJsonrpc(JsonRpcResponse.VERSION);
        jsonRpcRequest.setMethod(ETH_GAS_ESTIMATE_METHOD);
        jsonRpcRequest.setParams(getJsonRpcRequestParams(receiverHexAddress, ""));

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
    void successForEthGasEstimateTransferHbarsInSmartContract() {
        JsonRpcRequest jsonRpcRequest = new JsonRpcRequest();
        jsonRpcRequest.setId(1L);
        jsonRpcRequest.setJsonrpc(JsonRpcResponse.VERSION);
        jsonRpcRequest.setMethod(ETH_GAS_ESTIMATE_METHOD);
        jsonRpcRequest.setParams(getJsonRpcRequestParams(contractHexAddress, transferHbarsToReceiverInputData));

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
        JsonRpcRequest jsonRpcRequest = new JsonRpcRequest();
        jsonRpcRequest.setId(1L);
        jsonRpcRequest.setJsonrpc(JsonRpcResponse.VERSION);
        jsonRpcRequest.setMethod(ETH_CALL_METHOD);
        jsonRpcRequest.setParams(getJsonRpcRequestParams(contractHexAddress, multiplySimpleNumbersSelector));

        when(serviceFactory.lookup(ETH_CALL_METHOD)).thenReturn(new DummyEthCallService(4L));

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

    @Test
    void successForEthCallForAccountBalanceFunction() {
        JsonRpcRequest jsonRpcRequest = new JsonRpcRequest();
        jsonRpcRequest.setId(1L);
        jsonRpcRequest.setJsonrpc(JsonRpcResponse.VERSION);
        jsonRpcRequest.setMethod(ETH_CALL_METHOD);
        jsonRpcRequest.setParams(getJsonRpcRequestParams(contractHexAddress, getSenderBalanceInputData));

        when(serviceFactory.lookup(ETH_CALL_METHOD)).thenReturn(new DummyEthCallService(560000261L));

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
                .jsonPath("$.result").isEqualTo(Bytes.wrap(String.valueOf(560000261L).getBytes()).toHexString());

        verify(serviceFactory).lookup(ETH_CALL_METHOD);
        assertThat(meterRegistry.find(METRIC).timers())
                .hasSize(1)
                .first()
                .returns(ETH_CALL_METHOD, t -> t.getId().getTag("method"))
                .returns(JsonRpcSuccessResponse.SUCCESS, t -> t.getId().getTag("status"));
    }

    private List getJsonRpcRequestParams(final String recipientHexValue, final String dataHexValue) {
        final var ethParams = new HashMap<>();
        ethParams.put(from, senderHexAddress);
        ethParams.put(to, recipientHexValue);
        ethParams.put(gas, gasHexValue);
        ethParams.put(gasPrice, gasPriceHexValue);
        ethParams.put(value, valueHexValue);
        ethParams.put(data, dataHexValue);
        final var params = new ArrayList<>();
        params.add(ethParams);
        params.add(latestTag);

        return params;
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

        private final long value;

        public DummyEthCallService(final long value) {
            this.value = value;
        }

        @Override
        public String getMethod() {
            return ETH_CALL_METHOD;
        }

        @Override
        public Object get(Object request) {
            return Bytes.wrap(String.valueOf(value).getBytes()).toHexString();
        }
    }
}
