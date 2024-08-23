/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.service;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.mirror.web3.web3j.TestWeb3jServiceState;

public class AbstractContractCallHistoricalServiceTest extends AbstractContractCallServiceTest {
    protected static final long EVM_V_34_BLOCK = 50L;
    protected static final long EVM_V_38_BLOCK = 100L;
    protected static final long EVM_V_46_BLOCK = 150L;

    protected Entity senderHistorical;

    protected Range<Long> timestampRangeAfterEvm34Block;

    protected Range<Long> timestampRangeEvm38Block;

    protected static RecordFile recordFileBeforeEvm34;

    protected static RecordFile recordFileAfterEvm34;

    protected static RecordFile recordFileEvm38;

    protected static RecordFile recordFileEvm46;

    void historicalBlocksPersist() {
        recordFileBeforeEvm34 = domainBuilder
                .recordFile()
                .customize(f -> f.index(EVM_V_34_BLOCK - 1))
                .persist();

        recordFileAfterEvm34 = domainBuilder
                .recordFile()
                .customize(f -> f.index(EVM_V_34_BLOCK))
                .persist();

        recordFileEvm38 = domainBuilder
                .recordFile()
                .customize(f -> f.index(EVM_V_38_BLOCK))
                .persist();

        recordFileEvm46 = domainBuilder
                .recordFile()
                .customize(f -> f.index(EVM_V_46_BLOCK))
                .persist();

        timestampRangeAfterEvm34Block =
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd());

        timestampRangeEvm38Block =
                Range.closedOpen(recordFileEvm38.getConsensusStart(), recordFileEvm38.getConsensusEnd());
    }

    protected void setupTestWeb3jServiceState(TestWeb3jServiceState state) {
        switch (state) {
            case BEFORE_EVM_34_BLOCK -> {
                testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK - 1)));
                // create Historical range
                testWeb3jService.setHistoricalRange(Range.closedOpen(
                        recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd()));
            }
            case AFTER_EVM_34_BLOCK -> {
                testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK)));
                // create Historical range
                testWeb3jService.setHistoricalRange(Range.closedOpen(
                        recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
            }
            case LATEST -> {
                testWeb3jService.setBlockType(BlockType.LATEST);
                // // create Historical range
                testWeb3jService.setHistoricalRange(null);
            }
        }
    }
}
