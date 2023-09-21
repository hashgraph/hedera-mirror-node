/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(OutputCaptureExtension.class)
class LoggingFilterTest {

    private static final Duration WAIT = Duration.ofSeconds(10L);

    private final LoggingFilter loggingFilter = new LoggingFilter();

    @Test
    void filterOnSuccess(CapturedOutput output) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build());
        exchange.getResponse().setRawStatusCode(200);

        StepVerifier.withVirtualTime(() -> loggingFilter.filter(
                        exchange,
                        serverWebExchange ->
                                Mono.defer(() -> exchange.getResponse().setComplete())))
                .thenAwait(WAIT)
                .expectComplete()
                .verify(WAIT);

        assertLog(output, "INFO", "\\w+ GET / in \\d+ ms: 200");
    }

    @Test
    void filterPath(CapturedOutput output) {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/").build());
        exchange.getResponse().setRawStatusCode(200);

        StepVerifier.withVirtualTime(() -> loggingFilter.filter(
                        exchange,
                        serverWebExchange ->
                                Mono.defer(() -> exchange.getResponse().setComplete())))
                .thenAwait(WAIT)
                .expectComplete()
                .verify(WAIT);

        assertThat(output).asString().isEmpty();
    }

    @Test
    void filterXForwardedFor(CapturedOutput output) {
        String clientIp = "10.0.0.100";
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/")
                .header(LoggingFilter.X_FORWARDED_FOR, clientIp)
                .build());
        exchange.getResponse().setRawStatusCode(200);

        StepVerifier.withVirtualTime(() -> loggingFilter.filter(
                        exchange,
                        serverWebExchange ->
                                Mono.defer(() -> exchange.getResponse().setComplete())))
                .thenAwait(WAIT)
                .expectComplete()
                .verify(WAIT);

        assertLog(output, "INFO", clientIp + " GET / in \\d+ ms: 200");
    }

    @Test
    void filterOnCancel(CapturedOutput output) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build());

        StepVerifier.withVirtualTime(() -> loggingFilter.filter(
                        exchange, serverWebExchange -> exchange.getResponse().setComplete()))
                .thenCancel()
                .verify(WAIT);

        assertLog(output, "WARN", "\\w+ GET / in \\d+ ms: cancelled");
    }

    @Test
    void filterOnError(CapturedOutput output) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build());
        exchange.getResponse().setRawStatusCode(500);

        var exception = new IllegalArgumentException("error");
        StepVerifier.withVirtualTime(() -> loggingFilter
                        .filter(exchange, serverWebExchange -> Mono.error(exception))
                        .onErrorResume((t) -> exchange.getResponse().setComplete()))
                .thenAwait(WAIT)
                .expectComplete()
                .verify(WAIT);

        assertLog(output, "WARN", "\\w+ GET / in \\d+ ms: " + exception.getMessage());
    }

    private void assertLog(CapturedOutput logOutput, String level, String pattern) {
        assertThat(logOutput).asString().hasLineCount(1).contains(level).containsPattern(pattern);
    }
}
