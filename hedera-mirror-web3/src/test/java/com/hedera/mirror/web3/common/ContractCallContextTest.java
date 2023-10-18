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

package com.hedera.mirror.web3.common;

import static com.hedera.mirror.web3.common.ContractCallContext.UNSET_TIMESTAMP;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContractCallContextTest {

    @Test
    void testGet() {
        ContractCallContext context = ContractCallContext.init(null);
        assertThat(ContractCallContext.get()).isEqualTo(context);
        context.close();
    }

    @Test
    void testReset() {
        ContractCallContext context = ContractCallContext.init(null);
        ContractCallContext.get().setEstimate(true);
        ContractCallContext.get().setCreate(true);

        context.reset();

        assertThat(context.isEstimate()).isFalse();
        assertThat(context.isCreate()).isFalse();
        context.close();
    }

    @Test
    void testClose() {
        ContractCallContext context = ContractCallContext.init(null);

        context.close();

        assertThat(ContractCallContext.get()).isNull();
    }

    @Test
    void testBlockTimestampIsClearedOnReset() {
        ContractCallContext context = ContractCallContext.init(null);

        long blockTimestamp = 1234567890L;
        context.setBlockTimestamp(blockTimestamp);
        assertThat(context.getBlockTimestamp()).isEqualTo(blockTimestamp);

        context.reset();
        assertThat(context.getBlockTimestamp()).isEqualTo(UNSET_TIMESTAMP);

        context.close();
    }
}
