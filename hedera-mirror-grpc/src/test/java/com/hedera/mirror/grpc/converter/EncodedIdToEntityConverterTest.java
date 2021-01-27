package com.hedera.mirror.grpc.converter;

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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.hedera.mirror.grpc.domain.Entity;
import com.hedera.mirror.grpc.domain.EntityType;

public class EncodedIdToEntityConverterTest {
    private final EncodedIdToEntityConverter converter = new EncodedIdToEntityConverter();

    @DisplayName("Convert Instant to Long")
    @ParameterizedTest(name = "with input {0} and output {1}")
    @CsvSource({
            "2814792716779530, 10, 10, 10",
            "9223372036854775807, 32767, 65535, 4294967295",
            "0, 0, 0, 0",
            "1001, 0, 0, 1001"
    })
    void convert(Long encodedInput, Long shardOutput, Long realmOutput, Long numOutput) {
        Entity result = converter.convert(encodedInput);
        Entity expected = Entity.builder()
                .id(encodedInput)
                .entityShard(shardOutput)
                .entityRealm(realmOutput)
                .entityNum(numOutput)
                .entityTypeId(EntityType.ACCOUNT)
                .build();
        assertEquals(expected, result);
    }

    @Test
    void convertNull() {
        Entity result = converter.convert(null);
        assertNull(result);
    }
}
