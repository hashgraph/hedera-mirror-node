package com.hedera.mirror.grpc.jmeter.sampler.hts;

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

import com.google.common.base.Stopwatch;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;

import com.hedera.hashgraph.sdk.HederaNetworkException;
import com.hedera.hashgraph.sdk.HederaPrecheckStatusException;
import com.hedera.hashgraph.sdk.Status;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.mirror.grpc.jmeter.handler.SDKClientHandler;
import com.hedera.mirror.grpc.jmeter.props.hts.TokenTransferPublishRequest;
import com.hedera.mirror.grpc.jmeter.sampler.PublishSampler;
import com.hedera.mirror.grpc.jmeter.sampler.result.TransactionSubmissionResult;

@Log4j2
@RequiredArgsConstructor
public class TokenTransfersPublishSampler extends PublishSampler {
    private final TokenTransferPublishRequest tokenTransferPublishRequest;
    private final SDKClientHandler sdkClient;
    private final boolean verifyTransactions;
    private Stopwatch publishStopwatch;

    @SneakyThrows
    public List<TransactionId> submitTokenTransferTransactions() {
        TransactionSubmissionResult result = new TransactionSubmissionResult();
        Stopwatch totalStopwatch = Stopwatch.createStarted();
        AtomicInteger networkFailures = new AtomicInteger();
        AtomicInteger unknownFailures = new AtomicInteger();
        Map<Status, Integer> hederaResponseCodeEx = new HashMap<>();

        // publish TransactionsPerBatchCount number of transactions to the node
        log.trace("Submit transaction to {}, tokenTransferPublisher: {}", sdkClient
                .getNodeInfo(), tokenTransferPublishRequest);

        for (int i = 0; i < tokenTransferPublishRequest.getTransactionsPerBatchCount(); i++) {

            try {
                publishStopwatch = Stopwatch.createStarted();
                TransactionId transactionId = sdkClient
                        .submitTokenTransfer(tokenTransferPublishRequest.getTokenId(), tokenTransferPublishRequest
                                .getOperatorId(), tokenTransferPublishRequest
                                .getRecipientId(), tokenTransferPublishRequest.getTransferAmount());
                publishLatencyStatistics.addValue(publishStopwatch.elapsed(TimeUnit.MILLISECONDS));
                result.onNext(transactionId);
            } catch (HederaPrecheckStatusException preEx) {
                hederaResponseCodeEx.compute(preEx.status, (key, val) -> (val == null) ? 1 : val + 1);
            } catch (HederaNetworkException preEx) {
                networkFailures.incrementAndGet();
            } catch (Exception ex) {
                unknownFailures.incrementAndGet();
                log.error("Unexpected exception publishing transactions {} to {}: {}", i,
                        sdkClient.getNodeInfo().getNodeId(), ex);
            }
        }

        log.info("Submitted {} token transfers for token {} from {} to {} in {} on node {}. {} preCheckErrors, {} " +
                        "networkErrors, {} unknown errors", tokenTransferPublishRequest
                        .getTransactionsPerBatchCount(), tokenTransferPublishRequest.getTokenId(),
                tokenTransferPublishRequest
                        .getOperatorId(), tokenTransferPublishRequest.getRecipientId(), totalStopwatch,
                sdkClient.getNodeInfo().getNodeId(),
                StringUtils.join(hederaResponseCodeEx), networkFailures.get(), unknownFailures.get());
        printPublishStats("Token Transfer publish node " + sdkClient.getNodeInfo().getNodeId().toString());
        result.onComplete();

        // verify transactions
        if (verifyTransactions) {
            return sdkClient.getValidTransactions(result.getTransactionIdList());
        }

        return result.getTransactionIdList();
    }
}

