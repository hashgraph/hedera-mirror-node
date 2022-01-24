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

import java.net.InetSocketAddress;
import java.net.URI;
import javax.inject.Named;
import lombok.CustomLog;
import org.apache.commons.lang3.StringUtils;
import org.reactivestreams.Publisher;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.CollectionUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@CustomLog
@Named
class LoggingFilter implements WebFilter {

    private static final String ACTUATOR_PATH = "/actuator/";
    private static final String LOCALHOST = "127.0.0.1";
    private static final String LOG_FORMAT = "{} {} {} in {} ms: {}";
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        log.info("filter1: {}", exchange.getRequest().getPath());
        return chain.filter(exchange).transformDeferred((call) -> filter(exchange, call));
    }

    private Publisher<Void> filter(ServerWebExchange exchange, Mono<Void> call) {
        long start = System.currentTimeMillis();
        log.info("filter2: {}", exchange.getRequest().getPath());
        return call.doOnEach((signal) -> onTerminalSignal(exchange, signal.getThrowable(), start))
                .doOnCancel(() -> onTerminalSignal(exchange, new CancelledException(), start));
    }

    private void onTerminalSignal(ServerWebExchange exchange, Throwable cause, long start) {
        ServerHttpResponse response = exchange.getResponse();
        log.info("onTerminalSignal {}: {}", exchange.getRequest().getPath(), cause);
        if (response.isCommitted() || cause instanceof CancelledException) {
            logRequest(exchange, start, cause);
        } else {
            log.info("beforeCommit");
            response.beforeCommit(() -> {
                logRequest(exchange, start, cause);
                return Mono.empty();
            });
        }
    }

    private void logRequest(ServerWebExchange exchange, long startTime, Throwable t) {
        log.info("logRequest {}: {}", exchange.getRequest().getPath(), t);
        long elapsed = System.currentTimeMillis() - startTime;
        ServerHttpRequest request = exchange.getRequest();
        URI uri = request.getURI();
        Object message = t != null ? t.getMessage() : exchange.getResponse().getStatusCode();
        Object[] params = new Object[] {getClient(request), request.getMethod(), uri, elapsed, message};

        if (t != null) {
            log.warn(LOG_FORMAT, params);
        } else if (StringUtils.startsWith(uri.getPath(), ACTUATOR_PATH)) {
            log.debug(LOG_FORMAT, params);
        } else {
            log.info(LOG_FORMAT, params);
        }
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
        CancelledException() {
            super("cancelled");
        }
    }
}
