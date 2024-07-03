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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestClient;

@RequiredArgsConstructor
@RunWith(SpringRunner.class)
@EnableConfigurationProperties(value = RestJavaProperties.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RestSpecTest extends RestJavaIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
    void getStartedWithFile() throws Exception {
        var file = new File("../hedera-mirror-rest/__tests__/specs/network/supply/no-params.json");
        RestSpec restSpec = OBJECT_MAPPER.readValue(file, RestSpec.class);
        var normalizedRestSpec = RestSpecNormalized.from(restSpec);
        specDomainBuilder.addAccounts(normalizedRestSpec.setup().accounts());
        System.out.println(restSpec);
        System.out.println(normalizedRestSpec);
    }
}
