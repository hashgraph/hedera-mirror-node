/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.service;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.web3j.generated.ExchangeRatePrecompile;
import com.hedera.mirror.web3.web3j.generated.PrngSystemContract;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class ContractCallSystemPrecompileTest extends AbstractContractCallServiceTest {

    @Test
    void exchangeRatePrecompileTinycentsToTinybarsTestEthCall() throws Exception {
        final var contract = testWeb3jService.deploy(ExchangeRatePrecompile::deploy);
        final var result =
                contract.call_tinycentsToTinybars(BigInteger.valueOf(100L)).send();
        final var functionCall = contract.send_tinycentsToTinybars(BigInteger.valueOf(100L), BigInteger.ZERO);
        assertThat(result).isEqualTo(BigInteger.valueOf(8L));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void exchangeRatePrecompileTinybarsToTinycentsTestEthCall() throws Exception {
        final var contract = testWeb3jService.deploy(ExchangeRatePrecompile::deploy);
        final var result =
                contract.call_tinybarsToTinycents(BigInteger.valueOf(100L)).send();
        final var functionCall = contract.send_tinybarsToTinycents(BigInteger.valueOf(100L), BigInteger.ZERO);
        assertThat(result).isEqualTo(BigInteger.valueOf(1200L));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void exchangeRatePrecompileTinycentsToTinybarsTestEthCallAndEstimateWithValueRevertExecution() {
        final var contract = testWeb3jService.deploy(ExchangeRatePrecompile::deploy);
        final var functionCall = contract.send_tinycentsToTinybars(BigInteger.valueOf(100L), BigInteger.valueOf(100L));
        String expectedErrorMessage = CONTRACT_REVERT_EXECUTED.name();
        assertThatThrownBy(functionCall::send)
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage(expectedErrorMessage);
        verifyEstimateGasRevertExecution(functionCall, expectedErrorMessage, MirrorEvmTransactionException.class);
    }

    @Test
    void exchangeRatePrecompileTinybarsToTinycentsTestEthCallAndEstimateWithValueRevertExecution() {
        final var contract = testWeb3jService.deploy(ExchangeRatePrecompile::deploy);
        final var functionCall = contract.send_tinybarsToTinycents(BigInteger.valueOf(100L), BigInteger.valueOf(100L));
        String expectedErrorMessage = CONTRACT_REVERT_EXECUTED.name();
        assertThatThrownBy(functionCall::send)
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage(expectedErrorMessage);
        verifyEstimateGasRevertExecution(functionCall, expectedErrorMessage, MirrorEvmTransactionException.class);
    }

    @Test
    void pseudoRandomGeneratorPrecompileFunctionsTestEthEstimateGas() {
        final var contract = testWeb3jService.deploy(PrngSystemContract::deploy);
        final var functionCall = contract.send_getPseudorandomSeed(BigInteger.ZERO);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void pseudoRandomGeneratorPrecompileFunctionsTestEthEstimateGasWithValueRevertExecution() {
        final var contract = testWeb3jService.deploy(PrngSystemContract::deploy);
        final var functionCall = contract.send_getPseudorandomSeed(BigInteger.valueOf(100));
        verifyEstimateGasRevertExecution(
                functionCall, CONTRACT_REVERT_EXECUTED.name(), MirrorEvmTransactionException.class);
    }

    @Test
    void pseudoRandomGeneratorPrecompileFunctionsTestEthCall() throws Exception {
        final var contract = testWeb3jService.deploy(PrngSystemContract::deploy);
        final var result = contract.call_getPseudorandomSeed().send();
        assertEquals(32, result.length, "The string should represent a 32-byte long array");
    }

    @Test
    void pseudoRandomGeneratorPrecompileFunctionsTestEthCallWithValueRevertExecution() {
        final var contract = testWeb3jService.deploy(PrngSystemContract::deploy);
        final var functionCall = contract.send_getPseudorandomSeed(BigInteger.valueOf(100));
        assertThatThrownBy(functionCall::send)
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage(CONTRACT_REVERT_EXECUTED.name());
    }
}
