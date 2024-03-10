/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.controller;

import static com.hedera.mirror.web3.validation.HexValidator.MESSAGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.exception.BlockNumberNotFoundException;
import com.hedera.mirror.web3.exception.BlockNumberOutOfRangeException;
import com.hedera.mirror.web3.exception.EntityNotFoundException;
import com.hedera.mirror.web3.exception.InvalidParametersException;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.service.ContractCallService;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.mirror.web3.viewmodel.ContractCallRequest;
import com.hedera.mirror.web3.viewmodel.GenericErrorResponse;
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.core.StringContains;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@ExtendWith(SpringExtension.class)
@WebMvcTest(controllers = ContractController.class)
class ContractControllerTest {

    private static final String CALL_URI = "/api/v1/contracts/call";
    private static final String ONE_BYTE_HEX = "80";

    @Resource
    private MockMvc mockMvc;

    @Resource
    private ObjectMapper objectMapper;

    @MockBean
    private ContractCallService service;

    @MockBean
    private Bucket bucket;

    @Autowired
    private MirrorNodeEvmProperties evmProperties;

    @BeforeEach
    void setUp() {
        given(bucket.tryConsume(1)).willReturn(true);
    }

    @SneakyThrows
    private String convert(Object object) {
        return objectMapper.writeValueAsString(object);
    }

    @SneakyThrows
    private ResultActions contractCall(ContractCallRequest request) {
        return mockMvc.perform(post(CALL_URI)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .content(convert(request)));
    }

    @NullAndEmptySource
    @ValueSource(strings = {"0x00000000000000000000000000000000000007e7"})
    @ParameterizedTest
    void estimateGas(String to) throws Exception {
        final var request = request();
        request.setEstimate(true);
        request.setTo(to);
        contractCall(request).andExpect(status().isOk());
    }

    @ValueSource(longs = {2000, -2000, Long.MAX_VALUE, 0})
    @ParameterizedTest
    void estimateGasWithInvalidGasParameter(long gas) throws Exception {
        final var errorString = gas < 21000L
                ? numberErrorString("gas", "greater", 21000L)
                : numberErrorString("gas", "less", 15_000_000L);
        final var request = request();
        request.setEstimate(true);
        request.setGas(gas);
        contractCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(convert(new GenericErrorResponse(errorString))));
    }

    @Test
    void exceedingRateLimit() throws Exception {
        for (var i = 0; i < 3; i++) {
            contractCall(request()).andExpect(status().isOk());
        }

        given(bucket.tryConsume(1)).willReturn(false);
        contractCall(request()).andExpect(status().isTooManyRequests());
    }

    @NullAndEmptySource
    @ValueSource(
            strings = {
                " ",
                "0x",
                "0xghijklmno",
                "0x00000000000000000000000000000000000004e",
                "0x00000000000000000000000000000000000004e2a",
                "0x000000000000000000000000000000Z0000007e7",
                "00000000001239847e"
            })
    @ParameterizedTest
    void callInvalidTo(String to) throws Exception {
        final var request = request();
        request.setTo(to);
        contractCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(new StringContains("to field")));
    }

    @Test
    void callMissingTo() throws Exception {
        final var exceptionMessage = "No such contract or token";
        final var request = request();

        given(service.processCall(any())).willThrow(new EntityNotFoundException(exceptionMessage));

        contractCall(request)
                .andExpect(status().isNotFound())
                .andExpect(content().string(convert(new GenericErrorResponse(exceptionMessage))));
    }

    @EmptySource
    @ValueSource(
            strings = {
                " ",
                "0x",
                "0xghijklmno",
                "0x00000000000000000000000000000000000004e",
                "0x00000000000000000000000000000000000004e2a",
                "0x000000000000000000000000000000Z0000007e7",
                "00000000001239847e"
            })
    @ParameterizedTest
    void callInvalidFrom(String from) throws Exception {
        final var errorString = "from field ".concat(MESSAGE);
        final var request = request();
        request.setFrom(from);
        contractCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(convert(new GenericErrorResponse(errorString))));
    }

    @Test
    void callInvalidValue() throws Exception {
        final var error = "value field must be greater than or equal to 0";
        final var request = request();
        request.setValue(-1L);
        contractCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(convert(new GenericErrorResponse(error))));
    }

    @Test
    void exceedingDataCallSizeOnEstimate() throws Exception {
        var error = "data field of size 51204 contains invalid hexadecimal characters or exceeds 51200 characters";
        final var request = request();
        final var dataAsHex =
                ONE_BYTE_HEX.repeat((int) evmProperties.getMaxDataSize().toBytes() + 1);
        request.setData("0x" + dataAsHex);
        request.setEstimate(true);
        contractCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(convert(new GenericErrorResponse(error))));
    }

    @Test
    void exceedingDataCreateSizeOnEstimate() throws Exception {
        var error = "data field of size 51204 contains invalid hexadecimal characters or exceeds 51200 characters";
        final var request = request();
        final var dataAsHex =
                ONE_BYTE_HEX.repeat((int) evmProperties.getMaxDataSize().toBytes() + 1);
        request.setTo(null);
        request.setData("0x" + dataAsHex);
        request.setEstimate(true);

        contractCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(convert(new GenericErrorResponse(error))));
    }

    @Test
    void callWithMalformedJsonBody() throws Exception {
        var request = "{from: 0x00000000000000000000000000000000000004e2\"";
        mockMvc.perform(post(CALL_URI)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(content()
                        .string(convert(new GenericErrorResponse(
                                "Unable to parse JSON",
                                "JSON parse error: Unexpected character ('f' (code 102)): was expecting double-quote to start field name",
                                StringUtils.EMPTY))));
    }

    @Test
    void callWithUnsupportedMediaTypeBody() throws Exception {
        final var request = request();
        mockMvc.perform(post(CALL_URI)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(convert(request)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content()
                        .string(convert(new GenericErrorResponse(
                                "Unsupported Media Type",
                                "Content-Type 'text/plain;charset=UTF-8' is not supported",
                                StringUtils.EMPTY))));
    }

    @Test
    void callRevertMethodAndExpectDetailMessage() throws Exception {
        final var detailedErrorMessage = "Custom revert message";
        final var hexDataErrorMessage =
                "0x08c379a000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000015437573746f6d20726576657274206d6573736167650000000000000000000000";
        final var request = request();
        request.setData("0xa26388bb");

        given(service.processCall(any()))
                .willThrow(new MirrorEvmTransactionException(
                        CONTRACT_REVERT_EXECUTED, detailedErrorMessage, hexDataErrorMessage));

        contractCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content()
                        .string(convert(new GenericErrorResponse(
                                CONTRACT_REVERT_EXECUTED.name(), detailedErrorMessage, hexDataErrorMessage))));
    }

    @Test
    void callWithInvalidParameter() throws Exception {
        final var error = "No such contract or token";
        final var request = request();

        given(service.processCall(any())).willThrow(new InvalidParametersException(error));
        contractCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(convert(new GenericErrorResponse(error))));
    }

    @Test
    void callInvalidGasPrice() throws Exception {
        final var errorString = numberErrorString("gasPrice", "greater", 0);
        final var request = request();
        request.setGasPrice(-1L);

        contractCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(convert(new GenericErrorResponse(errorString))));
    }

    @Test
    void transferWithoutSender() throws Exception {
        final var errorString = "from field must not be empty";
        final var request = request();
        request.setFrom(null);

        contractCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(convert(new GenericErrorResponse(errorString))));
    }

    @NullAndEmptySource
    @ParameterizedTest
    @ValueSource(strings = {"earliest", "latest", "0", "0x1a", "pending", "safe", "finalized"})
    void callValidBlockType(String value) throws Exception {
        final var request = request();
        request.setBlock(BlockType.of(value));

        contractCall(request).andExpect(status().isOk());
    }

    @Test
    void callNegativeBlock() throws Exception {
        mockMvc.perform(post(CALL_URI)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"block\": \"-1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void callWithBlockNumberOutOfRangeExceptionTest() throws Exception {
        final var request = request();
        given(service.processCall(any())).willThrow(new BlockNumberOutOfRangeException("Unknown block number"));

        contractCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(convert(new GenericErrorResponse("Unknown block number"))));
    }

    @Test
    void callWithBlockNumberNotFoundExceptionTest() throws Exception {
        final var request = request();
        given(service.processCall(any())).willThrow(new BlockNumberNotFoundException());

        contractCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(convert(new GenericErrorResponse("Unknown block number"))));
    }

    @Test
    void callSuccess() throws Exception {
        final var request = request();
        request.setData("0x1079023a0000000000000000000000000000000000000000000000000000000000000156");
        request.setValue(0);

        contractCall(request).andExpect(status().isOk());
    }

    @NullAndEmptySource
    @ParameterizedTest
    void callSuccessWithNullAndEmptyData(String data) throws Exception {
        final var request = request();
        request.setData(data);
        request.setValue(0);

        contractCall(request).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "1aa"})
    void callBadRequestWithInvalidHexData(String data) throws Exception {
        final var request = request();
        request.setData(data);
        request.setValue(0);

        contractCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(new StringContains("contains invalid odd length characters")));
    }

    @Test
    void transferSuccess() throws Exception {
        final var request = request();
        request.setData(null);

        contractCall(request).andExpect(status().isOk());
    }

    /*
     * https://stackoverflow.com/questions/62723224/webtestclient-cors-with-spring-boot-and-webflux
     * The Spring WebTestClient CORS testing requires that the URI contain any hostname and port.
     */
    @Test
    void callSuccessCors() throws Exception {
        mockMvc.perform(options(CALL_URI)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Origin", "http://example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(header().string("Access-Control-Allow-Methods", "POST"));
    }

    private ContractCallRequest request() {
        final var request = new ContractCallRequest();
        request.setBlock(BlockType.LATEST);
        request.setData("0x1079023a");
        request.setFrom("0x00000000000000000000000000000000000004e2");
        request.setGas(200000L);
        request.setGasPrice(78282329L);
        request.setTo("0x00000000000000000000000000000000000004e4");
        request.setValue(23);
        return request;
    }

    private String numberErrorString(String field, String direction, long num) {
        return String.format("%s field must be %s than or equal to %d", field, direction, num);
    }

    @TestConfiguration
    public static class TestConfig {
        @Bean
        public MirrorNodeEvmProperties evmProperties() {
            return new MirrorNodeEvmProperties();
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
