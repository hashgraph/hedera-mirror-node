/*
 * Copyright (C) 2020-2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.test.e2e.acceptance.config;

import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.hashgraph.sdk.ReceiptStatusException;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import java.util.List;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.support.RetryTemplate;

@Configuration
@EnableRetry
@RequiredArgsConstructor
class ClientConfiguration {

    private final AcceptanceTestProperties acceptanceTestProperties;

    @Bean
    RestClientCustomizer restClientCustomizer() {
        var baseUrl = acceptanceTestProperties.getRestProperties().getBaseUrl();
        var clientProperties = acceptanceTestProperties.getWebClientProperties();
        var factorySettings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(clientProperties.getConnectionTimeout())
                .withReadTimeout(clientProperties.getReadTimeout());
        var logger = LoggerFactory.getLogger(MirrorNodeClient.class);

        return builder -> builder.baseUrl(baseUrl)
                .defaultHeaders(httpHeaders -> {
                    httpHeaders.setAccept((List.of(MediaType.APPLICATION_JSON)));
                    httpHeaders.setContentType(MediaType.APPLICATION_JSON);
                    httpHeaders.setCacheControl(CacheControl.noStore());
                })
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(factorySettings))
                .requestInterceptor((request, body, execution) -> {
                    var response = execution.execute(request, body);
                    var statusCode = response.getStatusCode();

                    if (logger.isDebugEnabled() || statusCode != HttpStatus.NOT_FOUND) {
                        logger.info("{} {}: {}", request.getMethod(), request.getURI(), statusCode);
                    }

                    return response;
                });
    }

    @Bean
    RetryTemplate retryTemplate() {
        List<Class<? extends Throwable>> retryableExceptions = List.of(
                PrecheckStatusException.class,
                ReceiptStatusException.class,
                RuntimeException.class,
                TimeoutException.class);
        return RetryTemplate.builder()
                .fixedBackoff(acceptanceTestProperties.getBackOffPeriod().toMillis())
                .maxAttempts(acceptanceTestProperties.getMaxRetries())
                .retryOn(retryableExceptions)
                .traversingCauses()
                .build();
    }
}
