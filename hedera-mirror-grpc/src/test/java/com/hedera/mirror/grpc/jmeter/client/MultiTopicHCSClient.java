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

import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TopicID;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;

import com.hedera.mirror.api.proto.ConsensusTopicQuery;
import com.hedera.mirror.grpc.jmeter.props.MessageListener;
import com.hedera.mirror.grpc.jmeter.props.TopicSubscription;
import com.hedera.mirror.grpc.jmeter.sampler.HCSTopicSampler;

@Log4j2
public class MultiTopicHCSClient extends AbstractJavaSamplerClient {
    private final String basePattern = "%s.%s";
    private final String clientPattern = "client%s[%d]";
    JavaSamplerContext javaSamplerContext;
    private String propertiesBase;
    private Map<TopicSubscription, HCSTopicSampler> consensusServiceReactiveSamplers;
    private String host;
    private int port;
    private int clientCount;

    @Override
    public void setupTest(JavaSamplerContext javaSamplerContext) {
        consensusServiceReactiveSamplers = new HashMap<>();
        this.javaSamplerContext = javaSamplerContext;

        propertiesBase = javaSamplerContext.getParameter("propertiesBase", "hedera.mirror.test.performance");
        host = getTestParam("host", "localhost");
        port = Integer.parseInt(getTestParam("port", "5600"));
        clientCount = Integer.parseInt(getTestParam("clientCount", "0"));

        for (int i = 0; i < clientCount; i++) {
            TopicSubscription topicSubscription = TopicSubscription.builder()
                    .topicId(convertClientParamToLong("TopicId", i))
                    .startTime(convertClientParamToLong("StartTime", i))
                    .endTime(convertClientParamToLong("EndTime", i))
                    .limit(convertClientParamToLong("Limit", i))
                    .historicMessagesCount(convertClientParamToInt("HistoricMessagesCount", i))
                    .incomingMessageCount(convertClientParamToInt("IncomingMessageCount", i))
                    .subscribeTimeoutSeconds(convertClientParamToInt("SubscribeTimeoutSeconds", i))
                    .milliSecWaitBefore(convertClientParamToInt("MilliSecWaitBefore", i))
                    .build();

            log.debug("Created TopicSubscription : {}", topicSubscription);

            consensusServiceReactiveSamplers.put(topicSubscription, createSampler(topicSubscription));
        }

        super.setupTest(javaSamplerContext);
    }

    @Override
    public Arguments getDefaultParameters() {
        Arguments defaultParameters = new Arguments();
        defaultParameters.addArgument("propertiesBase", "hedera.mirror.test.performance");
        return defaultParameters;
    }

    @Override
    public SampleResult runTest(JavaSamplerContext javaSamplerContext) {
        // run designated clients in order
        return sequentialRun();
    }

    private HCSTopicSampler createSampler(TopicSubscription topicSubscription) {
        ConsensusTopicQuery.Builder builder = ConsensusTopicQuery.newBuilder()
                .setLimit(topicSubscription.getLimit())
                .setConsensusStartTime(Timestamp.newBuilder().setSeconds(topicSubscription.getStartTime()).build())
                .setTopicID(
                        TopicID.newBuilder()
                                .setRealmNum(topicSubscription.getRealmNum())
                                .setTopicNum(topicSubscription.getTopicId())
                                .build());

        if (topicSubscription.getEndTime() > 0) {
            builder.setConsensusEndTime(Timestamp.newBuilder().setSeconds(topicSubscription.getEndTime()).build());
        }

        return new HCSTopicSampler(host, port, builder.build());
    }

    private SampleResult sequentialRun() {
        SampleResult result = new SampleResult();
        int successSamplesCount = 0;
        result.sampleStart();

        try {
            HCSTopicSampler.SamplerResult response = null;
            for (TopicSubscription subscription : consensusServiceReactiveSamplers.keySet()) {
                if (subscription.getMilliSecWaitBefore() > 0) {
                    log.debug("Waiting {} ms before subscribing", subscription.getMilliSecWaitBefore());
                    Thread.sleep(subscription.getMilliSecWaitBefore(), 0);
                }

                MessageListener listener = MessageListener.builder()
                        .historicMessagesCount(subscription.getHistoricMessagesCount())
                        .futureMessagesCount(subscription.getIncomingMessageCount())
                        .messagesLatchWaitSeconds(subscription.getSubscribeTimeoutSeconds())
                        .build();

                response = consensusServiceReactiveSamplers.get(subscription).subscribeTopic(listener);

                if (!response.isSuccess()) {
                    log.debug("Failure in subscribe topic test : " + subscription);
                } else {
                    successSamplesCount++;
                    log.debug("Successfully performed subscription : " + subscription);
                }
            }

            result.setResponseMessage("Successfully performed subscriptions");
            result.setResponseCodeOK();
            result.sampleEnd();
            result.setResponseData(response.toString().getBytes());
            result.setSuccessful(response.isSuccess());
        } catch (Exception ex) {
            log.error("Error subscribing to topic", ex);

            StringWriter stringWriter = new StringWriter();
            ex.printStackTrace(new PrintWriter(stringWriter));

            result.setResponseMessage("Failure in subscribes");
            result.setResponseCode("500");
            result.sampleEnd();
            result.setResponseMessage("Exception: " + ex);
            result.setResponseData(stringWriter.toString().getBytes());
            result.setDataType(SampleResult.TEXT);
            result.setResponseCode("500");
        }

        log.info("{} out of {} samples passed", successSamplesCount, clientCount);

        return result;
    }

    private SampleResult concurrentRun() {
        SampleResult result = new SampleResult();

        return result;
    }

    private String getTestParam(String property, String defaultVal) {
        String retrievedValue = getTestParam(property);
        if (retrievedValue == null || retrievedValue.isEmpty()) {
            return defaultVal;
        }

        return retrievedValue;
    }

    private String getTestParam(String property) {
        String value = javaSamplerContext.getJMeterProperties()
                .getProperty(String.format(basePattern, propertiesBase, property));
        log.trace("Retrieved {} prop as {}", property, value);
        return value;
    }

    private long convertClientParamToLong(String property, int num) {
        String value = getTestParam(String.format(clientPattern, property, num));
        return Long.parseLong(value);
    }

    private int convertClientParamToInt(String property, int num) {
        String value = getTestParam(String.format(clientPattern, property, num));
        return Integer.parseInt(value);
    }
}
