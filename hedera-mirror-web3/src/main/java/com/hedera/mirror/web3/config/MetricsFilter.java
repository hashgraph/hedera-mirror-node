/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import static org.springframework.http.HttpHeaders.CONTENT_LENGTH;
import static org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Meter.MeterProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.inject.Named;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.CustomLog;
import org.apache.catalina.connector.ResponseFacade;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

@CustomLog
@Named
class MetricsFilter extends OncePerRequestFilter {

    static final String REQUEST_BYTES = "hedera.mirror.web3.request.bytes";
    static final String RESPONSE_BYTES = "hedera.mirror.web3.response.bytes";

    private static final String METHOD = "method";
    private static final String URI = "uri";

    private final MeterProvider<DistributionSummary> requestBytesProvider;
    private final MeterProvider<DistributionSummary> responseBytesProvider;

    MetricsFilter(MeterRegistry meterRegistry) {
        this.requestBytesProvider = DistributionSummary.builder(REQUEST_BYTES)
                .baseUnit("bytes")
                .description("The size of the request in bytes")
                .withRegistry(meterRegistry);
        this.responseBytesProvider = DistributionSummary.builder(RESPONSE_BYTES)
                .baseUnit("bytes")
                .description("The size of the response in bytes")
                .withRegistry(meterRegistry);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            filterChain.doFilter(request, response);
        } finally {
            recordMetrics(request, response);
        }
    }

    private void recordMetrics(HttpServletRequest request, ServletResponse response) {
        if (request.getAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE) instanceof String uri) {
            var tags = Tags.of(METHOD, request.getMethod(), URI, uri);

            var contentLengthHeader = request.getHeader(CONTENT_LENGTH);
            if (contentLengthHeader != null) {
                long contentLength = Math.max(0L, NumberUtils.toLong(contentLengthHeader));
                requestBytesProvider.withTags(tags).record(contentLength);
            }

            var responseFacade = WebUtils.getNativeResponse(response, ResponseFacade.class);
            if (responseFacade != null) {
                var responseSize = responseFacade.getContentWritten();
                responseBytesProvider.withTags(tags).record(responseSize);
            }
        }
    }
}
