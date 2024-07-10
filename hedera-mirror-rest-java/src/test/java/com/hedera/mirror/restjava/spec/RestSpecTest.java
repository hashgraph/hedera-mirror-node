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
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestClient;

@RequiredArgsConstructor
@RunWith(SpringRunner.class)
@EnableConfigurationProperties(value = RestJavaProperties.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RestSpecTest extends RestJavaIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

//    @TestConfiguration(proxyBeanMethods = false)
//    class Configuration {
//        @Bean
//        @ServiceConnection("rest-api")
//
//    }

    @Resource
    private SpecDomainBuilder specDomainBuilder;

    @Autowired
    private RestJavaProperties properties;

    protected RestClient.Builder restClientBuilder;

    private String baseUrl;

    @LocalServerPort
    private int port;

    @BeforeEach
    final void setup() {
        baseUrl = "http://localhost:%d".formatted(port);
        restClientBuilder = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Access-Control-Request-Method", "GET")
                .defaultHeader("Origin", "http://example.com");
    }

    @Test
    void phase1WithSingleFile() throws Exception {
        var file = new File("../hedera-mirror-rest/__tests__/specs/network/supply/no-params.json");
        runSpecTest(file);
        System.out.println("ran " + file.getName());
    }

    private void runSpecTest(File specFile) throws Exception {
        RestSpec restSpec = OBJECT_MAPPER.readValue(specFile, RestSpec.class);
        var normalizedRestSpec = RestSpecNormalized.from(restSpec);
        specDomainBuilder.addAccounts(normalizedRestSpec.setup().accounts());

        for (var specTest : normalizedRestSpec.tests()) {
            for (var url : specTest.urls()) {
                var restClient = restClientBuilder.baseUrl(baseUrl + url).build();
                var response = restClient.get()
                        .retrieve()
                        .toEntity(String.class);
                assertThat(response.getStatusCode().value()).isEqualTo(specTest.responseStatus());
                JSONAssert.assertEquals(specTest.responseJson().toString(), response.getBody(), JSONCompareMode.LENIENT);
            }
        }
    }
}
