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

import com.hedera.services.store.contracts.precompile.Precompile;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PrecompileFactoryTest {

    private PrecompileFactory subject;

    @BeforeEach
    void setUp() {
        subject = new PrecompileFactory(Set.of(new MockPrecompile()));
    }

    @Test
    void nonExistingAbiReturnsNull() {
        int functionSelector = 0x11111111;
        final Precompile result = subject.lookup(functionSelector);
        assertThat(result).isNull();
    }
}
