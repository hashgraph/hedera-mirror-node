package com.hedera.mirror.common.converter;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

class WeiBarTinyBarConverterTest {
    private static final WeiBarTinyBarConverter converter = WeiBarTinyBarConverter.INSTANCE;
    private static final Long defaultGas = 1234567890123L;
    private static final byte[] defaultGasBytes = BigInteger.valueOf(defaultGas).toByteArray();

    @Test
    void byteArrayWeiBarToTinyBar() {
        Assertions.assertThat(converter.weiBarToTinyBar((byte[]) null)).isNull();
        Assertions.assertThat(converter.weiBarToTinyBar(defaultGasBytes))
                .isEqualTo(BigInteger.valueOf(123).toByteArray());
    }

    @Test
    void longWeiBarToTinyBar() {
        Assertions.assertThat(converter.weiBarToTinyBar((Long) null)).isNull();
        Assertions.assertThat(converter.weiBarToTinyBar(defaultGas))
                .isEqualTo(123L);
    }
}
