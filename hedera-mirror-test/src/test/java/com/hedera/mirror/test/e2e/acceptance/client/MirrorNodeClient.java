/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

import static org.awaitility.Awaitility.await;

import com.google.common.base.Stopwatch;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.SubscriptionHandle;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TopicMessageQuery;
import com.hedera.mirror.rest.model.AccountBalanceTransactions;
import com.hedera.mirror.rest.model.AccountInfo;
import com.hedera.mirror.rest.model.BlocksResponse;
import com.hedera.mirror.rest.model.ContractActionsResponse;
import com.hedera.mirror.rest.model.ContractCallRequest;
import com.hedera.mirror.rest.model.ContractCallResponse;
import com.hedera.mirror.rest.model.ContractResponse;
import com.hedera.mirror.rest.model.ContractResult;
import com.hedera.mirror.rest.model.ContractResultsResponse;
import com.hedera.mirror.rest.model.CryptoAllowancesResponse;
import com.hedera.mirror.rest.model.NetworkExchangeRateSetResponse;
import com.hedera.mirror.rest.model.NetworkNode;
import com.hedera.mirror.rest.model.NetworkNodesResponse;
import com.hedera.mirror.rest.model.NetworkStakeResponse;
import com.hedera.mirror.rest.model.Nft;
import com.hedera.mirror.rest.model.NftTransactionHistory;
import com.hedera.mirror.rest.model.Schedule;
import com.hedera.mirror.rest.model.TokenAllowancesResponse;
import com.hedera.mirror.rest.model.TokenBalancesResponse;
import com.hedera.mirror.rest.model.TokenInfo;
import com.hedera.mirror.rest.model.TokenRelationshipResponse;
import com.hedera.mirror.rest.model.TokensResponse;
import com.hedera.mirror.rest.model.TransactionByIdResponse;
import com.hedera.mirror.rest.model.TransactionsResponse;
import com.hedera.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;
import com.hedera.mirror.test.e2e.acceptance.config.Web3Properties;
import com.hedera.mirror.test.e2e.acceptance.props.Order;
import com.hedera.mirror.test.e2e.acceptance.util.TestUtil;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.CustomLog;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.springframework.retry.RetryContext;
import org.springframework.retry.policy.MaxAttemptsRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

@CustomLog
@Named
public class MirrorNodeClient {

    private final AcceptanceTestProperties acceptanceTestProperties;
    private final RestClient restClient;
    private final RetryTemplate retryTemplate;
    private final RestClient web3Client;

    public MirrorNodeClient(
            AcceptanceTestProperties acceptanceTestProperties,
            RestClient.Builder restClientBuilder,
            Web3Properties web3Properties) {
        this.acceptanceTestProperties = acceptanceTestProperties;
        this.restClient = restClientBuilder.build();
        this.web3Client = StringUtils.isBlank(web3Properties.getBaseUrl())
                ? restClient
                : restClientBuilder.baseUrl(web3Properties.getBaseUrl()).build();
        var properties = acceptanceTestProperties.getRestProperties();
        this.retryTemplate = RetryTemplate.builder()
                .customPolicy(new MaxAttemptsRetryPolicy(properties.getMaxAttempts()) {
                    @Override
                    public boolean canRetry(RetryContext context) {
                        return super.canRetry(context) && properties.shouldRetry(context.getLastThrowable());
                    }
                })
                .exponentialBackoff(properties.getMinBackoff(), 2.0, properties.getMaxBackoff())
                .build();

        var virtualThreadFactory = Thread.ofVirtual().name("awaitility", 1).factory();
        var executorService = Executors.newThreadPerTaskExecutor(virtualThreadFactory);
        Awaitility.pollExecutorService(executorService);
    }

    public SubscriptionResponse subscribeToTopic(SDKClient sdkClient, TopicMessageQuery topicMessageQuery)
            throws Throwable {
        log.debug("Subscribing to topic.");
        SubscriptionResponse subscriptionResponse = new SubscriptionResponse();
        SubscriptionHandle subscription = topicMessageQuery
                .setErrorHandler(subscriptionResponse::handleThrowable)
                .subscribe(sdkClient.getClient(), subscriptionResponse::handleConsensusTopicResponse);

        subscriptionResponse.setSubscription(subscription);

        // allow time for connection to be made and error to be caught
        await("responseEncountered")
                .atMost(Durations.ONE_MINUTE)
                .pollDelay(Durations.ONE_HUNDRED_MILLISECONDS)
                .until(() -> subscriptionResponse.hasResponse());

        if (subscriptionResponse.errorEncountered()) {
            throw subscriptionResponse.getResponseError();
        }

        return subscriptionResponse;
    }

    public SubscriptionResponse subscribeToTopicAndRetrieveMessages(
            SDKClient sdkClient, TopicMessageQuery topicMessageQuery, int numMessages, long latency) throws Throwable {
        latency = latency <= 0 ? acceptanceTestProperties.getMessageTimeout().toSeconds() : latency;
        log.debug("Subscribing to topic, expecting {} within {} seconds.", numMessages, latency);

        CountDownLatch messageLatch = new CountDownLatch(numMessages);
        SubscriptionResponse subscriptionResponse = new SubscriptionResponse();
        Stopwatch stopwatch = Stopwatch.createStarted();

        SubscriptionHandle subscription = topicMessageQuery
                .setErrorHandler(subscriptionResponse::handleThrowable)
                .subscribe(sdkClient.getClient(), resp -> {
                    // add expected messages only to messages list
                    if (subscriptionResponse.getMirrorHCSResponses().size() < numMessages) {
                        subscriptionResponse.handleConsensusTopicResponse(resp);
                    }
                    messageLatch.countDown();
                });

        subscriptionResponse.setSubscription(subscription);

        if (!messageLatch.await(latency, TimeUnit.SECONDS)) {
            stopwatch.stop();
            log.error(
                    "{} messages were expected within {} s. {} not yet received after {}",
                    numMessages,
                    latency,
                    messageLatch.getCount(),
                    stopwatch);
        } else {
            stopwatch.stop();
            log.info(
                    "Success, received {} out of {} messages received in {}.",
                    numMessages - messageLatch.getCount(),
                    numMessages,
                    stopwatch);
        }

        subscriptionResponse.setElapsedTime(stopwatch);

        if (subscriptionResponse.errorEncountered()) {
            throw subscriptionResponse.getResponseError();
        }

        return subscriptionResponse;
    }

    public CryptoAllowancesResponse getAccountCryptoAllowance(String accountId) {
        log.debug("Verify account '{}''s crypto allowance is returned by Mirror Node", accountId);
        return callRestEndpoint("/accounts/{accountId}/allowances/crypto", CryptoAllowancesResponse.class, accountId);
    }

    public CryptoAllowancesResponse getAccountCryptoAllowanceBySpender(String accountId, String spenderId) {
        log.debug("Verify account '{}''s crypto allowance for {} is returned by Mirror Node", accountId, spenderId);
        return callRestEndpoint(
                "/accounts/{accountId}/allowances/crypto?spender.id={spenderId}",
                CryptoAllowancesResponse.class,
                accountId,
                spenderId);
    }

    public TokenAllowancesResponse getAccountTokenAllowanceBySpender(
            String accountId, String tokenId, String spenderId) {
        log.debug(
                "Verify account '{}''s token allowance for token {} and spender {} is returned by Mirror Node",
                accountId,
                tokenId,
                spenderId);
        return callRestEndpoint(
                "/accounts/{accountId}/allowances/tokens?token.id={tokenId}&spender.id={spenderId}",
                TokenAllowancesResponse.class,
                accountId,
                tokenId,
                spenderId);
    }

    public ContractResponse getContractInfo(String contractId) {
        log.debug("Verify contract '{}' is returned by Mirror Node", contractId);
        return callRestEndpoint("/contracts/{contractId}", ContractResponse.class, contractId);
    }

    public ContractResponse getContractInfoWithNotFound(String contractId) {
        log.debug("Verify contract '{}' is not found", contractId);
        return callRestEndpointNoRetry("/contracts/{contractId}", ContractResponse.class, contractId);
    }

    public ContractResultsResponse getContractResultsById(String contractId) {
        log.debug("Verify contract results '{}' is returned by Mirror Node", contractId);
        return callRestEndpoint("/contracts/{contractId}/results", ContractResultsResponse.class, contractId);
    }

    public ContractResult getContractResultByTransactionId(String transactionId) {
        log.debug("Verify contract result '{}' is returned by Mirror Node", transactionId);
        return callRestEndpoint("/contracts/results/{transactionId}", ContractResult.class, transactionId);
    }

    public ContractActionsResponse getContractResultActionsByTransactionId(String transactionId) {
        log.debug("Verify contract result '{}' is returned by Mirror Node", transactionId);
        return callRestEndpoint("/contracts/results/{id}/actions", ContractActionsResponse.class, transactionId);
    }

    public NetworkExchangeRateSetResponse getExchangeRates() {
        log.debug("Get exchange rates by Mirror Node");
        return callRestEndpoint("/network/exchangerate", NetworkExchangeRateSetResponse.class);
    }

    public ContractCallResponse contractsCall(ContractCallRequest request) {
        return callPostRestEndpoint("/contracts/call", ContractCallResponse.class, request);
    }

    public BlocksResponse getBlocks(Order order, long limit) {
        log.debug("Get blocks data by Mirror Node");
        return callRestEndpoint("/blocks?order={order}&limit={limit}", BlocksResponse.class, order, limit);
    }

    public List<NetworkNode> getNetworkNodes() {
        List<NetworkNode> nodes = new ArrayList<>();
        String next = "/network/nodes?limit=25";

        do {
            var response = callRestEndpoint(next, NetworkNodesResponse.class);
            nodes.addAll(response.getNodes());
            next = response.getLinks() != null ? response.getLinks().getNext() : null;
        } while (next != null);

        return nodes;
    }

    public NetworkStakeResponse getNetworkStake() {
        String stakeEndpoint = "/network/stake";
        return callRestEndpoint(stakeEndpoint, NetworkStakeResponse.class);
    }

    public Nft getNftInfo(String tokenId, long serialNumber) {
        log.debug("Verify serial number '{}' for token '{}' is returned by Mirror Node", serialNumber, tokenId);
        return callRestEndpoint("/tokens/{tokenId}/nfts/{serialNumber}", Nft.class, tokenId, serialNumber);
    }

    public NftTransactionHistory getNftTransactions(TokenId tokenId, Long serialNumber) {
        log.debug(
                "Get list of transactions for token '{}' and serial number '{}' from Mirror Node",
                tokenId,
                serialNumber);
        return callRestEndpoint(
                "/tokens/{tokenId}/nfts/{serialNumber}/transactions",
                NftTransactionHistory.class,
                tokenId,
                serialNumber);
    }

    public Schedule getScheduleInfo(String scheduleId) {
        log.debug("Verify schedule '{}' is returned by Mirror Node", scheduleId);
        return callRestEndpoint("/schedules/{scheduleId}", Schedule.class, scheduleId);
    }

    public TokenBalancesResponse getTokenBalances(String tokenId) {
        log.debug("Verify token balances '{}' is returned by Mirror Node", tokenId);
        return callRestEndpoint("/tokens/{tokenId}/balances", TokenBalancesResponse.class, tokenId);
    }

    public TokenInfo getTokenInfo(String tokenId) {
        log.debug("Verify token '{}' is returned by Mirror Node", tokenId);
        return callRestEndpoint("/tokens/{tokenId}", TokenInfo.class, tokenId);
    }

    public TokensResponse getTokens(String tokenId) {
        log.debug("Verify token with query parameter '{}' is returned by Mirror Node", tokenId);
        return callRestEndpoint("/tokens/?token.id={tokenId}", TokensResponse.class, tokenId);
    }

    public TransactionsResponse getTransactionInfoByTimestamp(String timestamp) {
        log.debug("Verify transaction with consensus timestamp '{}' is returned by Mirror Node", timestamp);
        return callRestEndpoint("/transactions?timestamp={timestamp}", TransactionsResponse.class, timestamp);
    }

    public TransactionByIdResponse getTransactions(String transactionId) {
        log.debug("Verify transaction '{}' is returned by Mirror Node", transactionId);
        return callRestEndpoint("/transactions/{transactionId}", TransactionByIdResponse.class, transactionId);
    }

    public TokenRelationshipResponse getTokenRelationships(AccountId accountId, TokenId tokenId) {
        log.debug(
                "Verify tokenRelationship  for account '{}' and token '{}' is returned by Mirror Node",
                accountId,
                tokenId);
        return callRestEndpoint(
                "/accounts/{accountId}/tokens?token.id={tokenId}", TokenRelationshipResponse.class, accountId, tokenId);
    }

    public AccountBalanceTransactions getAccountDetailsUsingAlias(@NonNull AccountId accountId) {
        log.debug("Retrieving account details for accountId '{}'", accountId);
        return callRestEndpoint(
                "/accounts/{accountId}",
                AccountBalanceTransactions.class,
                TestUtil.getAliasFromPublicKey(accountId.aliasKey));
    }

    public AccountInfo getAccountDetailsUsingEvmAddress(@NonNull AccountId accountId) {
        log.debug("Retrieving account details for accountId '{}'", accountId);
        return callRestEndpoint("/accounts/{accountId}", AccountInfo.class, accountId.evmAddress);
    }

    public AccountInfo getAccountDetailsByAccountId(@NonNull AccountId accountId) {
        log.debug("Retrieving account details for accountId '{}'", accountId);
        return callRestEndpoint("/accounts/{accountId}", AccountInfo.class, accountId.toString());
    }

    public void unSubscribeFromTopic(SubscriptionHandle subscription) {
        subscription.unsubscribe();
        log.info("Unsubscribed from {}", subscription);
    }

    private <T> T callRestEndpoint(String uri, Class<T> classType, Object... uriVariables) {
        return retryTemplate.execute(
                x -> restClient.get().uri(uri, uriVariables).retrieve().body(classType));
    }

    private <T> T callRestEndpointNoRetry(String uri, Class<T> classType, Object... uriVariables) {
        return restClient.get().uri(uri, uriVariables).retrieve().body(classType);
    }

    private <T, R> T callPostRestEndpoint(String uri, Class<T> classType, R request) {
        return retryTemplate.execute(
                x -> web3Client.post().uri(uri).body(request).retrieve().body(classType));
    }
}
