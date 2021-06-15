package com.hedera.mirror.importer.domain;

/*
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

import static org.assertj.core.api.Assertions.assertThat;

import com.hederahashgraph.api.proto.java.TokenType;
import org.junit.jupiter.api.Test;

public class TokenTypeEnumTest {

    @Test
    void fromId() {
        assertThat(TokenTypeEnum.fromId(TokenType.FUNGIBLE_COMMON_VALUE)).isEqualTo(TokenTypeEnum.FUNGIBLE_COMMON);
        assertThat(TokenTypeEnum.fromId(TokenType.NON_FUNGIBLE_UNIQUE_VALUE))
                .isEqualTo(TokenTypeEnum.NON_FUNGIBLE_UNIQUE);
        assertThat(TokenTypeEnum.fromId(-1))
                .isEqualTo(TokenTypeEnum.FUNGIBLE_COMMON);
    }
}
