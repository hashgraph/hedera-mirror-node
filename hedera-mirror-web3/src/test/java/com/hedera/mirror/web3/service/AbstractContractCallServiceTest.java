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

import static com.hedera.mirror.web3.utils.ContractCallTestUtil.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.service.model.CallServiceParameters.CallType;
import com.hedera.mirror.web3.service.model.ContractExecutionParameters;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.mirror.web3.web3j.TestWeb3jService;
import com.hedera.mirror.web3.web3j.TestWeb3jService.Web3jTestConfiguration;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.Key;
import jakarta.annotation.Resource;
import java.math.BigInteger;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.annotation.Import;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;

@Import(Web3jTestConfiguration.class)
@SuppressWarnings("unchecked")
abstract class AbstractContractCallServiceTest extends Web3IntegrationTest {

    @Resource
    protected TestWeb3jService testWeb3jService;

    public static Key getKeyWithDelegatableContractId(final Contract contract) {
        final var contractAddress = Address.fromHexString(contract.getContractAddress());

        return Key.newBuilder()
                .setDelegatableContractId(EntityIdUtils.contractIdFromEvmAddress(contractAddress))
                .build();
    }

    public static Key getKeyWithContractId(final Contract contract) {
        final var contractAddress = Address.fromHexString(contract.getContractAddress());

        return Key.newBuilder()
                .setContractID(EntityIdUtils.contractIdFromEvmAddress(contractAddress))
                .build();
    }

    protected void testEstimateGas(final RemoteFunctionCall<?> functionCall, final Contract contract) {
        // Given
        final var estimateGasUsedResult = longValueOf.applyAsLong(testWeb3jService.getEstimatedGas());

        // When
        final var actualGasUsed = gasUsedAfterExecution(getContractExecutionParameters(functionCall, contract));

        // Then
        assertThat(isWithinExpectedGasRange(estimateGasUsedResult, actualGasUsed))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimateGasUsedResult, actualGasUsed)
                .isTrue();
    }

    @BeforeEach
    void setup() {
        domainBuilder.recordFile().persist();
    }

    @AfterEach
    void cleanup() {
        testWeb3jService.setEstimateGas(false);
        testWeb3jService.setSender(Address.ZERO);
    }

    @SuppressWarnings("try")
    protected long gasUsedAfterExecution(final ContractExecutionParameters serviceParameters) {
        return ContractCallContext.run(ctx -> {
            ctx.initializeStackFrames(store.getStackedStateFrames());
            long result = processor
                    .execute(serviceParameters, serviceParameters.getGas())
                    .getGasUsed();

            assertThat(store.getStackedStateFrames().height()).isEqualTo(1);
            return result;
        });
    }

    protected void verifyEthCallAndEstimateGas(
            final RemoteFunctionCall<TransactionReceipt> functionCall,
            final Contract contract,
            final Optional<Entity> sender) {
        final var actualGasUsed = gasUsedAfterExecution(getContractExecutionParameters(functionCall, contract, sender));

        testWeb3jService.setEstimateGas(true);
        final AtomicLong estimateGasUsedResult = new AtomicLong();
        // Verify ethCall
        assertDoesNotThrow(
                () -> estimateGasUsedResult.set(functionCall.send().getGasUsed().longValue()));

        // Verify estimateGas
        assertThat(isWithinExpectedGasRange(estimateGasUsedResult.get(), actualGasUsed))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimateGasUsedResult.get(), actualGasUsed)
                .isTrue();
    }

    protected void verifyEthCallAndEstimateGas(
            final RemoteFunctionCall<TransactionReceipt> functionCall, final Contract contract) {
        final var actualGasUsed = gasUsedAfterExecution(getContractExecutionParameters(functionCall, contract));

        testWeb3jService.setEstimateGas(true);
        final AtomicLong estimateGasUsedResult = new AtomicLong();
        // Verify ethCall
        assertDoesNotThrow(
                () -> estimateGasUsedResult.set(functionCall.send().getGasUsed().longValue()));

        // Verify estimateGas
        assertThat(isWithinExpectedGasRange(estimateGasUsedResult.get(), actualGasUsed))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimateGasUsedResult.get(), actualGasUsed)
                .isTrue();
    }

    protected ContractExecutionParameters getContractExecutionParameters(
            final RemoteFunctionCall<TransactionReceipt> functionCall,
            final Contract contract,
            final Optional<Entity> sender,
            final long value) {
        Address address;
        final var senderEntity = sender.orElseGet(this::persistAccountEntity);
        final var evmAddress = senderEntity.getEvmAddress();
        if (new byte[20] == evmAddress) {
            address = Address.wrap(
                    Bytes.wrap(sender.orElseGet(this::persistAccountEntity).getEvmAddress()));
        } else {
            address = Address.fromHexString(getAddressFromEntity(senderEntity));
        }

        final var senderAccount = new HederaEvmAccount(address);

        return ContractExecutionParameters.builder()
                .block(BlockType.LATEST)
                .callData(Bytes.fromHexString(functionCall.encodeFunctionCall()))
                .callType(CallType.ETH_CALL)
                .gas(TRANSACTION_GAS_LIMIT)
                .isEstimate(false)
                .isStatic(false)
                .receiver(Address.fromHexString(contract.getContractAddress()))
                .sender(senderAccount)
                .value(value)
                .build();
    }

    protected Entity persistAccountEntity() {
        return domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.ACCOUNT).deleted(false).balance(1_000_000_000_000L))
                .persist();
    }

    protected ContractExecutionParameters getContractExecutionParameters(
            final RemoteFunctionCall<TransactionReceipt> functionCall,
            final Contract contract,
            final Optional<Entity> sender) {

        Address address;
        final var senderEntity = sender.orElseGet(this::persistAccountEntity);
        final var evmAddress = senderEntity.getEvmAddress();
        if (new byte[20] == evmAddress) {
            address = Address.wrap(
                    Bytes.wrap(sender.orElseGet(this::persistAccountEntity).getEvmAddress()));
        } else {
            address = Address.fromHexString(getAddressFromEntity(senderEntity));
        }

        final var senderAccount = new HederaEvmAccount(address);

        return ContractExecutionParameters.builder()
                .block(BlockType.LATEST)
                .callData(Bytes.fromHexString(functionCall.encodeFunctionCall()))
                .callType(CallType.ETH_CALL)
                .gas(TRANSACTION_GAS_LIMIT)
                .isEstimate(false)
                .isStatic(false)
                .receiver(Address.fromHexString(contract.getContractAddress()))
                .sender(senderAccount)
                .value(0L)
                .build();
    }

    protected ContractExecutionParameters getContractExecutionParameters(
            final RemoteFunctionCall<?> functionCall, final Contract contract) {

        final var senderAccount = new HederaEvmAccount(Address.wrap(Bytes.wrap(domainBuilder.evmAddress())));

        return ContractExecutionParameters.builder()
                .block(BlockType.LATEST)
                .callData(Bytes.fromHexString(functionCall.encodeFunctionCall()))
                .callType(CallType.ETH_CALL)
                .gas(TRANSACTION_GAS_LIMIT)
                .isEstimate(false)
                .isStatic(false)
                .receiver(Address.fromHexString(contract.getContractAddress()))
                .sender(senderAccount)
                .value(0L)
                .build();
    }

    protected void testEstimateGas(
            final RemoteFunctionCall<TransactionReceipt> functionCall,
            final Contract contract,
            final Optional<Entity> sender,
            final long value)
            throws Exception {
        testWeb3jService.setEstimateGas(true);

        functionCall.send();
        final var estimateGasUsedResult = longValueOf.applyAsLong(testWeb3jService.getTransactionResult());

        final var actualGasUsed =
                gasUsedAfterExecution(getContractExecutionParameters(functionCall, contract, sender, value));

        // Then
        assertThat(isWithinExpectedGasRange(estimateGasUsedResult, actualGasUsed))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimateGasUsedResult, actualGasUsed)
                .isTrue();
    }

    protected String getAddressFromEntity(Entity entity) {
        final var evmAddress = entity.getEvmAddress();
        if (new byte[20].equals(evmAddress)) {
            return EntityIdUtils.asHexedEvmAddress(new Id(entity.getShard(), entity.getRealm(), entity.getId()));
        } else {
            return Address.fromHexString(Bytes.wrap(entity.getEvmAddress()).toHexString())
                    .toHexString();
            //            address = Address.fromHexString(getAddressFromEntity(senderEntity));
        }

        //        return EntityIdUtils.asHexedEvmAddress(new Id(entity.getShard(), entity.getRealm(), entity.getId()));
    }

    protected String getAliasFromEntity(Entity entity) {
        final var evmAddress = entity.getEvmAddress();
        if (new byte[20].equals(evmAddress)) {
            return EntityIdUtils.asHexedEvmAddress(new Id(entity.getShard(), entity.getRealm(), entity.getId()));
        } else {
            return Address.fromHexString(Bytes.wrap(entity.getEvmAddress()).toHexString())
                    .toHexString();
        }
    }

    protected void testEstimateGas(
            final RemoteFunctionCall<TransactionReceipt> functionCall,
            final Contract contract,
            final Optional<Entity> sender)
            throws Exception {
        testWeb3jService.setEstimateGas(true);

        functionCall.send();
        final var estimateGasUsedResult = longValueOf.applyAsLong(testWeb3jService.getTransactionResult());

        final var actualGasUsed = gasUsedAfterExecution(getContractExecutionParameters(functionCall, contract, sender));

        // Then
        assertThat(isWithinExpectedGasRange(estimateGasUsedResult, actualGasUsed))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimateGasUsedResult, actualGasUsed)
                .isTrue();
    }

    public enum KeyType {
        ADMIN_KEY(1),
        KYC_KEY(2),
        FREEZE_KEY(4),
        WIPE_KEY(8),
        SUPPLY_KEY(16),
        FEE_SCHEDULE_KEY(32),
        PAUSE_KEY(64);
        final BigInteger keyTypeNumeric;

        KeyType(Integer keyTypeNumeric) {
            this.keyTypeNumeric = BigInteger.valueOf(keyTypeNumeric);
        }

        public BigInteger getKeyTypeNumeric() {
            return keyTypeNumeric;
        }
    }
}
