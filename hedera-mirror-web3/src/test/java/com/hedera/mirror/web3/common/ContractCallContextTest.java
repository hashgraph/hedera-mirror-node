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

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.web3.evm.store.StackedStateFrames;
import com.hedera.mirror.web3.evm.store.StackedStateFramesTest.BareDatabaseAccessor;
import com.hedera.mirror.web3.evm.store.accessor.DatabaseAccessor;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContractCallContextTest {

    private final StackedStateFrames stackedStateFrames;

    private final DomainBuilder domainBuilder = new DomainBuilder();

    public ContractCallContextTest() {
        stackedStateFrames = new StackedStateFrames(List.<DatabaseAccessor<Object, ?>>of(
                new BareDatabaseAccessor<Object, Character>() {}, new BareDatabaseAccessor<Object, String>() {}));
    }

    @Test
    void testGet() {
        ContractCallContext context = ContractCallContext.init();
        assertThat(ContractCallContext.get()).isEqualTo(context);
        context.close();
    }

    @Test
    void testClose() {
        ContractCallContext context = ContractCallContext.init();

        context.close();

        assertThat(ContractCallContext.get()).isNull();
    }

    @Test
    void testRecordFileIsClearedOnReset() {
        ContractCallContext context = ContractCallContext.init();
        final var recordFile = domainBuilder.recordFile().get();
        context.setRecordFile(recordFile);
        assertThat(context.getRecordFile()).isEqualTo(recordFile);

        context.reset();
        assertThat(context.getRecordFile()).isNull();

        context.close();
    }

    @Test
    void testReset() {
        ContractCallContext context = ContractCallContext.init();
        context.setRecordFile(RecordFile.builder().consensusEnd(123L).build());
        context.initializeStackFrames(stackedStateFrames);
        stackedStateFrames.push();
        context.setStack(stackedStateFrames.top());

        context.reset();
        assertThat(context.getRecordFile()).isNull();
        assertThat(context.getStack()).isEqualTo(context.getStackBase());

        context.close();
    }
}
