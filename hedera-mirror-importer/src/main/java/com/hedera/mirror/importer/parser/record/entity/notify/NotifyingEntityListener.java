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
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;

import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.sql.SqlProperties;

@Log4j2
@Named
@RequiredArgsConstructor
public class NotifyingEntityListener implements EntityListener {

    private static final String SQL = "select pg_notify('topic_message', ?)";
    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().setPropertyNamingStrategy(SNAKE_CASE);

    private final SqlProperties sqlProperties;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void onTopicMessage(TopicMessage topicMessage) throws ImporterException {
        try {
            if (sqlProperties.isNotifyTopicMessage()) {
                String json = OBJECT_MAPPER.writeValueAsString(topicMessage);

                if (json.length() >= sqlProperties.getMaxJsonPayloadSize()) {
                    log.warn("Unable to notify large payload of size {}B: {}", json.length(), topicMessage);
                    return;
                }

                jdbcTemplate.update(SQL, json);
            }
        } catch (Exception e) {
            throw new ParserException(e);
        }
    }
}
