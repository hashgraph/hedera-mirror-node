/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.monitor.config;

import jakarta.inject.Named;
import java.io.Serial;
import java.net.InetSocketAddress;
import java.net.URI;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.CollectionUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Log4j2
@Named
public class LoggingFilter implements WebFilter {

    static final String X_FORWARDED_FOR = "X-Forwarded-For";

    @SuppressWarnings("java:S1075")
    private static final String ACTUATOR_PATH = "/actuator/";

    private static final String LOCALHOST = "127.0.0.1";
    private static final String LOG_FORMAT = "{} {} {} in {} ms: {}";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long start = System.currentTimeMillis();
        return chain.filter(exchange)
                .transformDeferred(call -> call.doOnEach(signal -> doFilter(exchange, signal.getThrowable(), start))
                        .doOnCancel(() -> doFilter(exchange, new CancelledException(), start)));
    }

    private void doFilter(ServerWebExchange exchange, Throwable cause, long start) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted() || cause instanceof CancelledException) {
            logRequest(exchange, start, cause);
        } else {
            response.beforeCommit(() -> {
                logRequest(exchange, start, cause);
                return Mono.empty();
            });
        }
    }

    private void logRequest(ServerWebExchange exchange, long startTime, Throwable cause) {
        long elapsed = System.currentTimeMillis() - startTime;
        ServerHttpRequest request = exchange.getRequest();
        URI uri = request.getURI();
        Object message = exchange.getResponse().getStatusCode();
        Level level = Level.INFO;

        if (cause != null) {
            level = Level.WARN;
            message = cause.getMessage();
        } else if (StringUtils.startsWith(uri.getPath(), ACTUATOR_PATH)) {
            level = Level.DEBUG;
        }

        log.log(level, LOG_FORMAT, getClient(request), request.getMethod(), uri, elapsed, message);
    }

    private String getClient(ServerHttpRequest request) {
        String xForwardedFor = CollectionUtils.firstElement(request.getHeaders().get(X_FORWARDED_FOR));

        if (StringUtils.isNotBlank(xForwardedFor)) {
            return xForwardedFor;
        }

        InetSocketAddress remoteAddress = request.getRemoteAddress();

        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().toString();
        }

        return LOCALHOST;
    }

    private static class CancelledException extends RuntimeException {
        private static final String MESSAGE = "cancelled";

        @Serial
        private static final long serialVersionUID = -1065743479862315529L;

        CancelledException() {
            super(MESSAGE);
        }
    }
}
