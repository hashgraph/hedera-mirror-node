package com.hedera.datagenerator.sdk.supplier.consensus;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import lombok.Builder;
import lombok.Value;

import com.hedera.datagenerator.common.Utility;
import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicCreateTransaction;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PublicKey;

@Builder
@Value
public class ConsensusCreateTopicTransactionSupplier implements TransactionSupplier<ConsensusTopicCreateTransaction> {

    //Optional
    private final String adminKey;
    private final String autoRenewAccountId;

    @Builder.Default
    private final long maxTransactionFee = 1_000_000_000;

    @Override
    public ConsensusTopicCreateTransaction get() {
        ConsensusTopicCreateTransaction consensusTopicCreateTransaction = new ConsensusTopicCreateTransaction()
                .setMaxTransactionFee(maxTransactionFee)
                .setTopicMemo(Utility.getMemo("Mirror node created test topic"))
                .setTransactionMemo(Utility.getMemo("Mirror node created test topic"));

        if (adminKey != null) {
            Ed25519PublicKey key = Ed25519PublicKey.fromString(adminKey);
            consensusTopicCreateTransaction
                    .setAdminKey(key)
                    .setSubmitKey(key);
        }
        if (autoRenewAccountId != null) {
            consensusTopicCreateTransaction
                    .setAutoRenewAccountId(AccountId.fromString(autoRenewAccountId));
        }
        return consensusTopicCreateTransaction;
    }
}
