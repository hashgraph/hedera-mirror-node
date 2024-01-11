/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.evm.store.accessor;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.hedera.mirror.web3.Web3IntegrationTest;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class TokenAllowanceDatabaseAccessorTest extends Web3IntegrationTest {

    private final TokenAllowanceDatabaseAccessor tokenAllowanceDatabaseAccessor;

    @Test
    void testGet() {
        final var tokenAllowance = domainBuilder.tokenAllowance().persist();

        assertThat(tokenAllowanceDatabaseAccessor.get(tokenAllowance.getId(), Optional.empty()))
                .get()
                .isEqualTo(tokenAllowance);
    }

    @Test
    void testGetHistorical() {
        final var tokenAllowance = domainBuilder.tokenAllowance().persist();

        assertThat(tokenAllowanceDatabaseAccessor.get(
                        tokenAllowance.getId(), Optional.of(tokenAllowance.getTimestampLower())))
                .get()
                .isEqualTo(tokenAllowance);
    }
}
