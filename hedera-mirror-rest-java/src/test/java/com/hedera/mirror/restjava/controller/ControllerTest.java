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

package com.hedera.mirror.restjava.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.mirror.rest.model.Error;
import com.hedera.mirror.rest.model.ErrorStatusMessagesInner;
import com.hedera.mirror.restjava.RestJavaIntegrationTest;
import com.hedera.mirror.restjava.RestJavaProperties;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ThrowableAssert;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec;
import org.springframework.web.client.RestClient.RequestHeadersUriSpec;

@RequiredArgsConstructor
@RunWith(SpringRunner.class)
@EnableConfigurationProperties(value = RestJavaProperties.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class ControllerTest extends RestJavaIntegrationTest {

    private static final String BASE_PATH = "/api/v1/";

    @Autowired
    private RestJavaProperties properties;

    protected RestClient.Builder restClientBuilder;

    private String baseUrl;

    @LocalServerPort
    private int port;

    @BeforeEach
    final void setup() {
        baseUrl = "http://localhost:%d%s".formatted(port, BASE_PATH);
        restClientBuilder = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Access-Control-Request-Method", "GET")
                .defaultHeader("Origin", "http://example.com");
    }

    protected final void validateError(
            ThrowableAssert.ThrowingCallable callable,
            Class<? extends HttpClientErrorException> clazz,
            String message) {
        assertThatThrownBy(callable)
                .isInstanceOf(clazz)
                .asInstanceOf(InstanceOfAssertFactories.type(clazz))
                .extracting(r -> r.getResponseBodyAs(Error.class)
                        .getStatus()
                        .getMessages()
                        .get(0))
                .returns(null, ErrorStatusMessagesInner::getData)
                .returns(null, ErrorStatusMessagesInner::getDetail)
                .extracting(ErrorStatusMessagesInner::getMessage)
                .asInstanceOf(InstanceOfAssertFactories.STRING)
                .containsIgnoringCase(message);
    }

    protected abstract class EndpointTest {

        protected RestClient restClient;

        protected abstract String getUrl();

        @BeforeEach
        void setup() {
            var suffix = getUrl().replace("/api/v1/", "");
            restClient = restClientBuilder.baseUrl(baseUrl + suffix).build();
        }

        /*
         * This method allows subclasses to do any setup work like entity persistence and provide a default URI and
         * parameters for the parent class to run its common set of tests.
         */
        protected abstract RequestHeadersSpec<?> defaultRequest(RequestHeadersUriSpec<?> uriSpec);

        @Test
        void cors() {
            // When
            var headers = defaultRequest(restClient.get())
                    .retrieve()
                    .toBodilessEntity()
                    .getHeaders();

            // Then
            assertThat(headers.getAccessControlAllowOrigin()).isEqualTo("*");
        }

        @Test
        void headers() {
            // When
            var headers = defaultRequest(restClient.get())
                    .retrieve()
                    .toBodilessEntity()
                    .getHeaders();

            // Then
            var headersConfig = properties.getResponse().getHeaders();
            var headersPathKey = "%s%s".formatted(BASE_PATH, getUrl());
            var headersExpected = headersConfig.getPath().getOrDefault(headersPathKey, headersConfig.getDefaults());

            headersExpected.forEach((expectedName, expectedValue) -> {
                var headerValues = headers.get(expectedName);
                assertThat(headerValues).isNotNull();
                assertThat(headerValues).contains(expectedValue);
            });
        }

        @Test
        void methodNotAllowed() {
            // When
            ThrowingCallable callable =
                    () -> defaultRequest(restClient.post()).retrieve().toBodilessEntity();

            // Then
            validateError(callable, HttpClientErrorException.MethodNotAllowed.class, "Method Not Allowed");
        }
    }
}
