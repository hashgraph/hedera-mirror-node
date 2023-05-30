/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.test.e2e.acceptance.client;

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
import jakarta.inject.Named;
import java.util.List;
import org.springframework.retry.support.RetryTemplate;

@Named
public class ScheduleClient extends AbstractNetworkClient {

    public ScheduleClient(SDKClient sdkClient, RetryTemplate retryTemplate) {
        super(sdkClient, retryTemplate);
    }

    public NetworkTransactionResponse createSchedule(
            ExpandedAccountId payerAccountId, Transaction<?> transaction, KeyList signatureKeyList) {
        var memo = getMemo("Create schedule");
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
                scheduleCreateTransaction.addSignature(pk.getPublicKey(), signature);
            });
        }

        var response = executeTransactionAndRetrieveReceipt(scheduleCreateTransaction);
        var scheduleId = response.getReceipt().scheduleId;
        log.info("Created new schedule {} with memo '{}' via {}", scheduleId, memo, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse signSchedule(ExpandedAccountId expandedAccountId, ScheduleId scheduleId) {
        ScheduleSignTransaction scheduleSignTransaction =
                new ScheduleSignTransaction().setScheduleId(scheduleId).setTransactionMemo(getMemo("Sign schedule"));

        var keyList = KeyList.of(expandedAccountId.getPrivateKey());
        var response = executeTransactionAndRetrieveReceipt(scheduleSignTransaction, keyList);
        log.info("Signed schedule {} via {}", scheduleId, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse deleteSchedule(ScheduleId scheduleId) {
        ScheduleDeleteTransaction scheduleDeleteTransaction = new ScheduleDeleteTransaction()
                .setScheduleId(scheduleId)
                .setTransactionMemo(getMemo("Delete schedule"));

        var response = executeTransactionAndRetrieveReceipt(scheduleDeleteTransaction);
        log.info("Deleted schedule {} via {}", scheduleId, response.getTransactionId());
        return response;
    }

    public ScheduleInfo getScheduleInfo(ScheduleId scheduleId) {
        return executeQuery(() -> new ScheduleInfoQuery().setScheduleId(scheduleId));
    }
}
