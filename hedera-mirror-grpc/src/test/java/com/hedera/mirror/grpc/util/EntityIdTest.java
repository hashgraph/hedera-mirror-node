package com.hedera.mirror.grpc.util;

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

import static com.hedera.mirror.grpc.util.EntityId.NUM_BITS;
import static com.hedera.mirror.grpc.util.EntityId.REALM_BITS;
import static com.hedera.mirror.grpc.util.EntityId.SHARD_BITS;
import static org.assertj.core.api.Assertions.assertThat;

import com.hederahashgraph.api.proto.java.TopicID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EntityIdTest {

    @ParameterizedTest
    @CsvSource({
            "0, 0, 0, 0",
            "0, 0, 10, 10",
            "0, 0, 4294967295, 4294967295",
            "10, 10, 10, 2814792716779530",
            "32767, 65535, 4294967295, 9223372036854775807", // max +ve for shard, max for realm, max for num = max
            // +ve long
            "32767, 0, 0, 9223090561878065152"
    })
    void testEntityEncoding(long shard, long realm, long num, long encodedId) {
        TopicID topicID = TopicID.newBuilder().setShardNum(shard).setRealmNum(realm).setTopicNum(num).build();
        assertThat(EntityId.encode(topicID)).isEqualTo(encodedId);
    }

    @Test
    void testEntityEncodingNullResult() {
        assertThat(EntityId.encode(null)).isNull();

        TopicID topicID = TopicID.newBuilder().setShardNum(1L << SHARD_BITS).setRealmNum(0).setTopicNum(0).build();
        assertThat(EntityId.encode(topicID)).isNull();

        topicID = TopicID.newBuilder().setShardNum(0).setRealmNum(1L << REALM_BITS).setTopicNum(0).build();
        assertThat(EntityId.encode(topicID)).isNull();

        topicID = TopicID.newBuilder().setShardNum(0).setRealmNum(0).setTopicNum(1L << NUM_BITS).build();
        assertThat(EntityId.encode(topicID)).isNull();

        topicID = TopicID.newBuilder().setShardNum(-1).setRealmNum(0).setTopicNum(0).build();
        assertThat(EntityId.encode(topicID)).isNull();

        topicID = TopicID.newBuilder().setShardNum(0).setRealmNum(-1).setTopicNum(0).build();
        assertThat(EntityId.encode(topicID)).isNull();

        topicID = TopicID.newBuilder().setShardNum(0).setRealmNum(0).setTopicNum(-1).build();
        assertThat(EntityId.encode(topicID)).isNull();
    }
}
