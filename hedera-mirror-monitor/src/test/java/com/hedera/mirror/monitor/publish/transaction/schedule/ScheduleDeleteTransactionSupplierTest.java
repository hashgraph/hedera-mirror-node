package com.hedera.mirror.monitor.publish.transaction.schedule;

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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.hedera.hashgraph.sdk.ScheduleDeleteTransaction;
import com.hedera.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;

class ScheduleDeleteTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        ScheduleDeleteTransactionSupplier scheduleDeleteTransactionSupplier = new ScheduleDeleteTransactionSupplier();
        scheduleDeleteTransactionSupplier.setScheduleId(SCHEDULE_ID.toString());
        ScheduleDeleteTransaction actual = scheduleDeleteTransactionSupplier.get();

        assertThat(actual)
                .returns(MAX_TRANSACTION_FEE_HBAR, ScheduleDeleteTransaction::getMaxTransactionFee)
                .returns(SCHEDULE_ID, ScheduleDeleteTransaction::getScheduleId);
    }

    @Test
    void createWithCustomData() {
        ScheduleDeleteTransactionSupplier scheduleDeleteTransactionSupplier = new ScheduleDeleteTransactionSupplier();
        scheduleDeleteTransactionSupplier.setMaxTransactionFee(1);
        scheduleDeleteTransactionSupplier.setScheduleId(SCHEDULE_ID.toString());
        ScheduleDeleteTransaction actual = scheduleDeleteTransactionSupplier.get();

        assertThat(actual)
                .returns(ONE_TINYBAR, ScheduleDeleteTransaction::getMaxTransactionFee)
                .returns(SCHEDULE_ID, ScheduleDeleteTransaction::getScheduleId);
    }

    @Override
    protected Class getSupplierClass() {
        return ScheduleDeleteTransactionSupplier.class;
    }
}
