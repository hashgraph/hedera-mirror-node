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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class LongToInstantConverterTest {

    private final LongToInstantConverter converter = new LongToInstantConverter();

    @DisplayName("Convert Long to Instant")
    @ParameterizedTest(name = "with input {0} and output {1}")
    @CsvSource({
            ",",
            "1564433324226490001, 2019-07-29T20:48:44.226490001Z",
            "1564433324000000000, 2019-07-29T20:48:44Z",
            "1, 1970-01-01T00:00:00.000000001Z"
    })
    void testConvert(Long input, Instant expected) {
        Instant result = converter.convert(input);
        Assertions.assertEquals(expected, result);
    }

    @Test
    void convertNull() {
        Instant result = converter.convert(null);
        assertNull(result);
    }

    @Test
    void convertLongMin() {
        Instant result = converter.convert(Long.MIN_VALUE);
        Instant.ofEpochSecond(-123, -456);
        assertEquals(Instant.parse("1677-09-21T00:12:43.145224192Z"), result);
    }

    @Test
    void convertLongMax() {
        Instant result = converter.convert(Long.MAX_VALUE);
        assertEquals(Instant.parse("2262-04-11T23:47:16.854775807Z"), result);
    }
}
