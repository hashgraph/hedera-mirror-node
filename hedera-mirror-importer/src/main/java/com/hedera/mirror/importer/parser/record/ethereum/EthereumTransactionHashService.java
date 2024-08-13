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

package com.hedera.mirror.importer.parser.record.ethereum;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import jakarta.validation.constraints.NotNull;

public interface EthereumTransactionHashService {

    /**
     * Calculates the keccak256 hash of the provided ethereum transaction data
     *
     * @param callDataId The file id with the call data, note if it's set, the content is hex encoded bytes with
     *                   optional 0x prefix
     * @param consensusTimestamp The consensus timestamp of the ethereum transaction
     * @param data The raw bytes of the ethereum transaction, the call data may be offloaded to a file
     * @return The 32-byte keccak256 hash or an empty byte array if the hash could not be calculated
     */
    byte[] getHash(EntityId callDataId, long consensusTimestamp, byte @NotNull [] data);

    /**
     * Calculates the keccak256 hash of the {@link  EthereumTransaction}. Note the gas fields in the object must be the
     * values decoded from the ethereum transaction bytes, without conversion.
     *
     * @param ethereumTransaction The ethereum transaction object to calculate hash for
     * @return The 32-byte keccak256 hash or an empty byte array if the hash could not be calculated
     */
    byte[] getHash(@NotNull EthereumTransaction ethereumTransaction);
}
