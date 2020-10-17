package com.hedera.mirror.grpc.jmeter.client;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;

import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.mirror.grpc.jmeter.handler.PropertiesHandler;
import com.hedera.mirror.grpc.jmeter.sampler.HTSRESTSampler;

@Log4j2
public class HTSRESTClient extends AbstractJavaSamplerClient {
    private PropertiesHandler propHandler;
    private HTSRESTSampler htsrestSampler;

    @Override
    public void setupTest(JavaSamplerContext javaSamplerContext) {
        propHandler = new PropertiesHandler(javaSamplerContext);

        // read in nodes list, topic id, number of messages, message size
        String restBaseUrl = propHandler.getTestParam("restBaseUrl", "localhost:5551");

        // node info expected in comma separated list of <node_IP>:<node_accountId>:<node_port>
        List<TransactionId> transactionIds = (List<TransactionId>) javaSamplerContext.getJMeterVariables()
                .getObject("");
        List<String> formattedTransactionIds = new ArrayList<>();
        for (TransactionId transactionId : transactionIds) {
            //TODO There has to be a better way to do this
            String transactionIdString = transactionId.toString();
            int indexOfBadPeriod = transactionIdString.lastIndexOf(".");
            formattedTransactionIds.add(new StringBuilder().append(transactionIdString.replaceFirst("@", "-")
                    .substring(0, indexOfBadPeriod)).append("-")
                    .append(transactionIdString.substring(indexOfBadPeriod + 1)).toString()
            );
        }

        htsrestSampler = new HTSRESTSampler(restBaseUrl, formattedTransactionIds);
    }

    @Override
    public Arguments getDefaultParameters() {
        Arguments defaultParameters = new Arguments();
        defaultParameters.addArgument("propertiesBase", "hedera.mirror.test.performance");
        return defaultParameters;
    }

    @Override
    public SampleResult runTest(JavaSamplerContext context) {
        htsrestSampler.retrieveTokenTransactions();
        return null;
    }
}
