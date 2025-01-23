/*
 * Copyright (C) 2019-2025 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.common.converter;

import java.math.BigInteger;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class WeiBarTinyBarConverterTest {

    private static final WeiBarTinyBarConverter CONVERTER = WeiBarTinyBarConverter.INSTANCE;
    private static final Long DEFAULT_GAS = 1234567890123L;

    @Test
    void convertBytes() {
        var emptyBytes = new byte[] {};
        var bigInteger = BigInteger.valueOf(DEFAULT_GAS);
        var expectedBytes = BigInteger.valueOf(123).toByteArray();
        var expectedNegativeBytes = BigInteger.valueOf(-123).toByteArray();

        Assertions.assertThat(CONVERTER.convert(null, true)).isNull();
        Assertions.assertThat(CONVERTER.convert(null, false)).isNull();
        Assertions.assertThat(CONVERTER.convert(emptyBytes, true)).isSameAs(emptyBytes);
        Assertions.assertThat(CONVERTER.convert(emptyBytes, false)).isSameAs(emptyBytes);
        Assertions.assertThat(CONVERTER.convert(bigInteger.toByteArray(), true)).isEqualTo(expectedBytes);
        Assertions.assertThat(CONVERTER.convert(bigInteger.toByteArray(), false))
                .isEqualTo(expectedBytes);
        Assertions.assertThat(CONVERTER.convert(bigInteger.negate().toByteArray(), true))
                .isEqualTo(expectedNegativeBytes);
        Assertions.assertThat(CONVERTER.convert(bigInteger.negate().toByteArray(), false))
                .isNotEqualTo(expectedBytes)
                .isNotEqualTo(expectedNegativeBytes);
    }

    @Test
    void convertLong() {
        Assertions.assertThat(CONVERTER.convert(null)).isNull();
        Assertions.assertThat(CONVERTER.convert(DEFAULT_GAS)).isEqualTo(123L);
    }
}
