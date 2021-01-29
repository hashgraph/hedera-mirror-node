package com.hedera.mirror.importer.domain;

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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import org.junit.jupiter.api.Test;

public class TopicMessageTest {

    // Test serialization to JSON to verify contract with PostgreSQL listen/notify
    @Test
    void toJson() throws Exception {
        TopicMessage topicMessage = new TopicMessage();
        topicMessage.setChunkNum(1);
        topicMessage.setChunkTotal(2);
        topicMessage.setConsensusTimestamp(1594401417000000000L);
        topicMessage.setMessage(new byte[] {1, 2, 3});
        topicMessage.setPayerAccountId(EntityId.of("0.1.1000", EntityTypeEnum.ACCOUNT));
        topicMessage.setRealmNum(0);
        topicMessage.setRunningHash(new byte[] {4, 5, 6});
        topicMessage.setRunningHashVersion(2);
        topicMessage.setSequenceNumber(1L);
        topicMessage.setTopicNum(1001);
        topicMessage.setValidStartTimestamp(1594401416000000000L);

        ObjectMapper objectMapper = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        String json = objectMapper.writeValueAsString(topicMessage);
        assertThat(json).isEqualTo("{" +
                "\"@type\":\"TopicMessage\"," +
                "\"chunk_num\":1," +
                "\"chunk_total\":2," +
                "\"consensus_timestamp\":1594401417000000000," +
                "\"message\":\"AQID\"," +
                "\"payer_account_id\":4294968296," +
                "\"realm_num\":0," +
                "\"running_hash\":\"BAUG\"," +
                "\"running_hash_version\":2," +
                "\"sequence_number\":1," +
                "\"topic_num\":1001," +
                "\"valid_start_timestamp\":1594401416000000000}");
    }
}
