/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.web3.evm.pricing.RatesAndFeesLoader.DEFAULT_FEE_SCHEDULE;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.ESTIMATE_GAS_ERROR_MESSAGE;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.TRANSACTION_GAS_LIMIT;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.isWithinExpectedGasRange;
import static com.hedera.mirror.web3.validation.HexValidator.HEX_PREFIX;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.balance.TokenBalance;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.utils.EvmTokenUtils;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.service.model.CallServiceParameters.CallType;
import com.hedera.mirror.web3.service.model.ContractDebugParameters;
import com.hedera.mirror.web3.service.model.ContractExecutionParameters;
import com.hedera.mirror.web3.utils.ContractFunctionProviderRecord;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.mirror.web3.web3j.TestWeb3jService;
import com.hedera.mirror.web3.web3j.TestWeb3jService.Web3jTestConfiguration;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.swirlds.state.State;
import jakarta.annotation.Resource;
import java.math.BigInteger;
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
public abstract class AbstractContractCallServiceTest extends Web3IntegrationTest {

    protected static final String TREASURY_ADDRESS = EvmTokenUtils.toAddress(2).toHexString();
    protected static final byte[] EXCHANGE_RATES_SET = ExchangeRateSet.newBuilder()
            .setCurrentRate(ExchangeRate.newBuilder()
                    .setCentEquiv(12)
                    .setHbarEquiv(1)
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(4102444800L))
                    .build())
            .setNextRate(ExchangeRate.newBuilder()
                    .setCentEquiv(15)
                    .setHbarEquiv(1)
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(4102444800L))
                    .build())
            .build()
            .toByteArray();

    @Resource
    protected TestWeb3jService testWeb3jService;

    @Resource
    protected MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @Resource
    protected State state;

    @Resource
    protected ContractExecutionService contractExecutionService;

    protected RecordFile genesisRecordFile;
    protected Entity treasuryEntity;

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

    @BeforeEach
    protected void setup() {
        // Change this to not be epoch once services fixes config updates for non-genesis flow
        genesisRecordFile = domainBuilder
                .recordFile()
                .customize(f -> f.consensusEnd(0L).consensusStart(0L).index(0L))
                .persist();
        treasuryEntity = domainBuilder
                .entity()
                .customize(e -> e.id(2L).num(2L).balance(5000000000000000000L))
                .persist();
        domainBuilder.entity().customize(e -> e.id(98L).num(98L)).persist();
        domainBuilder
                .fileData()
                .customize(f -> f.entityId(EntityId.of(112L)).fileData(EXCHANGE_RATES_SET))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(
                                treasuryEntity.getCreatedTimestamp(), treasuryEntity.toEntityId()))
                        .balance(treasuryEntity.getBalance()))
                .persist();
        domainBuilder
                .fileData()
                .customize(f -> f.entityId(EntityId.of(111L)).fileData(DEFAULT_FEE_SCHEDULE.toByteArray()))
                .persist();
        testWeb3jService.reset();
    }

    @AfterEach
    void cleanup() {
        testWeb3jService.reset();
    }

    protected long gasUsedAfterExecution(final ContractExecutionParameters serviceParameters) {
        try {
            return contractExecutionService.callContract(serviceParameters).getGasUsed();
        } catch (MirrorEvmTransactionException e) {
            var result = e.getResult();

            // Some tests expect to fail but still want to capture the gas used
            if (result != null) {
                return result.getGasUsed();
            }

            throw e;
        }
    }

    protected void verifyEthCallAndEstimateGas(
            final RemoteFunctionCall<TransactionReceipt> functionCall, final Contract contract) {
        final var actualGasUsed = gasUsedAfterExecution(getContractExecutionParameters(functionCall, contract));

        testWeb3jService.setEstimateGas(true);
        final AtomicLong estimateGasUsedResult = new AtomicLong();
        // Verify eth_call
        assertDoesNotThrow(
                () -> estimateGasUsedResult.set(functionCall.send().getGasUsed().longValue()));

        // Verify eth_estimateGas
        assertThat(isWithinExpectedGasRange(estimateGasUsedResult.get(), actualGasUsed))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimateGasUsedResult.get(), actualGasUsed)
                .isTrue();
    }

    protected <T extends Exception> void verifyEstimateGasRevertExecution(
            final RemoteFunctionCall<TransactionReceipt> functionCall,
            final String exceptionMessage,
            Class<T> exceptionClass) {

        testWeb3jService.setEstimateGas(true);
        // Verify estimate reverts with proper message
        assertThatThrownBy(functionCall::send).isInstanceOf(exceptionClass).hasMessage(exceptionMessage);
    }

    protected void verifyEthCallAndEstimateGasWithValue(
            final RemoteFunctionCall<TransactionReceipt> functionCall,
            final Contract contract,
            final Address payerAddress,
            final long value) {
        final var actualGasUsed =
                gasUsedAfterExecution(getContractExecutionParameters(functionCall, contract, payerAddress, value));

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
            final RemoteFunctionCall<?> functionCall, final Contract contract) {
        return getContractExecutionParameters(functionCall, contract, Address.ZERO, 0L);
    }

    protected ContractExecutionParameters getContractExecutionParameters(
            final Bytes data, final Address receiver, final Address payerAddress, final long value) {
        return ContractExecutionParameters.builder()
                .block(BlockType.LATEST)
                .callData(data)
                .callType(CallType.ETH_CALL)
                .gas(TRANSACTION_GAS_LIMIT)
                .isEstimate(false)
                .isStatic(false)
                .receiver(receiver)
                .sender(new HederaEvmAccount(payerAddress))
                .value(value)
                .build();
    }

    protected ContractExecutionParameters getContractExecutionParameters(
            final RemoteFunctionCall<?> functionCall,
            final Contract contract,
            final Address payerAddress,
            final long value) {
        return getContractExecutionParameters(
                Bytes.fromHexString(functionCall.encodeFunctionCall()),
                Address.fromHexString(contract.getContractAddress()),
                payerAddress,
                value);
    }

    protected Entity tokenEntityPersist() {
        return domainBuilder.entity().customize(e -> e.type(EntityType.TOKEN)).persist();
    }

    protected Entity accountEntityPersist() {
        return domainBuilder
                .entity()
                .customize(e ->
                        e.type(EntityType.ACCOUNT).evmAddress(null).alias(null).balance(100_000_000_000_000_000L))
                .persist();
    }

    protected Entity accountEntityWithEvmAddressPersist() {
        return domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.ACCOUNT).balance(1_000_000_000_000L))
                .persist();
    }

    protected void tokenAccountPersist(final Entity token, final Entity account) {
        tokenAccountPersist(token, account.toEntityId().getId());
    }

    protected void tokenAccountPersist(final Entity token, final Long accountId) {
        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(token.getId())
                        .accountId(accountId)
                        .freezeStatus(TokenFreezeStatusEnum.UNFROZEN)
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .associated(true))
                .persist();
    }

    protected void persistAccountBalance(Entity account, long balance, long timestamp) {
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(timestamp, account.toEntityId()))
                        .balance(balance))
                .persist();
    }

    protected void persistAccountBalance(Entity account, long balance) {
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(account.getCreatedTimestamp(), account.toEntityId()))
                        .balance(balance))
                .persist();
    }

    protected void persistTokenBalance(Entity account, Entity token, long timestamp) {
        domainBuilder
                .tokenBalance()
                .customize(ab -> ab.id(new TokenBalance.Id(timestamp, account.toEntityId(), token.toEntityId()))
                        .balance(100))
                .persist();
    }

    protected String getAddressFromEntity(Entity entity) {
        return EvmTokenUtils.toAddress(entity.toEntityId()).toHexString();
    }

    protected String getAliasFromEntity(Entity entity) {
        return Bytes.wrap(entity.getEvmAddress()).toHexString();
    }

    protected ContractDebugParameters getDebugParameters(
            final ContractFunctionProviderRecord functionProvider, final Bytes callDataBytes) {
        return ContractDebugParameters.builder()
                .block(functionProvider.block())
                .callData(callDataBytes)
                .consensusTimestamp(domainBuilder.timestamp())
                .gas(TRANSACTION_GAS_LIMIT)
                .receiver(functionProvider.contractAddress())
                .sender(new HederaEvmAccount(functionProvider.sender()))
                .value(functionProvider.value())
                .build();
    }

    protected ContractFunctionProviderRecord getContractFunctionProviderWithSender(
            final String contract, final Entity sender) {
        final var contractAddress = Address.fromHexString(contract);
        final var senderAddress = Address.fromHexString(getAliasFromEntity(sender));
        return ContractFunctionProviderRecord.builder()
                .contractAddress(contractAddress)
                .sender(senderAddress)
                .build();
    }

    protected String getAddressFromEntityId(final EntityId entity) {
        return HEX_PREFIX
                + EntityIdUtils.asHexedEvmAddress(new Id(entity.getShard(), entity.getRealm(), entity.getNum()));
    }

    protected String getAddressFromEvmAddress(final byte[] evmAddress) {
        return Address.wrap(Bytes.wrap(evmAddress)).toHexString();
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
