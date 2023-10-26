/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.domain.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.token.Token;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class AbstractTokenTest {

    @ParameterizedTest
    @CsvSource(
            textBlock = """
            -100, , -100
            , 500, 500
            1200,-500, 700
            """)
    void shouldUpdateTotalSupplyWithNewSupply(Long totalSupply, Long newSupply, Long expected) {
        var token = new Token();
        token.setTotalSupply(totalSupply);
        token.updateTotalSupply(newSupply);
        assertThat(token.getTotalSupply()).isEqualTo(expected);
    }
}
