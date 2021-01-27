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
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import com.hedera.hashgraph.sdk.HederaNetworkException;
import com.hedera.hashgraph.sdk.HederaPrecheckStatusException;
import com.hedera.hashgraph.sdk.Status;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.mirror.grpc.jmeter.handler.SDKClientHandler;
import com.hedera.mirror.grpc.jmeter.props.hts.RESTGetByIdsRequest;
import com.hedera.mirror.grpc.jmeter.props.hts.TokenTransferPublishAndRetrieveRequest;
import com.hedera.mirror.grpc.jmeter.props.hts.TokenTransferPublishRequest;
import com.hedera.mirror.grpc.jmeter.sampler.PublishSampler;
import com.hedera.mirror.grpc.jmeter.sampler.result.hts.TokenTransferPublishAndRetrieveResult;
import com.hedera.mirror.grpc.util.Utility;

@Log4j2
public class TokenTransfersPublishAndRetrieveSampler extends PublishSampler {
    private final TokenTransferPublishRequest tokenTransferPublishRequest;
    private final RESTGetByIdsRequest restGetByIdsRequest;
    private final SDKClientHandler sdkClient;
    private Stopwatch publishStopwatch;
    private final WebClient webClient;
    private static final String REST_PATH = "/api/v1/transactions/{id}";

    public TokenTransfersPublishAndRetrieveSampler(TokenTransferPublishAndRetrieveRequest request,
                                                   SDKClientHandler sdkClient) {
        this.tokenTransferPublishRequest = request.getTokenTransferPublishRequest();
        this.restGetByIdsRequest = request.getRestGetByIdsRequest();
        this.sdkClient = sdkClient;
        this.webClient = WebClient.create(request.getRestGetByIdsRequest().getRestBaseUrl());
    }

    @SneakyThrows
    public long submitTokenTransferTransactions() {
        TokenTransferPublishAndRetrieveResult result = new TokenTransferPublishAndRetrieveResult(sdkClient.getNodeInfo()
                .getNodeId());
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
                //Convert the transaction id to be REST compliant, and retrieve the transaction
                String retrievedTransaction = getTransaction(Utility
                        .getRESTCompliantTransactionIdString(transactionId));
                Instant received = Instant.now();
                //TODO Having trouble wrangling the result object into a POJO, this is a workaround.
                JSONObject obj = new JSONObject(retrievedTransaction).getJSONArray("transactions")
                        .getJSONObject(0);
                //Submit the needed metrics to the result.
                result.onNext(obj.getString("consensus_timestamp"),
                        obj.getString("valid_start_timestamp"), received);
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
        printPublishStats("Token Transfer publish node " + sdkClient.getNodeInfo().getNodeId());
        result.onComplete();
        return result.getTransactionCount();
    }

    private String getTransaction(String transactionId) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path(REST_PATH).build(transactionId))
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(Retry
                        .fixedDelay(restGetByIdsRequest.getRestRetryMax(), Duration
                                .ofMillis(restGetByIdsRequest.getRestRetryBackoffMs())))
                .block();
    }
}

