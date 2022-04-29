package com.hedera.mirror.monitor.publish.transaction.schedule;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

import org.junit.jupiter.api.Test;

import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.ScheduleCreateTransaction;
import com.hedera.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;

class ScheduleCreateTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        ScheduleCreateTransactionSupplier scheduleCreateTransactionSupplier = new ScheduleCreateTransactionSupplier();
        scheduleCreateTransactionSupplier.setOperatorAccountId(ACCOUNT_ID.toString());
        scheduleCreateTransactionSupplier.setPayerAccount(ACCOUNT_ID_2.toString());
        ScheduleCreateTransaction actual = scheduleCreateTransactionSupplier.get();

        assertThat(actual)
                .returns(null, a -> a.getAdminKey())
                .returns(MAX_TRANSACTION_FEE_HBAR, ScheduleCreateTransaction::getMaxTransactionFee)
                .returns(ACCOUNT_ID_2, ScheduleCreateTransaction::getPayerAccountId)
                .extracting(ScheduleCreateTransaction::getScheduleMemo, STRING)
                .contains("Mirror node created test schedule");
    }

    @Test
    void createWithCustomData() {
        PublicKey adminKey = PrivateKey.generate().getPublicKey();

        ScheduleCreateTransactionSupplier scheduleCreateTransactionSupplier = new ScheduleCreateTransactionSupplier();
        scheduleCreateTransactionSupplier.setAdminKey(adminKey.toString());
        scheduleCreateTransactionSupplier.setMaxTransactionFee(1);
        scheduleCreateTransactionSupplier.setOperatorAccountId(ACCOUNT_ID.toString());
        scheduleCreateTransactionSupplier.setPayerAccount(ACCOUNT_ID_2.toString());
        ScheduleCreateTransaction actual = scheduleCreateTransactionSupplier.get();

        assertThat(actual)
                .returns(adminKey, a -> a.getAdminKey())
                .returns(ONE_TINYBAR, ScheduleCreateTransaction::getMaxTransactionFee)
                .returns(ACCOUNT_ID_2, ScheduleCreateTransaction::getPayerAccountId)
                .extracting(ScheduleCreateTransaction::getScheduleMemo, STRING)
                .contains("Mirror node created test schedule");
    }

    @Override
    protected Class getSupplierClass() {
        return ScheduleCreateTransactionSupplier.class;
    }
}
