package com.hedera.mirror.web3.controller;

import static com.hedera.mirror.web3.controller.ApiContractController.METRIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import javax.annotation.Resource;
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

import com.hedera.mirror.web3.service.ApiContractServiceFactory;
import com.hedera.mirror.web3.service.eth.ApiContractEthService;

@ExtendWith(SpringExtension.class)
@WebFluxTest(controllers = ApiContractController.class)
class ApiContractControllerTest {
    private static final String METHOD = "eth_call";
    private static final String RESULT = "0x1";

    @Resource
    private WebTestClient webClient;

    @Resource
    private ApiContractController apiContractController;

    @Resource
    private MeterRegistry meterRegistry;

    @MockBean
    private ApiContractServiceFactory serviceFactory;

    @BeforeEach
    void setup() {
        meterRegistry.clear();
        apiContractController.timers.clear();
        when(serviceFactory.isValid(METHOD)).thenReturn(true);
    }

    @Test
    void success() {
        JsonRpcRequest jsonRpcRequest = new JsonRpcRequest();
        jsonRpcRequest.setId(1L);
        jsonRpcRequest.setJsonrpc(JsonRpcResponse.VERSION);
        jsonRpcRequest.setMethod(METHOD);
        jsonRpcRequest.setParams(buildDummyParams());

        when(serviceFactory.lookup(METHOD)).thenReturn(new DummyEthCallService());
        webClient.post()
                .uri("/api/v1/contracts?estimate=true")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(jsonRpcRequest))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.error").doesNotExist()
                .jsonPath("$.id").isEqualTo(jsonRpcRequest.getId())
                .jsonPath("$.jsonrpc").isEqualTo(JsonRpcResponse.VERSION)
                .jsonPath("$.gas").isEqualTo(RESULT);

        verify(serviceFactory).lookup(METHOD);
        assertThat(meterRegistry.find(METRIC).timers())
                .hasSize(1)
                .first()
                .returns(METHOD, t -> t.getId().getTag("method"))
                .returns(JsonRpcSuccessResponse.SUCCESS, t -> t.getId().getTag("status"));
    }

    private Object buildDummyParams() {
        List paramList = new ArrayList<>();
        LinkedHashMap<String,String> params = new LinkedHashMap<>();
        params.put("from","0x00000000000000000000000000000000000004e2");
        params.put("to","0x00000000000000000000000000000000000004e4");
        params.put("gas","0x76c0");
        params.put("gasPrice","0x76c0");
        params.put("value","0");
        params.put("data","0x6f0fccab00000000000000000000000000000000000000000000000000000000000004e5");
        paramList.add(params);
        paramList.add("latest");
       return paramList;
    }

    @TestConfiguration
    static class Config {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    private static class DummyEthCallService implements ApiContractEthService<Object, Object> {

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
