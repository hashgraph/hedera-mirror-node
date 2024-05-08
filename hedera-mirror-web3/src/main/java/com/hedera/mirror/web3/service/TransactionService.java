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

import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.Optional;

public interface TransactionService {

    /**
     * @param transactionId the {@link TransactionID} of the transaction
     * @return {@link Transaction} with the {@code transactionId}
     */
    Optional<Transaction> findByTransactionId(TransactionID transactionId);

    /**
     * @param transactionHash the ethereum transaction hash
     * @return {@link EthereumTransaction} with the {@code transactionHash}
     */
    Optional<EthereumTransaction> findByEthHash(byte[] transactionHash);
}
