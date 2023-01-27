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

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import lombok.Data;

import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.ScheduleDeleteTransaction;
import com.hedera.hashgraph.sdk.ScheduleId;
import com.hedera.mirror.monitor.publish.transaction.TransactionSupplier;

@Data
public class ScheduleDeleteTransactionSupplier implements TransactionSupplier<ScheduleDeleteTransaction> {
    @NotBlank
    private String scheduleId;

    @Min(1)
    private long maxTransactionFee = 1_000_000_000;

    @Override
    public ScheduleDeleteTransaction get() {
        return new ScheduleDeleteTransaction()
                .setMaxTransactionFee(Hbar.fromTinybars(maxTransactionFee))
                .setScheduleId(ScheduleId.fromString(scheduleId));
    }
}
