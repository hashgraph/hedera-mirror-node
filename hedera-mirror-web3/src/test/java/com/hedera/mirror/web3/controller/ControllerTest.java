/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.web3.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.service.ContractCallService;
import com.hedera.mirror.web3.viewmodel.GenericErrorResponse;
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.support.TransactionOperations;

@ContextConfiguration(classes = { ControllerTest.Config.class })
@ExtendWith(SpringExtension.class)
public abstract class ControllerTest {

    @Resource
    protected MockMvc mockMvc;

    @Resource
    protected ObjectMapper objectMapper;

    @Autowired
    protected MirrorNodeEvmProperties evmProperties;

    @MockBean
    protected ContractCallService contractCallService;

    @MockBean(name = "rateLimitBucket")
    protected Bucket rateLimitBucket;

    @MockBean(name = "gasLimitBucket")
    protected Bucket gasLimitBucket;

    @BeforeEach
    final void mockBuckets() {
        when(rateLimitBucket.tryConsume(anyLong())).thenReturn(true);
        when(gasLimitBucket.tryConsume(anyLong())).thenReturn(true);
    }

    protected abstract class EndpointTest {

        /**
         * @return HTTP method for the endpoint
         */
        protected abstract HttpMethod getMethod();

        /**
         * @return URI for the endpoint
         */
        protected abstract String getUrl();

        /**
         * @return path parameters for the endpoint (empty by default)
         */
        protected Object[] getParameters() {
            return new Object[0];
        }

        /**
         * This method can be overridden by subclasses to do any setup work.
         * E.g. adding headers, query parameters, etc. for the parent class to run its common set of tests.
         *
         * @param requestBuilder the request builder to customize
         * @return the customized {@link MockHttpServletRequestBuilder}
         */
        protected MockHttpServletRequestBuilder customizeRequest(MockHttpServletRequestBuilder requestBuilder) {
            return requestBuilder;
        }

        /**
         * @return the {@link ResultActions} for the request
         */
        protected ResultActions performRequest() {
            return performRequest(null);
        }

        /**
         * @param requestBody the request body (if any) to include in the request
         * @return the {@link ResultActions} for the request
         */
        @SneakyThrows
        protected ResultActions performRequest(@Nullable Object requestBody) {
            return mockMvc.perform(buildRequest(requestBody));
        }

        /**
         * @param expectedBody the expected response body
         * @return the {@link ResultMatcher} for the response body
         */
        protected ResultMatcher responseBody(final Object expectedBody) {
            return content().string(convertToJson(expectedBody));
        }

        /**
         * @param object the object to convert
         * @return the JSON string representation of the object
         */
        @SneakyThrows
        protected String convertToJson(Object object) {
            return objectMapper.writeValueAsString(object);
        }

        private MockHttpServletRequestBuilder buildRequest() {
            return buildRequest(null);
        }

        private MockHttpServletRequestBuilder buildRequest(@Nullable Object requestBody) {
            final MockHttpServletRequestBuilder requestBuilder = customizeRequest(
                    MockMvcRequestBuilders
                            .request(getMethod(), getUrl(), getParameters())
                            .accept(MediaType.APPLICATION_JSON)
                            .contentType(MediaType.APPLICATION_JSON)
            );

            if (requestBody != null) {
                final String json = requestBody instanceof String ? (String) requestBody : convertToJson(requestBody);
                return requestBuilder.content(json);
            } else {
                return requestBuilder;
            }
        }

        /**
         * The Spring WebTestClient CORS testing requires that the URI contain any hostname and port.
         * <a href="https://stackoverflow.com/questions/62723224/webtestclient-cors-with-spring-boot-and-webflux">...</a>
         */
        @Test
        void cors() throws Exception {
            mockMvc.perform(options(getUrl(), getParameters())
                            .accept(MediaType.APPLICATION_JSON)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Origin", "https://example.com")
                            .header("Access-Control-Request-Method", getMethod().name()))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                    .andExpect(header().string("Access-Control-Allow-Methods", getMethod().name()));
        }

        /**
         * Tests the endpoint with unsupported media type.
         */
        @Test
        void unsupportedMediaType() throws Exception {
            if (getMethod() == HttpMethod.GET) {
                return;
            }
            mockMvc.perform(buildRequest()
                            .accept(MediaType.APPLICATION_JSON)
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("text"))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(content()
                            .string(convertToJson(new GenericErrorResponse(
                                    "Unsupported Media Type",
                                    "Content-Type 'text/plain;charset=UTF-8' is not supported",
                                    StringUtils.EMPTY
                            ))));
        }

        @Test
        void malformedJsonBody() throws Exception {
            if (getMethod() == HttpMethod.GET) {
                return;
            }
            var requestJson = "{from: 0x00000000000000000000000000000000000004e2\"";
            mockMvc.perform(buildRequest(requestJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(responseBody(new GenericErrorResponse(
                            "Unable to parse JSON",
                            "JSON parse error: Unexpected character ('f' (code 102)): was expecting double-quote to start field name",
                            StringUtils.EMPTY)));
        }

        @Test
        void exceedingRateLimit() throws Exception {
            for (var i = 0; i < 3; i++) {
                mockMvc.perform(buildRequest()).andExpect(status().isOk());
            }
            when(rateLimitBucket.tryConsume(1)).thenReturn(false);
            mockMvc.perform(buildRequest()).andExpect(status().isTooManyRequests());
        }

        @Test
        void exceedingGasLimit() throws Exception {
            for (var i = 0; i < 3; i++) {
                mockMvc.perform(buildRequest()).andExpect(status().isOk());
            }
            when(gasLimitBucket.tryConsume(anyLong())).thenReturn(false);
            mockMvc.perform(buildRequest()).andExpect(status().isTooManyRequests());
        }
    }

    @TestConfiguration
    public static class Config {

        @Bean
        MirrorNodeEvmProperties evmProperties() {
            return new MirrorNodeEvmProperties();
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        EntityManager entityManager() {
            return mock(EntityManager.class);
        }

        @Bean
        TransactionOperations transactionOperations() {
            return mock(TransactionOperations.class);
        }
    }
}
