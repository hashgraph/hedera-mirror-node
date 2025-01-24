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

import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.BLOCK_INFO_STATE_KEY;

import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.state.Utils;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
public class BlockInfoSingleton implements SingletonState<BlockInfo> {

    @Override
    public String getKey() {
        return BLOCK_INFO_STATE_KEY;
    }

    @Override
    public BlockInfo get() {
        var recordFile = ContractCallContext.get().getRecordFile();
        var startTimestamp = Utils.convertToTimestamp(recordFile.getConsensusStart());
        var endTimestamp = Utils.convertToTimestamp(recordFile.getConsensusEnd());

        return BlockInfo.newBuilder()
                .blockHashes(Bytes.EMPTY)
                .consTimeOfLastHandledTxn(endTimestamp)
                .firstConsTimeOfCurrentBlock(endTimestamp)
                .firstConsTimeOfLastBlock(startTimestamp)
                .lastBlockNumber(recordFile.getIndex() - 1) // Library internally increments last by one for current
                .migrationRecordsStreamed(true)
                .build();
    }
}
