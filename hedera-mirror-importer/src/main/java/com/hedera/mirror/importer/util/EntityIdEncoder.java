package com.hedera.mirror.importer.util;

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

/**
 * Encodes given shard, realm, num into 8 bytes long.
 * Encoding is done using bitwise operators to use full 64bit in a simple way. Using mathematical operators would make
 * it complicated to use full 64 bits
 * Format:
 * First 16 bits are for shard, next 16 for realm, last 32 bits are for entity num.
 * Placing entity num in the end has the advantage that encoded ids less than 4294967296 would also be human readable.
 * Such an encoded id, say X, would be for the entity 0.0.X.
 */
public class EntityIdEncoder {
    static final int SHARD_BITS = 16;
    static final int REALM_BITS = 16;
    static final int NUM_BITS = 32; // bits for entity num
    private static final long SHARD_MASK = (1L << SHARD_BITS) - 1;
    private static final long REALM_MASK = (1L << REALM_BITS) - 1;
    private static final long NUM_MASK = (1L << NUM_BITS) - 1;

    // TODO: verify validity of payer, node, cud entity id
    public static long encode(long shardNum, long realmNum, long entityNum) {
        if (shardNum > SHARD_MASK || realmNum > REALM_MASK || entityNum > NUM_MASK) {
            throw new IllegalArgumentException("entity is outside range of encoding constraints: "
                    + shardNum + "." + realmNum + "." + entityNum);
        }
        return (entityNum & NUM_MASK) |
                (realmNum & REALM_MASK) << NUM_BITS |
                (shardNum & SHARD_MASK) << (REALM_BITS + NUM_BITS);
    }
}
