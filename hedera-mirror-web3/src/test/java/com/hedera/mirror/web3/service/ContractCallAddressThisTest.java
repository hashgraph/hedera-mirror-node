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

import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.entityIdFromEvmAddress;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.isWithinExpectedGasRange;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.longValueOf;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.hedera.mirror.web3.web3j.generated.TestAddressThis;
import com.hedera.mirror.web3.web3j.generated.TestNestedAddressThis;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.testcontainers.shaded.org.apache.commons.lang3.StringUtils;

class ContractCallAddressThisTest extends AbstractContractCallServiceTest {

    @Resource
    protected ContractExecutionService contractCallService;

    @SpyBean
    private ContractExecutionService contractExecutionService;

    @Test
    void deployAddressThisContract() {
        final var contract = testWeb3jService.deploy(TestAddressThis::deploy);
        final var serviceParameters = testWeb3jService.serviceParametersForTopLevelContractCreate(
                contract.getContractBinary(), ETH_ESTIMATE_GAS, Address.ZERO);
        final long actualGas = 57764L;
        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)), actualGas))
                .isTrue();
    }

    @Test
    void addressThisFromFunction() {
        final var contract = testWeb3jService.deploy(TestAddressThis::deploy);
        final var functionCall = contract.send_testAddressThisFunction();
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void addressThisEthCallWithoutEvmAlias() throws Exception {
        // Given
        final var contract = testWeb3jService.deployWithoutPersist(TestAddressThis::deploy);
        addressThisContractPersist(
                testWeb3jService.getContractRuntime(), Address.fromHexString(contract.getContractAddress()));
        final List<Bytes> capturedOutputs = new ArrayList<>();
        doAnswer(invocation -> {
                    HederaEvmTransactionProcessingResult result =
                            (HederaEvmTransactionProcessingResult) invocation.callRealMethod();
                    capturedOutputs.add(result.getOutput()); // Capture the result
                    return result;
                })
                .when(contractExecutionService)
                .callContract(any(), any());

        // When
        final var result = contract.call_getAddressThis().send();

        // Then
        final var successfulResponse = "0x" + StringUtils.leftPad(result.substring(2), 64, '0');
        assertThat(successfulResponse).isEqualTo(capturedOutputs.getFirst().toHexString());
    }

    @Test
    void deployNestedAddressThisContract() {
        final var contract = testWeb3jService.deploy(TestNestedAddressThis::deploy);
        final var serviceParamaters = testWeb3jService.serviceParametersForTopLevelContractCreate(
                contract.getContractBinary(), ETH_ESTIMATE_GAS, Address.ZERO);
        final long actualGas = 95401L;
        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractCallService.processCall(serviceParamaters)), actualGas))
                .isTrue();
    }

    private void addressThisContractPersist(byte[] runtimeBytecode, Address contractAddress) {
        final var addressThisContractEntityId = entityIdFromEvmAddress(contractAddress);
        final var addressThisEvmAddress = toEvmAddress(addressThisContractEntityId);
        domainBuilder
                .entity()
                .customize(e -> e.id(addressThisContractEntityId.getId())
                        .num(addressThisContractEntityId.getNum())
                        .evmAddress(addressThisEvmAddress)
                        .type(CONTRACT)
                        .balance(1500L))
                .persist();
        domainBuilder
                .contract()
                .customize(c -> c.id(addressThisContractEntityId.getId()).runtimeBytecode(runtimeBytecode))
                .persist();
        domainBuilder.recordFile().customize(f -> f.bytes(runtimeBytecode)).persist();
    }
}
