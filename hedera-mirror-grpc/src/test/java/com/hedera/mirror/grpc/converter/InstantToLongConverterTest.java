package com.hedera.mirror.grpc.converter;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class InstantToLongConverterTest {

    private final InstantToLongConverter converter = new InstantToLongConverter();

    @DisplayName("Convert Instant to Long")
    @ParameterizedTest(name = "with input {0} and output {1}")
    @CsvSource({
            ",",
            "2019-07-29T20:48:44.226490001Z, 1564433324226490001",
            "2019-07-29T20:48:44Z, 1564433324000000000",
            "1970-01-01T00:00:00.000000001Z, 1",
            "2262-04-11T23:47:16.854775807Z, 9223372036854775807",
            "3000-04-11T23:47:16.854775807Z, 9223372036854775807",
            "1677-09-21T00:12:43.545224193Z, 0"
    })
    void convert(Instant input, Long expected) {
        Long result = converter.convert(input);
        assertEquals(expected, result);
    }

    @Test
    void convertNull() {
        Long result = converter.convert(null);
        assertNull(result);
    }

    @Test
    void convertLongMin() {
        Long result = converter.convert(Instant.MIN);
        assertEquals(0, result);
    }

    @Test
    void convertLongMax() {
        Long result = converter.convert(Instant.MAX);
        assertEquals(Long.MAX_VALUE, result);
    }
}
