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

package com.hedera.mirror.restjava.spec;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.mirror.restjava.RestJavaIntegrationTest;
import com.hedera.mirror.restjava.RestJavaProperties;
import com.hedera.mirror.restjava.spec.builder.SpecDomainBuilder;
import com.hedera.mirror.restjava.spec.model.RestSpec;
import com.hedera.mirror.restjava.spec.model.RestSpecNormalized;
import com.hedera.mirror.restjava.spec.config.SpecTestConfig;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;

@RequiredArgsConstructor
@RunWith(SpringRunner.class)
@EnableConfigurationProperties(value = RestJavaProperties.class)
@SpringBootTest(
        classes = SpecTestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.main.allow-bean-definition-overriding=true"})
public class RestSpecTest extends RestJavaIntegrationTest {
    private static final int JS_REST_API_CONTAINER_PORT = 5551;


    private static final Path REST_BASE_PATH = Path.of("..", "hedera-mirror-rest", "__tests__", "specs");
    private static final List<Path> SELECTED_SPECS = List.of(
            REST_BASE_PATH.resolve("accounts/alias-into-evm-address.json"),
            REST_BASE_PATH.resolve("blocks/no-records.json"),
            REST_BASE_PATH.resolve("accounts/specific-id.json")
    );

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private SpecDomainBuilder specDomainBuilder;

    @Resource
    private GenericContainer<?> jsRestApi;

    @Resource
    private RestJavaProperties properties;

    @LocalServerPort
    private int port;

    private RestClient.Builder restClientBuilder;

    private String baseJsRestApiUrl;

    @BeforeEach
    final void setup() {
        baseJsRestApiUrl = "http://%s:%d".formatted(jsRestApi.getHost(), jsRestApi.getMappedPort(JS_REST_API_CONTAINER_PORT));
        restClientBuilder = RestClient.builder()
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Access-Control-Request-Method", "GET")
                .defaultHeader("Origin", "http://example.com");
    }

    @ParameterizedTest
    @MethodSource("getSpecFilesToRun")
    void runSpecTests(Path specFilePath) throws IOException, JSONException {
        var spec = RestSpecNormalized.from(objectMapper.readValue(specFilePath.toFile(), RestSpec.class));
        log.info("Running spec '{}'", spec.description());
        setupDatabase(spec);

        for (var specTest : spec.tests()) {
            for (var url : specTest.urls()) {
                var restClient = restClientBuilder.baseUrl(baseJsRestApiUrl + url).build();
                var response = restClient.get()
                        .retrieve()
                        .toEntity(String.class);

                assertThat(response.getStatusCode().value()).isEqualTo(specTest.responseStatus());
                JSONAssert.assertEquals(specTest.responseJson(), response.getBody(), JSONCompareMode.LENIENT);
            }
        }
    }

    private void setupDatabase(RestSpecNormalized normalizedRestSpec) {
        var setup = normalizedRestSpec.setup();
        specDomainBuilder.addAccounts(setup.accounts());
        specDomainBuilder.addTokenAccounts(setup.tokenAccounts());
    }

    private static Stream<Arguments> getSpecFilesToRun() {
        return SELECTED_SPECS.stream().map(Arguments::of);
    }
}
