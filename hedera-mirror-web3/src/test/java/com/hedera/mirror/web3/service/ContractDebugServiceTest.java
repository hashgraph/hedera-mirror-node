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

package com.hedera.mirror.web3.service;

import static com.hedera.mirror.web3.utils.OpcodeTracerUtil.OPTIONS;
import static com.hedera.mirror.web3.utils.OpcodeTracerUtil.gasComparator;
import static com.hedera.mirror.web3.utils.OpcodeTracerUtil.toHumanReadableMessage;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.doAnswer;

import com.google.protobuf.ByteString;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.convert.BytesDecoder;
import com.hedera.mirror.web3.evm.contracts.execution.OpcodesProcessingResult;
import com.hedera.mirror.web3.service.model.ContractDebugParameters;
import com.hedera.mirror.web3.utils.ContractFunctionProviderEnum;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

@RequiredArgsConstructor
class ContractDebugServiceTest extends ContractCallTestSetup {

    private static final Long DEFAULT_CALL_VALUE = 0L;
    private final ContractDebugService contractDebugService;

    @Captor
    private ArgumentCaptor<ContractDebugParameters> paramsCaptor;

    @Captor
    private ArgumentCaptor<Long> gasCaptor;

    private HederaEvmTransactionProcessingResult resultCaptor;
    private ContractCallContext contextCaptor;

    @BeforeEach
    void setUpEntities() {
        // Obligatory data
        genesisBlockPersist();
        historicalBlocksPersist();
        historicalDataPersist();
        precompileContractPersist();
        // Account entities
        receiverPersist();
        notAssociatedSpenderEntityPersist();
    }

    @BeforeEach
    void setUpArgumentCaptors() {
        doAnswer(invocation -> {
            final var transactionProcessingResult =
                    (HederaEvmTransactionProcessingResult) invocation.callRealMethod();
            resultCaptor = transactionProcessingResult;
            contextCaptor = ContractCallContext.get();
            return transactionProcessingResult;
        })
                .when(processor)
                .execute(paramsCaptor.capture(), gasCaptor.capture());
    }

    @ParameterizedTest
    @EnumSource(NestedEthCallContractFunctionsNegativeCases.class)
    void failedNestedCallWithHardcodedResult(final ContractFunctionProviderEnum function) {
        nestedEthCallsContractPersist();
        final var params = serviceParametersForDebug(
                function,
                NESTED_CALLS_ABI_PATH,
                NESTED_ETH_CALLS_CONTRACT_ADDRESS,
                DEFAULT_CALL_VALUE,
                domainBuilder.timestamp());
        verifyOpcodeTracerCall(params, function);
    }

    private void verifyOpcodeTracerCall(
            final ContractDebugParameters params, final ContractFunctionProviderEnum function) {
        if (function.getExpectedErrorMessage() != null) {
            verifyThrowingOpcodeTracerCall(params, function);
        } else {
            verifySuccessfulOpcodeTracerCall(params, function);
        }
        assertThat(paramsCaptor.getValue()).isEqualTo(params);
        assertThat(gasCaptor.getValue()).isEqualTo(params.getGas());
    }

    @SneakyThrows
    private void verifyThrowingOpcodeTracerCall(
            final ContractDebugParameters params, final ContractFunctionProviderEnum function) {
        final var actual = contractDebugService.processOpcodeCall(params, OPTIONS);
        assertThat(actual.transactionProcessingResult().isSuccessful()).isFalse();
        assertThat(actual.transactionProcessingResult().getOutput()).isEqualTo(Bytes.EMPTY);
        assertThat(actual.transactionProcessingResult())
                .satisfiesAnyOf(
                        result -> assertThat(result.getRevertReason())
                                .isPresent()
                                .map(BytesDecoder::maybeDecodeSolidityErrorStringToReadableMessage)
                                .hasValue(function.getExpectedErrorMessage()),
                        result -> assertThat(result.getHaltReason())
                                .isPresent()
                                .map(ExceptionalHaltReason::getDescription)
                                .hasValue(function.getExpectedErrorMessage()));
        assertThat(actual.opcodes().size()).isNotZero();
        assertThat(toHumanReadableMessage(actual.opcodes().getLast().reason()))
                .isEqualTo(function.getExpectedErrorMessage());
    }

    private void verifySuccessfulOpcodeTracerCall(
            final ContractDebugParameters params, final ContractFunctionProviderEnum function) {
        final var actual = contractDebugService.processOpcodeCall(params, OPTIONS);
        final var expected = new OpcodesProcessingResult(resultCaptor, contextCaptor.getOpcodes());

        if (function.getExpectedResultFields() != null) {
            assertThat(actual.transactionProcessingResult().getOutput().toHexString())
                    .isEqualTo(functionEncodeDecoder.encodedResultFor(
                            function.getName(), NESTED_CALLS_ABI_PATH, function.getExpectedResultFields()));
        }

        // Compare transaction processing result
        assertThat(actual.transactionProcessingResult())
                .usingRecursiveComparison()
                .ignoringFields("logs")
                .isEqualTo(expected.transactionProcessingResult());
        // Compare opcodes with gas tolerance
        assertThat(actual.opcodes())
                .usingRecursiveComparison()
                .withComparatorForFields(gasComparator(), "gas")
                .isEqualTo(expected.opcodes());
    }

    @Getter
    @RequiredArgsConstructor
    private enum NestedEthCallContractFunctionsNegativeCases implements ContractFunctionProviderEnum {
        GET_TOKEN_INFO_HISTORICAL(
                "nestedGetTokenInfoAndHardcodedResult",
                new Object[]{NFT_ADDRESS_HISTORICAL},
                new Object[]{"hardcodedResult"},
                BlockType.of(String.valueOf(EVM_V_34_BLOCK - 1))),
        GET_TOKEN_INFO(
                "nestedGetTokenInfoAndHardcodedResult",
                new Object[]{Address.ZERO},
                new Object[]{"hardcodedResult"},
                BlockType.LATEST),
        HTS_GET_APPROVED_HISTORICAL(
                "nestedHtsGetApprovedAndHardcodedResult",
                new Object[]{NFT_ADDRESS_HISTORICAL, 1L},
                new Object[]{"hardcodedResult"},
                BlockType.of(String.valueOf(EVM_V_34_BLOCK - 1))),
        HTS_GET_APPROVED(
                "nestedHtsGetApprovedAndHardcodedResult",
                new Object[]{Address.ZERO, 1L},
                new Object[]{"hardcodedResult"},
                BlockType.LATEST),
        MINT_TOKEN_HISTORICAL(
                "nestedMintTokenAndHardcodedResult",
                new Object[]{
                        NFT_ADDRESS_HISTORICAL,
                        0L,
                        new byte[][]{ByteString.copyFromUtf8("firstMeta").toByteArray()}
                },
                new Object[]{"hardcodedResult"},
                BlockType.of(String.valueOf(EVM_V_34_BLOCK - 1))),
        MINT_TOKEN(
                "nestedMintTokenAndHardcodedResult",
                new Object[]{
                        Address.ZERO,
                        0L,
                        new byte[][]{ByteString.copyFromUtf8("firstMeta").toByteArray()}
                },
                new Object[]{"hardcodedResult"},
                BlockType.LATEST);

        private final String name;
        private final Object[] functionParameters;
        private final Object[] expectedResultFields;
        private final BlockType block;
    }
}
