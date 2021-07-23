package com.hedera.mirror.monitor.config;

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
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.reactivestreams.Publisher;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.CollectionUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Log4j2
@Named
public class LoggingFilter implements WebFilter {

    private static final String ACTUATOR_PATH = "/actuator/";
    private static final String LOCALHOST = "127.0.0.1";
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return chain.filter(exchange).transformDeferred(call -> doFilter(exchange, call));
    }

    private Publisher<Void> doFilter(ServerWebExchange exchange, Mono<Void> call) {
        long startTime = System.currentTimeMillis();
        return call.doOnSuccess(done -> onSuccess(exchange, startTime))
                .doOnError(cause -> onError(exchange, startTime, cause));
    }

    private void onSuccess(ServerWebExchange exchange, long startTime) {
        long elapsed = System.currentTimeMillis() - startTime;
        ServerHttpRequest request = exchange.getRequest();
        URI uri = request.getURI();
        Level level = StringUtils.startsWith(uri.getPath(), ACTUATOR_PATH) ? Level.DEBUG : Level.INFO;
        log.log(level, "{} {} {} in {} ms: {}", getClient(request), request.getMethod(), uri, elapsed,
                exchange.getResponse().getStatusCode());
    }

    private void onError(ServerWebExchange exchange, long startTime, Throwable t) {
        long elapsed = System.currentTimeMillis() - startTime;
        ServerHttpRequest request = exchange.getRequest();
        log.warn("{} {} {} in {} ms: {}", getClient(request), request.getMethod(), request.getURI(), elapsed,
                t.getMessage());
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
}
