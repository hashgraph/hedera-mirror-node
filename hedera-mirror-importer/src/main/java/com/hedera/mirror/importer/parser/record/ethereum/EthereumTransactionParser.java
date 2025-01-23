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

package com.hedera.mirror.importer.parser.record.ethereum;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;

public interface EthereumTransactionParser {

    /**
     * Decodes raw ethereum transaction bytes into an {@link EthereumTransaction} object
     *
     * @param transactionBytes The raw ethereum transaction bytes
     * @return {@link EthereumTransaction} object
     */
    EthereumTransaction decode(byte[] transactionBytes);

    /**
     * Gets the keccak256 hash of the ethereum transaction
     *
     * @param callData The call data decoded from the ethereum transaction
     * @param callDataId The file id of the call data when it is offloaded from the ethereum transaction
     * @param consensusTimestamp The consensus timestamp of the ethereum transaction
     * @param transactionBytes The raw bytes of the ethereum transaction, note the call data might be offloaded to
     *                         callDataId
     * @return The keccak256 hash of the ethereum transaction, or an empty byte array if the hash cannot be computed
     */
    byte[] getHash(byte[] callData, EntityId callDataId, long consensusTimestamp, byte[] transactionBytes);
}
