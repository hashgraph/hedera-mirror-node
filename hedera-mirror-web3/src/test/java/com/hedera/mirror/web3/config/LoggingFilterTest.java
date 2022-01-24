package com.hedera.mirror.web3.config;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import lombok.CustomLog;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import com.hedera.mirror.web3.exception.InvalidParametersException;

@CustomLog
class LoggingFilterTest {

    private static final Duration WAIT = Duration.ofSeconds(5);

    private LoggingFilter loggingFilter;
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setup(TestInfo testInfo) {
        log.info("Executing {}", testInfo.getDisplayName());
        appender = new ListAppender<>();
        appender.start();
        logger = (Logger) LoggerFactory.getLogger(LoggingFilter.class);
        logger.addAppender(appender);
        loggingFilter = new LoggingFilter();
    }

    @AfterEach
    void cleanup(TestInfo testInfo) {
        log.info("Finished {}", testInfo.getDisplayName());
        logger.detachAndStopAllAppenders();
    }

    @CsvSource({
            "/, 200, INFO, 200",
            "/actuator/, 200, DEBUG, 200"
    })
    @ParameterizedTest
    void filterOnSuccess(String path, int code, String level) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
        exchange.getResponse().setRawStatusCode(code);

        loggingFilter.filter(exchange, serverWebExchange -> Mono.defer(() -> {
                    log.info("chain {}", path);
                    exchange.getResponse().setComplete().block();
                    return Mono.empty();
                }))
                .as(StepVerifier::create)
                .expectComplete()
                .verify(WAIT);

        assertThat(appender.list)
                .hasSize(1)
                .first()
                .returns(Level.toLevel(level), ILoggingEvent::getLevel)
                .extracting(ILoggingEvent::getFormattedMessage, InstanceOfAssertFactories.STRING)
                .containsPattern("\\w+ GET " + path + " in \\d+ ms: " + code);
    }

    @Test
    void filterOnCancel() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build());
        exchange.getResponse().setRawStatusCode(500);

        loggingFilter.filter(exchange, serverWebExchange -> exchange.getResponse().setComplete())
                .as(StepVerifier::create)
                .thenCancel()
                .verify(WAIT);

        assertThat(appender.list)
                .hasSize(1)
                .first()
                .returns(Level.WARN, ILoggingEvent::getLevel)
                .extracting(ILoggingEvent::getFormattedMessage, InstanceOfAssertFactories.STRING)
                .containsPattern("\\w+ GET / in \\d+ ms: cancelled");
    }

    @Test
    void filterOnError() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build());
        exchange.getResponse().setRawStatusCode(500);

        var exception = new InvalidParametersException("error");
        loggingFilter.filter(exchange, serverWebExchange -> Mono.error(exception))
                .onErrorResume((t) -> {
                    exchange.getResponse().setRawStatusCode(500);
                    return exchange.getResponse().setComplete();
                })
                .as(StepVerifier::create)
                .expectComplete()
                .verify(WAIT);

        assertThat(appender.list)
                .hasSize(1)
                .first()
                .returns(Level.WARN, ILoggingEvent::getLevel)
                .extracting(ILoggingEvent::getFormattedMessage, InstanceOfAssertFactories.STRING)
                .containsPattern("\\w+ GET / in \\d+ ms: " + exception.getMessage());
    }
}
