/*
 * Copyright (C) 2019-2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.evm.properties;

import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.exception.MissingResultException;
import com.hedera.mirror.web3.repository.RecordFileRepository;
import com.hedera.node.app.service.evm.contracts.execution.BlockMetaSource;
import com.hedera.node.app.service.evm.contracts.execution.HederaBlockValues;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.frame.BlockValues;

@Named
@RequiredArgsConstructor
public class StaticBlockMetaSource implements BlockMetaSource {
    private final RecordFileRepository recordFileRepository;

    @Override
    public Hash getBlockHash(long blockNo) {
        final var recordFile = recordFileRepository.findByIndex(blockNo);
        return recordFile
                .map(rf -> ethHashFrom(rf.getHash()))
                .orElseThrow(() -> new MissingResultException(String.format("No record file with index: %d", blockNo)));
    }

    @Override
    public BlockValues computeBlockValues(long gasLimit) {
        var recordFile = ContractCallContext.get().getRecordFile();
        if (Objects.isNull(recordFile)) {
            recordFile = recordFileRepository
                    .findLatest()
                    .orElseThrow(() -> new MissingResultException("No record file available."));
        }
        return new HederaBlockValues(
                gasLimit, recordFile.getIndex(), Instant.ofEpochSecond(0, recordFile.getConsensusStart()));
    }

    public static Hash ethHashFrom(final String hash) {
        return Hash.fromHexString(StringUtils.substring(hash, 0, 64));
    }
}
