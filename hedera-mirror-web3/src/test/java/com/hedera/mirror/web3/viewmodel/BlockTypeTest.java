package com.hedera.mirror.web3.viewmodel;

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

import static java.lang.Long.MAX_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class BlockTypeTest {
    @CsvSource({
            "," + MAX_VALUE,
            "''," + MAX_VALUE,
            "0,0",
            MAX_VALUE + "," + MAX_VALUE,
            "0x0,0",
            "0x1a,26",
            "earliest,0",
            "EARLIEST,0",
            "latest," + MAX_VALUE,
            "latest," + MAX_VALUE,
            "pending," + MAX_VALUE,
            "PENDING," + MAX_VALUE,
    })
    @ParameterizedTest
    void valid(String value, long number) {
        var blockType = BlockType.of(value);
        var valueLower = StringUtils.isNotEmpty(value) ? value.toLowerCase() : "latest";
        assertThat(blockType)
                .isNotNull()
                .returns(valueLower, BlockType::name)
                .returns(number, BlockType::number);
    }

    @ValueSource(strings = {
            MAX_VALUE + "1",
            "0xabcdefghijklmnopqrstuvwxyz",
            "abcdefghijklmnopqrstuvwxyz",
            "lastest"
    })
    @ParameterizedTest
    void invalid(String value) {
        assertThatThrownBy(() -> BlockType.of(value)).isInstanceOf(IllegalArgumentException.class);
    }
}
