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
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.mirror.restjava.RestJavaIntegrationTest;
import com.hedera.mirror.restjava.spec.builder.SpecDomainBuilder;
import com.hedera.mirror.restjava.spec.model.RestSpec;
import com.hedera.mirror.restjava.spec.model.RestSpecNormalized;
import com.hedera.mirror.restjava.spec.config.SpecTestConfig;
import com.hedera.mirror.restjava.spec.model.SpecTestNormalized;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import lombok.SneakyThrows;

import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.TestFactory;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;

@RunWith(SpringRunner.class)
@SpringBootTest(
        classes = SpecTestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.main.allow-bean-definition-overriding=true"})
public class RestSpecTest extends RestJavaIntegrationTest {

    private static final Consumer<RestSpecNormalized> NO_OP_FUNCTION = s -> {};
    private static final int JS_REST_API_CONTAINER_PORT = 5551;
    private static final Path REST_BASE_PATH = Path.of("..", "hedera-mirror-rest", "__tests__", "specs");
    private static final List<Path> SELECTED_SPECS = List.of(
            REST_BASE_PATH.resolve("accounts/alias-into-evm-address.json"),
            REST_BASE_PATH.resolve("blocks/no-records.json"),
            REST_BASE_PATH.resolve("accounts/specific-id.json")
    );

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final SpecDomainBuilder specDomainBuilder;

    RestSpecTest(
            GenericContainer<?> jsRestApi,
            ObjectMapper objectMapper,
            SpecDomainBuilder specDomainBuilder) {
        this.objectMapper = objectMapper;
        this.specDomainBuilder = specDomainBuilder;

        var baseJsRestApiUrl = "http://%s:%d".formatted(jsRestApi.getHost(), jsRestApi.getMappedPort(JS_REST_API_CONTAINER_PORT));
        this.restClient = RestClient.builder()
                .baseUrl(baseJsRestApiUrl)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Access-Control-Request-Method", "GET")
                .defaultHeader("Origin", "http://example.com")
                .build();
    }

    @TestFactory
    Stream<DynamicContainer> generateTestsFromSpecs() {
        var dynamicContainers = new ArrayList<DynamicContainer>();
        for (var specFilePath : specsToTest()) {
            RestSpecNormalized normalizedSpec;
            try {
                normalizedSpec = RestSpecNormalized.from(objectMapper.readValue(specFilePath.toFile(), RestSpec.class));
            } catch (IOException e) {
                dynamicContainers.add(dynamicContainer(REST_BASE_PATH.relativize(specFilePath).toString(),
                        Stream.of(dynamicTest("Unable to parse spec file", ()-> {throw e;}))));
                continue;
            }

            var normalizedSpecTests = normalizedSpec.tests();
            for (var i = 0; i < normalizedSpecTests.size(); i++) {
                var test = normalizedSpecTests.get(i);
                Consumer<RestSpecNormalized> setupFunction = i == 0 ? this::setupDatabase : NO_OP_FUNCTION;
                var testCases = test.urls().stream()
                        .map(url -> dynamicTest(url, () -> testSpecUrl(setupFunction, url, test, normalizedSpec)));
                dynamicContainers.add(dynamicContainer(
                        "%s: '%s'".formatted(REST_BASE_PATH.relativize(specFilePath), normalizedSpec.description()),
                        testCases));
            }
        }
        return Stream.of(dynamicContainer("Dynamic test cases from spec files, base: %s".formatted(REST_BASE_PATH), dynamicContainers));
    }

    private List<Path> specsToTest() {
        return SELECTED_SPECS;
    }

    private void setupDatabase(RestSpecNormalized normalizedRestSpec) {
        // TODO, with @TestFactory, no lifecycle methods, so need to clean DB explicitly
        specDomainBuilder.addSetupEntities(normalizedRestSpec.setup());
    }

    @SneakyThrows
    private void testSpecUrl(Consumer<RestSpecNormalized> setupFunction, String url, SpecTestNormalized specTest, RestSpecNormalized spec) {
        setupFunction.accept(spec);

        var response = restClient.get()
                .uri(url)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(specTest.responseStatus());
        JSONAssert.assertEquals(specTest.responseJson(), response.getBody(), JSONCompareMode.LENIENT);
    }
}
