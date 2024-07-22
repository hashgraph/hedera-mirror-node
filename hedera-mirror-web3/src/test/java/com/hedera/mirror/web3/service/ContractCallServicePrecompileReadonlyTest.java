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

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;
import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;
import static com.hedera.mirror.web3.evm.pricing.RatesAndFeesLoader.FEE_SCHEDULE_ENTITY_ID;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.entityIdFromEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.service.ContractCallTestSetup.AUTO_RENEW_ACCOUNT_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestSetup.FUNGIBLE_HBAR_TOKEN_AND_KEYS;
import static com.hedera.mirror.web3.service.ContractCallTestSetup.FUNGIBLE_TOKEN_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestSetup.FUNGIBLE_TOKEN_ADDRESS_WITH_EXPIRY;
import static com.hedera.mirror.web3.service.ContractCallTestSetup.NFT_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestSetup.NFT_HBAR_TOKEN_AND_KEYS;
import static com.hedera.mirror.web3.service.ContractCallTestSetup.OWNER_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestSetup.SPENDER_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestUtil.ECDSA_KEY;
import static com.hedera.mirror.web3.service.ContractCallTestUtil.ED25519_KEY;
import static com.hedera.mirror.web3.service.ContractCallTestUtil.KEY_PROTO;
import static com.hedera.mirror.web3.service.ContractCallTestUtil.KEY_WITH_ECDSASecp256K1;
import static com.hedera.mirror.web3.service.ContractCallTestUtil.KEY_WITH_ED25519;
import static com.hedera.mirror.web3.service.ContractCallTestUtil.SENDER_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestUtil.SENDER_ALIAS;
import static com.hedera.mirror.web3.service.ContractCallTestUtil.SENDER_PUBLIC_KEY;
import static com.hedera.services.utils.EntityIdUtils.contractIdFromEvmAddress;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.EthereumTransaction;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.common.domain.token.TokenSupplyTypeEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.config.Web3jTestConfiguration;
import com.hedera.mirror.web3.evm.pricing.RatesAndFeesLoader;
import com.hedera.mirror.web3.utils.ContractFunctionProviderEnum;
import com.hedera.mirror.web3.web3j.TestWeb3jService;
import com.hedera.mirror.web3.web3j.generated.PrecompileTestContract;
import com.hedera.mirror.web3.web3j.generated.PrecompileTestContract.Expiry;
import com.hedera.mirror.web3.web3j.generated.PrecompileTestContract.FungibleTokenInfo;
import com.hedera.mirror.web3.web3j.generated.PrecompileTestContract.HederaToken;
import com.hedera.mirror.web3.web3j.generated.PrecompileTestContract.KeyValue;
import com.hedera.mirror.web3.web3j.generated.PrecompileTestContract.TokenInfo;
import com.hedera.mirror.web3.web3j.generated.PrecompileTestContract.TokenKey;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.Import;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.DynamicBytes;

@Import(Web3jTestConfiguration.class)
@SuppressWarnings("unchecked")
@RequiredArgsConstructor
@TestInstance(PER_CLASS)
class ContractCallServicePrecompileReadonlyTest extends Web3IntegrationTest {

    private final TestWeb3jService testWeb3jService;
    private Entity sender;

    private final String FUNGIBLE_TOKEN_NAME = "FTokenName";
    private final String FUNGIBLE_TOKEN_SYMBOL = "FTokenSymbol";
    private final String FUNGIBLE_TOKEN_MEMO = "FTokenMemo";
    private final int FUNGIBLE_TOKEN_DECIMALS = 12;
    private final long FUNGIBLE_TOKEN_TOTAL_SUPPLY = 1_000_000;
    private final long CREATED_TIMESTAMP = System.currentTimeMillis();
    private final long AUTO_RENEW_PERIOD = 8_000_000L;
    private final long EXPIRATION_TIMESTAMP = CREATED_TIMESTAMP + TimeUnit.SECONDS.toNanos(AUTO_RENEW_PERIOD);

    private Entity owner;

    @BeforeEach
    void setupPerTest() {
        exchangeRatePersist();
        feeSchedulesPersist();
    }

    @Nested
    class ReadOnlyTestCases {

        @BeforeEach
        void setup() {
            recordFilePersist();
            senderPersist();
            ownerPersist();
        }

        @ParameterizedTest
        @CsvSource(
                textBlock =
                        """
                    0x0000000000000000000000000000000000000413,
                    0x627306090abab3a6e1400e9345bc60c78a8bef57
                    """)
        void ethCallTokenFrozen(final String accountAddressOrAliasString) throws Exception {
            // Given
            final var tokenEntity = fungibleTokenPersist();
            final var tokenAddress = toAddress(tokenEntity.getTokenId()).toHexString();
            final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);
            tokenAccountPersist(sender.getId(), tokenEntity.getTokenId(), TokenFreezeStatusEnum.FROZEN);

            // When
            var result = contract.call_isTokenFrozen(tokenAddress, accountAddressOrAliasString)
                    .send();

            // Then
            assertThat(result).isEqualTo(true);
        }

        @ParameterizedTest
        @CsvSource(
                textBlock =
                        """
                    FUNGIBLE_COMMON, 0x0000000000000000000000000000000000000413,
                    FUNGIBLE_COMMON, 0x627306090abab3a6e1400e9345bc60c78a8bef57,
                    NON_FUNGIBLE_UNIQUE, 0x0000000000000000000000000000000000000413,
                    NON_FUNGIBLE_UNIQUE, 0x627306090abab3a6e1400e9345bc60c78a8bef57
                    """)
        void ethCallIsKycGranted(final TokenTypeEnum tokenType, final String accountAddressOrAliasString)
                throws Exception {
            // Given
            final var token = tokenType == TokenTypeEnum.FUNGIBLE_COMMON ? fungibleTokenPersist() : nftPersist();
            final var tokenAddress = toAddress(token.getTokenId()).toHexString();
            final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);
            tokenAccountPersist(sender.getId(), token.getTokenId(), TokenFreezeStatusEnum.FROZEN);

            // When
            var result = contract.call_isKycGranted(tokenAddress, accountAddressOrAliasString)
                    .send();

            // Then
            assertThat(result).isEqualTo(true);
        }

        @ParameterizedTest
        @CsvSource(
                textBlock =
                        """
                    FUNGIBLE_COMMON,
                    NON_FUNGIBLE_UNIQUE
                    """)
        void ethCallIsTokenAddress(final TokenTypeEnum tokenType) throws Exception {
            // Given
            final var tokenEntity = tokenType == TokenTypeEnum.FUNGIBLE_COMMON ? fungibleTokenPersist() : nftPersist();
            final var tokenAddress = toAddress(tokenEntity.getTokenId()).toHexString();
            final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

            // When
            var result = contract.call_isTokenAddress(tokenAddress).send();

            // Then
            assertThat(result).isEqualTo(true);
        }

        @ParameterizedTest
        @CsvSource(
                textBlock =
                        """
                    FUNGIBLE_COMMON,
                    NON_FUNGIBLE_UNIQUE
                    """)
        void ethCallGetDefaultKyc(final TokenTypeEnum tokenType) throws Exception {
            // Given
            final var tokenEntity = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                    ? fungibleTokenPersist(domainBuilder.key())
                    : nftPersist(domainBuilder.key());
            final var tokenAddress = toAddress(tokenEntity.getTokenId()).toHexString();
            final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

            // When
            var result = contract.call_getTokenDefaultKyc(tokenAddress).send();

            // Then
            assertThat(result).isEqualTo(true);
        }

        @ParameterizedTest
        @CsvSource(
                textBlock =
                        """
                    FUNGIBLE_COMMON,
                    NON_FUNGIBLE_UNIQUE
                    """)
        void ethCallGetType(final TokenTypeEnum tokenType) throws Exception {
            // Given
            final var tokenEntity = tokenType == TokenTypeEnum.FUNGIBLE_COMMON ? fungibleTokenPersist() : nftPersist();
            final var tokenAddress = toAddress(tokenEntity.getTokenId()).toHexString();
            final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

            // When
            var result = contract.call_getType(tokenAddress).send();

            // Then
            final var expected = tokenType == TokenTypeEnum.FUNGIBLE_COMMON ? BigInteger.ZERO : BigInteger.ONE;
            assertThat(result).isEqualTo(expected);
        }

        @ParameterizedTest
        @CsvSource(
                textBlock =
                        """
                    FUNGIBLE_COMMON,
                    NON_FUNGIBLE_UNIQUE
                    """)
        void ethCallGetTokenDefaultFreeze(final TokenTypeEnum tokenType) throws Exception {
            // Given
            final var tokenEntity =
                    tokenType == TokenTypeEnum.FUNGIBLE_COMMON ? fungibleTokenPersist(true) : nftPersist(true);
            final var tokenAddress = toAddress(tokenEntity.getTokenId()).toHexString();
            final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

            // When
            var result = contract.call_getTokenDefaultFreeze(tokenAddress).send();

            // Then
            assertThat(result).isEqualTo(true);
        }

        @Test
        void ethCallGetInformationForFungibleToken() throws Exception {
            // Given
            autoRenewAccountPersist();
            final var tokenEntity = fungibleTokenPersist(Key.newBuilder()
                    .setECDSASecp256K1(KEY_WITH_ECDSASecp256K1.getECDSASecp256K1())
                    .build()
                    .toByteArray());
            final var tokenAddress = toAddress(tokenEntity.getTokenId()).toHexString();
            final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

            // When
            var result =
                    contract.call_getInformationForFungibleToken(tokenAddress).send();

            // Then
            final var keyValue = new KeyValue(
                    Bool.DEFAULT,
                    org.web3j.abi.datatypes.Address.DEFAULT,
                    DynamicBytes.DEFAULT,
                    new DynamicBytes(Arrays.copyOfRange(KEY_PROTO, 2, KEY_PROTO.length)),
                    org.web3j.abi.datatypes.Address.DEFAULT);
            final var expected = new FungibleTokenInfo(
                    new TokenInfo(
                            new HederaToken(
                                    FUNGIBLE_TOKEN_NAME,
                                    FUNGIBLE_TOKEN_SYMBOL,
                                    AUTO_RENEW_ACCOUNT_ADDRESS.toHexString(),
                                    FUNGIBLE_TOKEN_MEMO,
                                    Boolean.FALSE, // infinite supply
                                    BigInteger.valueOf(FUNGIBLE_TOKEN_TOTAL_SUPPLY), // max supply
                                    Boolean.FALSE, // freeze default
                                    List.of(
                                            new TokenKey(BigInteger.valueOf(1L), keyValue),
                                            new TokenKey(BigInteger.valueOf(2L), keyValue),
                                            new TokenKey(BigInteger.valueOf(4L), keyValue),
                                            new TokenKey(BigInteger.valueOf(8L), keyValue),
                                            new TokenKey(BigInteger.valueOf(16L), keyValue),
                                            new TokenKey(BigInteger.valueOf(32L), keyValue),
                                            new TokenKey(BigInteger.valueOf(64L), keyValue)),
                                    new Expiry(
                                            BigInteger.valueOf(TimeUnit.NANOSECONDS.toSeconds(EXPIRATION_TIMESTAMP)),
                                            AUTO_RENEW_ACCOUNT_ADDRESS.toHexString(),
                                            BigInteger.valueOf(AUTO_RENEW_PERIOD))),
                            BigInteger.valueOf(FUNGIBLE_TOKEN_TOTAL_SUPPLY), // total supply
                            Boolean.FALSE,
                            Boolean.FALSE,
                            Boolean.FALSE,
                            List.of(),
                            List.of(),
                            List.of(),
                            "0x01"),
                    new BigInteger(String.valueOf(FUNGIBLE_TOKEN_DECIMALS)));
            assertThat(result).isEqualTo(expected);
        }

        @Test
        void ethCallGetInformationForNFT() throws Exception {
            // Given
            final var tokenEntity = nftPersist(Key.newBuilder()
                    .setECDSASecp256K1(KEY_WITH_ECDSASecp256K1.getECDSASecp256K1())
                    .build()
                    .toByteArray());
            final var tokenAddress = toAddress(tokenEntity.getTokenId()).toHexString();
            final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

            // When
            var result = contract.call_getInformationForNonFungibleToken(tokenAddress, BigInteger.ONE)
                    .send();

            // Then
            //            final var expected = new NonFungibleTokenInfo(
            //                    new TokenInfo(),
            //                    BigInteger.ONE, // serial number
            //                    String ownerId,
            //                    BigInteger creationTime, byte[] metadata, String spenderId
            //            );
            //            assertThat(result).isEqualTo(expected);
        }

        @ParameterizedTest
        @CsvSource(
                textBlock =
                        """
                    FUNGIBLE_COMMON,      1
                    FUNGIBLE_COMMON,      4
                    FUNGIBLE_COMMON,      8
                    FUNGIBLE_COMMON,      16
                    NON_FUNGIBLE_UNIQUE,  2
                    NON_FUNGIBLE_UNIQUE,  32
                    NON_FUNGIBLE_UNIQUE,  64
                    """)
        void ethCallGetKeyWithContractAddress(final TokenTypeEnum tokenType, final BigInteger keyType)
                throws Exception {
            // Given
            final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);
            final var contractAddress = Address.fromHexString(contract.getContractAddress());
            final var key = Key.newBuilder()
                    .setContractID(contractIdFromEvmAddress(contractAddress))
                    .build();
            final var token = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                    ? fungibleTokenPersist(key.toByteArray())
                    : nftPersist(key.toByteArray());
            final var tokenAddress = toAddress(token.getTokenId()).toHexString();
            tokenAccountPersist(sender.getId(), token.getTokenId(), TokenFreezeStatusEnum.UNFROZEN);

            // When
            var result = contract.call_getTokenKeyPublic(tokenAddress, keyType).send();

            // Then
            final var expected = new KeyValue(
                    new Bool(false),
                    new org.web3j.abi.datatypes.Address(contractAddress.toHexString()),
                    DynamicBytes.DEFAULT,
                    DynamicBytes.DEFAULT,
                    org.web3j.abi.datatypes.Address.DEFAULT);
            assertThat(result).isEqualTo(expected);
        }

        @ParameterizedTest
        @CsvSource(
                textBlock =
                        """
                    FUNGIBLE_COMMON,      1
                    FUNGIBLE_COMMON,      4
                    FUNGIBLE_COMMON,      8
                    FUNGIBLE_COMMON,      16
                    NON_FUNGIBLE_UNIQUE,  2
                    NON_FUNGIBLE_UNIQUE,  32
                    NON_FUNGIBLE_UNIQUE,  64
                    """)
        void ethCallGetKeyWithEd25519Key(final TokenTypeEnum tokenType, final BigInteger keyType) throws Exception {
            // Given
            final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);
            final var key =
                    Key.newBuilder().setEd25519(KEY_WITH_ED25519.getEd25519()).build();
            final var token = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                    ? fungibleTokenPersist(key.toByteArray())
                    : nftPersist(key.toByteArray());
            final var tokenAddress = toAddress(token.getTokenId()).toHexString();
            tokenAccountPersist(sender.getId(), token.getTokenId(), TokenFreezeStatusEnum.UNFROZEN);

            // When
            var result = contract.call_getTokenKeyPublic(tokenAddress, keyType).send();

            // Then
            final var expected = new KeyValue(
                    new Bool(false),
                    org.web3j.abi.datatypes.Address.DEFAULT,
                    new DynamicBytes(ED25519_KEY),
                    DynamicBytes.DEFAULT,
                    org.web3j.abi.datatypes.Address.DEFAULT);
            assertThat(result).isEqualTo(expected);
        }

        @ParameterizedTest
        @CsvSource(
                textBlock =
                        """
                    FUNGIBLE_COMMON,      1
                    FUNGIBLE_COMMON,      4
                    FUNGIBLE_COMMON,      8
                    FUNGIBLE_COMMON,      16
                    NON_FUNGIBLE_UNIQUE,  2
                    NON_FUNGIBLE_UNIQUE,  32
                    NON_FUNGIBLE_UNIQUE,  64
                    """)
        void ethCallGetKeyWithEcdsaKey(final TokenTypeEnum tokenType, final BigInteger keyType) throws Exception {
            // Given
            final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);
            final var key = Key.newBuilder()
                    .setECDSASecp256K1(KEY_WITH_ECDSASecp256K1.getECDSASecp256K1())
                    .build();
            final var token = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                    ? fungibleTokenPersist(key.toByteArray())
                    : nftPersist(key.toByteArray());
            final var tokenAddress = toAddress(token.getTokenId()).toHexString();
            tokenAccountPersist(sender.getId(), token.getTokenId(), TokenFreezeStatusEnum.UNFROZEN);

            // When
            var result = contract.call_getTokenKeyPublic(tokenAddress, keyType).send();

            // Then

            final var expected = new KeyValue(
                    new Bool(false),
                    org.web3j.abi.datatypes.Address.DEFAULT,
                    DynamicBytes.DEFAULT,
                    new DynamicBytes(ECDSA_KEY),
                    org.web3j.abi.datatypes.Address.DEFAULT);
            assertThat(result).isEqualTo(expected);
        }

        @ParameterizedTest
        @CsvSource(
                textBlock =
                        """
                    FUNGIBLE_COMMON,      1
                    FUNGIBLE_COMMON,      4
                    FUNGIBLE_COMMON,      8
                    FUNGIBLE_COMMON,      16
                    NON_FUNGIBLE_UNIQUE,  2
                    NON_FUNGIBLE_UNIQUE,  32
                    NON_FUNGIBLE_UNIQUE,  64
                    """)
        void ethCallGetKeyWithDelegatableContractAddress(final TokenTypeEnum tokenType, final BigInteger keyType)
                throws Exception {
            // Given
            final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);
            final var contractAddress = Address.fromHexString(contract.getContractAddress());
            final var key = Key.newBuilder()
                    .setDelegatableContractId(contractIdFromEvmAddress(contractAddress))
                    .build();
            final var token = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                    ? fungibleTokenPersist(key.toByteArray())
                    : nftPersist(key.toByteArray());
            final var tokenAddress = toAddress(token.getTokenId()).toHexString();
            tokenAccountPersist(sender.getId(), token.getTokenId(), TokenFreezeStatusEnum.UNFROZEN);

            // When
            var result = contract.call_getTokenKeyPublic(tokenAddress, keyType).send();

            // Then
            final var expected = new KeyValue(
                    new Bool(false),
                    org.web3j.abi.datatypes.Address.DEFAULT,
                    DynamicBytes.DEFAULT,
                    DynamicBytes.DEFAULT,
                    new org.web3j.abi.datatypes.Address(contractAddress.toHexString()));
            assertThat(result).isEqualTo(expected);
        }
    }

    //    @ParameterizedTest
    //    @EnumSource(ContractReadFunctions.class)
    //    void evmPrecompileReadOnlyTokenFunctionsTestEthCall(final ContractReadFunctions contractFunc) {
    //        final var functionHash = functionEncodeDecoder.functionHashFor(
    //                contractFunc.name, PRECOMPILE_TEST_CONTRACT_ABI_PATH, contractFunc.functionParameters);
    //        final var serviceParameters = serviceParametersForExecution(
    //                functionHash, PRECOMPILE_TEST_CONTRACT_ADDRESS, ETH_CALL, 0L, BlockType.LATEST);
    //        switch (contractFunc) {
    //            case GET_CUSTOM_FEES_FOR_TOKEN_WITH_FIXED_FEE -> customFeePersist(FIXED_FEE);
    //            case GET_CUSTOM_FEES_FOR_TOKEN_WITH_FRACTIONAL_FEE,
    //                    GET_INFORMATION_FOR_TOKEN_FUNGIBLE,
    //                    GET_INFORMATION_FOR_TOKEN_NFT,
    //                    GET_FUNGIBLE_TOKEN_INFO,
    //                    GET_NFT_INFO -> customFeePersist(FRACTIONAL_FEE);
    //            case GET_CUSTOM_FEES_FOR_TOKEN_WITH_ROYALTY_FEE -> customFeePersist(ROYALTY_FEE);
    //        }
    //        final var successfulResponse = functionEncodeDecoder.encodedResultFor(
    //                contractFunc.name, PRECOMPILE_TEST_CONTRACT_ABI_PATH, contractFunc.expectedResultFields);
    //
    //        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
    //    }

    //    @ParameterizedTest
    //    @EnumSource(ContractReadFunctions.class)
    //    void evmPrecompileReadOnlyTokenFunctionsTestEthEstimateGas(final ContractReadFunctions contractFunc) {
    //        final var functionHash = functionEncodeDecoder.functionHashFor(
    //                contractFunc.name, PRECOMPILE_TEST_CONTRACT_ABI_PATH, contractFunc.functionParameters);
    //        final var serviceParameters = serviceParametersForExecution(
    //                functionHash, PRECOMPILE_TEST_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 0L, BlockType.LATEST);
    //
    //        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);
    //
    //        assertThat(isWithinExpectedGasRange(
    //                        longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)),
    // expectedGasUsed))
    //                .isTrue();
    //    }

    //    @Test
    //    void nftInfoForInvalidSerialNo() {
    //        final var functionHash = functionEncodeDecoder.functionHashFor(
    //                "getInformationForNonFungibleToken", PRECOMPILE_TEST_CONTRACT_ABI_PATH, NFT_ADDRESS, 4L);
    //        final var serviceParameters = serviceParametersForExecution(
    //                functionHash, PRECOMPILE_TEST_CONTRACT_ADDRESS, ETH_CALL, 0L, BlockType.LATEST);
    //
    //        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
    //                .isInstanceOf(MirrorEvmTransactionException.class);
    //    }

    //    @Test
    //    void tokenInfoForNonTokenAccount() {
    //        final var functionHash = functionEncodeDecoder.functionHashFor(
    //                "getInformationForFungibleToken", PRECOMPILE_TEST_CONTRACT_ABI_PATH, SENDER_ADDRESS);
    //        final var serviceParameters = serviceParametersForExecution(
    //                functionHash, PRECOMPILE_TEST_CONTRACT_ADDRESS, ETH_CALL, 0L, BlockType.LATEST);
    //
    //        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
    //                .isInstanceOf(MirrorEvmTransactionException.class);
    //    }

    @Getter
    @RequiredArgsConstructor
    enum ContractReadFunctions implements ContractFunctionProviderEnum {
        GET_CUSTOM_FEES_FOR_TOKEN_WITH_FIXED_FEE(
                "getCustomFeesForToken", new Object[] {FUNGIBLE_TOKEN_ADDRESS}, new Object[] {
                    new Object[] {100L, FUNGIBLE_TOKEN_ADDRESS, false, false, SENDER_ALIAS},
                    new Object[0],
                    new Object[0]
                }),
        GET_CUSTOM_FEES_FOR_TOKEN_WITH_FRACTIONAL_FEE(
                "getCustomFeesForToken",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS},
                new Object[] {new Object[0], new Object[] {100L, 10L, 1L, 1000L, true, SENDER_ALIAS}, new Object[0]}),
        GET_CUSTOM_FEES_FOR_TOKEN_WITH_ROYALTY_FEE(
                "getCustomFeesForToken", new Object[] {FUNGIBLE_TOKEN_ADDRESS}, new Object[] {
                    new Object[0], new Object[0], new Object[] {20L, 10L, 100L, FUNGIBLE_TOKEN_ADDRESS, SENDER_ALIAS}
                }),
        GET_TOKEN_EXPIRY("getExpiryInfoForToken", new Object[] {FUNGIBLE_TOKEN_ADDRESS_WITH_EXPIRY}, new Object[] {
            1000L, AUTO_RENEW_ACCOUNT_ADDRESS, 8_000_000L
        }),
        HTS_GET_APPROVED("htsGetApproved", new Object[] {NFT_ADDRESS, 1L}, new Object[] {SENDER_ALIAS}),
        HTS_ALLOWANCE(
                "htsAllowance",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ADDRESS, SPENDER_ADDRESS},
                new Object[] {13L}),
        HTS_IS_APPROVED_FOR_ALL(
                "htsIsApprovedForAll", new Object[] {NFT_ADDRESS, SENDER_ADDRESS, SPENDER_ADDRESS}, new Object[] {true
                }),
        GET_NFT_INFO("getInformationForNonFungibleToken", new Object[] {NFT_ADDRESS, 1L}, new Object[] {
            new Object[] {
                NFT_HBAR_TOKEN_AND_KEYS,
                1_000_000_000L,
                false,
                false,
                true,
                new Object[] {0L, 0L, 0L, 0L, false, SENDER_ALIAS},
                "0x01"
            },
            1L,
            OWNER_ADDRESS,
            1475067194L,
            "NFT_METADATA_URI".getBytes(),
            SPENDER_ADDRESS
        }),
        GET_INFORMATION_FOR_TOKEN_FUNGIBLE(
                "getInformationForToken", new Object[] {FUNGIBLE_TOKEN_ADDRESS}, new Object[] {
                    FUNGIBLE_HBAR_TOKEN_AND_KEYS,
                    12345L,
                    false,
                    false,
                    true,
                    new Object[] {100L, 10L, 1L, 1000L, true, SENDER_ALIAS},
                    "0x01"
                }),
        GET_INFORMATION_FOR_TOKEN_NFT("getInformationForToken", new Object[] {NFT_ADDRESS}, new Object[] {
            NFT_HBAR_TOKEN_AND_KEYS,
            1_000_000_000L,
            false,
            false,
            true,
            new Object[] {0L, 0L, 0L, 0L, false, SENDER_ALIAS},
            "0x01"
        });

        private final String name;
        private final Object[] functionParameters;
        private final Object[] expectedResultFields;
    }

    private void senderPersist() {
        final var senderEntityId = entityIdFromEvmAddress(SENDER_ADDRESS);

        this.sender = domainBuilder
                .entity()
                .customize(e -> e.id(senderEntityId.getId())
                        .num(senderEntityId.getNum())
                        .type(ACCOUNT)
                        .evmAddress(SENDER_ALIAS.toArray())
                        .deleted(false)
                        .alias(SENDER_PUBLIC_KEY.toByteArray())
                        .balance(10000 * 100_000_000L))
                .persist();
        testWeb3jService.setSender(toAddress(sender.toEntityId()).toHexString());
    }

    private void exchangeRatePersist() {
        final long nanos = 1_234_567_890L;
        final ExchangeRateSet exchangeRatesSet = ExchangeRateSet.newBuilder()
                .setCurrentRate(ExchangeRate.newBuilder()
                        .setCentEquiv(1)
                        .setHbarEquiv(12)
                        .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(nanos))
                        .build())
                .setNextRate(ExchangeRate.newBuilder()
                        .setCentEquiv(2)
                        .setHbarEquiv(31)
                        .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(2_234_567_890L))
                        .build())
                .build();

        domainBuilder
                .fileData()
                .customize(f ->
                        f.fileData(exchangeRatesSet.toByteArray()).entityId(RatesAndFeesLoader.EXCHANGE_RATE_ENTITY_ID))
                .persist();
    }

    protected void feeSchedulesPersist() {
        final long expiry = 1_234_567_890L;
        final CurrentAndNextFeeSchedule feeSchedules = CurrentAndNextFeeSchedule.newBuilder()
                .setCurrentFeeSchedule(FeeSchedule.newBuilder()
                        .setExpiryTime(TimestampSeconds.newBuilder().setSeconds(expiry))
                        .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                                .setHederaFunctionality(EthereumTransaction)
                                .addFees(FeeData.newBuilder()
                                        .setServicedata(FeeComponents.newBuilder()
                                                .setGas(852000)
                                                .build()))))
                .setNextFeeSchedule(FeeSchedule.newBuilder()
                        .setExpiryTime(TimestampSeconds.newBuilder().setSeconds(Long.MAX_VALUE))
                        .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                                .setHederaFunctionality(EthereumTransaction)
                                .addFees(FeeData.newBuilder()
                                        .setServicedata(FeeComponents.newBuilder()
                                                .setGas(852000)
                                                .build()))))
                .build();

        domainBuilder
                .fileData()
                .customize(f -> f.fileData(feeSchedules.toByteArray())
                        .entityId(FEE_SCHEDULE_ENTITY_ID)
                        .consensusTimestamp(1001L))
                .persist();
    }

    private Token fungibleTokenPersist() {
        return fungibleTokenPersist(null);
    }

    private Token fungibleTokenPersist(boolean freezeDefault) {
        final var tokenEntity = domainBuilder
                .entity()
                .customize(e -> e.type(TOKEN).balance(1500L).memo("TestMemo"))
                .persist();

        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .freezeDefault(freezeDefault))
                .persist();

        return token;
    }

    private Token fungibleTokenPersist(final byte[] key) {
        final var tokenEntity = domainBuilder
                .entity()
                .customize(e -> e.type(TOKEN)
                        .balance(1500L)
                        .memo(FUNGIBLE_TOKEN_MEMO)
                        .key(key)
                        .autoRenewAccountId(entityIdFromEvmAddress(AUTO_RENEW_ACCOUNT_ADDRESS)
                                .getId())
                        .autoRenewPeriod(AUTO_RENEW_PERIOD)
                        .createdTimestamp(CREATED_TIMESTAMP)
                        .expirationTimestamp(EXPIRATION_TIMESTAMP))
                .persist();

        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .supplyType(TokenSupplyTypeEnum.INFINITE)
                        .name(FUNGIBLE_TOKEN_NAME)
                        .symbol(FUNGIBLE_TOKEN_SYMBOL)
                        .decimals(FUNGIBLE_TOKEN_DECIMALS)
                        .totalSupply(FUNGIBLE_TOKEN_TOTAL_SUPPLY)
                        .treasuryAccountId(entityIdFromEvmAddress(AUTO_RENEW_ACCOUNT_ADDRESS))
                        .maxSupply(FUNGIBLE_TOKEN_TOTAL_SUPPLY)
                        .kycKey(key)
                        .feeScheduleKey(key)
                        .wipeKey(key)
                        .freezeKey(key)
                        .pauseKey(key)
                        .supplyKey(key))
                .persist();

        return token;
    }

    private Token nftPersist() {
        return nftPersist(null);
    }

    private Token nftPersist(boolean freezeDefault) {
        final var nftEntity = domainBuilder
                .entity()
                .customize(e -> e.type(TOKEN).balance(1500L))
                .persist();

        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(nftEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .freezeDefault(freezeDefault))
                .persist();

        domainBuilder
                .nft()
                .customize(n -> n.accountId(owner.toEntityId())
                        .spender(owner.toEntityId())
                        .metadata("NFT_METADATA_URI".getBytes())
                        .accountId(owner.toEntityId())
                        .timestampRange(Range.atLeast(1475067194949034022L))
                        .tokenId(nftEntity.getId()))
                .persist();
        return token;
    }

    private Token nftPersist(final byte[] key) {
        final var nftEntity = domainBuilder
                .entity()
                .customize(e -> e.type(TOKEN).balance(1500L).key(key))
                .persist();

        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(nftEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .kycKey(key)
                        .feeScheduleKey(key)
                        .freezeKey(key)
                        .pauseKey(key)
                        .wipeKey(key)
                        .supplyKey(key)
                        .wipeKey(key))
                .persist();

        domainBuilder
                .nft()
                .customize(n -> n.accountId(owner.toEntityId())
                        .spender(owner.toEntityId())
                        .metadata("NFT_METADATA_URI".getBytes())
                        .accountId(owner.toEntityId())
                        .timestampRange(Range.atLeast(1475067194949034022L))
                        .tokenId(nftEntity.getId()))
                .persist();
        return token;
    }

    private EntityId autoRenewAccountPersist() {
        final var autoRenewEntityId = entityIdFromEvmAddress(AUTO_RENEW_ACCOUNT_ADDRESS);

        domainBuilder
                .entity()
                .customize(e -> e.id(autoRenewEntityId.getId())
                        .num(autoRenewEntityId.getNum())
                        .evmAddress(null)
                        .alias(toEvmAddress(autoRenewEntityId)))
                .persist();
        return autoRenewEntityId;
    }

    private void tokenAccountPersist(
            final long accountEntityId, final long tokenEntityId, final TokenFreezeStatusEnum freezeStatus) {
        domainBuilder
                .tokenAccount()
                .customize(e -> e.freezeStatus(freezeStatus)
                        .accountId(accountEntityId)
                        .tokenId(tokenEntityId)
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .associated(true)
                        .balance(12L))
                .persist();
    }

    private void ownerPersist() {
        this.owner = domainBuilder
                .entity()
                .customize(e -> e.balance(10000 * 100_000_000L))
                .persist();
    }

    private void recordFilePersist() {
        domainBuilder.recordFile().persist();
    }
}
