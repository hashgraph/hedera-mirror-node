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

package com.hedera.mirror.web3.evm.store.contract.precompile;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.hedera.mirror.web3.config.IntegrationTestConfiguration;
import com.hedera.services.store.contracts.precompile.PrecompileMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(IntegrationTestConfiguration.class)
class PrecompileMapperTest {

    @Autowired
    private PrecompileMapper precompileMapper;

    @Test
    void nonExistingAbiReturnsEmpty() {
        int functionSelector = 0x11111111;
        final var result = precompileMapper.lookup(functionSelector);
        assertThat(result).isEmpty();
    }

    @Test
    void supportedPrecompileIsFound() {
        int functionSelector = 0x00000000;
        final var result = precompileMapper.lookup(functionSelector);
        assertThat(result).isNotEmpty();
    }

    @Test
    void unsupportedPrecompileThrowsException() {
        int functionSelector = 0x2cccc36f;

        assertThatThrownBy(() -> precompileMapper.lookup(functionSelector))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
