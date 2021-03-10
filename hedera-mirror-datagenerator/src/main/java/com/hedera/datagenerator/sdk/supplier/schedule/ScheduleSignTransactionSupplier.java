package com.hedera.datagenerator.sdk.supplier.schedule;

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

import java.util.List;
import java.util.Map;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.Getter;

import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.datagenerator.sdk.supplier.TransactionType;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.ScheduleId;
import com.hedera.hashgraph.sdk.ScheduleSignTransaction;

@Data
public class ScheduleSignTransactionSupplier implements TransactionSupplier<ScheduleSignTransaction> {
    @NotBlank
    private String scheduleId;

    @Getter(lazy = true)
    private final ScheduleId scheduleEntityId = ScheduleId.fromString(scheduleId);

    @NotEmpty
    private List<String> signingKeys;

    private TransactionType scheduledTransactionType;

    @Getter(lazy = true)
    private final Map<PublicKey, byte[]> signaturesMap = getSignaturesMap();

    @Min(1)
    private long maxTransactionFee = 1_000_000_000;

    @Override
    public ScheduleSignTransaction get() {
        ScheduleSignTransaction scheduleSignTransaction = new ScheduleSignTransaction()
                .setMaxTransactionFee(Hbar.fromTinybars(maxTransactionFee))
                .setScheduleId(getScheduleEntityId());

        // retrieve signature map and add to ScheduleSign
        getSignaturesMap().forEach(scheduleSignTransaction::addScheduleSignature);

        return scheduleSignTransaction;
    }
}
