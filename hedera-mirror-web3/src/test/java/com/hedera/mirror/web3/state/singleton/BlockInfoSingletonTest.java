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
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.repository.RecordFileRepository;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlockInfoSingletonTest {

    private final DomainBuilder domainBuilder = new DomainBuilder();
    private final MirrorNodeEvmProperties properties = new MirrorNodeEvmProperties();

    private BlockInfoSingleton blockInfoSingleton;

    @Mock
    private RecordFileRepository recordFileRepository;

    @BeforeEach
    void setup() {
        properties.setProperties(Map.of("hedera.recordStream.numOfBlockHashesInState", "2"));
        blockInfoSingleton = new BlockInfoSingleton(properties, recordFileRepository);
    }

    @ValueSource(longs = {0, 1, 2})
    @ParameterizedTest
    void get(long startIndex) {
        ContractCallContext.run(context -> {
            var recordFile1 = recordFile(startIndex);
            var recordFile2 = recordFile(startIndex + 1L);
            var recordFile3 = recordFile(startIndex + 2L);
            var recordFiles = new ArrayList<>(List.of(recordFile1, recordFile2));

            when(recordFileRepository.findByIndexRange(startIndex, startIndex + 1))
                    .thenReturn(recordFiles);
            context.setRecordFile(recordFile3);

            assertThat(blockInfoSingleton.get())
                    .isEqualTo(BlockInfo.newBuilder()
                            .blockHashes(Bytes.fromHex(
                                    recordFile1.getHash() + recordFile2.getHash() + recordFile3.getHash()))
                            .consTimeOfLastHandledTxn(convertToTimestamp(recordFile3.getConsensusEnd()))
                            .firstConsTimeOfCurrentBlock(convertToTimestamp(recordFile3.getConsensusEnd()))
                            .firstConsTimeOfLastBlock(convertToTimestamp(recordFile3.getConsensusStart()))
                            .lastBlockNumber(recordFile3.getIndex())
                            .migrationRecordsStreamed(true)
                            .build());
            return null;
        });
    }

    @Test
    void getGenesis() {
        ContractCallContext.run(context -> {
            var recordFile = recordFile(0L);
            context.setRecordFile(recordFile);

            assertThat(blockInfoSingleton.get())
                    .isEqualTo(BlockInfo.newBuilder()
                            .blockHashes(Bytes.fromHex(recordFile.getHash()))
                            .consTimeOfLastHandledTxn(convertToTimestamp(recordFile.getConsensusEnd()))
                            .firstConsTimeOfCurrentBlock(convertToTimestamp(recordFile.getConsensusEnd()))
                            .firstConsTimeOfLastBlock(convertToTimestamp(recordFile.getConsensusStart()))
                            .lastBlockNumber(recordFile.getIndex())
                            .migrationRecordsStreamed(true)
                            .build());
            return null;
        });
    }

    @Test
    void key() {
        assertThat(blockInfoSingleton.getKey()).isEqualTo("BLOCKS");
    }

    @Test
    void set() {
        ContractCallContext.run(context -> {
            var recordFile = recordFile(0L);
            context.setRecordFile(recordFile);
            blockInfoSingleton.set(BlockInfo.DEFAULT);
            assertThat(blockInfoSingleton.get()).isNotEqualTo(BlockInfo.DEFAULT);
            return null;
        });
    }

    private RecordFile recordFile(long index) {
        return domainBuilder.recordFile().customize(r -> r.index(index)).get();
    }
}
