package com.hedera.mirror.importer.config;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gcp.pubsub.core.PubSubTemplate;
import org.springframework.cloud.gcp.pubsub.integration.outbound.PubSubMessageHandler;
import org.springframework.cloud.gcp.pubsub.support.DefaultPublisherFactory;
import org.springframework.cloud.gcp.pubsub.support.DefaultSubscriberFactory;
import org.springframework.cloud.gcp.pubsub.support.PublisherFactory;
import org.springframework.cloud.gcp.pubsub.support.SubscriberFactory;
import org.springframework.cloud.gcp.pubsub.support.converter.JacksonPubSubMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

import com.hedera.mirror.importer.parser.record.PostgresWritingRecordParsedItemHandler;
import com.hedera.mirror.importer.parser.record.RecordItemListener;
import com.hedera.mirror.importer.parser.record.RecordItemParser;
import com.hedera.mirror.importer.parser.record.RecordStreamFileListener;
import com.hedera.mirror.importer.parser.record.pubsub.PubSubProperties;
import com.hedera.mirror.importer.parser.record.pubsub.PubSubRecordItemListener;
import com.hedera.mirror.importer.parser.record.pubsub.PubSubRecordStreamFileListener;

@Configuration
@Log4j2
@RequiredArgsConstructor
public class RecordParserConfiguration {

    // Required by PubSubRecordItemListener
    @Bean
    MessageChannel pubsubOutputChannel() {
        return new DirectChannel();
    }

    /**
     * Enable components to persist transactions into database
     */
    @ConditionalOnProperty(prefix = "hedera.mirror.parser.record.persist", name = "to", havingValue = "DATABASE",
            matchIfMissing = true)
    protected static class DatabaseConfiguration {
        @Bean
        @Primary
        RecordItemListener recordItemListener(RecordItemParser recordItemParser) {
            return recordItemParser;
        }

        @Bean
        @Primary
        RecordStreamFileListener recordStreamFileListener(
                PostgresWritingRecordParsedItemHandler postgresWritingRecordParsedItemHandler) {
            return postgresWritingRecordParsedItemHandler;
        }
    }

    /**
     * Enable components for pushing parsed transactions to PubSub
     */
    @ConditionalOnProperty(prefix = "hedera.mirror.parser.record.persist", name = "to", havingValue = "PUBSUB")
    protected static class PubSubConfiguration {
        @Bean
        @Primary
        RecordItemListener recordItemListener(PubSubRecordItemListener pubSubRecordItemListener) {
            return pubSubRecordItemListener;
        }

        @Bean
        @Primary
        RecordStreamFileListener recordStreamFileListener(PubSubRecordStreamFileListener pubSubRecordStreamFileListener) {
            return pubSubRecordStreamFileListener;
        }

        @Bean
        @ServiceActivator(inputChannel = "pubsubOutputChannel")
        MessageHandler pubSubMessageSender(SubscriberFactory subscriberFactory, PublisherFactory publisherFactory,
                                           PubSubProperties pubSubProperties) {
            PubSubTemplate pubSubTemplate = new PubSubTemplate(publisherFactory, subscriberFactory);
            pubSubTemplate.setMessageConverter(new JacksonPubSubMessageConverter(new ObjectMapper()));
            PubSubMessageHandler pubSubMessageHandler =
                    new PubSubMessageHandler(pubSubTemplate, pubSubProperties.getTopicName());
            // Optimize in future to use async to support higher TPS. Can do ~20-30/sec right now which is sufficient
            // to setup BQ dataset for mainnet. Exposing pubsub for testnet will have to wait.
            pubSubMessageHandler.setSync(true);
            return pubSubMessageHandler;
        }
    }
}
