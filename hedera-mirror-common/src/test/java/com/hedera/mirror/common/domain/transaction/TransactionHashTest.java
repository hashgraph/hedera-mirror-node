package com.hedera.mirror.common.domain.transaction;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionHashTest {


    @Test
    void testShardCalculation() {
        byte [] bytes = new byte[3];
        bytes[0] = (byte)0x81;
        bytes[1] = 0x22;
        bytes[2] = 0x25;

        TransactionHash.TransactionHashBuilder builder = TransactionHash.builder()
                .consensusTimestamp(1)
                .payerAccountId(5)
                .hash(bytes);

        assertThat(builder.build().calculateV1Shard()).isEqualTo(1);

        bytes[0] = 0x25;
        assertThat(builder.build().calculateV1Shard()).isEqualTo(5);
    }

    @Test
    void testHashIsValid() {
        var nullHash = TransactionHash.builder().build();
        assertThat(nullHash.hashIsValid()).isFalse();

        var emptyHash = TransactionHash.builder().hash(new byte[0]).build();
        assertThat(emptyHash.hashIsValid()).isFalse();

        byte [] bytes = new byte[3];
        bytes[0] = (byte)0x81;
        bytes[1] = 0x22;
        bytes[2] = 0x25;
        var nonEmptyHash = TransactionHash.builder().hash(bytes).build();
        assertThat(nonEmptyHash.hashIsValid()).isTrue();
    }
}
