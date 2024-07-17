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
import jakarta.annotation.Resource;
import java.io.File;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;

@RequiredArgsConstructor
@RunWith(SpringRunner.class)
@EnableConfigurationProperties(value = RestJavaProperties.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"spring.main.allow-bean-definition-overriding=true"})
public class RestSpecTest extends RestJavaIntegrationTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static GenericContainer<?> jsRestApi = new GenericContainer<>(
                    DockerImageName.parse("gcr.io/mirrornode/hedera-mirror-rest:latest"))
//                    new ImageFromDockerfile("localhost/testcontainers/restapi", false)
//                            .withDockerfile(Path.of("../hedera-mirror-rest/Dockerfile")))
            .withExposedPorts(5551);

    @TestConfiguration(proxyBeanMethods = false)
    static class Configuration {
        @Bean
        Network postgresqlNetwork() {
            return Network.newNetwork();
        }

        @Bean
        @ServiceConnection("postgresql")
        PostgreSQLContainer<?> postgresql(Network postgresqlNetwork) {
            var dockerImageName = DockerImageName.parse("postgres:14-alpine").asCompatibleSubstituteFor("postgres");
            var logger = LoggerFactory.getLogger(PostgreSQLContainer.class);
            var excluded = "terminating connection due to unexpected postmaster exit";
            var logConsumer = new FilteringConsumer(
                    new Slf4jLogConsumer(logger, true),
                    o -> !StringUtils.contains(o.getUtf8StringWithoutLineEnding(), excluded));
            return new PostgreSQLContainer<>(dockerImageName)
                    .withNetwork(postgresqlNetwork)
                    .withNetworkAliases("postgresql")
                    .withClasspathResourceMapping("init.sql", "/docker-entrypoint-initdb.d/init.sql", BindMode.READ_ONLY)
                    .withDatabaseName("mirror_node")
                    .withLogConsumer(logConsumer)
                    .withPassword("mirror_node_pass")
                    .withUsername("mirror_node");
        }
    }

    @Resource
    private SpecDomainBuilder specDomainBuilder;

    @Autowired
    Network prostgresqlNetwork;

    @Autowired
    private RestJavaProperties properties;

    protected RestClient.Builder restClientBuilder;

    private String baseUrl;
    private String baseJsRestApiUrl;

    @LocalServerPort
    private int port;

    @BeforeEach
    final void setup() {
        if (!jsRestApi.isRunning()) {
            jsRestApi
                    .withNetwork(prostgresqlNetwork)
                    .withEnv(Map.of(
                    "HEDERA_MIRROR_REST_REDIS_ENABLED", "false",
                    "HEDERA_MIRROR_REST_DB_HOST", "postgresql", // Postgresql container network alias
                    "HEDERA_MIRROR_REST_DB_PORT", PostgreSQLContainer.POSTGRESQL_PORT.toString()));
            jsRestApi.start();
        }

        baseUrl = "http://localhost:%d".formatted(port);
        baseJsRestApiUrl = "http://%s:%d".formatted(jsRestApi.getHost(), jsRestApi.getMappedPort(5551));

        restClientBuilder = RestClient.builder()
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Access-Control-Request-Method", "GET")
                .defaultHeader("Origin", "http://example.com");
    }

    @Test
    void phase1WithSingleFile() throws Exception {
        var file = new File("../hedera-mirror-rest/__tests__/specs/blocks/no-records.json");
        runSpecTest(file);
        System.out.println("ran " + file.getName());
    }

    private void runSpecTest(File specFile) throws Exception {
        RestSpec restSpec = OBJECT_MAPPER.readValue(specFile, RestSpec.class);
        var normalizedRestSpec = RestSpecNormalized.from(restSpec);
        specDomainBuilder.addAccounts(normalizedRestSpec.setup().accounts());

        for (var specTest : normalizedRestSpec.tests()) {
            for (var url : specTest.urls()) {
                var restClient = restClientBuilder.baseUrl(baseJsRestApiUrl + url).build();
                var response = restClient.get()
                        .retrieve()
                        .toEntity(String.class);
                assertThat(response.getStatusCode().value()).isEqualTo(specTest.responseStatus());
                JSONAssert.assertEquals(specTest.responseJson().toString(), response.getBody(), JSONCompareMode.LENIENT);
            }
        }
    }

    @RequiredArgsConstructor
    private static class FilteringConsumer implements Consumer<OutputFrame> {

        private final Consumer<OutputFrame> delegate;
        private final Predicate<OutputFrame> filter;

        @Override
        public void accept(OutputFrame outputFrame) {
            if (filter.test(outputFrame)) {
                delegate.accept(outputFrame);
            }
        }
    }
}
