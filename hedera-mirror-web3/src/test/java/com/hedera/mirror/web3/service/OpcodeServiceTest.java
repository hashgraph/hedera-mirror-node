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

import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;
import static com.hedera.mirror.common.domain.transaction.TransactionType.ETHEREUMTRANSACTION;
import static com.hedera.mirror.common.util.CommonUtils.instant;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.entityIdFromEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.NEW_ECDSA_KEY;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.NEW_ED25519_KEY;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.TRANSACTION_GAS_LIMIT;
import static com.hedera.mirror.web3.validation.HexValidator.HEX_PREFIX;
import static com.hedera.services.stream.proto.ContractAction.ResultDataCase.OUTPUT;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.web3.common.TransactionHashParameter;
import com.hedera.mirror.web3.common.TransactionIdOrHashParameter;
import com.hedera.mirror.web3.common.TransactionIdParameter;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.OpcodeTracerOptions;
import com.hedera.mirror.web3.exception.EntityNotFoundException;
import com.hedera.mirror.web3.web3j.generated.DynamicEthCalls;
import com.hedera.mirror.web3.web3j.generated.ExchangeRatePrecompile;
import com.hedera.mirror.web3.web3j.generated.NestedCalls;
import com.hedera.mirror.web3.web3j.generated.NestedCalls.HederaToken;
import com.hedera.mirror.web3.web3j.generated.NestedCalls.KeyValue;
import com.hedera.mirror.web3.web3j.generated.NestedCalls.TokenKey;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.KeyValueWrapper.KeyValueType;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.web3j.abi.TypeEncoder;
import org.web3j.tx.Contract;

@RequiredArgsConstructor
class OpcodeServiceTest extends AbstractContractCallServiceOpcodeTracerTest {

    private static final long ZERO_AMOUNT = 0L;
    private static final long DEFAULT_TRANSACTION_VALUE = 100_000_000_000L;
    private static final String SUCCESS_PREFIX = "0x0000000000000000000000000000000000000000000000000000000000000020";

    private final OpcodeService opcodeService;
    private final EvmEncodingFacade evmEncoder;

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                            CONTRACT_ID,                ADMIN_KEY,
                            CONTRACT_ID,                KYC_KEY,
                            CONTRACT_ID,                FREEZE_KEY,
                            CONTRACT_ID,                WIPE_KEY,
                            CONTRACT_ID,                SUPPLY_KEY,
                            CONTRACT_ID,                FEE_SCHEDULE_KEY,
                            CONTRACT_ID,                PAUSE_KEY,
                            ED25519,                    ADMIN_KEY,
                            ED25519,                    KYC_KEY,
                            ED25519,                    FREEZE_KEY,
                            ED25519,                    WIPE_KEY,
                            ED25519,                    SUPPLY_KEY,
                            ED25519,                    FEE_SCHEDULE_KEY,
                            ED25519,                    PAUSE_KEY,
                            ECDSA_SECPK256K1,           ADMIN_KEY,
                            ECDSA_SECPK256K1,           KYC_KEY,
                            ECDSA_SECPK256K1,           FREEZE_KEY,
                            ECDSA_SECPK256K1,           WIPE_KEY,
                            ECDSA_SECPK256K1,           SUPPLY_KEY,
                            ECDSA_SECPK256K1,           FEE_SCHEDULE_KEY,
                            ECDSA_SECPK256K1,           PAUSE_KEY,
                            DELEGATABLE_CONTRACT_ID,    ADMIN_KEY,
                            DELEGATABLE_CONTRACT_ID,    KYC_KEY,
                            DELEGATABLE_CONTRACT_ID,    FREEZE_KEY,
                            DELEGATABLE_CONTRACT_ID,    WIPE_KEY,
                            DELEGATABLE_CONTRACT_ID,    SUPPLY_KEY,
                            DELEGATABLE_CONTRACT_ID,    FEE_SCHEDULE_KEY,
                            DELEGATABLE_CONTRACT_ID,    PAUSE_KEY,
                            """)
    void updateTokenKeysAndGetUpdatedTokenKeyForFungibleToken(final KeyValueType keyValueType, final KeyType keyType) {
        // Given
        final var tokenEntity = fungibleTokenPersistWithTreasuryAccount(
                domainBuilder.entity().persist().toEntityId());
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);
        final var contractAddress = contract.getContractAddress();

        final var keyValue = getKeyValueForType(keyValueType, contractAddress);
        final var tokenKey = new TokenKey(keyType.getKeyTypeNumeric(), keyValue);
        final var expectedResult = TypeEncoder.encode(keyValue);
        final var expectedResultBytes = Bytes.fromHexString(expectedResult).toArray();

        final var functionCall = contract.call_updateTokenKeysAndGetUpdatedTokenKey(
                tokenAddress.toHexString(), List.of(tokenKey), keyType.getKeyTypeNumeric());
        final var callData =
                Bytes.fromHexString(functionCall.encodeFunctionCall()).toArray();

        final var options = new OpcodeTracerOptions();
        final var transactionIdOrHash =
                setUpEthereumTransactionWithSenderBalance(contract, callData, ZERO_AMOUNT, expectedResultBytes);

        // When
        final var opcodesResponse = opcodeService.processOpcodeCall(transactionIdOrHash, options);

        // Then
        verifyOpcodesResponseWithExpectedReturnValue(opcodesResponse, options, SUCCESS_PREFIX + expectedResult);
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                            CONTRACT_ID,                ADMIN_KEY,
                            CONTRACT_ID,                KYC_KEY,
                            CONTRACT_ID,                FREEZE_KEY,
                            CONTRACT_ID,                WIPE_KEY,
                            CONTRACT_ID,                SUPPLY_KEY,
                            CONTRACT_ID,                FEE_SCHEDULE_KEY,
                            CONTRACT_ID,                PAUSE_KEY,
                            ED25519,                    ADMIN_KEY,
                            ED25519,                    KYC_KEY,
                            ED25519,                    FREEZE_KEY,
                            ED25519,                    WIPE_KEY,
                            ED25519,                    SUPPLY_KEY,
                            ED25519,                    FEE_SCHEDULE_KEY,
                            ED25519,                    PAUSE_KEY,
                            ECDSA_SECPK256K1,           ADMIN_KEY,
                            ECDSA_SECPK256K1,           KYC_KEY,
                            ECDSA_SECPK256K1,           FREEZE_KEY,
                            ECDSA_SECPK256K1,           WIPE_KEY,
                            ECDSA_SECPK256K1,           SUPPLY_KEY,
                            ECDSA_SECPK256K1,           FEE_SCHEDULE_KEY,
                            ECDSA_SECPK256K1,           PAUSE_KEY,
                            DELEGATABLE_CONTRACT_ID,    ADMIN_KEY,
                            DELEGATABLE_CONTRACT_ID,    KYC_KEY,
                            DELEGATABLE_CONTRACT_ID,    FREEZE_KEY,
                            DELEGATABLE_CONTRACT_ID,    WIPE_KEY,
                            DELEGATABLE_CONTRACT_ID,    SUPPLY_KEY,
                            DELEGATABLE_CONTRACT_ID,    FEE_SCHEDULE_KEY,
                            DELEGATABLE_CONTRACT_ID,    PAUSE_KEY
                            """)
    void updateTokenKeysAndGetUpdatedTokenKeyForNFT(final KeyValueType keyValueType, final KeyType keyType) {
        // Given
        final var treasuryEntity = accountEntityPersist();
        final var tokenEntity = nftPersist(treasuryEntity);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);
        final var contractAddress = contract.getContractAddress();

        final var keyValue = getKeyValueForType(keyValueType, contractAddress);
        final var tokenKey = new TokenKey(keyType.getKeyTypeNumeric(), keyValue);
        final var expectedResult = TypeEncoder.encode(keyValue);
        final var expectedResultBytes = Bytes.fromHexString(expectedResult).toArray();

        final var functionCall = contract.call_updateTokenKeysAndGetUpdatedTokenKey(
                tokenAddress.toHexString(), List.of(tokenKey), keyType.getKeyTypeNumeric());
        final var callData =
                Bytes.fromHexString(functionCall.encodeFunctionCall()).toArray();

        final var options = new OpcodeTracerOptions();
        final var transactionIdOrHash =
                setUpEthereumTransactionWithSenderBalance(contract, callData, ZERO_AMOUNT, expectedResultBytes);

        // When
        final var opcodesResponse = opcodeService.processOpcodeCall(transactionIdOrHash, options);

        // Then
        verifyOpcodesResponseWithExpectedReturnValue(opcodesResponse, options, SUCCESS_PREFIX + expectedResult);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            FUNGIBLE_COMMON
            NON_FUNGIBLE_UNIQUE
            """)
    void updateTokenExpiryAndGetUpdatedTokenExpiry(final TokenTypeEnum tokenType) {
        // Given
        final var treasuryEntity = accountEntityPersist();
        final var tokenWithAutoRenewPair = persistTokenWithAutoRenewAndTreasuryAccounts(tokenType, treasuryEntity);
        final var tokenEntityId = tokenWithAutoRenewPair.getLeft();
        final var tokenAddress = toAddress(tokenEntityId.getId());
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);
        final var tokenExpiry = new NestedCalls.Expiry(
                BigInteger.valueOf(Instant.now().getEpochSecond() + 8_000_000L),
                toAddress(tokenWithAutoRenewPair.getRight().toEntityId()).toHexString(),
                BigInteger.valueOf(8_000_000));

        final var functionCall =
                contract.call_updateTokenExpiryAndGetUpdatedTokenExpiry(tokenAddress.toHexString(), tokenExpiry);
        final var callData =
                Bytes.fromHexString(functionCall.encodeFunctionCall()).toArray();
        final var expectedResult = TypeEncoder.encode(tokenExpiry);
        final var expectedResultBytes = Bytes.fromHexString(expectedResult).toArray();

        final var transactionIdOrHash =
                setUpEthereumTransactionWithSenderBalance(contract, callData, ZERO_AMOUNT, expectedResultBytes);

        final OpcodeTracerOptions options = new OpcodeTracerOptions();

        // When
        final var opcodesResponse = opcodeService.processOpcodeCall(transactionIdOrHash, options);

        // Then
        verifyOpcodesResponseWithExpectedReturnValue(opcodesResponse, options, HEX_PREFIX + expectedResult);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            FUNGIBLE_COMMON
            NON_FUNGIBLE_UNIQUE
            """)
    void updateTokenInfoAndGetUpdatedTokenInfoSymbol(final TokenTypeEnum tokenType) {
        // Given
        final var treasuryEntity = accountEntityPersist();
        Pair<Entity, Entity> tokenWithAutoRenewPair =
                persistTokenWithAutoRenewAndTreasuryAccounts(tokenType, treasuryEntity);
        final var tokenEntityId = tokenWithAutoRenewPair.getLeft();
        final var tokenAddress = toAddress(tokenEntityId.getId());
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);
        final var tokenInfo = populateHederaToken(
                contract.getContractAddress(),
                tokenType,
                treasuryEntity.toEntityId(),
                tokenWithAutoRenewPair.getRight());

        final var functionCall =
                contract.call_updateTokenInfoAndGetUpdatedTokenInfoSymbol(tokenAddress.toHexString(), tokenInfo);
        final var callData =
                Bytes.fromHexString(functionCall.encodeFunctionCall()).toArray();
        final var expectedResultBytes = tokenInfo.symbol.getBytes();
        final var expectedResult = evmEncoder.encodeSymbol(tokenInfo.symbol).toHexString();

        final var transactionIdOrHash =
                setUpEthereumTransactionWithSenderBalance(contract, callData, ZERO_AMOUNT, expectedResultBytes);
        final OpcodeTracerOptions options = new OpcodeTracerOptions();

        // When
        final var opcodesResponse = opcodeService.processOpcodeCall(transactionIdOrHash, options);
        // Then
        verifyOpcodesResponseWithExpectedReturnValue(opcodesResponse, options, expectedResult);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            FUNGIBLE_COMMON
            NON_FUNGIBLE_UNIQUE
            """)
    void updateTokenInfoAndGetUpdatedTokenInfoName(final TokenTypeEnum tokenType) {
        // Given
        final var treasuryEntity = accountEntityPersist();
        Pair<Entity, Entity> tokenWithAutoRenewPair =
                persistTokenWithAutoRenewAndTreasuryAccounts(tokenType, treasuryEntity);
        final var tokenEntityId = tokenWithAutoRenewPair.getLeft();
        final var tokenAddress = toAddress(tokenEntityId.getId());
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);
        final var tokenInfo = populateHederaToken(
                contract.getContractAddress(),
                tokenType,
                treasuryEntity.toEntityId(),
                tokenWithAutoRenewPair.getRight());

        final var functionCall =
                contract.call_updateTokenInfoAndGetUpdatedTokenInfoName(tokenAddress.toHexString(), tokenInfo);
        final var callData =
                Bytes.fromHexString(functionCall.encodeFunctionCall()).toArray();
        final var expectedResultBytes = tokenInfo.name.getBytes();
        final var expectedResult = evmEncoder.encodeName(tokenInfo.name).toHexString();

        final var transactionIdOrHash =
                setUpEthereumTransactionWithSenderBalance(contract, callData, ZERO_AMOUNT, expectedResultBytes);

        final OpcodeTracerOptions options = new OpcodeTracerOptions();

        // When
        final var opcodesResponse = opcodeService.processOpcodeCall(transactionIdOrHash, options);

        // Then
        verifyOpcodesResponseWithExpectedReturnValue(opcodesResponse, options, expectedResult);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            FUNGIBLE_COMMON
            NON_FUNGIBLE_UNIQUE
            """)
    void updateTokenInfoAndGetUpdatedTokenInfoMemo(final TokenTypeEnum tokenType) {
        // Given
        final var treasuryEntity = accountEntityPersist();
        Pair<Entity, Entity> tokenWithAutoRenewPair =
                persistTokenWithAutoRenewAndTreasuryAccounts(tokenType, treasuryEntity);
        final var tokenEntityId = tokenWithAutoRenewPair.getLeft();
        final var tokenAddress = toAddress(tokenEntityId.getId());
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);
        final var tokenInfo = populateHederaToken(
                contract.getContractAddress(),
                tokenType,
                treasuryEntity.toEntityId(),
                tokenWithAutoRenewPair.getRight());

        final var functionCall =
                contract.call_updateTokenInfoAndGetUpdatedTokenInfoMemo(tokenAddress.toHexString(), tokenInfo);
        final var callData =
                Bytes.fromHexString(functionCall.encodeFunctionCall()).toArray();
        final var expectedResultBytes = tokenInfo.memo.getBytes();
        final var expectedResult = evmEncoder.encodeName(tokenInfo.memo).toHexString();

        final var transactionIdOrHash =
                setUpEthereumTransactionWithSenderBalance(contract, callData, ZERO_AMOUNT, expectedResultBytes);

        final OpcodeTracerOptions options = new OpcodeTracerOptions();

        // When
        final var opcodesResponse = opcodeService.processOpcodeCall(transactionIdOrHash, options);

        // Then
        verifyOpcodesResponseWithExpectedReturnValue(opcodesResponse, options, expectedResult);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            FUNGIBLE_COMMON
            NON_FUNGIBLE_UNIQUE
            """)
    void deleteTokenAndGetTokenInfoIsDeleted(final TokenTypeEnum tokenType) {
        // Given
        final var treasuryEntity = accountEntityPersist();
        final var tokenEntityId = persistTokenWithAutoRenewAndTreasuryAccounts(tokenType, treasuryEntity)
                .getLeft();
        final var tokenAddress = toAddress(tokenEntityId.getId());
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);

        final var functionCall = contract.call_deleteTokenAndGetTokenInfoIsDeleted(tokenAddress.toHexString());
        final var callData =
                Bytes.fromHexString(functionCall.encodeFunctionCall()).toArray();
        final byte[] expectedResultBytes = {1};
        final var expectedResult = DomainUtils.bytesToHex(DomainUtils.leftPadBytes(expectedResultBytes, Bytes32.SIZE));

        final var transactionIdOrHash =
                setUpEthereumTransactionWithSenderBalance(contract, callData, ZERO_AMOUNT, expectedResultBytes);

        final OpcodeTracerOptions options = new OpcodeTracerOptions();

        // When
        final var opcodesResponse = opcodeService.processOpcodeCall(transactionIdOrHash, options);

        // Then
        verifyOpcodesResponseWithExpectedReturnValue(opcodesResponse, options, HEX_PREFIX + expectedResult);
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                            true, false, true, true
                            false, false, false, false
                            true, true, true, true
                            """)
    void createFungibleTokenAndGetIsTokenAndGetDefaultFreezeStatusAndGetDefaultKycStatus(
            final boolean withKeys,
            final boolean inheritKey,
            boolean defaultKycStatus,
            final boolean defaultFreezeStatus) {
        // Given
        defaultKycStatus = calculateDefaultKycStatus(defaultKycStatus);
        final var treasuryEntity = accountEntityPersist();
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);
        final var tokenInfo = getHederaToken(
                contract.getContractAddress(),
                TokenTypeEnum.FUNGIBLE_COMMON,
                withKeys,
                inheritKey,
                defaultFreezeStatus,
                treasuryEntity);

        final var functionCall =
                contract.call_createFungibleTokenAndGetIsTokenAndGetDefaultFreezeStatusAndGetDefaultKycStatus(
                        tokenInfo, BigInteger.ONE, BigInteger.ONE);
        final var callData =
                Bytes.fromHexString(functionCall.encodeFunctionCall()).toArray();
        final var expectedResult = getExpectedResultFromBooleans(defaultKycStatus, defaultFreezeStatus, true);
        final var expectedResultBytes = expectedResult.getBytes();

        final var transactionIdOrHash = setUpEthereumTransactionWithSenderBalance(
                contract, callData, DEFAULT_TRANSACTION_VALUE, expectedResultBytes);
        final OpcodeTracerOptions options = new OpcodeTracerOptions();

        // When
        final var opcodesResponse = opcodeService.processOpcodeCall(transactionIdOrHash, options);

        // Then
        verifyOpcodesResponseWithExpectedReturnValue(opcodesResponse, options, HEX_PREFIX + expectedResult);
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                            true, false, true, true
                            false, false, false, false
                            true, true, true, true
                            """)
    void createNFTAndGetIsTokenAndGetDefaultFreezeStatusAndGetDefaultKycStatus(
            final boolean withKeys,
            final boolean inheritKey,
            boolean defaultKycStatus,
            final boolean defaultFreezeStatus) {
        // Given
        defaultKycStatus = calculateDefaultKycStatus(defaultKycStatus);
        final var treasuryEntity = accountEntityPersist();
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);
        final var tokenInfo = getHederaToken(
                contract.getContractAddress(),
                TokenTypeEnum.NON_FUNGIBLE_UNIQUE,
                withKeys,
                inheritKey,
                defaultFreezeStatus,
                treasuryEntity);

        final var functionCall =
                contract.call_createNFTAndGetIsTokenAndGetDefaultFreezeStatusAndGetDefaultKycStatus(tokenInfo);
        final var callData =
                Bytes.fromHexString(functionCall.encodeFunctionCall()).toArray();
        final var expectedResult = getExpectedResultFromBooleans(defaultKycStatus, defaultFreezeStatus, true);
        final var expectedResultBytes = expectedResult.getBytes();

        final var transactionIdOrHash = setUpEthereumTransactionWithSenderBalance(
                contract, callData, DEFAULT_TRANSACTION_VALUE, expectedResultBytes);
        final OpcodeTracerOptions options = new OpcodeTracerOptions();

        // When
        final var opcodesResponse = opcodeService.processOpcodeCall(transactionIdOrHash, options);

        // Then
        verifyOpcodesResponseWithExpectedReturnValue(opcodesResponse, options, HEX_PREFIX + expectedResult);
    }

    @ParameterizedTest
    @CsvSource({
        "true, true, true",
        "false, true, true",
        "true, false, true",
        "true, true, false",
        "false, false, true",
        "false, true, false",
        "true, false, false",
        "false, false, false"
    })
    void callWithDifferentCombinationsOfTracerOptions(
            final boolean stack, final boolean memory, final boolean storage) {
        // Given
        final var senderEntity = accountPersistWithAccountBalances();
        final var treasuryEntity = accountEntityPersist();
        final var treasuryAddress = toAddress(treasuryEntity.getId());

        final var tokenEntity = persistTokenWithAutoRenewAndTreasuryAccounts(
                        TokenTypeEnum.FUNGIBLE_COMMON, treasuryEntity)
                .getLeft();
        final var tokenAddress = toAddress(tokenEntity.getId());
        final var options = new OpcodeTracerOptions(stack, memory, storage);
        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);

        final var functionCall = contract.send_mintTokenGetTotalSupplyAndBalanceOfTreasury(
                tokenAddress.toHexString(), BigInteger.valueOf(100), List.of(), treasuryAddress.toHexString());

        final var callData =
                Bytes.fromHexString(functionCall.encodeFunctionCall()).toArray();
        final var transactionIdOrHash = setUp(
                ETHEREUMTRANSACTION,
                contract,
                callData,
                true,
                true,
                senderEntity.toEntityId(),
                ZERO_AMOUNT,
                domainBuilder.timestamp());

        // When
        final var opcodesResponse = opcodeService.processOpcodeCall(transactionIdOrHash, options);

        // Then
        verifyOpcodesResponse(opcodesResponse, options);
    }

    @Test
    void callWithContractResultNotFoundExceptionTest() {
        // Given
        final var contract = testWeb3jService.deploy(ExchangeRatePrecompile::deploy);
        final var functionCall = contract.call_tinybarsToTinycents(BigInteger.TEN);
        final var callData = functionCall.encodeFunctionCall().getBytes();
        final var senderEntity = accountEntityPersist();
        final var consensusTimestamp = domainBuilder.timestamp();
        final var transactionIdOrHash = setUp(
                TransactionType.CONTRACTCALL,
                contract,
                callData,
                true,
                false,
                senderEntity.toEntityId(),
                DEFAULT_TRANSACTION_VALUE,
                consensusTimestamp);
        final OpcodeTracerOptions options = new OpcodeTracerOptions();

        // Then
        assertThatExceptionOfType(EntityNotFoundException.class)
                .isThrownBy(() -> opcodeService.processOpcodeCall(transactionIdOrHash, options))
                .withMessage("Contract result not found: " + consensusTimestamp);
    }

    @Test
    void callWithTransactionNotFoundExceptionTest() {
        // Given
        final var contract = testWeb3jService.deploy(ExchangeRatePrecompile::deploy);
        final var functionCall = contract.call_tinybarsToTinycents(BigInteger.TEN);
        final var callData = functionCall.encodeFunctionCall().getBytes();
        final var senderEntity = accountEntityPersist();

        final var transactionIdOrHash = setUp(
                TransactionType.CONTRACTCALL,
                contract,
                callData,
                false,
                true,
                senderEntity.toEntityId(),
                DEFAULT_TRANSACTION_VALUE,
                domainBuilder.timestamp());
        final OpcodeTracerOptions options = new OpcodeTracerOptions();

        // Then
        assertThatExceptionOfType(EntityNotFoundException.class)
                .isThrownBy(() -> opcodeService.processOpcodeCall(transactionIdOrHash, options))
                .withMessage("Transaction not found: " + transactionIdOrHash);
    }

    @Test
    void callWithContractTransactionHashNotFoundExceptionTest() {
        // Given
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);
        final var senderEntity = accountEntityPersist();
        final var treasuryEntity = accountEntityPersist();
        Pair<Entity, Entity> tokenWithAutoRenewPair =
                persistTokenWithAutoRenewAndTreasuryAccounts(TokenTypeEnum.FUNGIBLE_COMMON, treasuryEntity);
        final var tokenInfo = populateHederaToken(
                contract.getContractAddress(),
                TokenTypeEnum.FUNGIBLE_COMMON,
                treasuryEntity.toEntityId(),
                tokenWithAutoRenewPair.getRight());

        final OpcodeTracerOptions options = new OpcodeTracerOptions();
        final var functionCall =
                contract.call_createFungibleTokenAndGetIsTokenAndGetDefaultFreezeStatusAndGetDefaultKycStatus(
                        tokenInfo, BigInteger.ONE, BigInteger.ONE);
        final var transactionIdOrHash = setUp(
                ETHEREUMTRANSACTION,
                contract,
                functionCall.encodeFunctionCall().getBytes(),
                false,
                true,
                senderEntity.toEntityId(),
                DEFAULT_TRANSACTION_VALUE,
                domainBuilder.timestamp());

        // Then
        assertThatExceptionOfType(EntityNotFoundException.class)
                .isThrownBy(() -> opcodeService.processOpcodeCall(transactionIdOrHash, options))
                .withMessage("Contract transaction hash not found: " + transactionIdOrHash);
    }

    private byte getByteFromBoolean(final boolean bool) {
        return (byte) (bool ? 1 : 0);
    }

    private String getExpectedResultFromBooleans(Boolean... booleans) {
        StringBuilder result = new StringBuilder();
        for (Boolean booleanValue : booleans) {
            final var byteValue = getByteFromBoolean(booleanValue);
            result.append(DomainUtils.bytesToHex(DomainUtils.leftPadBytes(new byte[] {byteValue}, Bytes32.SIZE)));
        }
        return result.toString();
    }

    private KeyValue getKeyValueForType(final KeyValueType keyValueType, String contractAddress) {
        return switch (keyValueType) {
            case INHERIT_ACCOUNT_KEY -> new KeyValue(
                    Boolean.TRUE, Address.ZERO.toHexString(), new byte[0], new byte[0], Address.ZERO.toHexString());
            case CONTRACT_ID -> new KeyValue(
                    Boolean.FALSE, contractAddress, new byte[0], new byte[0], Address.ZERO.toHexString());
            case ED25519 -> new KeyValue(
                    Boolean.FALSE,
                    Address.ZERO.toHexString(),
                    NEW_ED25519_KEY,
                    new byte[0],
                    Address.ZERO.toHexString());
            case ECDSA_SECPK256K1 -> new KeyValue(
                    Boolean.FALSE, Address.ZERO.toHexString(), new byte[0], NEW_ECDSA_KEY, Address.ZERO.toHexString());
            case DELEGATABLE_CONTRACT_ID -> new KeyValue(
                    Boolean.FALSE, Address.ZERO.toHexString(), new byte[0], new byte[0], contractAddress);
            default -> throw new RuntimeException("Unsupported key type: " + keyValueType.name());
        };
    }

    private TransactionIdOrHashParameter setUp(
            final TransactionType transactionType,
            final Contract contract,
            final byte[] callData,
            final boolean persistTransaction,
            final boolean persistContractResult,
            final EntityId senderEntityId,
            final long transactionValue,
            final long consensusTimestamp) {
        return setUpForSuccessWithExpectedResult(
                transactionType,
                contract,
                callData,
                persistTransaction,
                persistContractResult,
                senderEntityId,
                transactionValue,
                consensusTimestamp);
    }

    private TransactionIdOrHashParameter setUpEthereumTransactionWithSenderBalance(
            final Contract contract, final byte[] callData, final long transactionValue, final byte[] expectedResult) {
        final var senderEntity = accountPersistWithAccountBalances();
        return setUpForSuccessWithExpectedResultAndBalance(
                ETHEREUMTRANSACTION,
                contract,
                callData,
                true,
                true,
                senderEntity.toEntityId(),
                transactionValue,
                expectedResult,
                senderEntity.getCreatedTimestamp() + 1);
    }

    private TransactionIdOrHashParameter setUpForSuccessWithExpectedResult(
            final TransactionType transactionType,
            final Contract contract,
            final byte[] callData,
            final boolean persistTransaction,
            final boolean persistContractResult,
            final EntityId senderEntityId,
            final long transactionValue,
            final long consensusTimestamp) {
        return setUpForSuccessWithExpectedResultAndBalance(
                transactionType,
                contract,
                callData,
                persistTransaction,
                persistContractResult,
                senderEntityId,
                transactionValue,
                null,
                consensusTimestamp);
    }

    private TransactionIdOrHashParameter setUpForSuccessWithExpectedResultAndBalance(
            final TransactionType transactionType,
            final Contract contract,
            final byte[] callData,
            final boolean persistTransaction,
            final boolean persistContractResult,
            final EntityId senderEntityId,
            final long transactionValue,
            final byte[] expectedResult,
            final long consensusTimestamp) {

        final var ethHash = domainBuilder.bytes(Bytes32.SIZE);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        final var transaction = persistTransaction(
                consensusTimestamp, contractEntityId, senderEntityId, transactionType, persistTransaction);

        final EthereumTransaction ethTransaction;
        if (transactionType == ETHEREUMTRANSACTION) {
            ethTransaction = persistEthereumTransaction(
                    callData,
                    consensusTimestamp,
                    ethHash,
                    senderEntityId,
                    contract.getContractAddress(),
                    persistTransaction,
                    transactionValue);
        } else {
            ethTransaction = null;
        }
        final var contractResult = createContractResult(
                consensusTimestamp,
                contractEntityId,
                callData,
                senderEntityId,
                transaction,
                persistContractResult,
                transactionValue);

        persistContractActionsWithExpectedResult(
                senderEntityId,
                consensusTimestamp,
                contractEntityId,
                contract.getContractAddress(),
                expectedResult,
                transactionValue);

        if (persistTransaction) {
            persistContractTransactionHash(
                    consensusTimestamp, contractEntityId, ethHash, senderEntityId, contractResult);
        }

        if (ethTransaction != null) {
            return new TransactionHashParameter(Bytes.of(ethTransaction.getHash()));
        } else {
            return new TransactionIdParameter(transaction.getPayerAccountId(), instant(transaction.getValidStartNs()));
        }
    }

    private Transaction persistTransaction(
            final long consensusTimestamp,
            final EntityId contractEntityId,
            final EntityId senderEntityId,
            final TransactionType transactionType,
            final boolean persistTransaction) {
        final var validStartNs = consensusTimestamp - 1;

        final var transactionBuilder = domainBuilder.transaction().customize(transaction -> transaction
                .consensusTimestamp(consensusTimestamp)
                .entityId(contractEntityId)
                .payerAccountId(senderEntityId)
                .type(transactionType.getProtoId())
                .validStartNs(validStartNs));

        return persistTransaction ? transactionBuilder.persist() : transactionBuilder.get();
    }

    private EthereumTransaction persistEthereumTransaction(
            final byte[] callData,
            final long consensusTimestamp,
            final byte[] ethHash,
            final EntityId senderEntityId,
            final String contractAddress,
            final boolean persistTransaction,
            final long value) {
        final var ethTransactionBuilder = domainBuilder
                .ethereumTransaction(false)
                .customize(ethereumTransaction -> ethereumTransaction
                        .callData(callData)
                        .consensusTimestamp(consensusTimestamp)
                        .gasLimit(TRANSACTION_GAS_LIMIT)
                        .hash(ethHash)
                        .payerAccountId(senderEntityId)
                        .toAddress(Address.fromHexString(contractAddress).toArray())
                        .value(
                                value > 0
                                        ? BigInteger.valueOf(value).toByteArray()
                                        : BigInteger.valueOf(ZERO_AMOUNT).toByteArray()));
        return persistTransaction ? ethTransactionBuilder.persist() : ethTransactionBuilder.get();
    }

    private ContractResult createContractResult(
            final long consensusTimestamp,
            final EntityId contractEntityId,
            final byte[] callData,
            final EntityId senderEntityId,
            final Transaction transaction,
            final boolean persistContractResult,
            final long value) {
        final var contractResultBuilder = domainBuilder.contractResult().customize(contractResult -> contractResult
                .amount(value > 0 ? value : ZERO_AMOUNT)
                .consensusTimestamp(consensusTimestamp)
                .contractId(contractEntityId.getId())
                .functionParameters(callData)
                .gasLimit(TRANSACTION_GAS_LIMIT)
                .senderId(senderEntityId)
                .transactionHash(transaction.getTransactionHash()));
        return persistContractResult ? contractResultBuilder.persist() : contractResultBuilder.get();
    }

    private void persistContractActionsWithExpectedResult(
            final EntityId senderEntityId,
            final long consensusTimestamp,
            final EntityId contractEntityId,
            final String contractAddress,
            final byte[] expectedResult,
            final long value) {
        domainBuilder
                .contractAction()
                .customize(contractAction -> contractAction
                        .caller(senderEntityId)
                        .callerType(EntityType.ACCOUNT)
                        .consensusTimestamp(consensusTimestamp)
                        .payerAccountId(senderEntityId)
                        .recipientContract(contractEntityId)
                        .recipientAddress(contractAddress.getBytes())
                        .gas(TRANSACTION_GAS_LIMIT)
                        .resultData(expectedResult)
                        .resultDataType(OUTPUT.getNumber())
                        .value(value > 0 ? value : ZERO_AMOUNT))
                .persist();
    }

    private void persistContractTransactionHash(
            final long consensusTimestamp,
            final EntityId contractEntityId,
            final byte[] ethHash,
            final EntityId senderEntityId,
            final ContractResult contractResult) {
        domainBuilder
                .contractTransactionHash()
                .customize(contractTransactionHash -> contractTransactionHash
                        .consensusTimestamp(consensusTimestamp)
                        .entityId(contractEntityId.getId())
                        .hash(ethHash)
                        .payerAccountId(senderEntityId.getId())
                        .transactionResult(contractResult.getTransactionResult()))
                .persist();
    }

    private Token nftPersist(final Entity treasuryEntity) {
        final var nftEntity = tokenEntityPersist();

        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(nftEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(treasuryEntity.toEntityId()))
                .persist();

        final var treasuryEntityId = token.getTreasuryAccountId();
        domainBuilder
                .nft()
                .customize(n -> n.accountId(treasuryEntityId)
                        .spender(treasuryEntityId)
                        .accountId(treasuryEntityId)
                        .tokenId(nftEntity.getId())
                        .serialNumber(1))
                .persist();
        return token;
    }

    private Entity accountPersistWithAccountBalances() {
        final var entity = accountEntityPersist();

        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(entity.getCreatedTimestamp(), EntityId.of(2)))
                        .balance(entity.getBalance()))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(entity.getCreatedTimestamp(), entity.toEntityId()))
                        .balance(entity.getBalance()))
                .persist();

        return entity;
    }

    private NestedCalls.HederaToken populateHederaToken(
            final String contractAddress,
            final TokenTypeEnum tokenType,
            final EntityId treasuryAccountId,
            Entity autoRenewAccount) {
        // expiration
        final var tokenEntity = domainBuilder
                .entity()
                .customize(e -> e.type(TOKEN).autoRenewAccountId(autoRenewAccount.getId()))
                .persist();
        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(tokenType).treasuryAccountId(treasuryAccountId))
                .persist();

        final var supplyKey = new NestedCalls.KeyValue(
                Boolean.FALSE,
                contractAddress,
                new byte[0],
                new byte[0],
                Address.ZERO.toHexString()); // the key needed for token minting or burning
        final var keys = new ArrayList<TokenKey>();
        keys.add(new NestedCalls.TokenKey(
                AbstractContractCallServiceTest.KeyType.SUPPLY_KEY.getKeyTypeNumeric(), supplyKey));
        return new NestedCalls.HederaToken(
                token.getName(),
                token.getSymbol(),
                getAddressFromEntityId(treasuryAccountId), // id of the account holding the initial token supply
                tokenEntity.getMemo(), // token description encoded in UTF-8 format
                true,
                BigInteger.valueOf(10_000L),
                false,
                keys,
                new NestedCalls.Expiry(
                        BigInteger.valueOf(Instant.now().getEpochSecond() + 8_000_000L),
                        getAddressFromEntity(autoRenewAccount),
                        BigInteger.valueOf(8_000_000)));
    }

    private HederaToken getHederaToken(
            final String contractAddress,
            final TokenTypeEnum tokenType,
            final boolean withKeys,
            final boolean inheritAccountKey,
            final boolean freezeDefault,
            final Entity treasuryEntity) {
        final List<TokenKey> tokenKeys = new LinkedList<>();
        final var keyType = inheritAccountKey ? KeyValueType.INHERIT_ACCOUNT_KEY : KeyValueType.ECDSA_SECPK256K1;
        if (withKeys) {
            tokenKeys.add(new TokenKey(KeyType.KYC_KEY.getKeyTypeNumeric(), getKeyValueForType(keyType, null)));
            tokenKeys.add(new TokenKey(KeyType.FREEZE_KEY.getKeyTypeNumeric(), getKeyValueForType(keyType, null)));
        }

        return populateHederaToken(contractAddress, tokenType, treasuryEntity.toEntityId(), freezeDefault, tokenKeys);
    }

    private NestedCalls.HederaToken populateHederaToken(
            final String contractAddress,
            final TokenTypeEnum tokenType,
            final EntityId treasuryAccountId,
            boolean freezeDefault,
            List<TokenKey> tokenKeys) {
        final var autoRenewAccount =
                accountEntityWithEvmAddressPersist(); // the account that is going to be charged for token renewal upon
        // expiration
        final var tokenEntity = domainBuilder
                .entity()
                .customize(e -> e.type(TOKEN).autoRenewAccountId(autoRenewAccount.getId()))
                .persist();
        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(tokenType).treasuryAccountId(treasuryAccountId))
                .persist();

        final var supplyKey = new NestedCalls.KeyValue(
                Boolean.FALSE,
                contractAddress,
                new byte[0],
                new byte[0],
                Address.ZERO.toHexString()); // the key needed for token minting or burning
        tokenKeys.add(new NestedCalls.TokenKey(
                AbstractContractCallServiceTest.KeyType.SUPPLY_KEY.getKeyTypeNumeric(), supplyKey));

        return new NestedCalls.HederaToken(
                token.getName(),
                token.getSymbol(),
                getAddressFromEntityId(treasuryAccountId), // id of the account holding the initial token supply
                tokenEntity.getMemo(), // token description encoded in UTF-8 format
                true,
                BigInteger.valueOf(10_000L),
                freezeDefault,
                tokenKeys,
                new NestedCalls.Expiry(
                        BigInteger.valueOf(Instant.now().getEpochSecond() + 8_000_000L),
                        getAliasFromEntity(autoRenewAccount),
                        BigInteger.valueOf(8_000_000)));
    }

    /**
     * Adjusts the default KYC status based on the modularized services flag.
     * <p>
     * In modularized services, the KYC status behaves inversely compared to mono: - Without a KYC key:
     * `KycNotApplicable` -> returns `TRUE`. - With a KYC key: Initial status is `Revoked` -> returns `FALSE`. This
     * method toggles the status when modularized services are enabled.
     *
     * @param defaultKycStatus The initial KYC status boolean.
     * @return The adjusted KYC status boolean.
     */
    private boolean calculateDefaultKycStatus(boolean defaultKycStatus) {
        return mirrorNodeEvmProperties.isModularizedServices() != defaultKycStatus;
    }
}
