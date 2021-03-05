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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.Getter;

import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.datagenerator.sdk.supplier.TransactionType;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.ScheduleId;
import com.hedera.hashgraph.sdk.ScheduleSignTransaction;
import com.hedera.hashgraph.sdk.Transaction;

@Data
public class ScheduleSignTransactionSupplier implements TransactionSupplier<ScheduleSignTransaction> {
    @NotBlank
    private String scheduleId;

    @Getter(lazy = true)
    private final ScheduleId scheduleEntityId = ScheduleId.fromString(scheduleId);

    @NotEmpty
    private List<String> signatoryKeys;

    private TransactionType scheduledTransactionType;

    private Map<String, String> scheduledTransactionProperties = new LinkedHashMap<>();

    @Getter(lazy = true)
    private final Transaction scheduledTransaction = getScheduledTransaction();

    @Getter(lazy = true)
    private final Map<PublicKey, byte[]> signaturesMap = getSignaturesMap();

    @Min(1)
    private long maxTransactionFee = 1_000_000_000;

    @Override
    public ScheduleSignTransaction get() {
        ScheduleSignTransaction scheduleSignTransaction = new ScheduleSignTransaction()
                .setMaxTransactionFee(Hbar.fromTinybars(maxTransactionFee))
                .setScheduleId(scheduleEntityId);

        // retrieve signature map and add to ScheduleSign
        getSignaturesMap().forEach(scheduleSignTransaction::addScheduleSignature);

        return scheduleSignTransaction;
    }

    private Transaction getScheduledTransaction() {
        // retrieve appropriate supplier for inner transaction
        return new ObjectMapper()
                .convertValue(scheduledTransactionProperties, scheduledTransactionType.getSupplier())
                .get();
    }

    private Map<PublicKey, byte[]> getSignaturesMap() {
        Map<PublicKey, byte[]> signatures = new ConcurrentHashMap<>();
        signatoryKeys.forEach(k -> {
            PrivateKey key = PrivateKey.fromString(k);
            signatures.putIfAbsent(
                    key.getPublicKey(),
                    key.signTransaction(scheduledTransaction));
        });

        return signatures;
    }
}
