/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.web3.viewmodel.BlockType;
import java.util.Optional;

public interface RecordFileService {

    /**
     * @param block the {@link BlockType} with the block number
     * @return the record file associated with the given block
     */
    Optional<RecordFile> findByBlockType(BlockType block);

    /**
     * @param timestamp the consensus timestamp of a transaction
     * @return the record file containing the transaction with the given timestamp
     */
    Optional<RecordFile> findByTimestamp(Long timestamp);
}
