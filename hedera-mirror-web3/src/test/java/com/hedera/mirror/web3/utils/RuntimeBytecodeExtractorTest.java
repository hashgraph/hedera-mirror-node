/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.utils;

import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.service.ContractExecutionService;
import com.hedera.mirror.web3.web3j.TestWeb3jService;
import com.hedera.mirror.web3.web3j.TestWeb3jService.Web3jTestConfiguration;
import com.hedera.mirror.web3.web3j.generated.DynamicEthCalls;
import com.hedera.mirror.web3.web3j.generated.ERCTestContractHistorical;
import com.hedera.mirror.web3.web3j.generated.EthCall;
import com.hedera.mirror.web3.web3j.generated.EvmCodes;
import com.hedera.mirror.web3.web3j.generated.EvmCodesHistorical;
import com.hedera.mirror.web3.web3j.generated.ExchangeRatePrecompileHistorical;
import com.hedera.mirror.web3.web3j.generated.NestedCallsHistorical;
import com.hedera.mirror.web3.web3j.generated.PrecompileTestContractHistorical;
import com.hedera.mirror.web3.web3j.generated.TestAddressThis;
import lombok.RequiredArgsConstructor;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Import;

@Import(Web3jTestConfiguration.class)
@RequiredArgsConstructor
class RuntimeBytecodeExtractorTest extends Web3IntegrationTest {

    private final ContractExecutionService contractExecutionService;
    private final TestWeb3jService testWeb3jService;

    @BeforeEach
    void setUp() {
        domainBuilder.recordFile().persist();
    }

    @Test
    void testExtractRuntimeBytecodeEvmCodes() {
        final var serviceParameters =
                testWeb3jService.serviceParametersForTopLevelContractCreate(EvmCodes.BINARY, ETH_CALL, Address.ZERO);
        assertThat(RuntimeBytecodeExtractor.extractRuntimeBytecode(EvmCodes.BINARY))
                .isEqualTo(contractExecutionService.processCall(serviceParameters));
    }

    @Test
    void testExtractRuntimeBytecodeEthCall() {
        final var serviceParameters =
                testWeb3jService.serviceParametersForTopLevelContractCreate(EthCall.BINARY, ETH_CALL, Address.ZERO);
        assertThat(RuntimeBytecodeExtractor.extractRuntimeBytecode(EthCall.BINARY))
                .isEqualTo(contractExecutionService.processCall(serviceParameters));
    }

    @Test
    void testExtractRuntimeBytecodeExchangeRateHistorical() {
        final var serviceParameters = testWeb3jService.serviceParametersForTopLevelContractCreate(
                ExchangeRatePrecompileHistorical.BINARY, ETH_CALL, Address.ZERO);
        assertThat(RuntimeBytecodeExtractor.extractRuntimeBytecode(ExchangeRatePrecompileHistorical.BINARY))
                .isEqualTo(contractExecutionService.processCall(serviceParameters));
    }

    @Test
    void testExtractRuntimeBytecodeMissingCODECOPY() {
        String initBytecode = "6080abcdef"; // No CODECOPY present
        final var exception = assertThrows(RuntimeException.class, () -> {
            RuntimeBytecodeExtractor.extractRuntimeBytecode(initBytecode);
        });
        assertThat(exception.getMessage()).isEqualTo("CODECOPY instruction (39) not found in init bytecode.");
    }

    @Test
    void testExtractRuntimeBytecodeMissingRuntimePrefix() {
        String initBytecode = "395ff3fe"; // CODECOPY present but no runtime code prefix
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            RuntimeBytecodeExtractor.extractRuntimeBytecode(initBytecode);
        });
        assertThat(thrown.getMessage()).isEqualTo("Runtime code prefix (6080) not found after CODECOPY.");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                DynamicEthCalls.BINARY,
                ERCTestContractHistorical.BINARY,
                EthCall.BINARY,
                EvmCodes.BINARY,
                EvmCodesHistorical.BINARY,
                ExchangeRatePrecompileHistorical.BINARY,
                NestedCallsHistorical.BINARY,
                PrecompileTestContractHistorical.BINARY,
                TestAddressThis.BINARY
            })
    void testIsInitBytecode(final String data) {
        assertThat(RuntimeBytecodeExtractor.isInitBytecode(data)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "  ",
                "0x",
                "0x39",
                "0xf30039",
            })
    void testIsInitBytecodeFalse(final String data) {
        assertThat(RuntimeBytecodeExtractor.isInitBytecode(data)).isFalse();
    }
}
