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

import com.hederahashgraph.api.proto.java.TopicID;
import lombok.experimental.UtilityClass;

/**
 * Encodes given shard, realm, num into 8 bytes long.
 * <p/>
 * Only 63 bits (excluding signed bit) are used for encoding to make it easy to encode/decode using mathematical
 * operations too. That's because javascript's (REST server) support for bitwise operations is very limited (truncates
 * numbers to 32 bits internally before bitwise operation).
 * <p/>
 * Format: <br/> First bit (sign bit) is left 0. <br/> Next 15 bits are for shard, followed by 16 bits for realm, and
 * then 32 bits for entity num. <br/> This encoding will support following ranges: <br/> shard: 0 - 32767 <br/> realm: 0
 * - 65535 <br/> num: 0 - 4294967295 <br/> Placing entity num in the end has the advantage that encoded ids <=
 * 4294967295 will also be human readable.
 */
@UtilityClass
public final class EntityId {

    static final int SHARD_BITS = 15;
    static final int REALM_BITS = 16;
    static final int NUM_BITS = 32; // bits for entity num
    private static final long SHARD_MASK = (1L << SHARD_BITS) - 1;
    private static final long REALM_MASK = (1L << REALM_BITS) - 1;
    private static final long NUM_MASK = (1L << NUM_BITS) - 1;

    public static Long encode(TopicID topicID) {
        if (topicID == null) {
            return null;
        }

        if (topicID.getShardNum() > SHARD_MASK || topicID.getShardNum() < 0 ||
                topicID.getRealmNum() > REALM_MASK || topicID.getRealmNum() < 0 ||
                topicID.getTopicNum() > NUM_MASK || topicID.getTopicNum() < 0) {
            return null;
        }

        return (topicID.getTopicNum() & NUM_MASK) |
                (topicID.getRealmNum() & REALM_MASK) << NUM_BITS |
                (topicID.getShardNum() & SHARD_MASK) << (REALM_BITS + NUM_BITS);
    }
}
