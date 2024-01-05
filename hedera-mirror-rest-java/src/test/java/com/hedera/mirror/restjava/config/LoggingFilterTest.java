/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.restjava.config;

import static com.google.common.net.HttpHeaders.X_FORWARDED_FOR;
import static org.assertj.core.api.Assertions.assertThat;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.ForwardedHeaderFilter;

@ExtendWith(OutputCaptureExtension.class)
class LoggingFilterTest {
    private final LoggingFilter loggingFilter = new LoggingFilter();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final MockFilterChain chain = new MockFilterChain();

    @Test
    @SneakyThrows
    void filterOnSuccess(CapturedOutput output) {
        var request = new MockHttpServletRequest("GET", "/");

        response.setStatus(HttpStatus.OK.value());

        loggingFilter.doFilter(request, response, new MockFilterChain());

        assertLog(output, "INFO", "\\w+ GET / in \\d+ ms: 200");
    }

    @Test
    @SneakyThrows
    void filterPath(CapturedOutput output) {
        var request = new MockHttpServletRequest("GET", "/actuator/");

        response.setStatus(HttpStatus.OK.value());

        loggingFilter.doFilter(request, response, chain);

        assertThat(output).asString().isEmpty();
    }

    @Test
    @SneakyThrows
    void filterXForwardedFor(CapturedOutput output) {
        String clientIp = "10.0.0.100";
        var request = new MockHttpServletRequest("GET", "/");
        ;
        request.addHeader(X_FORWARDED_FOR, clientIp);
        response.setStatus(HttpStatus.OK.value());

        new ForwardedHeaderFilter()
                .doFilter(request, response, (request1, response) -> loggingFilter.doFilter(request1, response, chain));

        assertLog(output, "INFO", clientIp + " GET / in \\d+ ms: 200");
    }

    @Test
    @SneakyThrows
    void filterOnError(CapturedOutput output) {
        var request = new MockHttpServletRequest("GET", "/");
        var exception = new IllegalArgumentException("error");

        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());

        loggingFilter.doFilter(request, response, (request1, response) -> {
            throw exception;
        });

        assertLog(output, "WARN", "\\w+ GET / in \\d+ ms: " + exception.getMessage());
    }

    private void assertLog(CapturedOutput logOutput, String level, String pattern) {
        assertThat(logOutput).asString().hasLineCount(1).contains(level).containsPattern(pattern);
    }
}
