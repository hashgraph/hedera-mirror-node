package com.hedera.mirror.web3.evm.properties;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import java.time.Instant;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.frame.BlockValues;

import com.hedera.mirror.web3.evm.exception.MissingResultException;
import com.hedera.mirror.web3.repository.RecordFileRepository;

@Singleton
@RequiredArgsConstructor
public class BlockMetaSourceProvider {

    private final RecordFileRepository recordFileRepository;

    public Hash getBlockHash(long blockNo) {
        final var recordFile = recordFileRepository.findByIndex(blockNo);
        return recordFile.map(file -> Hash.fromHexString(file.getHash())).orElseThrow(
                () -> new MissingResultException(String.format("No record file with index: %d", blockNo)));
    }

    public BlockValues computeBlockValues(long gasLimit) {
        final var latestRecordFile = recordFileRepository.findLatest()
                .orElseThrow(() -> new MissingResultException("No record file available."));
        return new SimulatedBlockMetaSource(gasLimit, latestRecordFile.getIndex(), Instant.ofEpochSecond(0,
                        latestRecordFile.getConsensusStart())
                .getEpochSecond());
    }
}
