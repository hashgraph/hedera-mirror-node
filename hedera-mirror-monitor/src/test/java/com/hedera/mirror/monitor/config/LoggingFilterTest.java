package com.hedera.mirror.monitor.config;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import java.time.Duration;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.WriterAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class LoggingFilterTest {

    private static final Duration WAIT = Duration.ofSeconds(5);

    private LoggingFilter loggingFilter;
    private StringWriter logOutput;
    private WriterAppender appender;

    @BeforeEach
    void setup() {
        loggingFilter = new LoggingFilter();
        logOutput = new StringWriter();
        appender = WriterAppender.newBuilder()
                .setLayout(PatternLayout.newBuilder().withPattern("%p|%m%n").build())
                .setName("stringAppender")
                .setTarget(logOutput)
                .build();
        Logger logger = (Logger) LogManager.getLogger(loggingFilter);
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        appender.start();
    }

    @AfterEach
    void cleanup() {
        appender.stop();
        Logger logger = (Logger) LogManager.getLogger(loggingFilter);
        logger.removeAppender(appender);
    }

    @CsvSource({
            "/, 200, INFO",
            "/actuator/, 200, DEBUG"
    })
    @ParameterizedTest
    void filterOnSuccess(String path, int code, String level) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
        exchange.getResponse().setRawStatusCode(code);

        loggingFilter.filter(exchange, serverWebExchange -> Mono.defer(() -> exchange.getResponse().setComplete()))
                .as(StepVerifier::create)
                .expectComplete()
                .verify(WAIT);

        assertLog(Level.getLevel(level), "\\w+ GET " + path + " in \\d+ ms: " + code);
    }

    @Test
    void filterXForwardedFor() {
        String clientIp = "10.0.0.100";
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/")
                .header(LoggingFilter.X_FORWARDED_FOR, clientIp)
                .build());
        exchange.getResponse().setRawStatusCode(200);

        loggingFilter.filter(exchange, serverWebExchange -> Mono.defer(() -> exchange.getResponse().setComplete()))
                .as(StepVerifier::create)
                .expectComplete()
                .verify(WAIT);

        assertLog(Level.INFO, clientIp + " GET / in \\d+ ms: 200");
    }

    @Test
    void filterOnCancel() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build());

        loggingFilter.filter(exchange, serverWebExchange -> exchange.getResponse().setComplete())
                .as(StepVerifier::create)
                .thenCancel()
                .verify(WAIT);

        assertLog(Level.WARN, "\\w+ GET / in \\d+ ms: cancelled");
    }

    @Test
    void filterOnError() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build());
        exchange.getResponse().setRawStatusCode(500);

        var exception = new IllegalArgumentException("error");
        loggingFilter.filter(exchange, serverWebExchange -> Mono.error(exception))
                .onErrorResume((t) -> exchange.getResponse().setComplete())
                .as(StepVerifier::create)
                .expectComplete()
                .verify(WAIT);

        assertLog(Level.WARN, "\\w+ GET / in \\d+ ms: " + exception.getMessage());
    }

    private void assertLog(Level level, String pattern) {
        assertThat(logOutput)
                .asString()
                .hasLineCount(1)
                .contains(level.toString())
                .containsPattern(pattern);
    }
}
