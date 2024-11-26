/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import static com.google.common.net.HttpHeaders.X_FORWARDED_FOR;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.web3.Web3Properties;
import java.nio.charset.StandardCharsets;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.ForwardedHeaderFilter;
import org.springframework.web.util.WebUtils;

@ExtendWith(OutputCaptureExtension.class)
class LoggingFilterTest {

    private final Web3Properties web3Properties = new Web3Properties();
    private final LoggingFilter loggingFilter = new LoggingFilter(web3Properties);
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final MockFilterChain chain = new MockFilterChain();

    @Test
    @SneakyThrows
    void filterOnSuccess(CapturedOutput output) {
        var request = new MockHttpServletRequest("GET", "/");

        response.setStatus(HttpStatus.OK.value());

        loggingFilter.doFilter(request, response, chain);

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
        request.addHeader(X_FORWARDED_FOR, clientIp);
        response.setStatus(HttpStatus.OK.value());

        new ForwardedHeaderFilter().doFilter(request, response, (req, res) -> loggingFilter.doFilter(req, res, chain));

        assertLog(output, "INFO", clientIp + " GET / in \\d+ ms: 200");
    }

    @Test
    @SneakyThrows
    void filterOnError(CapturedOutput output) {
        var request = new MockHttpServletRequest("GET", "/");
        var exception = new IllegalArgumentException("error");

        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());

        loggingFilter.doFilter(request, response, (req, res) -> {
            throw exception;
        });

        assertLog(output, "WARN", "\\w+ GET / in \\d+ ms: 500 " + exception.getMessage());
    }

    @Test
    @SneakyThrows
    void filterOnErrorAttribute(CapturedOutput output) {
        var request = new MockHttpServletRequest("GET", "/");
        var exception = new IllegalArgumentException("error");
        request.setAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE, exception);

        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());

        loggingFilter.doFilter(request, response, (req, res) -> {});

        assertLog(output, "WARN", "\\w+ GET / in \\d+ ms: 500 " + exception.getMessage());
    }

    @Test
    @SneakyThrows
    void post(CapturedOutput output) {
        var content = "{\"to\":\"0x00\"}";
        var request = new MockHttpServletRequest("POST", "/");
        request.setContent(content.getBytes(StandardCharsets.UTF_8));
        response.setStatus(HttpStatus.OK.value());

        loggingFilter.doFilter(request, response, (req, res) -> IOUtils.toString(req.getReader()));

        assertLog(output, "INFO", "\\w+ POST / in \\d+ ms: 200 Success - .+");
        assertThat(output.getOut()).contains(content);
    }

    @Test
    @SneakyThrows
    void postMultiLine(CapturedOutput output) {
        var content = " foo: bar\n";
        var request = new MockHttpServletRequest("POST", "/");
        request.setContent(content.getBytes(StandardCharsets.UTF_8));
        response.setStatus(HttpStatus.OK.value());

        loggingFilter.doFilter(request, response, (req, res) -> IOUtils.toString(req.getReader()));

        assertThat(output.getOut()).contains("foo:bar");
    }

    @Test
    @SneakyThrows
    void postLargeContent(CapturedOutput output) {
        int maxSize = web3Properties.getMaxPayloadLogSize();
        var content = RandomStringUtils.secure().next(maxSize + 1, "abcdef0123456789");
        var request = new MockHttpServletRequest("POST", "/");
        request.setContent(content.getBytes(StandardCharsets.UTF_8));
        response.setStatus(HttpStatus.OK.value());

        loggingFilter.doFilter(request, response, (req, res) -> IOUtils.toString(req.getReader()));

        assertThat(output.getOut()).contains(content.substring(0, maxSize)).doesNotContain(content);
    }

    private void assertLog(CapturedOutput logOutput, String level, String pattern) {
        assertThat(logOutput).asString().hasLineCount(1).contains(level).containsPattern(pattern);
    }
}
