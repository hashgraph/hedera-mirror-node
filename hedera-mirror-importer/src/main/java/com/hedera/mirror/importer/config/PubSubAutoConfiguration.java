package com.hedera.mirror.importer.config;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.cloud.gcp.autoconfigure.pubsub.GcpPubSubAutoConfiguration;
import org.springframework.cloud.gcp.pubsub.core.PubSubTemplate;
import org.springframework.cloud.gcp.pubsub.integration.outbound.PubSubMessageHandler;
import org.springframework.cloud.gcp.pubsub.support.converter.JacksonPubSubMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

import com.hedera.mirror.importer.parser.record.pubsub.ConditionalOnPubSubRecordParser;
import com.hedera.mirror.importer.parser.record.pubsub.PubSubProperties;

@Configuration
@AutoConfigureAfter(GcpPubSubAutoConfiguration.class)  // for SubscriberFactory and PublisherFactory
@ConditionalOnPubSubRecordParser
@RequiredArgsConstructor
public class PubSubAutoConfiguration {

    private final PubSubProperties pubSubProperties;

    private final PubSubTemplate pubSubTemplate;

    // Required by PubSubRecordItemListener
    @Bean
    MessageChannel pubsubOutputChannel() {
        return new DirectChannel();
    }

    @Bean
    @ServiceActivator(inputChannel = "pubsubOutputChannel")
    MessageHandler pubSubMessageSender() {
        pubSubTemplate.setMessageConverter(new JacksonPubSubMessageConverter(new ObjectMapper()));
        PubSubMessageHandler pubSubMessageHandler =
                new PubSubMessageHandler(pubSubTemplate, pubSubProperties.getTopicName());
        // Optimize in future to use async to support higher TPS. Can do ~20-30/sec right now which is sufficient
        // to setup BQ dataset for mainnet. Exposing pubsub for testnet will have to wait.
        pubSubMessageHandler.setSync(true);
        return pubSubMessageHandler;
    }
}
