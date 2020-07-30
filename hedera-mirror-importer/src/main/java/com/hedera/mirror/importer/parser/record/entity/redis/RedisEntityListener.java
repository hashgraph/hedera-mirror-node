package com.hedera.mirror.importer.parser.record.entity.redis;

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

import com.hederahashgraph.api.proto.java.NodeAddress;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.redis.core.RedisOperations;

import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;

@Log4j2
@Named
@RequiredArgsConstructor
public class RedisEntityListener implements EntityListener {

    private static final String CHANNEL = "topic_message";

    private final RedisOperations redisTemplate;

    @Override
    public void onTopicMessage(TopicMessage topicMessage) throws ImporterException {
        redisTemplate.opsForStream();
        NodeAddress nodeAddress = null;
        nodeAddress.getNodeCertHash().asReadOnlyByteBuffer();
    }
}
