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

import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.DEFAULT_TOKEN_EXPIRATION_PERIOD;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.DEFAULT_TOKEN_EXPIRATION_SECONDS;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.ECDSA_KEY;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.ED25519_KEY;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.ESTIMATE_GAS_ERROR_MESSAGE;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.TRANSACTION_GAS_LIMIT;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.isWithinExpectedGasRange;
import static com.hedera.mirror.web3.validation.HexValidator.HEX_PREFIX;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.service.model.CallServiceParameters.CallType;
import com.hedera.mirror.web3.service.model.ContractDebugParameters;
import com.hedera.mirror.web3.service.model.ContractExecutionParameters;
import com.hedera.mirror.web3.utils.ContractFunctionProviderRecord;
import com.hedera.mirror.web3.utils.ExpiryFactory;
import com.hedera.mirror.web3.utils.HederaTokenFactory;
import com.hedera.mirror.web3.utils.KeyValueFactory;
import com.hedera.mirror.web3.utils.TokenKeyFactory;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.mirror.web3.web3j.TestWeb3jService;
import com.hedera.mirror.web3.web3j.TestWeb3jService.Web3jTestConfiguration;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hedera.services.store.contracts.precompile.codec.KeyValueWrapper.KeyValueType;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.Key;
import jakarta.annotation.Resource;
import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;
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
public abstract class AbstractContractCallServiceTest extends Web3IntegrationTest {

    public static final String DEFAULT_TOKEN_NAME = "name";
    public static final String DEFAULT_TOKEN_SYMBOL = "symbol";
    public static final String DEFAULT_TOKEN_MEMO = "memo";

    @Resource
    protected TestWeb3jService testWeb3jService;

    @Resource
    protected MirrorNodeEvmProperties mirrorNodeEvmProperties;

    protected static ExpiryFactory expiryFactory;
    protected static KeyValueFactory keyValueFactory;
    protected static TokenKeyFactory tokenKeyFactory;
    protected static HederaTokenFactory hederaTokenFactory;

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
    final void setup() {
        domainBuilder.recordFile().persist();
        testWeb3jService.reset();
    }

    @AfterEach
    void cleanup() {
        testWeb3jService.reset();
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

    protected Entity persistAccountEntity() {
        return domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.ACCOUNT).deleted(false).balance(1_000_000_000_000L))
                .persist();
    }

    protected void persistAssociation(final Entity token, final Entity account) {
        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(token.getId())
                        .accountId(account.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .associated(true))
                .persist();
    }

    protected void persistAssociation(final Entity token, final Long accountId) {
        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(token.getId())
                        .accountId(accountId)
                        .freezeStatus(TokenFreezeStatusEnum.UNFROZEN)
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .associated(true))
                .persist();
    }

    protected String getAddressFromEntity(Entity entity) {
        return EntityIdUtils.asHexedEvmAddress(new Id(entity.getShard(), entity.getRealm(), entity.getNum()));
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

    protected <T> T getTokenExpiry(final Entity autoRenewAccountEntity) {
        return expiryFactory.getInstance(
                BigInteger.valueOf(DEFAULT_TOKEN_EXPIRATION_SECONDS),
                toAddress(autoRenewAccountEntity.toEntityId()).toHexString(),
                BigInteger.valueOf(DEFAULT_TOKEN_EXPIRATION_PERIOD));
    }

    protected <T> T getTokenExpiry(
            final long seconds, final String autoRenewAccountAddress, final long expirationPeriod) {
        return expiryFactory.getInstance(
                BigInteger.valueOf(seconds), autoRenewAccountAddress, BigInteger.valueOf(expirationPeriod));
    }

    protected <T> T getKeyValue(
            final Boolean inheritAccountKey,
            final String contractId,
            final byte[] ed25519,
            final byte[] ecdsaSecp256K1,
            final String delegatableContractId) {
        return keyValueFactory.getInstance(
                inheritAccountKey, contractId, ed25519, ecdsaSecp256K1, delegatableContractId);
    }

    protected <T> T getKeyValueForType(final KeyValueType keyValueType, String contractAddress) {
        return switch (keyValueType) {
            case INHERIT_ACCOUNT_KEY -> getKeyValue(
                    Boolean.TRUE, Address.ZERO.toHexString(), new byte[0], new byte[0], Address.ZERO.toHexString());
            case CONTRACT_ID -> getKeyValue(
                    Boolean.FALSE, contractAddress, new byte[0], new byte[0], Address.ZERO.toHexString());
            case ED25519 -> getKeyValue(
                    Boolean.FALSE, Address.ZERO.toHexString(), ED25519_KEY, new byte[0], Address.ZERO.toHexString());
            case ECDSA_SECPK256K1 -> getKeyValue(
                    Boolean.FALSE, Address.ZERO.toHexString(), new byte[0], ECDSA_KEY, Address.ZERO.toHexString());
            case DELEGATABLE_CONTRACT_ID -> getKeyValue(
                    Boolean.FALSE, Address.ZERO.toHexString(), new byte[0], new byte[0], contractAddress);
            default -> throw new RuntimeException("Unsupported key type: " + keyValueType.name());
        };
    }

    protected <T, U> T getTokenKey(final BigInteger keyType, final U keyValue) {
        return tokenKeyFactory.getInstance(keyType, keyValue);
    }

    protected <T, U, V> T getHederaToken(
            final String name,
            final String symbol,
            final String treasury,
            final String memo,
            final Boolean tokenSupplyType,
            final BigInteger maxSupply,
            final Boolean freezeDefault,
            final List<U> tokenKeys,
            final V expiry) {
        return hederaTokenFactory.getInstance(
                name, symbol, treasury, memo, tokenSupplyType, maxSupply, freezeDefault, tokenKeys, expiry);
    }

    protected <T, U> T getHederaToken(
            final TokenTypeEnum tokenType,
            final boolean withKeys,
            final boolean inheritAccountKey,
            final boolean freezeDefault,
            final Entity treasuryEntity) {
        final List<U> tokenKeys = new LinkedList<>();
        final var keyType = inheritAccountKey ? KeyValueType.INHERIT_ACCOUNT_KEY : KeyValueType.ECDSA_SECPK256K1;
        if (withKeys) {
            tokenKeys.add(getTokenKey(KeyType.KYC_KEY.getKeyTypeNumeric(), getKeyValueForType(keyType, null)));
            tokenKeys.add(getTokenKey(KeyType.FREEZE_KEY.getKeyTypeNumeric(), getKeyValueForType(keyType, null)));
        }

        if (tokenType == TokenTypeEnum.NON_FUNGIBLE_UNIQUE) {
            tokenKeys.add(getTokenKey(KeyType.SUPPLY_KEY.getKeyTypeNumeric(), getKeyValueForType(keyType, null)));
        }

        return getHederaToken(
                DEFAULT_TOKEN_NAME,
                DEFAULT_TOKEN_SYMBOL,
                toAddress(treasuryEntity.getId()).toHexString(),
                DEFAULT_TOKEN_MEMO,
                Boolean.TRUE, // finite amount
                BigInteger.valueOf(10L), // max supply
                freezeDefault, // freeze default
                tokenKeys,
                getTokenExpiry(domainBuilder.entity().persist()));
    }

    protected <T> T getHederaToken(
            final TokenTypeEnum tokenType, final Entity treasuryEntity, final Entity autoRenewAccountEntity) {
        return getHederaToken(
                DEFAULT_TOKEN_NAME,
                DEFAULT_TOKEN_SYMBOL,
                toAddress(treasuryEntity.getId()).toHexString(),
                DEFAULT_TOKEN_MEMO,
                tokenType == TokenTypeEnum.FUNGIBLE_COMMON ? Boolean.FALSE : Boolean.TRUE,
                BigInteger.valueOf(10L),
                Boolean.TRUE,
                List.of(),
                getTokenExpiry(autoRenewAccountEntity));
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
