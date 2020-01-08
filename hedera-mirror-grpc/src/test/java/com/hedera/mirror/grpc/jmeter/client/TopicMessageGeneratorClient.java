package com.hedera.mirror.grpc.jmeter.client;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

import com.hedera.mirror.grpc.jmeter.ConnectionHandler;
import com.hedera.mirror.grpc.jmeter.sampler.TopicMessageGeneratorSampler;

@Log4j2
public class TopicMessageGeneratorClient extends AbstractJavaSamplerClient {

    long topicNum;
    int historicMessagesCount;
    int futureMessagesCount;
    long newTopicsMessageDelay;
    long delSeqFrom;
    TopicMessageGeneratorSampler sampler;
    ConnectionHandler connHandl;
    int threadNum;
    String host;
    int port;
    String dbName;
    String dbUser;
    String dbPassword;

    @Override
    public void setupTest(JavaSamplerContext context) {
        // db props
        host = context.getParameter("host");
        port = context.getIntParameter("port");
        dbName = context.getParameter("dbName");
        dbUser = context.getParameter("dbUser");
        dbPassword = context.getParameter("dbPassword");

        // testcase props
        topicNum = context.getLongParameter("topicID");
        historicMessagesCount = context.getIntParameter("historicMessagesCount");
        futureMessagesCount = context.getIntParameter("newTopicsMessageCount");
        newTopicsMessageDelay = context.getLongParameter("newTopicsMessageDelay");
        delSeqFrom = context.getLongParameter("delSeqFrom");

        super.setupTest(context);
    }

    @Override
    public Arguments getDefaultParameters() {
        Arguments defaultParameters = new Arguments();
        defaultParameters.addArgument("host", "localhost");
        defaultParameters.addArgument("port", "5432");
        defaultParameters.addArgument("dbName", "mirror_node");
        defaultParameters.addArgument("dbUser", "mirror_grpc");
        defaultParameters.addArgument("dbPassword", "mirror_grpc_pass");
        defaultParameters.addArgument("topicID", "0");
        defaultParameters.addArgument("realmNum", "0");
        defaultParameters.addArgument("historicMessagesCount", "0");
        defaultParameters.addArgument("newTopicsMessageCount", "0");
        defaultParameters.addArgument("newTopicsMessageDelay", "0");
        defaultParameters.addArgument("delSeqFrom", "-1");
        return defaultParameters;
    }

    @Override
    public SampleResult runTest(JavaSamplerContext context) {
        SampleResult result = new SampleResult();
        boolean success = true;
        String response = "";
        result.sampleStart();

        try {
            // establish db connection in test to ensure failures are reported
            connHandl = new ConnectionHandler(host, port, dbName, dbUser, dbPassword);
            sampler = new TopicMessageGeneratorSampler(connHandl);

            log.info("Kicking off populateTopicMessages");
            response = sampler
                    .populateTopicMessages(topicNum, historicMessagesCount, futureMessagesCount,
                            newTopicsMessageDelay, delSeqFrom);

            if (response != "Success") {
                throw new Exception("TopicMessageGeneratorSampler response was not successful");
            }

            result.sampleEnd();
            result.setResponseData(response.getBytes());
            result.setResponseMessage("Successfully performed populateTopicMessages");
            result.setResponseCodeOK();
            log.info("Successfully performed populateTopicMessages");
        } catch (Exception ex) {
            result.sampleEnd();
            success = false;
            result.setResponseMessage("Exception: " + ex);
            log.error("Error populating topics: " + ex);

            StringWriter stringWriter = new StringWriter();
            ex.printStackTrace(new PrintWriter(stringWriter));
            result.setResponseData(stringWriter.toString().getBytes());
            result.setDataType(SampleResult.TEXT);
            result.setResponseCode("500");
        }

        result.setSuccessful(success);

        // shutdown test and avoid notifying waiting for signal - saves run time
        teardownTest(context);
        return result;
    }

    @Override
    public void teardownTest(JavaSamplerContext context) {
        try {
            log.info("Connection Handler close called");
            connHandl.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        super.teardownTest(context);
    }
}
