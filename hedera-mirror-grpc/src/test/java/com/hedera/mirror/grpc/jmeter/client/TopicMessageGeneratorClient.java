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
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;

import com.hedera.mirror.grpc.jmeter.ConnectionHandler;
import com.hedera.mirror.grpc.jmeter.props.MessageGenerator;
import com.hedera.mirror.grpc.jmeter.sampler.TopicMessageGeneratorSampler;

@Log4j2
public class TopicMessageGeneratorClient extends AbstractJavaSamplerClient {

    private long topicNum;
    private int historicMessagesCount;
    private int futureMessagesCount;
    private long newTopicsMessageDelay;
    private long delSeqFrom;
    private TopicMessageGeneratorSampler sampler;
    private ConnectionHandler connHandl;
    private int topicMessageEmitCycles;
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
        // db props
        host = context.getParameter("host", "localhost");
        port = context.getIntParameter("port", 5432);
        dbName = context.getParameter("dbName", "mirror_node");
        dbUser = context.getParameter("dbUser", "mirror_node");
        dbPassword = context.getParameter("dbPassword", "mirror_node_pass");

        super.setupTest(context);
    }

    /**
     * Runs test by calling sampler to manage topic message table. Reports success based on successful db operations
     */
    @Override
    public SampleResult runTest(JavaSamplerContext context) {
        SampleResult result = new SampleResult();
        boolean success = true;
        result.sampleStart();

        try {
            // establish db connection in test to ensure failures are reported
            connHandl = new ConnectionHandler(host, port, dbName, dbUser, dbPassword);
            sampler = new TopicMessageGeneratorSampler(connHandl);

            MessageGenerator messageGen = MessageGenerator.builder()
                    .topicNum(context.getLongParameter("topicID", 0))
                    .historicMessagesCount(context.getIntParameter("historicMessagesCount", 0))
                    .futureMessagesCount(context.getIntParameter("newTopicsMessageCount", 0))
                    .topicMessageEmitCycles(context.getIntParameter("topicMessageEmitCycles", 0))
                    .newTopicsMessageDelay(context.getLongParameter("newTopicsMessageDelay", 0L))
                    .deleteFromSequence(context.getLongParameter("delSeqFrom", -1L))
                    .build();

            log.info("Kicking off populateTopicMessages");
            sampler
                    .populateTopicMessages(messageGen);

            result.sampleEnd();
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

        return result;
    }

    @Override
    public void teardownTest(JavaSamplerContext context) {
        try {
            if (connHandl != null) {
                connHandl.close();
            }
        } catch (Exception ex) {
            log.error("Unable to close connection", ex);
        }

        super.teardownTest(context);
    }
}
