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
import static org.junit.jupiter.api.Assertions.*;

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

        assertAll(
                () -> assertThat(actual.getTransactionMemo()).contains("Mirror node created test schedule"),
                () -> assertThat(actual.getScheduleMemo()).contains("Mirror node created test schedule"),
                () -> assertThat(actual.getSignatures().get(NODE_ACCOUNT_ID).size()).isEqualTo(1),
                () -> assertThat(actual.getTransactionId().getScheduled()).isEqualTo(true),
                () -> assertThat(actual.getTransactionId().toString()).contains(ACCOUNT_ID.toString()),
                () -> assertThat(actual.getNodeAccountIds().size()).isEqualTo(1),
                () -> assertThat(actual.getNodeAccountIds().get(0)).isEqualTo(NODE_ACCOUNT_ID),
                () -> assertThat(actual.getPayerAccountId()).isNull(),
                () -> assertThat(actual.getMaxTransactionFee()).isEqualTo(MAX_TRANSACTION_FEE_HBAR),
                () -> assertThat(actual.getTransactionHash()).isNotNull(),
                () -> assertThat(actual.getTransactionHashPerNode().get(NODE_ACCOUNT_ID)).isNotNull(),
                () -> assertThat(actual.getTransactionHashPerNode().size()).isEqualTo(1)
        );
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

        assertAll(
                () -> assertThat(actual.getTransactionMemo()).contains("Mirror node created test schedule"),
                () -> assertThat(actual.getScheduleMemo()).contains("Mirror node created test schedule"),
                () -> assertThat(actual.getSignatures().get(NODE_ACCOUNT_ID).size()).isEqualTo(2),
                () -> assertThat(actual.getTransactionId().getScheduled()).isEqualTo(true),
                () -> assertThat(actual.getAdminKey()).isEqualTo(adminKey),
                () -> assertThat(actual.getPayerAccountId()).isEqualTo(ACCOUNT_ID_2),
                () -> assertThat(actual.getTransactionId().toString()).contains(ACCOUNT_ID.toString()),
                () -> assertThat(actual.getNodeAccountIds().size()).isEqualTo(1),
                () -> assertThat(actual.getNodeAccountIds().get(0)).isEqualTo(NODE_ACCOUNT_ID),
                () -> assertThat(actual.getMaxTransactionFee()).isEqualTo(ONE_TINYBAR),
                () -> assertThat(actual.getTransactionHash()).isNotNull(),
                () -> assertThat(actual.getTransactionHashPerNode().get(NODE_ACCOUNT_ID)).isNotNull(),
                () -> assertThat(actual.getTransactionHashPerNode().size()).isEqualTo(1)
        );
    }
}
