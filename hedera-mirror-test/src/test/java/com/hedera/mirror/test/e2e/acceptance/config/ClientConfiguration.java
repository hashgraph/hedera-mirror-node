package com.hedera.mirror.test.e2e.acceptance.config;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.hashgraph.sdk.ReceiptStatusException;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;

@Configuration
@RequiredArgsConstructor
@EnableRetry
class ClientConfiguration {

    private final AcceptanceTestProperties acceptanceTestProperties;

    @Bean
    RetryTemplate retryTemplate() {
        FixedBackOffPolicy fixedBackOffPolicy = new FixedBackOffPolicy();
        fixedBackOffPolicy.setBackOffPeriod(acceptanceTestProperties.getBackOffPeriod().toMillis());

        Map retryableExceptionMap = Map.of(
                PrecheckStatusException.class, true,
                TimeoutException.class, true,
                RuntimeException.class, true,
                ReceiptStatusException.class, true); // make configurable
        int maxRetries = acceptanceTestProperties.getMaxRetries();
        var simpleRetryPolicy = new SimpleRetryPolicy(maxRetries, retryableExceptionMap, true);

        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setBackOffPolicy(fixedBackOffPolicy);
        retryTemplate.setRetryPolicy(simpleRetryPolicy);
        return retryTemplate;
    }

    @Bean
    WebClient webClient() {
        HttpClient httpClient = HttpClient.create()
                .compress(true)
                .followRedirect(true)
                .option(
                        ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        (int) acceptanceTestProperties.getWebClientProperties().getConnectionTimeout().toMillis())
                .doOnConnected(connection -> {
                    connection.addHandlerLast(new ReadTimeoutHandler(
                            acceptanceTestProperties.getWebClientProperties().getReadTimeout().toMillis(),
                            TimeUnit.MILLISECONDS));
                    connection.addHandlerLast(new WriteTimeoutHandler(
                            acceptanceTestProperties.getWebClientProperties().getWriteTimeout().toMillis(),
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
                .filter((request, next) -> next.exchange(request).doOnNext(response ->
                        logger.info("{} {}: {}", request.method(), request.url(), response.statusCode()))
                )
                .build();
    }
}
