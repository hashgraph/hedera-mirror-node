/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.config;

import com.hedera.mirror.common.domain.StreamFile;
import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.common.domain.balance.AccountBalanceFile;
import com.hedera.mirror.common.domain.event.EventFile;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.importer.parser.ParserProperties;
import com.hedera.mirror.importer.parser.StreamFileParser;
import com.hedera.mirror.importer.parser.balance.AccountBalanceFileParser;
import com.hedera.mirror.importer.parser.balance.BalanceParserProperties;
import com.hedera.mirror.importer.parser.event.EventFileParser;
import com.hedera.mirror.importer.parser.event.EventParserProperties;
import com.hedera.mirror.importer.parser.record.RecordFileParser;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.NullChannel;
import org.springframework.integration.context.IntegrationContextUtils;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.integration.dsl.Pollers;
import org.springframework.messaging.MessageChannel;

@Configuration
public class MessagingConfiguration {

    // Shared channel containing all stream types until they're routed to the individual channels
    public static final String CHANNEL_STREAM = "stream";
    private static final String CHANNEL_BALANCE = CHANNEL_STREAM + ".balance";
    private static final String CHANNEL_EVENT = CHANNEL_STREAM + ".event";
    private static final String CHANNEL_RECORD = CHANNEL_STREAM + ".record";

    @Bean(CHANNEL_BALANCE)
    MessageChannel channelBalance(BalanceParserProperties properties) {
        return channel(properties);
    }

    @Bean(CHANNEL_EVENT)
    MessageChannel channelEvent(EventParserProperties properties) {
        return channel(properties);
    }

    @Bean(CHANNEL_RECORD)
    MessageChannel channelRecord(RecordParserProperties properties) {
        return channel(properties);
    }

    @Bean(IntegrationContextUtils.ERROR_CHANNEL_BEAN_NAME)
    MessageChannel errorChannel() {
        return new NullChannel();
    }

    @Bean
    IntegrationFlow integrationFlowBalance(AccountBalanceFileParser parser) {
        return integrationFlow(parser, AccountBalanceFile.class);
    }

    @Bean
    IntegrationFlow integrationFlowEvent(EventFileParser parser) {
        return integrationFlow(parser, EventFile.class);
    }

    @Bean
    IntegrationFlow integrationFlowRecord(RecordFileParser parser) {
        return integrationFlow(parser, RecordFile.class);
    }

    @Bean
    IntegrationFlow streamFileRouter() {
        return IntegrationFlow.from(CHANNEL_STREAM)
                .route(StreamFile.class, s -> channelName(s.getType()))
                .get();
    }

    private MessageChannel channel(ParserProperties properties) {
        if (properties.getQueueCapacity() <= 0) {
            return MessageChannels.direct().getObject();
        }

        return MessageChannels.queue(properties.getQueueCapacity()).getObject();
    }

    private <T extends StreamFile<?>> IntegrationFlow integrationFlow(
            StreamFileParser<T> parser, Class<T> streamFileType) {
        ParserProperties properties = parser.getProperties();
        return IntegrationFlow.from(channelName(properties.getStreamType()))
                .handle(
                        streamFileType,
                        (s, h) -> {
                            parser.parse(s);
                            return null;
                        },
                        e -> {
                            if (properties.getQueueCapacity() > 0) {
                                e.poller(Pollers.fixedDelay(properties.getFrequency()));
                            }
                        })
                .get();
    }

    private String channelName(StreamType streamType) {
        return CHANNEL_STREAM + "." + streamType.toString().toLowerCase();
    }
}
