/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static org.springframework.http.HttpStatus.METHOD_NOT_ALLOWED;
import static org.springframework.http.HttpStatus.OK;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;

@ExtendWith(SpringExtension.class)
@WebFluxTest(controllers = DummyController.class)
class DummyControllerTest {

    private static final String CALL_URI = "/api/v1/dummy";

    @Resource
    private WebTestClient webClient;

    @Test
    void success() {
        webClient
                .get()
                .uri(CALL_URI)
                .exchange()
                .expectStatus()
                .isEqualTo(OK)
                .expectBody(String.class)
                .isEqualTo("Hello world");
    }

    @Test
    void methodNotAllowed() {
        webClient
                .post()
                .uri(CALL_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isEqualTo(METHOD_NOT_ALLOWED);
    }
}
