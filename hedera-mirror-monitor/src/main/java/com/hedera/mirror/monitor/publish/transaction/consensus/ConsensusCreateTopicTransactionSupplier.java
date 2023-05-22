/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.monitor.publish.transaction.consensus;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.TopicCreateTransaction;
import com.hedera.mirror.monitor.publish.transaction.AdminKeyable;
import com.hedera.mirror.monitor.publish.transaction.TransactionSupplier;
import com.hedera.mirror.monitor.util.Utility;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class ConsensusCreateTopicTransactionSupplier
        implements TransactionSupplier<TopicCreateTransaction>, AdminKeyable {

    private String adminKey;

    private String autoRenewAccountId;

    @Min(1)
    private long maxTransactionFee = 1_000_000_000;

    @Override
    public TopicCreateTransaction get() {
        TopicCreateTransaction topicCreateTransaction = new TopicCreateTransaction()
                .setMaxTransactionFee(Hbar.fromTinybars(maxTransactionFee))
                .setTopicMemo(Utility.getMemo("Mirror node created test topic"));

        if (adminKey != null) {
            PublicKey key = PublicKey.fromString(adminKey);
            topicCreateTransaction.setAdminKey(key).setSubmitKey(key);
        }
        if (autoRenewAccountId != null) {
            topicCreateTransaction.setAutoRenewAccountId(AccountId.fromString(autoRenewAccountId));
        }
        return topicCreateTransaction;
    }
}
