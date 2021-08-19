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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import com.hedera.hashgraph.sdk.AccountCreateTransaction;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.Key;
import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.ScheduleCreateTransaction;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.mirror.monitor.publish.transaction.AdminKeyable;
import com.hedera.mirror.monitor.publish.transaction.TransactionSupplier;
import com.hedera.mirror.monitor.util.Utility;

@Data
@Log4j2
public class ScheduleCreateTransactionSupplier implements TransactionSupplier<ScheduleCreateTransaction>, AdminKeyable {

    private String adminKey;

    @Getter(lazy = true)
    private final Key adminPublicKey = PublicKey.fromString(adminKey);

    @Getter(lazy = true)
    private final List<PrivateKey> fullSignatoryList = createSignatoryKeys();

    @Min(1)
    private long initialBalance = 10_000_000;

    private boolean logKeys = false;

    @Min(1)
    private long maxTransactionFee = 1_000_000_000;

    @NotBlank
    private String nodeAccountId;

    @Getter(lazy = true)
    private final AccountId nodeId = AccountId.fromString(nodeAccountId);

    @NotBlank
    private String operatorAccountId;

    @Getter(lazy = true)
    private final AccountId operatorId = AccountId.fromString(operatorAccountId);

    private String payerAccount;

    @Getter(lazy = true)
    private final AccountId payerAccountId = createPayerAccountId();

    @Getter(lazy = true)
    private final List<PrivateKey> signingKeys = createSigningKeys();

    private boolean receiverSignatureRequired = true;

    @Min(0)
    private int signatoryCount = 1;

    @Min(0)
    private int totalSignatoryCount = 1;

    @Override
    public ScheduleCreateTransaction get() {
        Hbar maxTransactionFeeInHbar = Hbar.fromTinybars(getMaxTransactionFee());
        String accountMemo = Utility.getMemo("Mirror node created test account");
        AccountCreateTransaction innerTransaction = new AccountCreateTransaction()
                .setInitialBalance(Hbar.fromTinybars(getInitialBalance()))
                .setKey(getPublicKeys())
                .setAccountMemo(accountMemo)
                .setMaxTransactionFee(maxTransactionFeeInHbar)
                .setReceiverSignatureRequired(receiverSignatureRequired)
                .setTransactionMemo(accountMemo);

        // set nodeAccountId and freeze inner transaction
        TransactionId transactionId = TransactionId.generate(getOperatorId());
        innerTransaction.setTransactionId(transactionId.setScheduled(true));

        ScheduleCreateTransaction scheduleCreateTransaction = innerTransaction
                .schedule()
                .setMaxTransactionFee(maxTransactionFeeInHbar)
                .setScheduleMemo(Utility.getMemo("Mirror node created test schedule"))
                .setTransactionId(transactionId);

        if (adminKey != null) {
            scheduleCreateTransaction.setAdminKey(getAdminPublicKey());
        }

        if (payerAccount != null) {
            scheduleCreateTransaction.setPayerAccountId(getPayerAccountId());
        }

        // add initial set of required signatures to ScheduleCreate transaction
        if (totalSignatoryCount > 0) {
            scheduleCreateTransaction.setNodeAccountIds(Collections.singletonList(getNodeId()));
            getSigningKeys().forEach(pk -> {
                byte[] signature = pk.signTransaction(scheduleCreateTransaction);
                scheduleCreateTransaction.addSignature(
                        pk.getPublicKey(),
                        signature);
            });
            log.debug("Added {} signatures to ScheduleCreate", totalSignatoryCount);
        }

        return scheduleCreateTransaction;
    }

    private KeyList getPublicKeys() {
        KeyList keys = new KeyList();
        getFullSignatoryList().forEach(key -> keys.add(key.getPublicKey()));

        return keys;
    }

    private int getNumberOfSignatories() {
        return Math.min(signatoryCount, totalSignatoryCount);
    }

    private List<PrivateKey> createSigningKeys() {
        return getFullSignatoryList().subList(0, getNumberOfSignatories());
    }

    private List<PrivateKey> createSignatoryKeys() {
        List<PrivateKey> keys = new ArrayList<>();
        for (int i = 0; i < totalSignatoryCount; i++) {
            PrivateKey privateKey = PrivateKey.generate();
            if (logKeys) {
                log.info("privateKey {}: {}", i, privateKey);
                log.info("publicKey {}: {}", i, privateKey.getPublicKey());
            }
            keys.add(privateKey);
        }

        return keys;
    }

    private AccountId createPayerAccountId() {
        return payerAccount == null ? null : AccountId.fromString(payerAccount);
    }
}
