/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.state.singleton;

import static com.hedera.mirror.web3.state.Utils.convertToTimestamp;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.web3.ContextExtension;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ContextExtension.class)
class BlockInfoSingletonTest {

    private final BlockInfoSingleton blockInfoSingleton = new BlockInfoSingleton();
    private final DomainBuilder domainBuilder = new DomainBuilder();
    private final RecordFile recordFile = domainBuilder.recordFile().get();

    @Test
    void get() {
        ContractCallContext.get().setRecordFile(recordFile);
        assertThat(blockInfoSingleton.get())
                .isEqualTo(BlockInfo.newBuilder()
                        .blockHashes(Bytes.EMPTY)
                        .consTimeOfLastHandledTxn(convertToTimestamp(recordFile.getConsensusEnd()))
                        .firstConsTimeOfCurrentBlock(convertToTimestamp(recordFile.getConsensusEnd()))
                        .firstConsTimeOfLastBlock(convertToTimestamp(recordFile.getConsensusStart()))
                        .lastBlockNumber(recordFile.getIndex() - 1)
                        .migrationRecordsStreamed(true)
                        .build());
    }

    @Test
    void key() {
        assertThat(blockInfoSingleton.getKey()).isEqualTo("BLOCKS");
    }

    @Test
    void set() {
        ContractCallContext.get().setRecordFile(recordFile);
        blockInfoSingleton.set(BlockInfo.DEFAULT);
        assertThat(blockInfoSingleton.get()).isNotEqualTo(BlockInfo.DEFAULT);
    }
}
