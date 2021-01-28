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

import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TopicID;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.extern.log4j.Log4j2;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;

import com.hedera.mirror.api.proto.ConsensusTopicQuery;
import com.hedera.mirror.grpc.jmeter.handler.PropertiesHandler;
import com.hedera.mirror.grpc.jmeter.props.MessageListener;
import com.hedera.mirror.grpc.jmeter.sampler.HCSDirectStubTopicSampler;
import com.hedera.mirror.grpc.jmeter.sampler.HCSMAPITopicSampler;
import com.hedera.mirror.grpc.jmeter.sampler.HCSTopicSampler;
import com.hedera.mirror.grpc.jmeter.sampler.result.HCSSamplerResult;

@Log4j2
public class SingleTopicHCSClient extends AbstractJavaSamplerClient {

    private static ManagedChannel channel;
    private HCSTopicSampler hcsTopicSampler;
    private PropertiesHandler propHandler;

    private static synchronized void setChannel(String host, int port) {
        if (channel == null) {
            channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        }
    }

    /**
     * Setup test by instantiating client using user defined test properties
     */
    @Override
    public void setupTest(JavaSamplerContext context) {
        propHandler = new PropertiesHandler(context);
        String host = propHandler.getTestParam("host", "localhost");
        int port = propHandler.getIntTestParam("port", 5600);
        boolean sharedChannel = Boolean.valueOf(propHandler.getTestParam("sharedChannel", "false"));
        Long startTime = propHandler.getLongClientTestParam("StartTime", 0, null);
        int nanoStartTime = 0;

        // allow for subscribe x seconds from now.
        // Null value will take current time, negative will subtracts x seconds now and positive will add
        boolean nullStartTime = startTime == null;
        if (nullStartTime || startTime < 0) {
            Instant now = Instant.now();
            startTime = nullStartTime ? now.getEpochSecond() : now.plus(startTime, ChronoUnit.SECONDS).getEpochSecond();
            nanoStartTime = now.getNano();
        }

        long endTimeSecs = propHandler.getLongClientTestParam("EndTime", 0, 0L);
        long limit = propHandler.getLongClientTestParam("Limit", 0, 100L);

        ConsensusTopicQuery.Builder builder = ConsensusTopicQuery.newBuilder()
                .setConsensusStartTime(Timestamp.newBuilder().setSeconds(startTime).setNanos(nanoStartTime).build())
                .setTopicID(
                        TopicID.newBuilder()
                                .setRealmNum(propHandler.getLongClientTestParam("RealmNum", 0, 0L))
                                .setTopicNum(propHandler.getLongClientTestParam("TopicId", 0, 0L))
                                .build());

        if (endTimeSecs != 0) {
            builder.setConsensusEndTime(Timestamp.newBuilder().setSeconds(endTimeSecs).build());
        }

        if (limit > 0) {
            builder.setLimit(limit);
        }

        ConsensusTopicQuery consensusTopicQuery = builder.build();
        boolean useMAPI = Boolean.valueOf(propHandler.getClientTestParam("UseMAPI", 0, "false"));
        log.trace("useMAPI : {}", useMAPI);
        if (useMAPI) {
            setMAPITopicSampler(host, port, consensusTopicQuery);
        } else {
            setDirectStubSampler(sharedChannel, host, port, consensusTopicQuery);
        }

        super.setupTest(context);
    }

    private void setMAPITopicSampler(String host, int port, ConsensusTopicQuery consensusTopicQuery) {
        hcsTopicSampler = new HCSMAPITopicSampler(consensusTopicQuery, host + ":" + port);
    }

    private void setDirectStubSampler(boolean sharedChannel, String host, int port,
                                      ConsensusTopicQuery consensusTopicQuery) {
        if (sharedChannel) {
            setChannel(host, port);

            hcsTopicSampler = new HCSDirectStubTopicSampler(channel, consensusTopicQuery);
        } else {
            hcsTopicSampler = new HCSDirectStubTopicSampler(host, port, consensusTopicQuery);
        }
    }

    /**
     * Runs test by calling sampler subscribeTopic. Reports success based on call response from sampler
     */
    @Override
    public SampleResult runTest(JavaSamplerContext context) {
        SampleResult result = new SampleResult();
        HCSSamplerResult response = null;
        result.sampleStart();

        try {
            MessageListener listener = MessageListener.builder()
                    .historicMessagesCount(propHandler.getIntClientTestParam("HistoricMessagesCount", 0, "0"))
                    .futureMessagesCount(propHandler.getIntClientTestParam("IncomingMessageCount", 0, "0"))
                    .messagesLatchWaitSeconds(propHandler.getIntClientTestParam("SubscribeTimeoutSeconds", 0, "60"))
                    .statusPrintIntervalMinutes(propHandler.getIntClientTestParam("StatusPrintIntervalMinutes", 0, "1"))
                    .build();

            response = hcsTopicSampler.subscribeTopic(listener);

            result.sampleEnd();
            result.setResponseData(response.toString().getBytes());
            result.setSuccessful(response.isSuccess());

            if (!response.isSuccess()) {
                result.setResponseMessage("Failure in subscribe topic test");
                result.setResponseCode("500");
            } else {
                result.setResponseMessage("Successfully performed subscribe topic test");
                result.setResponseCodeOK();
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

        return result;
    }
}
