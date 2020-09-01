package com.hedera.mirror.importer.parser.record.entity.notify;

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

import static com.fasterxml.jackson.databind.PropertyNamingStrategy.SNAKE_CASE;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;

import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.parser.record.entity.ConditionOnEntityRecordParser;
import com.hedera.mirror.importer.parser.record.entity.EntityBatchEvent;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;

@ConditionOnEntityRecordParser
@Log4j2
@Named
@Order(3)
@RequiredArgsConstructor
public class NotifyingEntityListener implements EntityListener {

    private static final String SQL = "select pg_notify('topic_message', ?)";
    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().setPropertyNamingStrategy(SNAKE_CASE);

    private final NotifyProperties notifyProperties;
    private final JdbcTemplate jdbcTemplate;
    private final List<TopicMessage> topicMessages = new ArrayList<>();

    @Override
    public boolean isEnabled() {
        return notifyProperties.isEnabled();
    }

    @Override
    public void onTopicMessage(TopicMessage topicMessage) throws ImporterException {
        topicMessages.add(topicMessage);
    }

    @EventListener
    public void onBatch(EntityBatchEvent event) {
        jdbcTemplate.execute(SQL, callback(topicMessages));
        log.info("Finished notifying {} messages", topicMessages.size());
        topicMessages.clear();
    }

    private PreparedStatementCallback callback(Collection<TopicMessage> topicMessages) {
        return preparedStatement -> {
            for (TopicMessage topicMessage : topicMessages) {
                String json = toJson(topicMessage);
                if (json != null) {
                    preparedStatement.setString(1, json);
                    preparedStatement.addBatch();
                }
            }
            return preparedStatement.executeBatch();
        };
    }

    private String toJson(TopicMessage topicMessage) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(topicMessage);

            if (json.length() >= notifyProperties.getMaxJsonPayloadSize()) {
                log.warn("Unable to notify large payload of size {}B: {}", json.length(), topicMessage);
                return null;
            }

            return json;
        } catch (Exception e) {
            throw new ParserException(e);
        }
    }
}
