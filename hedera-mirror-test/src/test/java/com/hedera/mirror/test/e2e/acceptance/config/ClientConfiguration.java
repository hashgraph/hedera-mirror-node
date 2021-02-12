package com.hedera.mirror.test.e2e.acceptance.config;

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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
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
import reactor.netty.tcp.TcpClient;

import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.client.SDKClient;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient;
import com.hedera.mirror.test.e2e.acceptance.client.TopicClient;

@Configuration
@Log4j2
@RequiredArgsConstructor
@EnableRetry
public class ClientConfiguration {
    @Autowired
    private final AcceptanceTestProperties acceptanceTestProperties;

    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public SDKClient sdkClient() throws InterruptedException {
        return new SDKClient(acceptanceTestProperties);
    }

    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public MirrorNodeClient mirrorNodeClient() throws InterruptedException {
        return new MirrorNodeClient(sdkClient());
    }

    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public TopicClient topicClient() throws InterruptedException {
        return new TopicClient(sdkClient());
    }

    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public AccountClient accountClient() throws InterruptedException {
        return new AccountClient(sdkClient());
    }

    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public TokenClient tokenClient() throws InterruptedException {
        return new TokenClient(sdkClient());
    }

    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        FixedBackOffPolicy fixedBackOffPolicy = new FixedBackOffPolicy();
        fixedBackOffPolicy.setBackOffPeriod(acceptanceTestProperties.getSubscribeRetryBackoffPeriod().toMillis());
        retryTemplate.setBackOffPolicy(fixedBackOffPolicy);

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(acceptanceTestProperties.getSubscribeRetries());
        retryTemplate.setRetryPolicy(retryPolicy);
        return retryTemplate;
    }

    @Bean
    WebClient webClient() {
        TcpClient tcpClient = TcpClient
                .create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .doOnConnected(connection -> {
                    connection.addHandlerLast(new ReadTimeoutHandler(5000, TimeUnit.MILLISECONDS));
                    connection.addHandlerLast(new WriteTimeoutHandler(5000, TimeUnit.MILLISECONDS));
                })
                // enable request logging
                .wiretap(true);

        // support snake_case to avoid manually mapping JsonProperty on all properties
        ObjectMapper objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        Jackson2JsonDecoder jackson2JsonDecoder = new Jackson2JsonDecoder(objectMapper, MediaType.APPLICATION_JSON);
        Jackson2JsonEncoder jackson2JsonEncoder = new Jackson2JsonEncoder(objectMapper, MediaType.APPLICATION_JSON);

        return WebClient.builder()
                .baseUrl(acceptanceTestProperties.getRestPollingProperties().getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(HttpClient.from(tcpClient)))
                .codecs(clientCodecConfigurer -> {
                    clientCodecConfigurer.defaultCodecs().jackson2JsonDecoder(jackson2JsonDecoder);
                    clientCodecConfigurer.defaultCodecs().jackson2JsonEncoder(jackson2JsonEncoder);
                })
                .defaultHeaders(httpHeaders -> {
                    httpHeaders.setAccept((List.of(MediaType.APPLICATION_JSON)));
                    httpHeaders.setContentType(MediaType.APPLICATION_JSON);
                    httpHeaders.setCacheControl(CacheControl.noStore());
                })
                .build();
    }
}
