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

package com.hedera.mirror.web3.state;

import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.exception.MissingResultException;
import com.hedera.mirror.web3.repository.RecordFileRepository;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.ReadableKVStateBase;
import edu.umd.cs.findbugs.annotations.NonNull;
import jakarta.inject.Named;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

@Named
public class BlockInfoReadableKVState extends ReadableKVStateBase<Long, BlockInfo> {

    private final RecordFileRepository recordFileRepository;

    public static final long LATEST_BLOCK_KEY = Long.MAX_VALUE;

    protected BlockInfoReadableKVState(final RecordFileRepository recordFileRepository) {
        super("BLOCKS");
        this.recordFileRepository = recordFileRepository;
    }

    @Override
    protected BlockInfo readFromDataSource(@NonNull Long key) {
        var recordFile = ContractCallContext.get().getRecordFile();
        if (Objects.isNull(recordFile) || key == LATEST_BLOCK_KEY) {
            recordFile = recordFileRepository
                    .findLatest()
                    .orElseThrow(() -> new MissingResultException("No record file available."));
        } else {
            recordFile = recordFileRepository
                    .findByIndex(key)
                    .orElseThrow(() -> new MissingResultException("No record file available for index " + key + "."));
        }
        final var timestamp = recordFile.getConsensusStart();

        return BlockInfo.newBuilder()
                .lastBlockNumber(recordFile.getIndex())
                .blockHashes(Bytes.fromHex(ethHashFrom(recordFile.getHash())))
                .firstConsTimeOfCurrentBlock(convertMillisToTimestamp(timestamp))
                .build();
    }

    @NonNull
    @Override
    protected Iterator<Long> iterateFromDataSource() {
        return Collections.emptyIterator();
    }

    @Override
    public long size() {
        return 0;
    }

    private Timestamp convertMillisToTimestamp(long millis) {
        long seconds = millis / 1000;
        int nanos = (int) ((millis % 1000) * 1_000_000);
        return Timestamp.newBuilder().seconds(seconds).nanos(nanos).build();
    }

    private static String ethHashFrom(final String hash) {
        return StringUtils.substring(hash, 0, 64);
    }
}
