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

    private final String clientPattern = "%s.client%s[%d]";
    private String propertiesBasePattern;
    private Map<TopicSubscription, HCSTopicSampler> consensusServiceReactiveSamplers;
    private String host;
    private int port;
    private int clientCount;

    @Override
    public void setupTest(JavaSamplerContext javaSamplerContext) {
        consensusServiceReactiveSamplers = new HashMap<>();

        propertiesBasePattern = getTestParam(javaSamplerContext, "propertiesBase", "hedera.mirror.test.performance.");
        host = getTestParam(javaSamplerContext, "host", "localhost");
        port = Integer.parseInt(getTestParam(javaSamplerContext, "port", "5600"));
        clientCount = Integer
                .parseInt(getTestParam(javaSamplerContext, "clientCount", "hedera.mirror.test.performance" +
                        ".clientCount"));

        for (int i = 0; i < clientCount; i++) {
            TopicSubscription topicSubscription = TopicSubscription.builder()
                    .topicId(convertClientParamToLong("TopicId", i))
                    .startTime(convertClientParamToLong("StartTime", i))
                    .endTime(convertClientParamToLong("EndTime", i))
                    .limit(convertClientParamToLong("Limit", i))
                    .historicMessagesCount(convertClientParamToInt("HistoricMessagesCount", i))
                    .incomingMessageCount(convertClientParamToInt("IncomingMessageCount", i))
                    .subscribeTimeoutSeconds(convertClientParamToInt("SubscribeTimeoutSeconds", i))
                    .build();

            consensusServiceReactiveSamplers.put(topicSubscription, createSampler(topicSubscription));
        }

        super.setupTest(javaSamplerContext);
    }

    @Override
    public Arguments getDefaultParameters() {
        Arguments defaultParameters = new Arguments();
        defaultParameters.addArgument("host", "localhost");
        defaultParameters.addArgument("port", "5600");
        defaultParameters.addArgument("limit", "100");
        defaultParameters.addArgument("consensusStartTimeSeconds", "0");
        defaultParameters.addArgument("consensusEndTimeSeconds", "0");
        defaultParameters.addArgument("topicIDs", "0");
        defaultParameters.addArgument("realmNum", "0");
        defaultParameters.addArgument("historicMessagesCount", "0");
        defaultParameters.addArgument("newTopicsMessageCount", "0");
        defaultParameters.addArgument("messagesLatchWaitSeconds", "60");
        return defaultParameters;
    }

    @Override
    public SampleResult runTest(JavaSamplerContext javaSamplerContext) {
        SampleResult result = new SampleResult();
        int successSamplesCount = 0;
        result.sampleStart();

        try {
            for (TopicSubscription subscription : consensusServiceReactiveSamplers.keySet()) {
                MessageListener listener = MessageListener.builder()
                        .historicMessagesCount(subscription.getHistoricMessagesCount())
                        .futureMessagesCount(subscription.getIncomingMessageCount())
                        .messagesLatchWaitSeconds(subscription.getSubscribeTimeoutSeconds())
                        .build();

                HCSTopicSampler.SamplerResult response = consensusServiceReactiveSamplers
                        .get(subscription).subscribeTopic(listener);

                result.sampleEnd();
                result.setResponseData(response.toString().getBytes());
                result.setSuccessful(response.isSuccess());

                if (!response.isSuccess()) {
                    result.setResponseMessage("Failure in subscribe topic test : " + subscription);
                    result.setResponseCode("500");
                } else {
                    successSamplesCount++;
                    result.setResponseMessage("Successfully performed subscription : " + subscription);
                    result.setResponseCodeOK();
                }
            }
        } catch (Exception ex) {
            log.error("Error subscribing to topic", ex);

            StringWriter stringWriter = new StringWriter();
            ex.printStackTrace(new PrintWriter(stringWriter));
            result.sampleEnd();
            result.setResponseMessage("Exception: " + ex);
            result.setResponseData(stringWriter.toString().getBytes());
            result.setDataType(SampleResult.TEXT);
            result.setResponseCode("500");
        }

        log.info("{} out of {} samples passed", successSamplesCount, clientCount);

        return result;
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

    private String getTestParam(JavaSamplerContext javaSamplerContext, String property, String defaultVal) {
        String retrievedValue = javaSamplerContext.getJMeterProperties().getProperty(propertiesBasePattern + property);
        if (retrievedValue == null || retrievedValue.isEmpty()) {
            return defaultVal;
        }

        return retrievedValue;
    }

    private long convertClientParamToLong(String property, int num) {
        return Long.parseLong(String.format(clientPattern, propertiesBasePattern, property, num));
    }

    private int convertClientParamToInt(String property, int num) {
        return Integer.parseInt(String.format(clientPattern, propertiesBasePattern, property, num));
    }
}
