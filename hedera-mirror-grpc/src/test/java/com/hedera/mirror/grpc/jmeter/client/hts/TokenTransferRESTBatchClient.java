package com.hedera.mirror.grpc.jmeter.client.hts;

import static com.hedera.mirror.grpc.jmeter.client.hts.TokenTransferPublishClient.TRANSACTION_IDS_PROPERTY;

import java.util.ArrayList;
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

import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.mirror.grpc.jmeter.handler.PropertiesHandler;
import com.hedera.mirror.grpc.jmeter.props.hts.TokenTransferGetRequest;
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

        // read in nodes list, topic id, number of messages, message size
        restBaseUrl = propHandler.getTestParam("restBaseUrl", "localhost:5551");
        expectedTransactionCount = propHandler.getIntTestParam("expectedTransactionCount", 0);
        restMaxRetry = propHandler.getIntTestParam("restMaxRetry", 1000);
        restRetryBackoffMs = propHandler.getIntTestParam("restRetryBackoffMs", 50);
        batchRestTimeoutSeconds = propHandler.getIntTestParam("batchRestTimeoutSeconds", 10);

        // node info expected in comma separated list of <node_IP>:<node_accountId>:<node_port>
        List<TransactionId> transactionIds = (List<TransactionId>) javaSamplerContext.getJMeterVariables()
                .getObject(TRANSACTION_IDS_PROPERTY);
        formattedTransactionIds = new ArrayList<>();
        for (TransactionId transactionId : transactionIds) {
            //TODO There has to be a better way to do this
            String transactionIdString = transactionId.toString();
            int indexOfBadPeriod = transactionIdString.lastIndexOf(".");
            formattedTransactionIds.add(new StringBuilder().append(transactionIdString.replaceFirst("@", "-")
                    .substring(0, indexOfBadPeriod)).append("-")
                    .append(transactionIdString.substring(indexOfBadPeriod + 1)).toString()
            );
        }
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
            TokenTransferGetRequest restEntityRequest = TokenTransferGetRequest.builder()
                    .restBaseUrl(restBaseUrl)
                    .transactionIds(formattedTransactionIds)
                    .restRetryBackoffMs(restRetryBackoffMs)
                    .restRetryMax(restMaxRetry)
                    .build();

            log.info("Retrieving {} token transfer transactions", formattedTransactionIds.size());
            TokenTransferRESTBatchSampler tokenTransferRESTBatchSampler =
                    new TokenTransferRESTBatchSampler(restEntityRequest);
            AtomicInteger retrievedTransactions = new AtomicInteger(0);
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
