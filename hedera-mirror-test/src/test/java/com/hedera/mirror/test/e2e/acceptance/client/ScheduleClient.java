package com.hedera.mirror.test.e2e.acceptance.client;

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

import java.util.List;
import javax.inject.Named;
import org.springframework.retry.support.RetryTemplate;

import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.ScheduleCreateTransaction;
import com.hedera.hashgraph.sdk.ScheduleDeleteTransaction;
import com.hedera.hashgraph.sdk.ScheduleId;
import com.hedera.hashgraph.sdk.ScheduleInfo;
import com.hedera.hashgraph.sdk.ScheduleInfoQuery;
import com.hedera.hashgraph.sdk.ScheduleSignTransaction;
import com.hedera.hashgraph.sdk.Transaction;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;

@Named
public class ScheduleClient extends AbstractNetworkClient {

    public ScheduleClient(SDKClient sdkClient, RetryTemplate retryTemplate) {
        super(sdkClient, retryTemplate);
    }

    public NetworkTransactionResponse createSchedule(ExpandedAccountId payerAccountId, Transaction transaction,
                                                     KeyList signatureKeyList) {
        String memo = getMemo("Create schedule");
        ScheduleCreateTransaction scheduleCreateTransaction = new ScheduleCreateTransaction()
                .setAdminKey(payerAccountId.getPublicKey())
                .setPayerAccountId(payerAccountId.getAccountId())
                .setScheduleMemo(memo)
                .setScheduledTransaction(transaction)
                .setTransactionMemo(memo);

        if (signatureKeyList != null) {
            scheduleCreateTransaction
                    .setNodeAccountIds(List.of(sdkClient.getRandomNodeAccountId()))
                    .freezeWith(client);

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
                executeTransactionAndRetrieveReceipt(scheduleCreateTransaction);
        ScheduleId scheduleId = networkTransactionResponse.getReceipt().scheduleId;
        log.debug("Created new schedule {}", scheduleId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse signSchedule(ExpandedAccountId expandedAccountId,
                                                   ScheduleId scheduleId) {

        ScheduleSignTransaction scheduleSignTransaction = new ScheduleSignTransaction()
                .setScheduleId(scheduleId)
                .setTransactionMemo(getMemo("Sign schedule"));

        NetworkTransactionResponse networkTransactionResponse = executeTransactionAndRetrieveReceipt(
                scheduleSignTransaction,
                KeyList.of(expandedAccountId.getPrivateKey())
        );
        log.debug("Signed schedule {}", scheduleId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse deleteSchedule(ScheduleId scheduleId) {

        log.debug("Delete schedule {}", scheduleId);
        ScheduleDeleteTransaction scheduleDeleteTransaction = new ScheduleDeleteTransaction()
                .setScheduleId(scheduleId)
                .setTransactionMemo(getMemo("Delete schedule"));

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(scheduleDeleteTransaction);
        log.debug("Deleted schedule {}", scheduleId);

        return networkTransactionResponse;
    }

    public ScheduleInfo getScheduleInfo(ScheduleId scheduleId) {
        return executeQuery(() -> new ScheduleInfoQuery().setScheduleId(scheduleId));
    }
}
