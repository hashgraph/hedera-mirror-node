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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.mirror.restjava.RestJavaIntegrationTest;
import com.hedera.mirror.restjava.spec.builder.SpecDomainBuilder;
import com.hedera.mirror.restjava.spec.config.SpecTestConfig;
import com.hedera.mirror.restjava.spec.model.RestSpec;
import com.hedera.mirror.restjava.spec.model.RestSpecNormalized;
import com.hedera.mirror.restjava.spec.model.SpecTestNormalized;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.sql.DataSource;
import lombok.SneakyThrows;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.TestFactory;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatusCode;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;

@RunWith(SpringRunner.class)
@SpringBootTest(
        classes = SpecTestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.main.allow-bean-definition-overriding=true"})
public class RestSpecTest extends RestJavaIntegrationTest {

    private static final int JS_REST_API_CONTAINER_PORT = 5551;
    private static final Path REST_BASE_PATH = Path.of("..", "hedera-mirror-rest", "__tests__", "specs");
    private static final List<Path> SELECTED_SPECS = List.of(
            REST_BASE_PATH.resolve("accounts/{id}/allowances/crypto/alias-not-found.json"),
            REST_BASE_PATH.resolve("accounts/{id}/allowances/crypto/no-params.json"),
            REST_BASE_PATH.resolve("accounts/{id}/allowances/crypto/all-params.json"),
            REST_BASE_PATH.resolve("accounts/{id}/allowances/crypto/all-params.json"),
            REST_BASE_PATH.resolve("accounts/{id}/allowances/tokens/empty.json"),
            REST_BASE_PATH.resolve("accounts/{id}/allowances/tokens/no-params.json"),
            REST_BASE_PATH.resolve("accounts/{id}/allowances/tokens/specific-spender-id.json"),
            REST_BASE_PATH.resolve("accounts/{id}/allowances/tokens/spender-id-range-token-id-upper.json"),
            REST_BASE_PATH.resolve("accounts/{id}/rewards/no-params.json"),
            REST_BASE_PATH.resolve("accounts/{id}/rewards/no-rewards.json"),
            REST_BASE_PATH.resolve("accounts/{id}/rewards/specific-timestamp.json"),
            REST_BASE_PATH.resolve("accounts/specific-id.json"),
            REST_BASE_PATH.resolve("blocks/no-records.json"),
            REST_BASE_PATH.resolve("blocks/timestamp-param.json"),
            REST_BASE_PATH.resolve("blocks/all-params-together.json"),
            REST_BASE_PATH.resolve("blocks/limit-param.json"),
            REST_BASE_PATH.resolve("blocks/no-records.json"),
            REST_BASE_PATH.resolve("blocks/{id}/hash-64.json"),
            REST_BASE_PATH.resolve("blocks/{id}/hash-96.json"),
            REST_BASE_PATH.resolve("network/exchangerate/no-params.json"),
            REST_BASE_PATH.resolve("network/exchangerate/timestamp-upper-bound.json"),
            REST_BASE_PATH.resolve("network/fees/no-params.json"),
            REST_BASE_PATH.resolve("network/fees/order.json"),
            REST_BASE_PATH.resolve("network/fees/timestamp-not-found.json"),
            REST_BASE_PATH.resolve("network/stake/no-params.json"),
            REST_BASE_PATH.resolve("topics/{id}/messages/all-params.json"),
            REST_BASE_PATH.resolve("topics/{id}/messages/encoding.json"),
            REST_BASE_PATH.resolve("topics/{id}/messages/no-params.json"),
            REST_BASE_PATH.resolve("topics/{id}/messages/order.json"));

    private final ResourceDatabasePopulator databaseCleaner;
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final SpecDomainBuilder specDomainBuilder;

    RestSpecTest(
            @Value("classpath:cleanup.sql") Resource cleanupSqlResource,
            DataSource dataSource,
            GenericContainer<?> jsRestApi,
            ObjectMapper objectMapper,
            SpecDomainBuilder specDomainBuilder) {

        this.databaseCleaner = new ResourceDatabasePopulator(cleanupSqlResource);
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
        this.specDomainBuilder = specDomainBuilder;

        var baseJsRestApiUrl =
                "http://%s:%d".formatted(jsRestApi.getHost(), jsRestApi.getMappedPort(JS_REST_API_CONTAINER_PORT));
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
                dynamicContainers.add(dynamicContainer(
                        REST_BASE_PATH.relativize(specFilePath).toString(),
                        Stream.of(dynamicTest("Unable to parse spec file", () -> {
                            throw e;
                        }))));
                continue;
            }

            var normalizedSpecTests = normalizedSpec.tests();
            for (var test : normalizedSpecTests) {
                var testCases =
                        test.urls().stream().map(url -> dynamicTest(url, () -> testSpecUrl(url, test, normalizedSpec)));

                dynamicContainers.add(dynamicContainer(
                        "%s: '%s'".formatted(REST_BASE_PATH.relativize(specFilePath), normalizedSpec.description()),
                        Stream.concat(
                                Stream.of(dynamicTest("Setup database", () -> setupDatabase(normalizedSpec))),
                                testCases)));
            }
        }
        return Stream.of(dynamicContainer(
                "Dynamic test cases from spec files, base: %s".formatted(REST_BASE_PATH), dynamicContainers));
    }

    private List<Path> specsToTest() {
        return SELECTED_SPECS;
    }

    private void setupDatabase(RestSpecNormalized normalizedRestSpec) {
        /*
         * JUnit 5 dynamic tests do not enjoy the benefit of lifecycle methods (e.g. @BeforeEach etc.), so
         * the DB needs to first be cleaned explicitly prior to creating the spec file defined entities.
         */
        databaseCleaner.execute(dataSource);

        specDomainBuilder.customizeAndPersistEntities(normalizedRestSpec.setup());
    }

    @SneakyThrows
    private void testSpecUrl(String url, SpecTestNormalized specTest, RestSpecNormalized spec) {

        var response = restClient
                .get()
                .uri(url)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    // Override default handling of 4xx errors, and proceed to evaluate the response.
                })
                .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(specTest.responseStatus());
        JSONAssert.assertEquals(specTest.responseJson(), response.getBody(), JSONCompareMode.LENIENT);
    }
}
