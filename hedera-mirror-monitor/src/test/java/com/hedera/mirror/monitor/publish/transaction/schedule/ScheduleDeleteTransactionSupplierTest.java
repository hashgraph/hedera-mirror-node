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

import com.hedera.hashgraph.sdk.ScheduleDeleteTransaction;
import com.hedera.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;

class ScheduleDeleteTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        ScheduleDeleteTransactionSupplier scheduleDeleteTransactionSupplier = new ScheduleDeleteTransactionSupplier();
        scheduleDeleteTransactionSupplier.setScheduleId(SCHEDULE_ID.toString());
        ScheduleDeleteTransaction actual = scheduleDeleteTransactionSupplier.get();

        ScheduleDeleteTransaction expected = new ScheduleDeleteTransaction()
                .setMaxTransactionFee(MAX_TRANSACTION_FEE_HBAR)
                .setScheduleId(SCHEDULE_ID)
                .setTransactionMemo(actual.getTransactionMemo());

        assertAll(
                () -> assertThat(actual.getTransactionMemo()).contains("Mirror node deleted test schedule"),
                () -> assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
        );
    }

    @Test
    void createWithCustomData() {
        ScheduleDeleteTransactionSupplier scheduleDeleteTransactionSupplier = new ScheduleDeleteTransactionSupplier();
        scheduleDeleteTransactionSupplier.setMaxTransactionFee(1);
        scheduleDeleteTransactionSupplier.setScheduleId(SCHEDULE_ID.toString());
        ScheduleDeleteTransaction actual = scheduleDeleteTransactionSupplier.get();

        ScheduleDeleteTransaction expected = new ScheduleDeleteTransaction()
                .setMaxTransactionFee(ONE_TINYBAR)
                .setScheduleId(SCHEDULE_ID)
                .setTransactionMemo(actual.getTransactionMemo());

        assertAll(
                () -> assertThat(actual.getTransactionMemo()).contains("Mirror node deleted test schedule"),
                () -> assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
        );
    }
}
