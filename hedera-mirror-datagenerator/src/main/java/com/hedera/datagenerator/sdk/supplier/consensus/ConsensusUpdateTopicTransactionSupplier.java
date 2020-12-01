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

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import com.hedera.datagenerator.common.Utility;
import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.datagenerator.sdk.supplier.TransactionSupplierException;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicId;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicUpdateTransaction;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PublicKey;

@Data
public class ConsensusUpdateTopicTransactionSupplier implements TransactionSupplier<ConsensusTopicUpdateTransaction> {

    private static final List<String> requiredFields = Arrays.asList("topicId");

    //Required
    @NotBlank
    private String topicId;

    //Optional
    private String adminKey;

    private String autoRenewAccountId;

    @NotNull
    private Duration autoRenewPeriod = Duration.ofSeconds(8000000);

    @NotNull
    private Instant expirationTime = Instant.now().plus(120, ChronoUnit.DAYS);

    @Min(1)
    private long maxTransactionFee = 1_000_000_000;

    @Override
    public ConsensusTopicUpdateTransaction get() {

        if (StringUtils.isBlank(topicId) || StringUtils.isBlank(autoRenewAccountId)) {
            throw new TransactionSupplierException(this, requiredFields);
        }

        ConsensusTopicUpdateTransaction consensusTopicUpdateTransaction = new ConsensusTopicUpdateTransaction()
                .setExpirationTime(expirationTime)
                .setTopicId(ConsensusTopicId.fromString(topicId))
                .setTopicMemo(Utility.getMemo("Mirror node created test topic"))
                .setTransactionMemo(Utility.getMemo("Mirror node updated test topic"));

        if (adminKey != null) {
            Ed25519PublicKey key = Ed25519PublicKey.fromString(adminKey);
            consensusTopicUpdateTransaction
                    .setAdminKey(key)
                    .setSubmitKey(key);
        }
        if (autoRenewAccountId != null) {
            consensusTopicUpdateTransaction
                    .setAutoRenewAccountId(AccountId.fromString(autoRenewAccountId))
                    .setAutoRenewPeriod(autoRenewPeriod);
        }
        return consensusTopicUpdateTransaction;
    }
}
