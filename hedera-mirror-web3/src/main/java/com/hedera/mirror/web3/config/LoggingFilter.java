/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

import jakarta.inject.Named;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.CustomLog;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@CustomLog
@Named
class LoggingFilter extends OncePerRequestFilter {

    @SuppressWarnings("java:S1075")
    private static final String ACTUATOR_PATH = "/actuator/";

    private static final String LOG_FORMAT = "{} {} {} in {} ms: {}";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
        long start = System.currentTimeMillis();
        Exception cause = null;

        try {
            filterChain.doFilter(request, response);
        } catch (Exception t) {
            cause = t;
        } finally {
            logRequest(request, response, start, cause);
        }
    }

    private void logRequest(HttpServletRequest request, HttpServletResponse response, long startTime, Exception cause) {
        long elapsed = System.currentTimeMillis() - startTime;
        String uri = request.getRequestURI();
        var message = cause != null ? cause.getMessage() : response.getStatus();
        var params = new Object[] {request.getRemoteAddr(), request.getMethod(), uri, elapsed, message};

        if (StringUtils.startsWith(uri, ACTUATOR_PATH)) {
            log.debug(LOG_FORMAT, params);
        } else if (cause != null) {
            log.warn(LOG_FORMAT, params);
        } else {
            log.info(LOG_FORMAT, params);
        }
    }
}
