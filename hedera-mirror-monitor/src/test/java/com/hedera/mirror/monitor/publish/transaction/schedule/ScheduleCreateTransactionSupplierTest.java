package com.hedera.mirror.monitor.publish.transaction.schedule;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.ScheduleCreateTransaction;
import com.hedera.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;

class ScheduleCreateTransactionSupplierTest extends AbstractTransactionSupplierTest {

    private static final AccountId NODE_ACCOUNT_ID = AccountId.fromString("0.0.5");

    @Test
    void createWithMinimumData() {

        ScheduleCreateTransactionSupplier scheduleCreateTransactionSupplier = new ScheduleCreateTransactionSupplier();
        scheduleCreateTransactionSupplier.setNodeAccountId(NODE_ACCOUNT_ID.toString());
        scheduleCreateTransactionSupplier.setOperatorAccountId(ACCOUNT_ID.toString());
        ScheduleCreateTransaction actual = scheduleCreateTransactionSupplier.get();

        assertThat(actual)
                .satisfies(a -> assertThat(a.getTransactionMemo()).contains("Mirror node created test schedule"))
                .satisfies(a -> assertThat(a.getScheduleMemo()).contains("Mirror node created test schedule"))
                .satisfies(a -> assertThat(a.getSignatures().get(NODE_ACCOUNT_ID).size()).isEqualTo(1))
                .satisfies(a -> assertThat(a.getTransactionId().getScheduled()).isEqualTo(true))
                .satisfies(a -> assertThat(a.getTransactionId().toString()).contains(ACCOUNT_ID.toString()))
                .satisfies(a -> assertThat(a.getNodeAccountIds().size()).isEqualTo(1))
                .satisfies(a -> assertThat(a.getNodeAccountIds().get(0)).isEqualTo(NODE_ACCOUNT_ID))
                .satisfies(a -> assertThat(a.getPayerAccountId()).isNull())
                .satisfies(a -> assertThat(a.getMaxTransactionFee()).isEqualTo(MAX_TRANSACTION_FEE_HBAR))
                .satisfies(a -> assertThat(a.getTransactionHash()).isNotNull())
                .satisfies(a -> assertThat(a.getTransactionHashPerNode().get(NODE_ACCOUNT_ID)).isNotNull())
                .satisfies(a -> assertThat(a.getTransactionHashPerNode().size()).isEqualTo(1));
    }

    @Test
    void createWithCustomData() {
        PublicKey adminKey = PrivateKey.generate().getPublicKey();

        ScheduleCreateTransactionSupplier scheduleCreateTransactionSupplier = new ScheduleCreateTransactionSupplier();
        scheduleCreateTransactionSupplier.setAdminKey(adminKey.toString());
        scheduleCreateTransactionSupplier.setInitialBalance(1);
        scheduleCreateTransactionSupplier.setMaxTransactionFee(1);
        scheduleCreateTransactionSupplier.setNodeAccountId(NODE_ACCOUNT_ID.toString());
        scheduleCreateTransactionSupplier.setOperatorAccountId(ACCOUNT_ID.toString());
        scheduleCreateTransactionSupplier.setPayerAccount(ACCOUNT_ID_2.toString());
        scheduleCreateTransactionSupplier.setReceiverSignatureRequired(false);
        scheduleCreateTransactionSupplier.setSignatoryCount(2);
        scheduleCreateTransactionSupplier.setTotalSignatoryCount(2);
        ScheduleCreateTransaction actual = scheduleCreateTransactionSupplier.get();

        assertThat(actual)
                .satisfies(a -> assertThat(a.getTransactionMemo()).contains("Mirror node created test schedule"))
                .satisfies(a -> assertThat(a.getScheduleMemo()).contains("Mirror node created test schedule"))
                .satisfies(a -> assertThat(a.getSignatures().get(NODE_ACCOUNT_ID).size()).isEqualTo(2))
                .satisfies(a -> assertThat(a.getTransactionId().getScheduled()).isEqualTo(true))
                .satisfies(a -> assertThat(a.getAdminKey()).isEqualTo(adminKey))
                .satisfies(a -> assertThat(a.getPayerAccountId()).isEqualTo(ACCOUNT_ID_2))
                .satisfies(a -> assertThat(a.getTransactionId().toString()).contains(ACCOUNT_ID.toString()))
                .satisfies(a -> assertThat(a.getNodeAccountIds().size()).isEqualTo(1))
                .satisfies(a -> assertThat(a.getNodeAccountIds().get(0)).isEqualTo(NODE_ACCOUNT_ID))
                .satisfies(a -> assertThat(a.getMaxTransactionFee()).isEqualTo(ONE_TINYBAR))
                .satisfies(a -> assertThat(a.getTransactionHash()).isNotNull())
                .satisfies(a -> assertThat(a.getTransactionHashPerNode().get(NODE_ACCOUNT_ID)).isNotNull())
                .satisfies(a -> assertThat(a.getTransactionHashPerNode().size()).isEqualTo(1));
    }
}
