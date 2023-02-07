package com.hedera.mirror.monitor.publish.transaction.consensus;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import java.time.Duration;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.TopicId;
import com.hedera.hashgraph.sdk.TopicUpdateTransaction;
import com.hedera.mirror.monitor.publish.transaction.AdminKeyable;
import com.hedera.mirror.monitor.publish.transaction.TransactionSupplier;
import com.hedera.mirror.monitor.util.Utility;

@Data
public class ConsensusUpdateTopicTransactionSupplier implements TransactionSupplier<TopicUpdateTransaction>,
        AdminKeyable {

    private String adminKey;

    private String autoRenewAccountId;

    @NotNull
    @DurationMin(seconds = 1)
    private Duration autoRenewPeriod = Duration.ofSeconds(8000000);

    @Min(1)
    private long maxTransactionFee = 1_000_000_000;

    @NotBlank
    private String topicId;

    @Override
    public TopicUpdateTransaction get() {
        TopicUpdateTransaction topicUpdateTransaction = new TopicUpdateTransaction()
                .setMaxTransactionFee(Hbar.fromTinybars(maxTransactionFee))
                .setTopicId(TopicId.fromString(topicId))
                .setTopicMemo(Utility.getMemo("Mirror node updated test topic"));

        if (adminKey != null) {
            PublicKey key = PublicKey.fromString(adminKey);
            topicUpdateTransaction
                    .setAdminKey(key)
                    .setSubmitKey(key);
        }
        if (autoRenewAccountId != null) {
            topicUpdateTransaction
                    .setAutoRenewAccountId(AccountId.fromString(autoRenewAccountId))
                    .setAutoRenewPeriod(autoRenewPeriod);
        }
        return topicUpdateTransaction;
    }
}
