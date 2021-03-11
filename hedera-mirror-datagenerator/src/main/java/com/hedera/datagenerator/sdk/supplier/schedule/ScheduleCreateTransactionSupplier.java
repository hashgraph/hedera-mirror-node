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
import java.util.Collections;
import java.util.List;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import com.hedera.datagenerator.common.Utility;
import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.hashgraph.sdk.AccountCreateTransaction;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.Key;
import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.ScheduleCreateTransaction;
import com.hedera.hashgraph.sdk.TransactionId;

@Data
@Log4j2
public class ScheduleCreateTransactionSupplier implements TransactionSupplier<ScheduleCreateTransaction> {

    private String adminKey;

    @Getter(lazy = true)
    private final Key adminPublicKey = PublicKey.fromString(adminKey);

    @Min(1)
    private long maxTransactionFee = 1_000_000_000;

    private String payerAccount;

    @Getter(lazy = true)
    private final AccountId payerAccountId = AccountId.fromString(payerAccount);

    private boolean logKeys = false;

    private Integer signatoryCount;

    @Getter(lazy = true)
    private final List<PrivateKey> privateKeyList = getSigningKeys();

    @Getter(lazy = true)
    private final List<PrivateKey> fullSignatoryList = createSignatoryKeys();

    @Min(1)
    private final Integer totalSignatoryCount = 1;

    @NotBlank
    private String operatorAccountId;

    @NotBlank
    private String nodeAccountId;

    @Getter(lazy = true)
    private final AccountId operatorId = AccountId.fromString(operatorAccountId);

    @Getter(lazy = true)
    private final AccountId nodeId = AccountId.fromString(nodeAccountId);

    @Min(1)
    private long initialBalance = 10_000_000;

    private boolean receiverSignatureRequired = false;

    @Override
    public ScheduleCreateTransaction get() {
        Hbar maxTransactionFeeInHbar = Hbar.fromTinybars(getMaxTransactionFee());
        String accountMemo = Utility.getMemo("Mirror node created test account");
        AccountCreateTransaction scheduledTransaction = new AccountCreateTransaction()
                .setInitialBalance(Hbar.fromTinybars(getInitialBalance()))
                .setKey(getPublicKeys())
                .setAccountMemo(accountMemo)
                .setMaxTransactionFee(maxTransactionFeeInHbar)
                .setReceiverSignatureRequired(true)
                .setTransactionMemo(accountMemo);

        // set nodeAccountId and freeze inner transaction
        TransactionId transactionId = TransactionId.generate(getOperatorId());
        scheduledTransaction.setNodeAccountIds(Collections.singletonList(getNodeId()));
        scheduledTransaction.setTransactionId(transactionId.setScheduled(true));
        scheduledTransaction.freeze();

        String scheduleMemo = Utility.getMemo("Mirror node created test schedule");
        ScheduleCreateTransaction scheduleCreateTransaction = scheduledTransaction
                .schedule()
                .setMaxTransactionFee(maxTransactionFeeInHbar)
                .setScheduleMemo(scheduleMemo)
                .setTransactionId(transactionId)
                .setTransactionMemo(scheduleMemo);

        if (adminKey != null) {
            scheduleCreateTransaction.setAdminKey(getAdminPublicKey());
        }

        if (payerAccount != null) {
            scheduleCreateTransaction.setPayerAccountId(getPayerAccountId());
        }

        // add initial set of required signatures to ScheduleCreate transaction
        if (totalSignatoryCount > 0) {
            getPrivateKeyList().forEach(k -> scheduleCreateTransaction.addScheduleSignature(
                    k.getPublicKey(),
                    k.signTransaction(scheduledTransaction)));
        }

        return scheduleCreateTransaction;
    }

    /**
     * Get public KeyList for all signatories
     *
     * @return
     */
    protected KeyList getPublicKeys() {
        KeyList keys = new KeyList();
        getFullSignatoryList().forEach(key -> keys.add(key.getPublicKey()));

        return keys;
    }

    /**
     * Get number of signatories for initial ScheduleCreate
     *
     * @return
     */
    private int getNumberOfSignatories() {
        return signatoryCount == null ? totalSignatoryCount : signatoryCount;
    }

    /**
     * Get number of signatories for ScheduleCreate
     *
     * @return
     */
    private List<PrivateKey> getSigningKeys() {
        return getFullSignatoryList().subList(0, getNumberOfSignatories());
    }

    /**
     * Create full list of signatory keys
     *
     * @return
     */
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
}
