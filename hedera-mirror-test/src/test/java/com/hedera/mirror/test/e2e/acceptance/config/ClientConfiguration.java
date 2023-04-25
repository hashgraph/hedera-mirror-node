/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.hashgraph.sdk.ReceiptStatusException;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
@RequiredArgsConstructor
@EnableRetry
public class ClientConfiguration {

    public static final String REST_RETRY_TEMPLATE = "rest";

    private final AcceptanceTestProperties acceptanceTestProperties;

    @Bean
    RetryTemplate retryTemplate() {
        return retryTemplate(List.of(
                PrecheckStatusException.class,
                TimeoutException.class,
                RuntimeException.class,
                ReceiptStatusException.class));
    }

    @Bean(name = REST_RETRY_TEMPLATE)
    RetryTemplate restRetryTemplate() {
        return retryTemplate(List.of(AssertionError.class));
    }

    @Bean
    WebClient webClient() {
        HttpClient httpClient = HttpClient.create()
                .compress(true)
                .followRedirect(true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) acceptanceTestProperties
                        .getWebClientProperties()
                        .getConnectionTimeout()
                        .toMillis())
                .doOnConnected(connection -> {
                    connection.addHandlerLast(new ReadTimeoutHandler(
                            acceptanceTestProperties
                                    .getWebClientProperties()
                                    .getReadTimeout()
                                    .toMillis(),
                            TimeUnit.MILLISECONDS));
                    connection.addHandlerLast(new WriteTimeoutHandler(
                            acceptanceTestProperties
                                    .getWebClientProperties()
                                    .getWriteTimeout()
                                    .toMillis(),
                            TimeUnit.MILLISECONDS));
                })
                .wiretap(acceptanceTestProperties.getWebClientProperties().isWiretap()); // enable request logging

        // support snake_case to avoid manually mapping JsonProperty on all properties
        ObjectMapper objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        Jackson2JsonDecoder jackson2JsonDecoder = new Jackson2JsonDecoder(objectMapper, MediaType.APPLICATION_JSON);
        Jackson2JsonEncoder jackson2JsonEncoder = new Jackson2JsonEncoder(objectMapper, MediaType.APPLICATION_JSON);
        Logger logger = LogManager.getLogger(MirrorNodeClient.class);

        return WebClient.builder()
                .baseUrl(acceptanceTestProperties.getRestPollingProperties().getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(clientCodecConfigurer -> {
                    clientCodecConfigurer.defaultCodecs().jackson2JsonDecoder(jackson2JsonDecoder);
                    clientCodecConfigurer.defaultCodecs().jackson2JsonEncoder(jackson2JsonEncoder);
                })
                .defaultHeaders(httpHeaders -> {
                    httpHeaders.setAccept((List.of(MediaType.APPLICATION_JSON)));
                    httpHeaders.setContentType(MediaType.APPLICATION_JSON);
                    httpHeaders.setCacheControl(CacheControl.noStore());
                })
                .filter((request, next) -> next.exchange(request).doOnNext(response -> {
                    if (logger.isDebugEnabled() || response.statusCode() != HttpStatus.NOT_FOUND) {
                        logger.info("{} {}: {}", request.method(), request.url(), response.statusCode());
                    }
                }))
                .build();
    }

    private RetryTemplate retryTemplate(List<Class<? extends Throwable>> throwables) {
        return RetryTemplate.builder()
                .fixedBackoff(acceptanceTestProperties.getBackOffPeriod().toMillis())
                .maxAttempts(acceptanceTestProperties.getMaxRetries())
                .retryOn(throwables)
                .traversingCauses()
                .build();
    }
}
