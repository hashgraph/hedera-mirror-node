package com.hedera.mirror.importer.util;

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

import static com.hedera.mirror.importer.domain.EntityTypeEnum.ACCOUNT;
import static com.hedera.mirror.importer.domain.EntityTypeEnum.FILE;
import static com.hedera.mirror.importer.util.EntityIdEndec.NUM_BITS;
import static com.hedera.mirror.importer.util.EntityIdEndec.REALM_BITS;
import static com.hedera.mirror.importer.util.EntityIdEndec.SHARD_BITS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.exception.InvalidEntityException;

class EntityIdEndecTest {

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
        assertThat(EntityIdEndec.encode(shard, realm, num)).isEqualTo(encodedId);
    }

    @Test
    void throwsExceptionEncoding() {
        assertThrows(InvalidEntityException.class, () -> {
            EntityIdEndec.encode(1L << SHARD_BITS, 0, 0);
        });

        assertThrows(InvalidEntityException.class, () -> {
            EntityIdEndec.encode(0, 1L << REALM_BITS, 0);
        });

        assertThrows(InvalidEntityException.class, () -> {
            EntityIdEndec.encode(0, 0, 1L << NUM_BITS);
        });

        assertThrows(InvalidEntityException.class, () -> {
            EntityIdEndec.encode(-1, 0, 0);
        });

        assertThrows(InvalidEntityException.class, () -> {
            EntityIdEndec.encode(0, -1, 0);
        });

        assertThrows(InvalidEntityException.class, () -> {
            EntityIdEndec.encode(0, 0, -1);
        });
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0, 0, 0",
            "10, 0, 0, 10",
            "4294967295, 0, 0, 4294967295",
            "2814792716779530, 10, 10, 10",
            "9223372036854775807, 32767, 65535, 4294967295", // max +ve for shard, max for realm, max for num = max
            // +ve long
            "9223090561878065152, 32767, 0, 0"
    })
    void testEntityDecoding(long encodedId, long shard, long realm, long num) {
        assertThat(EntityIdEndec.decode(encodedId, ACCOUNT)).isEqualTo(EntityId.of(shard, realm, num, ACCOUNT));
    }

    @Test
    void throwsExceptionDecoding() {
        assertThrows(InvalidEntityException.class, () -> {
            EntityIdEndec.decode(-1, FILE);
        });
    }
}
