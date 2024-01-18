/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.record.entity.notify;

import static com.hedera.mirror.common.converter.ObjectToStringSerializer.OBJECT_MAPPER;

import com.google.common.base.Stopwatch;
import com.hedera.mirror.common.domain.topic.TopicMessage;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.importer.parser.record.entity.BatchPublisher;
import com.hedera.mirror.importer.parser.record.entity.ConditionOnEntityRecordParser;
import com.hedera.mirror.importer.parser.record.entity.ParserContext;
import com.hedera.mirror.importer.util.Utility;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.inject.Named;
import java.util.Collection;
import lombok.CustomLog;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;

@ConditionOnEntityRecordParser
@CustomLog
@Named
@Order(2)
public class NotifyingPublisher implements BatchPublisher {

    private static final String SQL = "select pg_notify('topic_message', ?)";

    private final NotifyProperties notifyProperties;
    private final JdbcTemplate jdbcTemplate;
    private final ParserContext parserContext;
    private final Timer timer;

    NotifyingPublisher(
            NotifyProperties notifyProperties,
            JdbcTemplate jdbcTemplate,
            MeterRegistry meterRegistry,
            ParserContext parserContext) {
        this.notifyProperties = notifyProperties;
        this.jdbcTemplate = jdbcTemplate;
        this.parserContext = parserContext;
        this.timer = PUBLISH_TIMER.tag("type", "notify").register(meterRegistry);
    }

    @Override
    public void onEnd(RecordFile recordFile) {
        if (!notifyProperties.isEnabled()) {
            return;
        }

        var topicMessages = parserContext.get(TopicMessage.class);
        if (topicMessages.isEmpty()) {
            return;
        }

        var stopwatch = Stopwatch.createStarted();
        timer.record(() -> jdbcTemplate.execute(SQL, callback(topicMessages)));
        log.info("Finished notifying {} messages in {}", topicMessages.size(), stopwatch);
    }

    private PreparedStatementCallback<int[]> callback(Collection<TopicMessage> topicMessages) {
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
            Utility.handleRecoverableError("Error serializing topicMessage to json", topicMessage, e);
            return null;
        }
    }
}
