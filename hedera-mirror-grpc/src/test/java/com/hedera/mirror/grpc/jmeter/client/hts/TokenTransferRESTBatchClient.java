package com.hedera.mirror.grpc.jmeter.client.hts;

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

import static com.hedera.mirror.grpc.jmeter.client.hts.TokenTransferPublishClient.TRANSACTION_IDS_PROPERTY;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.log4j.Log4j2;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;

import com.hedera.mirror.grpc.jmeter.handler.PropertiesHandler;
import com.hedera.mirror.grpc.jmeter.props.hts.RESTGetByIdsRequest;
import com.hedera.mirror.grpc.jmeter.sampler.hts.TokenTransferRESTBatchSampler;

@Log4j2
public class TokenTransferRESTBatchClient extends AbstractJavaSamplerClient {
    private PropertiesHandler propHandler;
    private List<String> formattedTransactionIds;
    private String restBaseUrl;
    private int expectedTransactionCount;
    private int restMaxRetry;
    private int restRetryBackoffMs;
    private int batchRestTimeoutSeconds;

    @Override
    public void setupTest(JavaSamplerContext javaSamplerContext) {
        propHandler = new PropertiesHandler(javaSamplerContext);

        // read in properties related to retrieving transactions via REST
        restBaseUrl = propHandler.getTestParam("restBaseUrl", "localhost:5551");
        restMaxRetry = propHandler.getIntTestParam("restMaxRetry", 1000);
        restRetryBackoffMs = propHandler.getIntTestParam("restRetryBackoffMs", 50);
        batchRestTimeoutSeconds = propHandler.getIntTestParam("batchRestTimeoutSeconds", 10);

        //The expected number of transactions to receive, determines success
        expectedTransactionCount = propHandler.getIntTestParam("expectedTransactionCount", 0);

        //The list of transactions ids to retrieve, should be REST compliant already
        formattedTransactionIds = (List<String>) javaSamplerContext.getJMeterVariables()
                .getObject(TRANSACTION_IDS_PROPERTY);
    }

    @Override
    public Arguments getDefaultParameters() {
        Arguments defaultParameters = new Arguments();
        defaultParameters.addArgument("propertiesBase", "hedera.mirror.test.performance");
        return defaultParameters;
    }

    @Override
    public SampleResult runTest(JavaSamplerContext context) {
        SampleResult result = new SampleResult();
        boolean success = false;
        ExecutorService executor = Executors
                .newSingleThreadExecutor();
        try {
            result.sampleStart();
            RESTGetByIdsRequest restEntityRequest = RESTGetByIdsRequest.builder()
                    .restBaseUrl(restBaseUrl)
                    .ids(formattedTransactionIds)
                    .restRetryBackoffMs(restRetryBackoffMs)
                    .restRetryMax(restMaxRetry)
                    .build();

            log.info("Retrieving {} token transfer transactions", formattedTransactionIds.size());
            TokenTransferRESTBatchSampler tokenTransferRESTBatchSampler =
                    new TokenTransferRESTBatchSampler(restEntityRequest);
            AtomicInteger retrievedTransactions = new AtomicInteger(0);
            //Run in the executor to control runtime
            executor.execute(() -> {
                retrievedTransactions.addAndGet(tokenTransferRESTBatchSampler.retrieveTransaction());
            });
            executor.awaitTermination(batchRestTimeoutSeconds, TimeUnit.SECONDS);
            success = retrievedTransactions.get() >= expectedTransactionCount;
            result.setResponseMessage(String.valueOf(retrievedTransactions.get()));
            result.setResponseCodeOK();
        } catch (Exception e) {
            log.error("Error retrieving token transfer transactions", e);
            result.setResponseMessage("Exception: " + e);
            result.setResponseCode("500");
        } finally {
            result.sampleEnd();
            result.setSuccessful(success);

            if (!executor.isShutdown()) {
                executor.shutdownNow();
            }
        }
        return result;
    }
}
