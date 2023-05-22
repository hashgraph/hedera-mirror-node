/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.importer.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.importer.domain.StreamFilename;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;

/*
 * This test class was introduced with issue 6023, and is focused around the MetricsExecutionInterceptor
 * afterExecution() method, specifically the micrometer metrics tags and values that are produced depending
 * on the S3 activity being intercepted.
 *
 * This required a bit of refactoring to make it possible to introduce the mock Timer.Builder and
 * DistributionSummary.Builder instances required for testing. The metric tags are captured via the
 * mock Timer.Builder only, and the DistributionSummary.Builder mock is ignored. The tags are then examined for the
 * expected results for the scenario. No other verification of MetricsExecutionInterceptor is performed at this time.
 */
class MetricsExecutionInterceptorTest {

    private static final int HTTP_STATUS_SUCCESS = 200;
    private static final String S3_REGION = "us-west-1";
    private static final String S3_BUCKET = "hedera-bucket";
    private static final String S3_HOST = "https://%s.s3.%s.amazonaws.com/".formatted(S3_BUCKET, S3_REGION);
    private static final String SEPARATOR = "/";
    private static final long SHARD = 0L;
    private static final long REALM = 0L;
    private static final long ACCOUNT_NUM = 4;
    private static final String NETWORK_NAME = "network";
    private static final long NODE_ID = ACCOUNT_NUM - 3L;
    private static final String BATCH_SIZE = "100";
    private final ExecutionAttributes executionAttributes = new ExecutionAttributes();

    @Mock(lenient = true)
    private MeterRegistry meterRegistry;

    @Mock
    private SdkHttpResponse sdkHttpResponse;

    @Mock
    private Context.AfterExecution afterExecutionContext;

    @Mock
    private Timer timer;

    @Mock
    private Timer.Builder timerBuilder;

    @Mock
    private DistributionSummary.Builder distributionSummaryBuilder;

    @Captor
    private ArgumentCaptor<String> timerTagsCaptor;

    private AutoCloseable openMocksCloseable;
    private MetricsExecutionInterceptor metricsExecutionInterceptor;

    @BeforeEach
    void setup() {
        openMocksCloseable = MockitoAnnotations.openMocks(this);

        when(afterExecutionContext.httpResponse()).thenReturn(sdkHttpResponse);
        when(sdkHttpResponse.statusCode()).thenReturn(HTTP_STATUS_SUCCESS);

        when(timerBuilder.tags(any(String.class))).thenReturn(timerBuilder);
        when(timerBuilder.register(meterRegistry)).thenReturn(timer);

        executionAttributes.putAttribute(
                MetricsExecutionInterceptor.START_TIME, Instant.now().minusSeconds(60L));

        metricsExecutionInterceptor =
                new MetricsExecutionInterceptor(meterRegistry, timerBuilder, distributionSummaryBuilder);
    }

    @AfterEach
    void tearDown() throws Exception {
        openMocksCloseable.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACCOUNT_ID", "NODE_ID"})
    void s3ListExecution(String pathTypeName) {
        var prefix = "ACCOUNT_ID".equals(pathTypeName)
                ? accountIdPrefix(StreamType.RECORD, SHARD, REALM, ACCOUNT_NUM)
                : nodeIdPrefix(StreamType.RECORD, NETWORK_NAME, SHARD, NODE_ID);
        var sdkHttpRequest = createListObjectsRequest(prefix, StreamFilename.EPOCH.getFilename());

        when(afterExecutionContext.httpRequest()).thenReturn(sdkHttpRequest);
        metricsExecutionInterceptor.afterExecution(afterExecutionContext, executionAttributes);

        verify(timerBuilder, times(1)).tags(timerTagsCaptor.capture());
        List<String> providedTags = timerTagsCaptor.getAllValues();

        verifyTags(providedTags, "list", NODE_ID, StreamType.RECORD);
    }

    @ParameterizedTest
    @CsvSource({
        "ACCOUNT_ID, RECORD, 2022-06-21T09_14_34.364804003Z.rcd, signed",
        "ACCOUNT_ID, RECORD, 2020-02-09T18_30_00.000084Z.rcd_sig, signature",
        "ACCOUNT_ID, RECORD, 2022-07-13T08_46_11.304284003Z_01.rcd.gz, sidecar",
        "NODE_ID, RECORD, 2022-06-21T09_14_34.364804003Z.rcd, signed",
        "NODE_ID, RECORD, 2020-02-09T18_30_00.000084Z.rcd_sig, signature",
        "NODE_ID, RECORD, 2022-07-13T08_46_11.304284003Z_01.rcd.gz, sidecar",
        "ACCOUNT_ID, EVENT, 2020-04-11T00_12_05.059945Z.evts, signed",
        "ACCOUNT_ID, EVENT, 2020-04-11T00_12_05.059945Z.evts_sig, signature",
        "NODE_ID, EVENT, 2020-04-11T00_12_05.059945Z.evts, signed",
        "NODE_ID, EVENT, 2020-04-11T00_12_05.059945Z.evts_sig, signature",
        "ACCOUNT_ID, BALANCE, 2021-03-10T22_12_56.075092Z_Balances.csv, signed",
        "ACCOUNT_ID, BALANCE, 2021-03-10T22_12_56.075092Z_Balances.csv_sig, signature",
        "ACCOUNT_ID, BALANCE, 2021-03-10T22_12_56.075092Z_Balances.pb.gz, signed",
        "ACCOUNT_ID, BALANCE, 2021-03-10T22_12_56.075092Z_Balances.pb_sig.gz, signature",
        "NODE_ID, BALANCE, 2021-03-10T22_12_56.075092Z_Balances.csv, signed",
        "NODE_ID, BALANCE, 2021-03-10T22_12_56.075092Z_Balances.csv_sig, signature",
        "NODE_ID, BALANCE, 2021-03-10T22_12_56.075092Z_Balances.pb.gz, signed",
        "NODE_ID, BALANCE, 2021-03-10T22_12_56.075092Z_Balances.pb_sig.gz, signature"
    })
    void s3GetObjectExecution(String pathTypeName, String streamTypeName, String fileName, String expectedAction) {

        var streamType = StreamType.valueOf(streamTypeName);
        var prefix = "ACCOUNT_ID".equals(pathTypeName)
                ? accountIdPrefix(streamType, SHARD, REALM, ACCOUNT_NUM)
                : nodeIdPrefix(streamType, NETWORK_NAME, SHARD, NODE_ID);

        var objectKey = prefix + fileName;
        var sdkHttpRequest = createGetObjectRequest(objectKey);

        when(afterExecutionContext.httpRequest()).thenReturn(sdkHttpRequest);
        metricsExecutionInterceptor.afterExecution(afterExecutionContext, executionAttributes);

        verify(timerBuilder, times(1)).tags(timerTagsCaptor.capture());
        List<String> providedTags = timerTagsCaptor.getAllValues();

        verifyTags(providedTags, expectedAction, NODE_ID, streamType);
    }

    private String nodeIdPrefix(StreamType streamType, String network, long shard, long nodeId) {
        return "%s/%d/%d/%s/".formatted(network, shard, nodeId, streamType.getNodeIdBasedSuffix());
    }

    private String accountIdPrefix(StreamType streamType, long shard, long realm, long accountNum) {
        return "%s/%s%d.%d.%d/".formatted(streamType.getPath(), streamType.getNodePrefix(), shard, realm, accountNum);
    }

    private void verifyTags(
            List<String> tags, String expectedAction, long expectedNodeId, StreamType expectedStreamType) {
        assertNotNull(tags);
        assertEquals(14, tags.size());

        // The tags are in a defined order, with the tag name followed by its value. All are Strings.
        assertEquals(expectedAction, tags.get(1));
        assertEquals("GET", tags.get(3));
        assertEquals(expectedNodeId, Long.valueOf(tags.get(5)));
        assertEquals("0", tags.get(7)); // realm
        assertEquals("0", tags.get(9)); // shard
        assertEquals("200", tags.get(11));
        assertEquals(expectedStreamType.name(), tags.get(13));
    }

    /*
     * Create S3 list HTTP request per https://docs.aws.amazon.com/AmazonS3/latest/API/API_ListObjectsV2.html
     */
    private SdkHttpRequest createListObjectsRequest(String prefix, String startAfter) {
        return SdkHttpRequest.builder()
                .protocol("https")
                .host(S3_HOST)
                .method(SdkHttpMethod.GET)
                .encodedPath("/")
                .appendRawQueryParameter("list-type", "2")
                .appendRawQueryParameter("delimiter", SEPARATOR)
                .appendRawQueryParameter("max-keys", BATCH_SIZE)
                .appendRawQueryParameter("prefix", prefix)
                .appendRawQueryParameter("start-after", prefix + startAfter)
                .build();
    }

    /*
     * Create S3 get object HTTP request per https://docs.aws.amazon.com/AmazonS3/latest/API/API_GetObject.html
     */
    private SdkHttpRequest createGetObjectRequest(String objectKey) {
        return SdkHttpRequest.builder()
                .protocol("https")
                .host(S3_HOST)
                .method(SdkHttpMethod.GET)
                .encodedPath(objectKey)
                .build();
    }
}
