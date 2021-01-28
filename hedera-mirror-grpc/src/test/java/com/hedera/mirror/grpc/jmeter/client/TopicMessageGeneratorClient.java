package com.hedera.mirror.grpc.jmeter.client;

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

import java.io.PrintWriter;
import java.io.StringWriter;
import lombok.extern.log4j.Log4j2;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;

import com.hedera.mirror.grpc.jmeter.handler.ConnectionHandler;
import com.hedera.mirror.grpc.jmeter.props.MessageGenerator;
import com.hedera.mirror.grpc.jmeter.sampler.TopicMessageGeneratorSampler;

@Log4j2
public class TopicMessageGeneratorClient extends AbstractJavaSamplerClient {

    private TopicMessageGeneratorSampler sampler;
    private ConnectionHandler connectionHandler;
    private String host;
    private int port;
    private String dbName;
    private String dbUser;
    private String dbPassword;

    /**
     * Setup test by instantiating client using user defined test properties
     */
    @Override
    public void setupTest(JavaSamplerContext context) {
        host = context.getParameter("host", "localhost");
        port = context.getIntParameter("port", 5432);
        dbName = context.getParameter("dbName", "mirror_node");
        dbUser = context.getParameter("dbUser", "mirror_node");
        dbPassword = context.getParameter("dbPassword", "mirror_node_pass");

        super.setupTest(context);
    }

    /**
     * Specifies and makes available parameters and their defaults to the jMeter GUI when editing Test Plans
     *
     * @return Sampler arguments
     */
    @Override
    public Arguments getDefaultParameters() {
        Arguments defaultParameters = new Arguments();
        defaultParameters.addArgument("host", "localhost");
        defaultParameters.addArgument("port", "5432");
        defaultParameters.addArgument("dbName", "mirror_node");
        defaultParameters.addArgument("dbUser", "mirror_node");
        defaultParameters.addArgument("dbPassword", "mirror_node_pass");
        defaultParameters.addArgument("topicID", "0");
        defaultParameters.addArgument("historicMessagesCount", "0");
        defaultParameters.addArgument("newTopicsMessageCount", "0");
        defaultParameters.addArgument("topicMessageEmitCycles", "0");
        defaultParameters.addArgument("newTopicsMessageDelay", "0");
        defaultParameters.addArgument("delSeqFrom", "-1");
        return defaultParameters;
    }

    /**
     * Runs test by calling sampler to manage topic message table. Reports success based on successful db operations
     *
     * @return Sample result
     */
    @Override
    public SampleResult runTest(JavaSamplerContext context) {
        SampleResult result = new SampleResult();

        try {
            // establish db connection in test to ensure failures are reported
            connectionHandler = new ConnectionHandler(host, port, dbName, dbUser, dbPassword);
            sampler = new TopicMessageGeneratorSampler(connectionHandler);

            MessageGenerator messageGen = MessageGenerator.builder()
                    .topicNum(context.getLongParameter("topicID", 0))
                    .historicMessagesCount(context.getIntParameter("historicMessagesCount", 0))
                    .futureMessagesCount(context.getIntParameter("newTopicsMessageCount", 0))
                    .topicMessageEmitCycles(context.getIntParameter("topicMessageEmitCycles", 0))
                    .newTopicsMessageDelay(context.getLongParameter("newTopicsMessageDelay", 0L))
                    .deleteFromSequence(context.getLongParameter("delSeqFrom", -1L))
                    .build();

            result.sampleStart();
            sampler.populateTopicMessages(messageGen);
            result.sampleEnd();
            result.setResponseMessage("Successfully performed populateTopicMessages");
            result.setResponseCodeOK();
            result.setSuccessful(true);
        } catch (Exception ex) {
            log.error("Error populating topic messages", ex);
            StringWriter stringWriter = new StringWriter();
            ex.printStackTrace(new PrintWriter(stringWriter));
            result.sampleEnd();
            result.setResponseMessage("Exception: " + ex);
            result.setResponseData(stringWriter.toString().getBytes());
            result.setDataType(SampleResult.TEXT);
            result.setResponseCode("500");
        }

        return result;
    }
}
