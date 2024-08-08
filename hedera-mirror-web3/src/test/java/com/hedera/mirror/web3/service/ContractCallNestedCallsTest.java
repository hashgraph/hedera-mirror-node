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

import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.service.ContractCallTestSetup.EVM_V_34_BLOCK;
import static com.hedera.mirror.web3.service.ContractCallTestSetup.NEW_ECDSA_KEY;
import static com.hedera.mirror.web3.service.ContractCallTestSetup.NEW_ED25519_KEY;
import static com.hedera.mirror.web3.service.ContractCallTestSetup.NFT_ADDRESS_HISTORICAL;
import static com.hedera.mirror.web3.service.ContractCallTestSetup.NON_FUNGIBLE_TOKEN_INHERIT_KEYS;
import static com.hedera.mirror.web3.service.ContractCallTestSetup.longValueOf;
import static com.hedera.mirror.web3.service.ContractCallTestUtil.ESTIMATE_GAS_ERROR_MESSAGE;
import static com.hedera.mirror.web3.service.ContractCallTestUtil.isWithinExpectedGasRange;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.web3.utils.ContractFunctionProviderEnum;
import com.hedera.mirror.web3.utils.KeyType;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.mirror.web3.web3j.generated.NestedCalls;
import com.hedera.mirror.web3.web3j.generated.NestedCalls.Expiry;
import com.hedera.mirror.web3.web3j.generated.NestedCalls.HederaToken;
import com.hedera.mirror.web3.web3j.generated.NestedCalls.KeyValue;
import com.hedera.mirror.web3.web3j.generated.NestedCalls.TokenKey;
import com.hedera.services.store.contracts.precompile.codec.KeyValueWrapper.KeyValueType;
import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;

class ContractCallNestedCallsTest extends AbstractContractCallServiceTest {

    private final long CREATE_TOKEN_VALUE = 3070 * 100_000_000L;

    //
    //    @ParameterizedTest
    //    @EnumSource(NestedEthCallContractFunctions.class)
    //    void nestedReadOnlyTokenFunctionsTestEthEstimateGas(NestedEthCallContractFunctions contractFunc) {
    //        final var functionHash = functionEncodeDecoder.functionHashFor(
    //                contractFunc.name, NESTED_CALLS_ABI_PATH, contractFunc.functionParameters);
    //        final var value =
    //                switch (contractFunc) {
    //                    case CREATE_FUNGIBLE_TOKEN_WITH_KEYS,
    //                            CREATE_FUNGIBLE_TOKEN_NO_KEYS,
    //                            CREATE_FUNGIBLE_TOKEN_INHERIT_KEYS,
    //                            CREATE_NON_FUNGIBLE_TOKEN_WITH_KEYS,
    //                            CREATE_NON_FUNGIBLE_TOKEN_NO_KEYS,
    //                            CREATE_NON_FUNGIBLE_TOKEN_INHERIT_KEYS -> 3070 * 100_000_000L;
    //                    default -> 0L;
    //                };
    //        final var serviceParameters = serviceParametersForExecution(
    //                functionHash, NESTED_ETH_CALLS_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, value, BlockType.LATEST);
    //
    //        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);
    //
    //        assertThat(longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)))
    //                .as("result must be within 5-20% bigger than the gas used from the first call")
    //                .isGreaterThanOrEqualTo((long) (expectedGasUsed * 1.05)) // expectedGasUsed value increased by 5%
    //                .isCloseTo(expectedGasUsed, Percentage.withPercentage(20)); // Maximum percentage
    //    }
    //
    //    @ParameterizedTest
    //    @EnumSource(NestedEthCallContractFunctionsNegativeCases.class)
    //    void failedNestedCallWithHardcodedResult(final NestedEthCallContractFunctionsNegativeCases func) {
    //        final var functionHash =
    //                functionEncodeDecoder.functionHashFor(func.name, NESTED_CALLS_ABI_PATH, func.functionParameters);
    //        final var serviceParameters = serviceParametersForExecution(
    //                functionHash, NESTED_ETH_CALLS_CONTRACT_ADDRESS, ETH_CALL, 0L, func.block);
    //
    //        final var successfulResponse =
    //                functionEncodeDecoder.encodedResultFor(func.name, NESTED_CALLS_ABI_PATH,
    // func.expectedResultFields);
    //
    //        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
    //    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                    FUNGIBLE_COMMON,    CONTRACT_ID,                ADMIN_KEY,
                    FUNGIBLE_COMMON,    CONTRACT_ID,                KYC_KEY,
                    FUNGIBLE_COMMON,    CONTRACT_ID,                FREEZE_KEY,
                    FUNGIBLE_COMMON,    CONTRACT_ID,                WIPE_KEY,
                    FUNGIBLE_COMMON,    CONTRACT_ID,                SUPPLY_KEY,
                    FUNGIBLE_COMMON,    CONTRACT_ID,                FEE_SCHEDULE_KEY,
                    FUNGIBLE_COMMON,    CONTRACT_ID,                PAUSE_KEY,
                    FUNGIBLE_COMMON,    ED25519,                    ADMIN_KEY,
                    FUNGIBLE_COMMON,    ED25519,                    KYC_KEY,
                    FUNGIBLE_COMMON,    ED25519,                    FREEZE_KEY,
                    FUNGIBLE_COMMON,    ED25519,                    WIPE_KEY,
                    FUNGIBLE_COMMON,    ED25519,                    SUPPLY_KEY,
                    FUNGIBLE_COMMON,    ED25519,                    FEE_SCHEDULE_KEY,
                    FUNGIBLE_COMMON,    ED25519,                    PAUSE_KEY,
                    FUNGIBLE_COMMON,    ECDSA_SECPK256K1,           ADMIN_KEY,
                    FUNGIBLE_COMMON,    ECDSA_SECPK256K1,           KYC_KEY,
                    FUNGIBLE_COMMON,    ECDSA_SECPK256K1,           FREEZE_KEY,
                    FUNGIBLE_COMMON,    ECDSA_SECPK256K1,           WIPE_KEY,
                    FUNGIBLE_COMMON,    ECDSA_SECPK256K1,           SUPPLY_KEY,
                    FUNGIBLE_COMMON,    ECDSA_SECPK256K1,           FEE_SCHEDULE_KEY,
                    FUNGIBLE_COMMON,    ECDSA_SECPK256K1,           PAUSE_KEY,
                    FUNGIBLE_COMMON,    DELEGATABLE_CONTRACT_ID,    ADMIN_KEY,
                    FUNGIBLE_COMMON,    DELEGATABLE_CONTRACT_ID,    KYC_KEY,
                    FUNGIBLE_COMMON,    DELEGATABLE_CONTRACT_ID,    FREEZE_KEY,
                    FUNGIBLE_COMMON,    DELEGATABLE_CONTRACT_ID,    WIPE_KEY,
                    FUNGIBLE_COMMON,    DELEGATABLE_CONTRACT_ID,    SUPPLY_KEY,
                    FUNGIBLE_COMMON,    DELEGATABLE_CONTRACT_ID,    FEE_SCHEDULE_KEY,
                    FUNGIBLE_COMMON,    DELEGATABLE_CONTRACT_ID,    PAUSE_KEY,
                    NON_FUNGIBLE_UNIQUE,    CONTRACT_ID,                ADMIN_KEY,
                    NON_FUNGIBLE_UNIQUE,    CONTRACT_ID,                KYC_KEY,
                    NON_FUNGIBLE_UNIQUE,    CONTRACT_ID,                FREEZE_KEY,
                    NON_FUNGIBLE_UNIQUE,    CONTRACT_ID,                WIPE_KEY,
                    NON_FUNGIBLE_UNIQUE,    CONTRACT_ID,                SUPPLY_KEY,
                    NON_FUNGIBLE_UNIQUE,    CONTRACT_ID,                FEE_SCHEDULE_KEY,
                    NON_FUNGIBLE_UNIQUE,    CONTRACT_ID,                PAUSE_KEY,
                    NON_FUNGIBLE_UNIQUE,    ED25519,                    ADMIN_KEY,
                    NON_FUNGIBLE_UNIQUE,    ED25519,                    KYC_KEY,
                    NON_FUNGIBLE_UNIQUE,    ED25519,                    FREEZE_KEY,
                    NON_FUNGIBLE_UNIQUE,    ED25519,                    WIPE_KEY,
                    NON_FUNGIBLE_UNIQUE,    ED25519,                    SUPPLY_KEY,
                    NON_FUNGIBLE_UNIQUE,    ED25519,                    FEE_SCHEDULE_KEY,
                    NON_FUNGIBLE_UNIQUE,    ED25519,                    PAUSE_KEY,
                    NON_FUNGIBLE_UNIQUE,    ECDSA_SECPK256K1,           ADMIN_KEY,
                    NON_FUNGIBLE_UNIQUE,    ECDSA_SECPK256K1,           KYC_KEY,
                    NON_FUNGIBLE_UNIQUE,    ECDSA_SECPK256K1,           FREEZE_KEY,
                    NON_FUNGIBLE_UNIQUE,    ECDSA_SECPK256K1,           WIPE_KEY,
                    NON_FUNGIBLE_UNIQUE,    ECDSA_SECPK256K1,           SUPPLY_KEY,
                    NON_FUNGIBLE_UNIQUE,    ECDSA_SECPK256K1,           FEE_SCHEDULE_KEY,
                    NON_FUNGIBLE_UNIQUE,    ECDSA_SECPK256K1,           PAUSE_KEY,
                    NON_FUNGIBLE_UNIQUE,    DELEGATABLE_CONTRACT_ID,    ADMIN_KEY,
                    NON_FUNGIBLE_UNIQUE,    DELEGATABLE_CONTRACT_ID,    KYC_KEY,
                    NON_FUNGIBLE_UNIQUE,    DELEGATABLE_CONTRACT_ID,    FREEZE_KEY,
                    NON_FUNGIBLE_UNIQUE,    DELEGATABLE_CONTRACT_ID,    WIPE_KEY,
                    NON_FUNGIBLE_UNIQUE,    DELEGATABLE_CONTRACT_ID,    SUPPLY_KEY,
                    NON_FUNGIBLE_UNIQUE,    DELEGATABLE_CONTRACT_ID,    FEE_SCHEDULE_KEY,
                    NON_FUNGIBLE_UNIQUE,    DELEGATABLE_CONTRACT_ID,    PAUSE_KEY
                    """)
    void updateTokenKeysAndGetUpdatedTokenKey(
            final TokenTypeEnum tokenType, final KeyValueType keyValueType, final KeyType keyType) throws Exception {
        // Given
        final var tokenEntityId = tokenType == TokenTypeEnum.FUNGIBLE_COMMON ? fungibleTokenPersist() : nftPersist();
        final var tokenAddress = toAddress(tokenEntityId.getTokenId());
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);
        final var contractAddress = contract.getContractAddress();

        final var keyValue = getKeyValueForType(keyValueType, contractAddress);
        final var tokenKey = new TokenKey(keyType.getKeyTypeNumeric(), keyValue);

        // When
        final var result = contract.call_updateTokenKeysAndGetUpdatedTokenKey(
                        tokenAddress.toHexString(), List.of(tokenKey), keyType.getKeyTypeNumeric())
                .send();

        // Then
        // ethCall
        assertThat(result).isEqualTo(keyValue);

        final var functionCall = contract.send_updateTokenKeysAndGetUpdatedTokenKey(
                tokenAddress.toHexString(), List.of(tokenKey), keyType.getKeyTypeNumeric());
        // estimateGas
        testEstimateGas(functionCall, contract);
    }

    protected void testEstimateGas(final RemoteFunctionCall<TransactionReceipt> functionCall, final Contract contract)
            throws Exception {
        testWeb3jService.setEstimateGas(true);

        functionCall.send();
        final var estimateGasUsedResult = longValueOf.applyAsLong(testWeb3jService.getOutput());

        final var actualGasUsed = gasUsedAfterExecution(getContractExecutionParameters(functionCall, contract));

        // Then
        assertThat(isWithinExpectedGasRange(estimateGasUsedResult, actualGasUsed))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimateGasUsedResult, actualGasUsed)
                .isTrue();

        testWeb3jService.setEstimateGas(false);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
                FUNGIBLE_COMMON
                NON_FUNGIBLE_UNIQUE
                """)
    void updateTokenExpiryAndGetUpdatedTokenExpiry(final TokenTypeEnum tokenType) throws Exception {
        // Given
        final var tokenEntityId = tokenType == TokenTypeEnum.FUNGIBLE_COMMON ? fungibleTokenPersist() : nftPersist();
        final var tokenAddress = toAddress(tokenEntityId.getTokenId());
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);
        final var autoRenewAccount = accountPersist();
        final var tokenExpiry = getTokenExpiry(autoRenewAccount);

        // When
        final var result = contract.call_updateTokenExpiryAndGetUpdatedTokenExpiry(
                        tokenAddress.toHexString(), tokenExpiry)
                .send();

        // Then
        assertThat(result).isEqualTo(tokenExpiry);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
                FUNGIBLE_COMMON
                NON_FUNGIBLE_UNIQUE
                """)
    void updateTokenInfoAndGetUpdatedTokenInfoSymbol(final TokenTypeEnum tokenType) throws Exception {
        // Given
        final var treasuryEntity = accountPersist();
        final var autoRenewAccount = accountPersist();
        final var tokenEntityId = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                ? fungibleTokenPersist(treasuryEntity)
                : nftPersist(treasuryEntity);
        final var tokenAddress = toAddress(tokenEntityId.getTokenId());
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);
        final var tokenInfo = getHederaToken(tokenType, treasuryEntity, autoRenewAccount);

        // When
        final var result = contract.call_updateTokenInfoAndGetUpdatedTokenInfoSymbol(
                        tokenAddress.toHexString(), tokenInfo)
                .send();

        // Then
        assertThat(result).isEqualTo(tokenInfo.symbol);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
                FUNGIBLE_COMMON
                NON_FUNGIBLE_UNIQUE
                """)
    void updateTokenInfoAndGetUpdatedTokenInfoName(final TokenTypeEnum tokenType) throws Exception {
        // Given
        final var treasuryEntity = accountPersist();
        final var autoRenewAccount = accountPersist();
        final var tokenEntityId = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                ? fungibleTokenPersist(treasuryEntity)
                : nftPersist(treasuryEntity);
        final var tokenAddress = toAddress(tokenEntityId.getTokenId());
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);
        final var tokenInfo = getHederaToken(tokenType, treasuryEntity, autoRenewAccount);

        // When
        final var result = contract.call_updateTokenInfoAndGetUpdatedTokenInfoName(
                        tokenAddress.toHexString(), tokenInfo)
                .send();

        // Then
        assertThat(result).isEqualTo(tokenInfo.name);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
                FUNGIBLE_COMMON
                NON_FUNGIBLE_UNIQUE
                """)
    void updateTokenInfoAndGetUpdatedTokenInfoMemo(final TokenTypeEnum tokenType) throws Exception {
        // Given
        final var treasuryEntity = accountPersist();
        final var autoRenewAccount = accountPersist();
        final var tokenEntityId = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                ? fungibleTokenPersist(treasuryEntity)
                : nftPersist(treasuryEntity);
        final var tokenAddress = toAddress(tokenEntityId.getTokenId());
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);
        final var tokenInfo = getHederaToken(tokenType, treasuryEntity, autoRenewAccount);

        // When
        final var result = contract.call_updateTokenInfoAndGetUpdatedTokenInfoMemo(
                        tokenAddress.toHexString(), tokenInfo)
                .send();

        // Then
        assertThat(result).isEqualTo(tokenInfo.memo);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
                FUNGIBLE_COMMON
                NON_FUNGIBLE_UNIQUE
                """)
    void deleteTokenAndGetTokenInfoIsDeleted(final TokenTypeEnum tokenType) throws Exception {
        // Given
        final var tokenEntityId = tokenType == TokenTypeEnum.FUNGIBLE_COMMON ? fungibleTokenPersist() : nftPersist();
        final var tokenAddress = toAddress(tokenEntityId.getTokenId());
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);

        // When
        final var result = contract.call_deleteTokenAndGetTokenInfoIsDeleted(tokenAddress.toHexString())
                .send();

        // Then
        assertThat(result).isTrue();
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
                true, true, true
                false, false, false
                """)
    void createFungibleTokenAndGetIsTokenAndGetDefaultFreezeStatusAndGetDefaultKycStatus(
            final boolean withKeys, final boolean defaultKycStatus, final boolean defaultFreezeStatus)
            throws Exception {
        // Given
        final var treasuryEntity = accountPersist();
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);
        final var tokenInfo =
                getHederaToken(TokenTypeEnum.FUNGIBLE_COMMON, withKeys, false, defaultFreezeStatus, treasuryEntity);
        tokenInfo.freezeDefault = defaultFreezeStatus;
        testWeb3jService.setValue(CREATE_TOKEN_VALUE);

        // When
        final var result =
                contract.call_createFungibleTokenAndGetIsTokenAndGetDefaultFreezeStatusAndGetDefaultKycStatus(
                                tokenInfo, BigInteger.ONE, BigInteger.ONE)
                        .send();

        // Then
        assertThat(result.component1()).isEqualTo(defaultKycStatus);
        assertThat(result.component2()).isEqualTo(defaultFreezeStatus);
        assertThat(result.component3()).isEqualTo(Boolean.TRUE); // is a token
    }

    @Test
    void createFungibleTokenAndGetIsTokenAndGetDefaultFreezeStatusAndGetDefaultKycStatusInheritKey() throws Exception {
        // Given
        final var treasuryEntity = accountPersist();
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);
        final var tokenInfo = getHederaToken(TokenTypeEnum.FUNGIBLE_COMMON, true, true, true, treasuryEntity);
        tokenInfo.freezeDefault = true;
        testWeb3jService.setValue(CREATE_TOKEN_VALUE);

        // When
        final var result =
                contract.call_createFungibleTokenAndGetIsTokenAndGetDefaultFreezeStatusAndGetDefaultKycStatus(
                                tokenInfo, BigInteger.ONE, BigInteger.ONE)
                        .send();

        // Then
        assertThat(result.component1()).isEqualTo(true);
        assertThat(result.component2()).isEqualTo(true);
        assertThat(result.component3()).isEqualTo(Boolean.TRUE); // is a token
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
                true, true, true
                false, false, false
                """)
    void createNFTAndGetIsTokenAndGetDefaultFreezeStatusAndGetDefaultKycStatus(
            final boolean withKeys, final boolean defaultKycStatus, final boolean defaultFreezeStatus)
            throws Exception {
        // Given
        final var treasuryEntity = accountPersist();
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);
        final var tokenInfo =
                getHederaToken(TokenTypeEnum.NON_FUNGIBLE_UNIQUE, withKeys, false, defaultFreezeStatus, treasuryEntity);
        tokenInfo.freezeDefault = defaultFreezeStatus;
        testWeb3jService.setValue(CREATE_TOKEN_VALUE);

        // When
        final var result = contract.call_createNFTAndGetIsTokenAndGetDefaultFreezeStatusAndGetDefaultKycStatus(
                        tokenInfo)
                .send();

        // Then
        assertThat(result.component1()).isEqualTo(defaultKycStatus);
        assertThat(result.component2()).isEqualTo(defaultFreezeStatus);
        assertThat(result.component3()).isEqualTo(Boolean.TRUE); // is a token
    }

    @Test
    void createNFTAndGetIsTokenAndGetDefaultFreezeStatusAndGetDefaultKycStatusInheritKey() throws Exception {
        // Given
        final var treasuryEntity = accountPersist();
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);
        final var tokenInfo = getHederaToken(TokenTypeEnum.NON_FUNGIBLE_UNIQUE, true, true, true, treasuryEntity);
        tokenInfo.freezeDefault = true;
        testWeb3jService.setValue(CREATE_TOKEN_VALUE);

        // When
        final var result = contract.call_createNFTAndGetIsTokenAndGetDefaultFreezeStatusAndGetDefaultKycStatus(
                        tokenInfo)
                .send();

        // Then
        assertThat(result.component1()).isEqualTo(true);
        assertThat(result.component2()).isEqualTo(true);
        assertThat(result.component3()).isEqualTo(Boolean.TRUE); // is a token
    }

    @Getter
    @RequiredArgsConstructor
    enum NestedEthCallContractFunctions implements ContractFunctionProviderEnum {
        CREATE_NON_FUNGIBLE_TOKEN_INHERIT_KEYS(
                "createNFTAndGetIsTokenAndGetDefaultFreezeStatusAndGetDefaultKycStatus",
                new Object[] {NON_FUNGIBLE_TOKEN_INHERIT_KEYS, 10L, 10},
                new Object[] {true, true, true});

        private final String name;
        private final Object[] functionParameters;
        private final Object[] expectedResultFields;
    }

    @Getter
    @RequiredArgsConstructor
    enum NestedEthCallContractFunctionsNegativeCases implements ContractFunctionProviderEnum {
        GET_TOKEN_INFO_HISTORICAL(
                "nestedGetTokenInfoAndHardcodedResult",
                new Object[] {NFT_ADDRESS_HISTORICAL},
                new Object[] {"hardcodedResult"},
                BlockType.of(String.valueOf(EVM_V_34_BLOCK - 1))),
        GET_TOKEN_INFO(
                "nestedGetTokenInfoAndHardcodedResult",
                new Object[] {Address.ZERO},
                new Object[] {"hardcodedResult"},
                BlockType.LATEST),
        HTS_GET_APPROVED_HISTORICAL(
                "nestedHtsGetApprovedAndHardcodedResult",
                new Object[] {NFT_ADDRESS_HISTORICAL, 1L},
                new Object[] {"hardcodedResult"},
                BlockType.of(String.valueOf(EVM_V_34_BLOCK - 1))),
        HTS_GET_APPROVED(
                "nestedHtsGetApprovedAndHardcodedResult",
                new Object[] {Address.ZERO, 1L},
                new Object[] {"hardcodedResult"},
                BlockType.LATEST),
        MINT_TOKEN_HISTORICAL(
                "nestedMintTokenAndHardcodedResult",
                new Object[] {
                    NFT_ADDRESS_HISTORICAL,
                    0L,
                    new byte[][] {ByteString.copyFromUtf8("firstMeta").toByteArray()}
                },
                new Object[] {"hardcodedResult"},
                BlockType.of(String.valueOf(EVM_V_34_BLOCK - 1))),
        MINT_TOKEN(
                "nestedMintTokenAndHardcodedResult",
                new Object[] {
                    Address.ZERO,
                    0L,
                    new byte[][] {ByteString.copyFromUtf8("firstMeta").toByteArray()}
                },
                new Object[] {"hardcodedResult"},
                BlockType.LATEST);

        private final String name;
        private final Object[] functionParameters;
        private final Object[] expectedResultFields;
        private final BlockType block;
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

    private Expiry getTokenExpiry(final Entity autoRenewAccountEntity) {
        return new Expiry(
                BigInteger.valueOf(4_000_000_000L),
                toAddress(autoRenewAccountEntity.toEntityId()).toHexString(),
                BigInteger.valueOf(8_000_000L));
    }

    private HederaToken getHederaToken(
            final TokenTypeEnum tokenType, final Entity treasuryEntity, final Entity autoRenewAccountEntity) {
        return new HederaToken(
                "name",
                "symbol",
                toAddress(treasuryEntity.getId()).toHexString(),
                "memo",
                tokenType == TokenTypeEnum.FUNGIBLE_COMMON ? Boolean.FALSE : Boolean.TRUE,
                BigInteger.valueOf(10L),
                Boolean.TRUE,
                List.of(),
                getTokenExpiry(autoRenewAccountEntity));
    }

    private HederaToken getHederaToken(
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

        if (tokenType == TokenTypeEnum.NON_FUNGIBLE_UNIQUE) {
            tokenKeys.add(new TokenKey(KeyType.SUPPLY_KEY.getKeyTypeNumeric(), getKeyValueForType(keyType, null)));
        }

        return new HederaToken(
                "name",
                "symbol",
                toAddress(treasuryEntity.getId()).toHexString(),
                "memo",
                Boolean.TRUE, // finite amount
                BigInteger.valueOf(10L), // max supply
                freezeDefault, // freeze default
                tokenKeys,
                getTokenExpiry(domainBuilder.entity().persist()));
    }

    private void recordFilePersist() {
        domainBuilder.recordFile().persist();
    }

    private Token fungibleTokenPersist() {
        return fungibleTokenPersist(domainBuilder.entity().persist());
    }

    private Token fungibleTokenPersist(final Entity treasuryEntity) {
        final var tokenEntity =
                domainBuilder.entity().customize(e -> e.type(TOKEN)).persist();

        return domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .treasuryAccountId(treasuryEntity.toEntityId()))
                .persist();
    }

    private Token nftPersist() {
        return nftPersist(domainBuilder.entity().persist());
    }

    private Token nftPersist(final Entity treasuryEntity) {
        final var nftEntity =
                domainBuilder.entity().customize(e -> e.type(TOKEN)).persist();

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

    private Entity accountPersist() {
        return domainBuilder.entity().customize(e -> e.evmAddress(null)).persist();
    }
}
