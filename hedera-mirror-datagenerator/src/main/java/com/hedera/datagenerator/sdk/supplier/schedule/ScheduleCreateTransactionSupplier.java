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

import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.Getter;

import com.hedera.datagenerator.common.Utility;
import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.datagenerator.sdk.supplier.TransactionType;
import com.hedera.hashgraph.sdk.AccountCreateTransaction;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.Key;
import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.ScheduleCreateTransaction;
import com.hedera.hashgraph.sdk.Transaction;

@Data
public class ScheduleCreateTransactionSupplier implements TransactionSupplier<ScheduleCreateTransaction> {

    private String adminKey;

    @Getter(lazy = true)
    private final Key adminPublicKey = PublicKey.fromString(adminKey);

    @Min(1)
    private long maxTransactionFee = 1_000_000_000;

    @Getter(lazy = true)
    private final String memo = Utility.getMemo("Mirror node created test schedule");

    private String payerAccount;

    @Getter(lazy = true)
    private final AccountId payerAccountId = AccountId.fromString(payerAccount);

    @NotNull
    private TransactionType scheduledTransactionType = TransactionType.ACCOUNT_CREATE;

    @Getter(lazy = true)
    private final Transaction scheduledTransaction = getTransactionToSign();

    private final Integer signatoryCount;

    @Getter(lazy = true)
    private final List<PrivateKey> privateKeyList = getSigningKeys();

    @Getter(lazy = true)
    private final List<PrivateKey> fullSignatoryList = createSignatoryKeys();

    @Min(1)
    private Integer totalSignatoryCount = 1;

    @Override
    public ScheduleCreateTransaction get() {
        ScheduleCreateTransaction scheduleCreateTransaction = new ScheduleCreateTransaction()
                .setMaxTransactionFee(Hbar.fromTinybars(maxTransactionFee))
                .setScheduleMemo(getMemo())
                .setTransactionMemo(getMemo());

        if (adminKey != null) {
            scheduleCreateTransaction.setAdminKey(getAdminPublicKey());
        }

        if (payerAccount != null) {
            scheduleCreateTransaction.setPayerAccountId(getPayerAccountId());
        }

        return scheduleCreateTransaction;
    }

    @Override
    public Transaction<?> getTransactionToSign() {
        // default to CryptoCreate as other supported transactions require prior creation and configuration of entities
        return new AccountCreateTransaction()
                .setInitialBalance(Hbar.fromTinybars(1_000_000_000L))
                .setKey(getPublicKeys())
                .setAccountMemo(getMemo())
                .setMaxTransactionFee(Hbar.fromTinybars(maxTransactionFee))
                .setReceiverSignatureRequired(true)
                .setTransactionMemo(getMemo());
    }

    @Override
    public List<PrivateKey> getSignatoryKeys() {
        return getSigningKeys();
    }

    private int getNumberOfSignatories() {
        return signatoryCount == null ? totalSignatoryCount : signatoryCount;
    }

    private List<PrivateKey> getSigningKeys() {
        return getFullSignatoryList().subList(0, getNumberOfSignatories());
    }

    private KeyList getPublicKeys() {
        KeyList keys = new KeyList();
        getFullSignatoryList().forEach(key -> {
            keys.add(key.getPublicKey());
        });

        return keys;
    }

    private List<PrivateKey> createSignatoryKeys() {
        List<PrivateKey> keys = new ArrayList<>();
        for (int i = 0; i < getTotalSignatoryCount(); i++) {
            keys.add(PrivateKey.generate());
        }

        return keys;
    }
}
