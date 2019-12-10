package com.hedera.mirror.grpc.domain;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.Consumer;
import javax.annotation.PostConstruct;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.data.r2dbc.core.DatabaseClient;

@Named
@RequiredArgsConstructor
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DomainBuilder {

    private final Instant now = Instant.now();
    private final DatabaseClient databaseClient;
    private long sequenceNumber = 0L;

    @PostConstruct
    void setup() {
        databaseClient.delete().from(TopicMessage.class).fetch().rowsUpdated().block();
    }

    public TopicMessage topicMessage() {
        return topicMessage(t -> {
        });
    }

    /**
     * Generates a Topic Message with sane defaults and inserts it into the database. The consensusTimestamp and
     * sequenceNumber auto-increase by one on each call.
     *
     * @param customizer allows one to customize the TopicMessage before it is inserted
     * @return the inserted TopicMessage
     */
    public TopicMessage topicMessage(Consumer<TopicMessage.TopicMessageBuilder> customizer) {
        TopicMessage.TopicMessageBuilder builder = TopicMessage.builder()
                .consensusTimestamp(now.plus(sequenceNumber, ChronoUnit.NANOS))
                .realmNum(0)
                .message(new byte[] {0, 1, 2})
                .runningHash(new byte[] {3, 4, 5})
                .sequenceNumber(++sequenceNumber)
                .topicNum(0);

        customizer.accept(builder);
        TopicMessage topicMessage = builder.build();
        insert(topicMessage);
        return topicMessage;
    }

    private <T> void insert(T domainObject) {
        databaseClient.insert()
                .into((Class<T>) domainObject.getClass())
                .using(domainObject)
                .fetch()
                .first()
                .block();
    }
}
