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
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.service.model.ContractExecutionParameters;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ContractCallAddressThisTest extends ContractCallTestSetup {

    @BeforeEach
    void beforeAll() {
        addressThisContractPersist();
    }

    @Test
    void deployAddressThisContract() {
        final var serviceParameters = serviceParametersForAddressThis(
                Bytes.wrap(functionEncodeDecoder.getContractBytes(ADDRESS_THIS_CONTRACT_INIT_BYTES_PATH)));
        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();
    }

    @Test
    void addressThisFromFunction() {
        final var functionHash =
                functionEncodeDecoder.functionHashFor("testAddressThis", ADDRESS_THIS_CONTRACT_ABI_PATH);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, ADDRESS_THIS_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 0L, BlockType.LATEST);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();
    }

    @Test
    void addressThisEthCallWithoutEvmAlias() {
        String addressThisContractAddressWithout0x =
                ADDRESS_THIS_CONTRACT_ADDRESS.toString().substring(2);
        String successfulResponse = "0x000000000000000000000000" + addressThisContractAddressWithout0x;
        final var functionHash =
                functionEncodeDecoder.functionHashFor("getAddressThis", ADDRESS_THIS_CONTRACT_ABI_PATH);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, ADDRESS_THIS_CONTRACT_ADDRESS, ETH_CALL, 0L, BlockType.LATEST);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
    }

    @Test
    void deployNestedAddressThisContract() {
        final var serviceParameters = serviceParametersForAddressThis(
                Bytes.wrap(functionEncodeDecoder.getContractBytes(NESTED_ADDRESS_THIS_CONTRACT_BYTES_PATH)));
        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();
    }

    private ContractExecutionParameters serviceParametersForAddressThis(final Bytes callData) {
        final var sender = new HederaEvmAccount(SENDER_ADDRESS);
        return ContractExecutionParameters.builder()
                .sender(sender)
                .value(0L)
                .receiver(Address.ZERO)
                .callData(callData)
                .callType(ETH_ESTIMATE_GAS)
                .block(BlockType.LATEST)
                .gas(15_000_000L)
                .isStatic(false)
                .isEstimate(true)
                .build();
    }

    private EntityId addressThisContractPersist() {
        final var addressThisContractBytes = functionEncodeDecoder.getContractBytes(ADDRESS_THIS_CONTRACT_BYTES_PATH);
        final var addressThisContractEntityId = entityIdFromEvmAddress(ADDRESS_THIS_CONTRACT_ADDRESS);
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
                .customize(c -> c.id(addressThisContractEntityId.getId()).runtimeBytecode(addressThisContractBytes))
                .persist();

        domainBuilder
                .contractState()
                .customize(c -> c.contractId(addressThisContractEntityId.getId())
                        .slot(Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000000")
                                .toArrayUnsafe())
                        .value(Bytes.fromHexString("0x4746573740000000000000000000000000000000000000000000000000000000")
                                .toArrayUnsafe()))
                .persist();

        domainBuilder
                .recordFile()
                .customize(f -> f.bytes(addressThisContractBytes))
                .persist();
        return addressThisContractEntityId;
    }
}
