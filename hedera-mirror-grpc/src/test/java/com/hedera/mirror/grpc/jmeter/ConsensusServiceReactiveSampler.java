package com.hedera.mirror.grpc.jmeter;

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

import io.grpc.StatusRuntimeException;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import java.io.PrintWriter;
import java.io.StringWriter;
import lombok.extern.log4j.Log4j2;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;
import org.springframework.data.r2dbc.core.DatabaseClient;
import org.springframework.data.r2dbc.core.DefaultReactiveDataAccessStrategy;
import org.springframework.data.r2dbc.dialect.PostgresDialect;

import com.hedera.mirror.grpc.jmeter.client.ConsensusServiceReactiveClient;

@Log4j2
public class ConsensusServiceReactiveSampler extends AbstractJavaSamplerClient {
    ConsensusServiceReactiveClient csclient = null;

    @Override
    public void setupTest(JavaSamplerContext context) {
        String host = context.getParameter("host");
        String port = context.getParameter("port");
        String limit = context.getParameter("limit");
        String consensusStartTimeSeconds = context.getParameter("consensusStartTimeSeconds");
        String consensusEndTimeSeconds = context.getParameter("consensusEndTimeSeconds");
        String topicID = context.getParameter("topicID");
        String realmNum = context.getParameter("realmNum");

        DatabaseClient dbClient = getDatabaseClient();
        PostgresqlConnection connection = getConnection();

        csclient = new ConsensusServiceReactiveClient(
                host,
                Integer.parseInt(port),
                Long.parseLong(topicID),
                Long.parseLong(realmNum),
                Long.parseLong(consensusStartTimeSeconds),
                Long.parseLong(consensusEndTimeSeconds),
                Long.parseLong(limit),
                dbClient,
                connection);

        super.setupTest(context);
    }

    @Override
    public Arguments getDefaultParameters() {
        Arguments defaultParameters = new Arguments();
        defaultParameters.addArgument("host", "localhost");
        defaultParameters.addArgument("port", "5600");
        defaultParameters.addArgument("limit", "5");
        defaultParameters.addArgument("consensusStartTimeSeconds", "0");
        defaultParameters.addArgument("consensusEndTimeSeconds", "0");
        defaultParameters.addArgument("topicID", "0");
        defaultParameters.addArgument("realmNum", "0");
        return defaultParameters;
    }

    public DatabaseClient getDatabaseClient() {

        log.info("Initialize connectionFactory and databaseClient");
        PostgresqlConnectionFactory connectionFactory = new PostgresqlConnectionFactory(
                PostgresqlConnectionConfiguration.builder()
                        .host("localhost")
                        .port(5432)
                        .username("mirror_grpc")
                        .password("mirror_grpc_pass")
                        .database("mirror_node")
                        .build());

        return DatabaseClient.builder()
                .connectionFactory(connectionFactory)
                .dataAccessStrategy(new DefaultReactiveDataAccessStrategy(PostgresDialect.INSTANCE))
                .build();
    }

    public PostgresqlConnection getConnection() {
        PostgresqlConnectionFactory connectionFactory = new PostgresqlConnectionFactory(
                PostgresqlConnectionConfiguration.builder()
                        .host("localhost")
                        .port(5432)
                        .username("mirror_grpc")
                        .password("mirror_grpc_pass")
                        .database("mirror_node")
                        .build());

        return connectionFactory.create().block();
    }

    @Override
    public SampleResult runTest(JavaSamplerContext context) {
        SampleResult result = new SampleResult();
        boolean success = true;
        String response = "";
        result.sampleStart();

        try {
            response = csclient.subscribeTopic();
            result.sampleEnd();
            result.setResponseData(response.getBytes());
            result.setResponseMessage("Successfully performed subscribe topic test");
            result.setResponseCodeOK();
            log.info("Successfully performed subscribe topic test");
        } catch (StatusRuntimeException ex) {
            result.sampleEnd();
            success = false;
            result.setResponseMessage("Exception: " + ex);
            log.error("Error subscribing to topic: " + ex);

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
            csclient.shutdown();
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }

        super.teardownTest(context);
    }
}
