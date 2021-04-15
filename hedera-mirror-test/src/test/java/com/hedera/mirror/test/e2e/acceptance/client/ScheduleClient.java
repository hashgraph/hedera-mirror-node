package com.hedera.mirror.test.e2e.acceptance.client;

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

import java.util.Collections;
import java.util.concurrent.TimeoutException;
import lombok.Value;
import lombok.extern.log4j.Log4j2;

import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.ReceiptStatusException;
import com.hedera.hashgraph.sdk.ScheduleCreateTransaction;
import com.hedera.hashgraph.sdk.ScheduleDeleteTransaction;
import com.hedera.hashgraph.sdk.ScheduleId;
import com.hedera.hashgraph.sdk.ScheduleSignTransaction;
import com.hedera.hashgraph.sdk.Transaction;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;

@Log4j2
@Value
public class ScheduleClient extends AbstractNetworkClient {

    public ScheduleClient(SDKClient sdkClient) {
        super(sdkClient);
        log.debug("Creating Schedule Client");
    }

    public NetworkTransactionResponse createSchedule(ExpandedAccountId payerAccountId, Transaction transaction,
                                                     String memo, KeyList signatureKeyList) throws ReceiptStatusException,
            PrecheckStatusException, TimeoutException {

        log.debug("Create new schedule");
        TransactionId transactionId = TransactionId.generate(sdkClient.getOperatorId()).setScheduled(true);
        transaction.setTransactionId(transactionId);

        ScheduleCreateTransaction scheduleCreateTransaction = transaction.schedule()
                .setAdminKey(payerAccountId.getPublicKey())
                .setMaxTransactionFee(sdkClient.getMaxTransactionFee())
                .setPayerAccountId(payerAccountId.getAccountId())
                .setScheduleMemo(memo)
                .setTransactionId(transactionId.setScheduled(false))
                .setTransactionMemo(memo);

        if (signatureKeyList != null) {
            scheduleCreateTransaction.setNodeAccountIds(Collections.singletonList(sdkClient.getNodeId()));

            // add initial set of required signatures to ScheduleCreate transaction
            signatureKeyList.forEach(k -> {
                PrivateKey pk = (PrivateKey) k;
                byte[] signature = pk.signTransaction(scheduleCreateTransaction);
                scheduleCreateTransaction.addSignature(
                        pk.getPublicKey(),
                        signature);
            });
        }

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(scheduleCreateTransaction, null);
        ScheduleId scheduleId = networkTransactionResponse.getReceipt().scheduleId;
        log.debug("Created new schedule {}", scheduleId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse signSchedule(ExpandedAccountId expandedAccountId,
                                                   ScheduleId scheduleId) throws ReceiptStatusException,
            PrecheckStatusException, TimeoutException {

        ScheduleSignTransaction scheduleSignTransaction = new ScheduleSignTransaction()
                .setMaxTransactionFee(sdkClient.getMaxTransactionFee())
                .setScheduleId(scheduleId);

        NetworkTransactionResponse networkTransactionResponse = executeTransactionAndRetrieveReceipt(
                scheduleSignTransaction,
                KeyList.of(expandedAccountId.getPrivateKey()));
        log.debug("Signed schedule {}", scheduleId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse deleteSchedule(ScheduleId scheduleId) throws ReceiptStatusException,
            PrecheckStatusException, TimeoutException {

        log.debug("Delete schedule {}", scheduleId);
        ScheduleDeleteTransaction scheduleDeleteTransaction = new ScheduleDeleteTransaction()
                .setMaxTransactionFee(sdkClient.getMaxTransactionFee())
                .setScheduleId(scheduleId);

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(scheduleDeleteTransaction, null);
        log.debug("Deleted schedule {}", scheduleId);

        return networkTransactionResponse;
    }
}
