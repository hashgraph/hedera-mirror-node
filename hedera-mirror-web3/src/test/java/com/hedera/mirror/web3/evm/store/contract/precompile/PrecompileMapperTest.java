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

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrecompileMapperTest {

    @InjectMocks
    private PrecompileMapper subject;

    @BeforeEach
    void setup() {
        subject.setSupportedPrecompiles(Set.of(new MockPrecompile()));
    }

    @Test
    void nonExistingAbiReturnsEmpty() {
        int functionSelector = 0x11111111;
        final var result = subject.lookup(functionSelector);
        assertThat(result).isEmpty();
    }

    @Test
    void supportedPrecompileIsFound() {
        int functionSelector = 0x00000000;
        final var result = subject.lookup(functionSelector);
        assertThat(result).isNotEmpty();
    }

    @Test
    void unsupportedPrecompileThrowsException() {
        int functionSelector = 0x189a554c;

        assertThatThrownBy(() -> subject.lookup(functionSelector)).isInstanceOf(UnsupportedOperationException.class);
    }
}
