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
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.repository.RecordFileRepository;
import com.hedera.mirror.web3.state.Utils;
import com.hedera.node.config.data.BlockRecordStreamConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import jakarta.inject.Named;
import java.nio.ByteBuffer;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;

@Named
@RequiredArgsConstructor
public class BlockInfoSingleton implements SingletonState<BlockInfo> {

    private static final int HASH_SIZE = 48;

    private final MirrorNodeEvmProperties properties;
    private final RecordFileRepository recordFileRepository;

    @Override
    public String getKey() {
        return BLOCK_INFO_STATE_KEY;
    }

    @Override
    public BlockInfo get() {
        var recordFile = ContractCallContext.get().getRecordFile();
        var blockHashes = getBlockHashes(recordFile);
        var startTimestamp = Utils.convertToTimestamp(recordFile.getConsensusStart());
        var endTimestamp = Utils.convertToTimestamp(recordFile.getConsensusEnd());

        return BlockInfo.newBuilder()
                .blockHashes(Bytes.wrap(blockHashes))
                .consTimeOfLastHandledTxn(endTimestamp)
                .firstConsTimeOfCurrentBlock(endTimestamp)
                .firstConsTimeOfLastBlock(startTimestamp)
                .lastBlockNumber(recordFile.getIndex())
                .migrationRecordsStreamed(true)
                .build();
    }

    /**
     * Loads the last 256 block hashes from the database into a single byte array. This is inefficient to do for every
     * request when only a few requests may have a BLOCKHASH operation. This method is temporary until the EVM library
     * supports custom implementations for operations that don't need to use the BlockInfo singleton.
     */
    @SneakyThrows
    private byte[] getBlockHashes(RecordFile recordFile) {
        if (recordFile.getIndex() == 0) {
            return Hex.decodeHex(recordFile.getHash());
        }

        var config = properties.getVersionedConfiguration().getConfigData(BlockRecordStreamConfig.class);
        int blockHashCount = config.numOfBlockHashesInState();
        long endIndex = recordFile.getIndex() - 1; // Optimization: Don't reload the record file from the context
        long startIndex = Math.max(0L, endIndex - blockHashCount + 1);

        var blocks = recordFileRepository.findByIndexRange(startIndex, endIndex);
        blocks.add(recordFile);
        var buffer = ByteBuffer.allocate(blocks.size() * HASH_SIZE);

        for (var block : blocks) {
            var hash = Hex.decodeHex(block.getHash());
            buffer.put(hash);
        }

        return buffer.array();
    }
}
