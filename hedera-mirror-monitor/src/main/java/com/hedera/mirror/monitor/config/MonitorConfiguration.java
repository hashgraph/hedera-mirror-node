package com.hedera.mirror.monitor.config;

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

import java.util.concurrent.Executors;
import javax.annotation.Resource;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.NullChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.endpoint.ReactiveMessageSourceProducer;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.GenericMessage;

import com.hedera.mirror.monitor.generator.TransactionGenerator;
import com.hedera.mirror.monitor.publish.PublishProperties;
import com.hedera.mirror.monitor.publish.PublishRequest;
import com.hedera.mirror.monitor.publish.TransactionPublisher;

@Log4j2
@Configuration
class MonitorConfiguration {

    @Resource
    private PublishProperties publishProperties;

    @Resource
    private TransactionGenerator transactionGenerator;

    @Resource
    private TransactionPublisher transactionPublisher;

    @Bean
    IntegrationFlow publishFlow() {
        return IntegrationFlows
                .from(new ReactiveMessageSourceProducer(() -> new GenericMessage<>(transactionGenerator.next())))
                .channel(c -> c.executor(Executors.newFixedThreadPool(publishProperties.getConnections())))
                .handle(PublishRequest.class, (p, h) -> transactionPublisher.publish(p))
                .nullChannel();
    }

    @Bean
    MessageChannel errorChannel() {
        return new NullChannel();
    }
}
