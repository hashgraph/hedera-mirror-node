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

import static com.hedera.mirror.web3.service.AbstractContractCallServiceTest.getKeyWithContractId;
import static com.hedera.mirror.web3.service.AbstractContractCallServiceTest.getKeyWithDelegatableContractId;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.*;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.EMPTY_UNTRIMMED_ADDRESS;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.ESTIMATE_GAS_ERROR_MESSAGE;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.KEY_WITH_ECDSA_TYPE;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.KEY_WITH_ED_25519_TYPE;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.LEDGER_ID;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.TRANSACTION_GAS_LIMIT;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.isWithinExpectedGasRange;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.longValueOf;
import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.CustomFee;
import com.hedera.mirror.common.domain.token.FallbackFee;
import com.hedera.mirror.common.domain.token.FractionalFee;
import com.hedera.mirror.common.domain.token.RoyaltyFee;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.common.domain.token.TokenPauseStatusEnum;
import com.hedera.mirror.common.domain.token.TokenSupplyTypeEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.service.model.ContractExecutionParameters;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.mirror.web3.web3j.TestWeb3jService;
import com.hedera.mirror.web3.web3j.generated.ModificationPrecompileTestContract;
import com.hedera.mirror.web3.web3j.generated.ModificationPrecompileTestContract.AccountAmount;
import com.hedera.mirror.web3.web3j.generated.ModificationPrecompileTestContract.Expiry;
import com.hedera.mirror.web3.web3j.generated.ModificationPrecompileTestContract.HederaToken;
import com.hedera.mirror.web3.web3j.generated.ModificationPrecompileTestContract.NftTransfer;
import com.hedera.mirror.web3.web3j.generated.ModificationPrecompileTestContract.TokenKey;
import com.hedera.mirror.web3.web3j.generated.ModificationPrecompileTestContract.TokenTransferList;
import com.hedera.mirror.web3.web3j.generated.ModificationPrecompileTestContract.TransferList;
import com.hedera.mirror.web3.web3j.generated.PrecompileTestContract;
import com.hedera.mirror.web3.web3j.generated.PrecompileTestContract.FixedFee;
import com.hedera.mirror.web3.web3j.generated.PrecompileTestContract.FungibleTokenInfo;
import com.hedera.mirror.web3.web3j.generated.PrecompileTestContract.KeyValue;
import com.hedera.mirror.web3.web3j.generated.PrecompileTestContract.NonFungibleTokenInfo;
import com.hedera.mirror.web3.web3j.generated.PrecompileTestContract.TokenInfo;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hedera.services.store.contracts.precompile.codec.KeyValueWrapper.KeyValueType;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Key.KeyCase;
import com.swirlds.base.time.Time;
import jakarta.annotation.Resource;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.annotation.Import;
import org.web3j.abi.DefaultFunctionReturnDecoder;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.tx.Contract;

@Import(TestWeb3jService.Web3jTestConfiguration.class)
class ContractCallServicePrecompileTest extends ContractCallTestSetup {

    @Resource
    private TestWeb3jService testWeb3jService;

    @BeforeEach
    void setup() {
        domainBuilder.recordFile().persist();
        final var sender = persistAccountEntity();
        testWeb3jService.setSender(getAliasFromEntity(sender));
    }

    @AfterEach
    void cleanup() {
        testWeb3jService.setEstimateGas(false);
        testWeb3jService.setSender("");
    }

    @Test
    void isTokenFrozen() throws Exception {
        // Given
        final var account = persistAccountEntity();
        final var tokenEntity = persistTokenEntity();
        domainBuilder
                .token()
                .customize(
                        t -> t.tokenId(tokenEntity.getId()).freezeDefault(true).type(TokenTypeEnum.FUNGIBLE_COMMON))
                .persist();
        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(account.getId())
                        .freezeStatus(TokenFreezeStatusEnum.FROZEN))
                .persist();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall =
                contract.call_isTokenFrozen(getAddressFromEntity(tokenEntity), getAddressFromEntity(account));

        // Then
        assertThat(functionCall.send()).isTrue();

        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void isTokenFrozenWithAlias() throws Exception {
        // Given
        final var account = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.ACCOUNT)
                        .alias(SENDER_PUBLIC_KEY.toByteArray())
                        .evmAddress(SENDER_ALIAS.toArray()))
                .persist();
        final var tokenEntity = persistTokenEntity();
        domainBuilder
                .token()
                .customize(
                        t -> t.tokenId(tokenEntity.getId()).freezeDefault(true).type(TokenTypeEnum.FUNGIBLE_COMMON))
                .persist();
        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(account.getId())
                        .freezeStatus(TokenFreezeStatusEnum.FROZEN))
                .persist();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall =
                contract.call_isTokenFrozen(getAddressFromEntity(tokenEntity), getAliasFromEntity(account));

        // Then
        assertThat(functionCall.send()).isTrue();

        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void isKycGranted() throws Exception {
        // Given
        final var account = persistAccountEntity();
        final var tokenEntity = persistFungibleToken();
        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(account.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED))
                .persist();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall =
                contract.call_isKycGranted(getAddressFromEntity(tokenEntity), getAddressFromEntity(account));

        // Then
        assertThat(functionCall.send()).isTrue();

        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void isKycGrantedWithAlias() throws Exception {
        // Given
        final Address senderAlias = Address.wrap(Bytes.wrap(
                recoverAddressFromPubKey(SENDER_PUBLIC_KEY.substring(2).toByteArray())));

        final var account = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.ACCOUNT)
                        .alias(SENDER_PUBLIC_KEY.toByteArray())
                        .evmAddress(senderAlias.toArray()))
                .persist();
        final var tokenEntity = persistFungibleToken();
        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(account.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED))
                .persist();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall =
                contract.call_isKycGranted(getAddressFromEntity(tokenEntity), getAliasFromEntity(account));

        // Then
        assertThat(functionCall.send()).isTrue();

        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void isKycGrantedForNFT() throws Exception {
        // Given
        final var account = persistAccountEntity();
        final var tokenEntity = persistNft();
        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(account.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED))
                .persist();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall =
                contract.call_isKycGranted(getAddressFromEntity(tokenEntity), getAddressFromEntity(account));

        // Then
        assertThat(functionCall.send()).isTrue();

        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void isKycGrantedForNFTWithAlias() throws Exception {
        // Given
        final Address senderAlias = Address.wrap(Bytes.wrap(
                recoverAddressFromPubKey(SENDER_PUBLIC_KEY.substring(2).toByteArray())));

        final var account = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.ACCOUNT)
                        .alias(SENDER_PUBLIC_KEY.toByteArray())
                        .evmAddress(senderAlias.toArray()))
                .persist();
        final var tokenEntity = persistNft();
        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(account.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED))
                .persist();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall =
                contract.call_isKycGranted(getAddressFromEntity(tokenEntity), getAliasFromEntity(account));

        // Then
        assertThat(functionCall.send()).isTrue();

        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void isTokenAddress() throws Exception {
        // Given
        final var tokenEntity = persistFungibleToken();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_isTokenAddress(getAddressFromEntity(tokenEntity));

        // Then
        assertThat(functionCall.send()).isTrue();

        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void isTokenAddressNFT() throws Exception {
        // Given
        final var tokenEntity = persistNft();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_isTokenAddress(getAddressFromEntity(tokenEntity));

        // Then
        assertThat(functionCall.send()).isTrue();

        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void getDefaultKycToken() throws Exception {
        // Given
        domainBuilder.recordFile().customize(f -> f.index(0L)).persist();
        final var tokenEntity = persistTokenEntity();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .type(TokenTypeEnum.FUNGIBLE_COMMON))
                .persist();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_getTokenDefaultKyc(getAddressFromEntity(tokenEntity));

        // Then
        assertThat(functionCall.send()).isTrue();

        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void getDefaultKycNFT() throws Exception {
        // Given
        final var tokenEntity = persistTokenEntity();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE))
                .persist();
        domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId()).serialNumber(1L))
                .persist();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_getTokenDefaultKyc(getAddressFromEntity(tokenEntity));

        // Then
        assertThat(functionCall.send()).isTrue();

        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void getTokenType() throws Exception {
        // Given
        final var tokenEntity = persistFungibleToken();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_getType(getAddressFromEntity(tokenEntity));

        // Then
        assertThat(functionCall.send()).isEqualTo(BigInteger.ZERO);

        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void getTokenTypeNFT() throws Exception {
        // Given
        final var tokenEntity = persistNft();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_getType(getAddressFromEntity(tokenEntity));

        // Then
        assertThat(functionCall.send()).isEqualTo(BigInteger.ONE);

        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void getTokenDefaultFreeze() throws Exception {
        // Given
        final var tokenEntity = persistTokenEntity();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .freezeDefault(true))
                .persist();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_getTokenDefaultFreeze(getAddressFromEntity(tokenEntity));

        // Then
        assertThat(functionCall.send()).isTrue();

        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void getNFTDefaultFreeze() throws Exception {
        // Given
        final var tokenEntity = persistTokenEntity();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .freezeDefault(true))
                .persist();
        domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId()).serialNumber(1L))
                .persist();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_getTokenDefaultFreeze(getAddressFromEntity(tokenEntity));

        // Then
        assertThat(functionCall.send()).isTrue();

        testEstimateGas(functionCall, contract, 0L);
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
    FUNGIBLE_COMMON, ECDSA_SECPK256K1, ADMIN_KEY
    FUNGIBLE_COMMON, ECDSA_SECPK256K1, KYC_KEY
    FUNGIBLE_COMMON, ECDSA_SECPK256K1, FREEZE_KEY
    FUNGIBLE_COMMON, ECDSA_SECPK256K1, WIPE_KEY
    FUNGIBLE_COMMON, ECDSA_SECPK256K1, SUPPLY_KEY
    FUNGIBLE_COMMON, ECDSA_SECPK256K1, FEE_SCHEDULE_KEY
    FUNGIBLE_COMMON, ECDSA_SECPK256K1, PAUSE_KEY
    FUNGIBLE_COMMON, ED25519, ADMIN_KEY
    FUNGIBLE_COMMON, ED25519, FREEZE_KEY
    FUNGIBLE_COMMON, ED25519, WIPE_KEY
    FUNGIBLE_COMMON, ED25519, SUPPLY_KEY
    FUNGIBLE_COMMON, ED25519, FEE_SCHEDULE_KEY
    FUNGIBLE_COMMON, ED25519, PAUSE_KEY
    FUNGIBLE_COMMON, CONTRACT_ID, ADMIN_KEY
    FUNGIBLE_COMMON, CONTRACT_ID, FREEZE_KEY
    FUNGIBLE_COMMON, CONTRACT_ID, WIPE_KEY
    FUNGIBLE_COMMON, CONTRACT_ID, SUPPLY_KEY
    FUNGIBLE_COMMON, CONTRACT_ID, FEE_SCHEDULE_KEY
    FUNGIBLE_COMMON, CONTRACT_ID, PAUSE_KEY
    FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID, ADMIN_KEY
    FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID, FREEZE_KEY
    FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID, WIPE_KEY
    FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID, SUPPLY_KEY
    FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID, FEE_SCHEDULE_KEY
    FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID, PAUSE_KEY
    NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, ADMIN_KEY
    NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, KYC_KEY
    NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, FREEZE_KEY
    NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, WIPE_KEY
    NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, SUPPLY_KEY
    NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, FEE_SCHEDULE_KEY
    NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, PAUSE_KEY
    NON_FUNGIBLE_UNIQUE, ED25519, ADMIN_KEY
    NON_FUNGIBLE_UNIQUE, ED25519, FREEZE_KEY
    NON_FUNGIBLE_UNIQUE, ED25519, WIPE_KEY
    NON_FUNGIBLE_UNIQUE, ED25519, SUPPLY_KEY
    NON_FUNGIBLE_UNIQUE, ED25519, FEE_SCHEDULE_KEY
    NON_FUNGIBLE_UNIQUE, ED25519, PAUSE_KEY
    NON_FUNGIBLE_UNIQUE, CONTRACT_ID, ADMIN_KEY
    NON_FUNGIBLE_UNIQUE, CONTRACT_ID, FREEZE_KEY
    NON_FUNGIBLE_UNIQUE, CONTRACT_ID, WIPE_KEY
    NON_FUNGIBLE_UNIQUE, CONTRACT_ID, SUPPLY_KEY
    NON_FUNGIBLE_UNIQUE, CONTRACT_ID, FEE_SCHEDULE_KEY
    NON_FUNGIBLE_UNIQUE, CONTRACT_ID, PAUSE_KEY
    NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID, ADMIN_KEY
    NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID, FREEZE_KEY
    NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID, WIPE_KEY
    NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID, SUPPLY_KEY
    NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID, FEE_SCHEDULE_KEY
    NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID, PAUSE_KEY
""")
    void getTokenKey(
            final TokenTypeEnum tokenType,
            final KeyValueType keyValueType,
            final AbstractContractCallServiceTest.KeyType keyType)
            throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        final var tokenEntity = getTokenWithKey(tokenType, keyValueType, keyType, contract);

        // When
        final var functionCall =
                contract.call_getTokenKeyPublic(getAddressFromEntity(tokenEntity), keyType.getKeyTypeNumeric());

        final var expectedKey = getKeyValueForType(keyValueType, contract.getContractAddress());

        // Then
        assertThat(functionCall.send()).isEqualTo(expectedKey);

        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void getCustomFeesForTokenWithFixedFee() throws Exception {
        // Given
        final var collectorAccount = persistAccountEntity();
        final var tokenEntity = persistFungibleToken();
        final var fixedFee = com.hedera.mirror.common.domain.token.FixedFee.builder()
                .amount(100L)
                .collectorAccountId(collectorAccount.toEntityId())
                .denominatingTokenId(tokenEntity.toEntityId())
                .build();
        domainBuilder
                .customFee()
                .customize(f -> f.fixedFees(List.of(fixedFee))
                        .fractionalFees(List.of())
                        .royaltyFees(List.of())
                        .tokenId(tokenEntity.getId()))
                .persist();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_getCustomFeesForToken(getAddressFromEntity(tokenEntity));

        final var expectedFee = new FixedFee(
                BigInteger.valueOf(100L),
                getAddressFromEntity(tokenEntity),
                false,
                false,
                Address.fromHexString(
                                Bytes.wrap(collectorAccount.getEvmAddress()).toHexString())
                        .toHexString());

        // Then
        assertThat(functionCall.send().component1().getFirst()).isEqualTo(expectedFee);

        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void getCustomFeesForTokenWithFractionalFee() throws Exception {
        // Given
        final var collectorAccount = persistAccountEntity();
        final var tokenEntity = persistFungibleToken();
        final var fractionalFee = FractionalFee.builder()
                .collectorAccountId(collectorAccount.toEntityId())
                .denominator(10L)
                .minimumAmount(1L)
                .maximumAmount(1000L)
                .netOfTransfers(true)
                .numerator(100L)
                .build();
        domainBuilder
                .customFee()
                .customize(f -> f.fractionalFees(List.of(fractionalFee))
                        .fixedFees(List.of())
                        .royaltyFees(List.of())
                        .tokenId(tokenEntity.getId()))
                .persist();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_getCustomFeesForToken(getAddressFromEntity(tokenEntity));

        final var expectedFee = new PrecompileTestContract.FractionalFee(
                BigInteger.valueOf(100L),
                BigInteger.valueOf(10L),
                BigInteger.valueOf(1L),
                BigInteger.valueOf(1000L),
                true,
                Address.fromHexString(
                                Bytes.wrap(collectorAccount.getEvmAddress()).toHexString())
                        .toHexString());

        // Then
        assertThat(functionCall.send().component2().getFirst()).isEqualTo(expectedFee);

        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void getCustomFeesForTokenWithRoyaltyFee() throws Exception {
        // Given
        final var collectorAccount = persistAccountEntity();
        final var tokenEntity = persistFungibleToken();
        final var royaltyFee = RoyaltyFee.builder()
                .collectorAccountId(collectorAccount.toEntityId())
                .denominator(10L)
                .fallbackFee(FallbackFee.builder()
                        .amount(100L)
                        .denominatingTokenId(tokenEntity.toEntityId())
                        .build())
                .numerator(20L)
                .build();
        domainBuilder
                .customFee()
                .customize(f -> f.royaltyFees(List.of(royaltyFee))
                        .fixedFees(List.of())
                        .fractionalFees(List.of())
                        .tokenId(tokenEntity.getId()))
                .persist();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_getCustomFeesForToken(getAddressFromEntity(tokenEntity));

        final var expectedFee = new PrecompileTestContract.RoyaltyFee(
                BigInteger.valueOf(20L),
                BigInteger.valueOf(10L),
                BigInteger.valueOf(100L),
                EntityIdUtils.asHexedEvmAddress(
                        new Id(tokenEntity.getShard(), tokenEntity.getRealm(), tokenEntity.getNum())),
                false,
                Address.fromHexString(
                                Bytes.wrap(collectorAccount.getEvmAddress()).toHexString())
                        .toHexString());

        // Then
        assertThat(functionCall.send().component3().getFirst()).isEqualTo(expectedFee);

        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void getExpiryForToken() throws Exception {
        // Given
        final var expiryPeriod = 9999999999999L;
        final var autoRenewExpiry = 100000000L;
        final var autoRenewAccount = persistAccountEntity();
        final var tokenEntity = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.TOKEN)
                        .autoRenewAccountId(autoRenewAccount.getId())
                        .expirationTimestamp(expiryPeriod)
                        .autoRenewPeriod(autoRenewExpiry))
                .persist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(TokenTypeEnum.FUNGIBLE_COMMON))
                .persist();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_getExpiryInfoForToken(getAddressFromEntity(tokenEntity));

        final var expectedExpiry = new PrecompileTestContract.Expiry(
                BigInteger.valueOf(expiryPeriod).divide(BigInteger.valueOf(1_000_000_000L)),
                Address.fromHexString(
                                Bytes.wrap(autoRenewAccount.getEvmAddress()).toHexString())
                        .toHexString(),
                BigInteger.valueOf(autoRenewExpiry));

        // Then
        assertThat(functionCall.send()).isEqualTo(expectedExpiry);

        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void getAllowanceForToken() throws Exception {
        // Given
        final var amountGranted = 50L;
        final var owner = persistAccountEntity();
        final var spender = persistAccountEntity();
        final var tokenEntity = persistFungibleToken();

        domainBuilder
                .tokenAllowance()
                .customize(a -> a.tokenId(tokenEntity.getId())
                        .owner(owner.getNum())
                        .spender(spender.getNum())
                        .amount(amountGranted)
                        .amountGranted(amountGranted))
                .persist();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_htsAllowance(
                getAddressFromEntity(tokenEntity), getAliasFromEntity(owner), getAliasFromEntity(spender));

        // Then
        assertThat(functionCall.send()).isEqualTo(BigInteger.valueOf(amountGranted));

        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void isApprovedForAllNFT() throws Exception {
        // Given
        final var owner = persistAccountEntity();
        final var spender = persistAccountEntity();
        final var tokenEntity = persistNft();

        domainBuilder
                .nftAllowance()
                .customize(a -> a.tokenId(tokenEntity.getId())
                        .owner(owner.getNum())
                        .spender(spender.getNum())
                        .approvedForAll(true))
                .persist();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_htsIsApprovedForAll(
                getAddressFromEntity(tokenEntity), getAliasFromEntity(owner), getAliasFromEntity(spender));

        // Then
        assertThat(functionCall.send()).isEqualTo(Boolean.TRUE);

        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void getFungibleTokenInfo() throws Exception {
        // Given
        final var treasury = persistAccountEntity();
        final var feeCollector = persistAccountEntity();
        final var tokenEntity =
                domainBuilder.entity().customize(e -> e.type(EntityType.TOKEN)).persist();
        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .treasuryAccountId(treasury.toEntityId()))
                .persist();

        final var customFees =
                persistCustomFeesWithFeeCollector(feeCollector, tokenEntity, TokenTypeEnum.FUNGIBLE_COMMON);

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_getInformationForFungibleToken(getAddressFromEntity(tokenEntity));

        final var expectedTokenKeys = getExpectedTokenKeys(tokenEntity, token);

        final var expectedExpiry = new PrecompileTestContract.Expiry(
                BigInteger.valueOf(tokenEntity.getExpirationTimestamp()).divide(BigInteger.valueOf(1_000_000_000L)),
                Address.ZERO.toHexString(),
                BigInteger.valueOf(tokenEntity.getAutoRenewPeriod()));
        final var expectedHederaToken = new PrecompileTestContract.HederaToken(
                token.getName(),
                token.getSymbol(),
                getAddressFromEvmAddress(treasury.getEvmAddress()),
                tokenEntity.getMemo(),
                token.getSupplyType().equals(TokenSupplyTypeEnum.FINITE),
                BigInteger.valueOf(token.getMaxSupply()),
                token.getFreezeDefault(),
                expectedTokenKeys,
                expectedExpiry);

        final var fixedFees = new ArrayList<FixedFee>();
        fixedFees.add(getFixedFee(customFees.getFixedFees().getFirst(), feeCollector));

        final var fractionalFees = new ArrayList<PrecompileTestContract.FractionalFee>();
        fractionalFees.add(getFractionalFee(customFees.getFractionalFees().getFirst(), feeCollector));

        final var royaltyFees = new ArrayList<PrecompileTestContract.RoyaltyFee>();

        final var expectedTokenInfo = new TokenInfo(
                expectedHederaToken,
                BigInteger.valueOf(token.getTotalSupply()),
                tokenEntity.getDeleted(),
                false,
                false,
                fixedFees,
                fractionalFees,
                royaltyFees,
                LEDGER_ID);
        final var expectedFungibleTokenInfo =
                new FungibleTokenInfo(expectedTokenInfo, BigInteger.valueOf(token.getDecimals()));

        // Then
        assertThat(functionCall.send()).isEqualTo(expectedFungibleTokenInfo);

        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void getNonFungibleTokenInfo() throws Exception {
        // Given
        final var owner = persistAccountEntity();
        final var treasury = persistAccountEntity();
        final var feeCollector = persistAccountEntity();
        final var tokenEntity =
                domainBuilder.entity().customize(e -> e.type(EntityType.TOKEN)).persist();
        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(treasury.toEntityId()))
                .persist();
        final var nft = domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId()).serialNumber(1L).accountId(owner.toEntityId()))
                .persist();

        final var customFees =
                persistCustomFeesWithFeeCollector(feeCollector, tokenEntity, TokenTypeEnum.NON_FUNGIBLE_UNIQUE);

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall =
                contract.call_getInformationForNonFungibleToken(getAddressFromEntity(tokenEntity), BigInteger.ONE);

        final var expectedTokenKeys = getExpectedTokenKeys(tokenEntity, token);

        final var expectedExpiry = new PrecompileTestContract.Expiry(
                BigInteger.valueOf(tokenEntity.getExpirationTimestamp()).divide(BigInteger.valueOf(1_000_000_000L)),
                Address.ZERO.toHexString(),
                BigInteger.valueOf(tokenEntity.getAutoRenewPeriod()));
        final var expectedHederaToken = new PrecompileTestContract.HederaToken(
                token.getName(),
                token.getSymbol(),
                getAddressFromEvmAddress(treasury.getEvmAddress()),
                tokenEntity.getMemo(),
                token.getSupplyType().equals(TokenSupplyTypeEnum.FINITE),
                BigInteger.valueOf(token.getMaxSupply()),
                token.getFreezeDefault(),
                expectedTokenKeys,
                expectedExpiry);

        final var fixedFees = new ArrayList<FixedFee>();
        fixedFees.add(getFixedFee(customFees.getFixedFees().getFirst(), feeCollector));

        final var fractionalFees = new ArrayList<PrecompileTestContract.FractionalFee>();

        final var royaltyFees = new ArrayList<PrecompileTestContract.RoyaltyFee>();
        royaltyFees.add(getRoyaltyFee(customFees.getRoyaltyFees().getFirst(), feeCollector));

        final var expectedTokenInfo = new TokenInfo(
                expectedHederaToken,
                BigInteger.valueOf(token.getTotalSupply()),
                tokenEntity.getDeleted(),
                false,
                false,
                fixedFees,
                fractionalFees,
                royaltyFees,
                LEDGER_ID);
        final var expectedNonFungibleTokenInfo = new NonFungibleTokenInfo(
                expectedTokenInfo,
                BigInteger.valueOf(nft.getSerialNumber()),
                getAddressFromEntity(owner),
                BigInteger.valueOf(token.getCreatedTimestamp()).divide(BigInteger.valueOf(1_000_000_000L)),
                nft.getMetadata(),
                Address.ZERO.toHexString());

        // Then
        assertThat(functionCall.send()).isEqualTo(expectedNonFungibleTokenInfo);

        testEstimateGas(functionCall, contract, 0L);
    }

    @ParameterizedTest
    @EnumSource(TokenTypeEnum.class)
    void getTokenInfo(final TokenTypeEnum tokenType) throws Exception {
        // Given
        final var treasury = persistAccountEntity();
        final var feeCollector = persistAccountEntity();
        final var tokenEntity =
                domainBuilder.entity().customize(e -> e.type(EntityType.TOKEN)).persist();
        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(tokenType).treasuryAccountId(treasury.toEntityId()))
                .persist();

        final var customFees = persistCustomFeesWithFeeCollector(feeCollector, tokenEntity, tokenType);

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_getInformationForToken(getAddressFromEntity(tokenEntity));

        final var expectedTokenKeys = getExpectedTokenKeys(tokenEntity, token);

        final var expectedExpiry = new PrecompileTestContract.Expiry(
                BigInteger.valueOf(tokenEntity.getExpirationTimestamp()).divide(BigInteger.valueOf(1_000_000_000L)),
                Address.ZERO.toHexString(),
                BigInteger.valueOf(tokenEntity.getAutoRenewPeriod()));
        final var expectedHederaToken = new PrecompileTestContract.HederaToken(
                token.getName(),
                token.getSymbol(),
                getAddressFromEvmAddress(treasury.getEvmAddress()),
                tokenEntity.getMemo(),
                token.getSupplyType().equals(TokenSupplyTypeEnum.FINITE),
                BigInteger.valueOf(token.getMaxSupply()),
                token.getFreezeDefault(),
                expectedTokenKeys,
                expectedExpiry);

        final var fixedFees = new ArrayList<FixedFee>();
        fixedFees.add(getFixedFee(customFees.getFixedFees().getFirst(), feeCollector));

        final var fractionalFees = new ArrayList<PrecompileTestContract.FractionalFee>();
        if (TokenTypeEnum.FUNGIBLE_COMMON.equals(tokenType)) {
            fractionalFees.add(getFractionalFee(customFees.getFractionalFees().getFirst(), feeCollector));
        }

        final var royaltyFees = new ArrayList<PrecompileTestContract.RoyaltyFee>();
        if (TokenTypeEnum.NON_FUNGIBLE_UNIQUE.equals(tokenType)) {
            royaltyFees.add(getRoyaltyFee(customFees.getRoyaltyFees().getFirst(), feeCollector));
        }

        final var expectedTokenInfo = new TokenInfo(
                expectedHederaToken,
                BigInteger.valueOf(token.getTotalSupply()),
                tokenEntity.getDeleted(),
                false,
                false,
                fixedFees,
                fractionalFees,
                royaltyFees,
                LEDGER_ID);

        // Then
        assertThat(functionCall.send()).isEqualTo(expectedTokenInfo);

        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void transferFrom() throws Exception {
        // Given
        final var owner = persistAccountEntity();
        final var spender = persistAccountEntity();
        final var recipient = persistAccountEntity();

        final var tokenEntity =
                domainBuilder.entity().customize(e -> e.type(EntityType.TOKEN)).persist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .treasuryAccountId(owner.toEntityId()))
                .persist();

        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(spender.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .associated(true))
                .persist();
        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(recipient.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .associated(true))
                .persist();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var contractId = testWeb3jService.getEntityId();
        domainBuilder
                .tokenAccount()
                .customize(ta ->
                        ta.tokenId(tokenEntity.getId()).accountId(contractId).associated(true))
                .persist();

        domainBuilder
                .tokenAllowance()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .spender(testWeb3jService.getEntityId())
                        .amount(10L)
                        .owner(spender.getId()))
                .persist();

        // When
        final var functionCall = contract.call_transferFrom(
                getAddressFromEntity(tokenEntity),
                getAliasFromEntity(spender),
                getAliasFromEntity(recipient),
                BigInteger.ONE);
        functionCall.send();

        // Then
        testEstimateGas(functionCall, contract, 0L);
    }

    @ParameterizedTest
    @CsvSource("1, 0")
    void approve(final BigInteger allowance) throws Exception {
        // Given
        final var spender = persistAccountEntity();
        final var tokenEntity = persistFungibleToken();

        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(spender.getId())
                        .associated(true))
                .persist();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var contractId = testWeb3jService.getEntityId();
        domainBuilder
                .tokenAccount()
                .customize(ta ->
                        ta.tokenId(tokenEntity.getId()).accountId(contractId).associated(true))
                .persist();

        // When
        final var functionCall = contract.call_approveExternal(
                getAddressFromEntity(tokenEntity), getAddressFromEntity(spender), allowance);
        functionCall.send();

        // Then
        testEstimateGas(functionCall, contract, 0L);
    }

    @ParameterizedTest
    @CsvSource("true, false")
    void approveNFT(final Boolean approve) throws Exception {
        // Given
        final var owner = persistAccountEntity();
        final var spender = persistAccountEntity();

        final var tokenEntity =
                domainBuilder.entity().customize(e -> e.type(EntityType.TOKEN)).persist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE))
                .persist();

        domainBuilder
                .tokenAccount()
                .customize(ta ->
                        ta.tokenId(tokenEntity.getId()).accountId(owner.getId()).associated(true))
                .persist();
        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(spender.getId())
                        .associated(true))
                .persist();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var contractId = testWeb3jService.getEntityId();
        domainBuilder
                .tokenAccount()
                .customize(ta ->
                        ta.tokenId(tokenEntity.getId()).accountId(contractId).associated(true))
                .persist();

        domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId())
                        .serialNumber(1L)
                        .accountId(EntityIdUtils.entityIdFromId(new Id(0, 0, contractId))))
                .persist();

        // When
        final var functionCall = contract.call_approveNFTExternal(
                getAddressFromEntity(tokenEntity),
                approve ? getAddressFromEntity(spender) : Address.ZERO.toHexString(),
                BigInteger.ONE);

        functionCall.send();

        // Then
        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void setApprovalForAll() throws Exception {
        // Given
        final var spender = persistAccountEntity();

        final var tokenEntity =
                domainBuilder.entity().customize(e -> e.type(EntityType.TOKEN)).persist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE))
                .persist();

        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(spender.getId())
                        .associated(true))
                .persist();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var contractId = testWeb3jService.getEntityId();
        domainBuilder
                .tokenAccount()
                .customize(ta ->
                        ta.tokenId(tokenEntity.getId()).accountId(contractId).associated(true))
                .persist();

        domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId())
                        .serialNumber(1L)
                        .accountId(EntityIdUtils.entityIdFromId(new Id(0, 0, contractId))))
                .persist();

        // When
        final var functionCall = contract.call_setApprovalForAllExternal(
                getAddressFromEntity(tokenEntity), getAddressFromEntity(spender), Boolean.TRUE);
        functionCall.send();

        // Then
        testEstimateGas(functionCall, contract, 0L);
    }

    @ParameterizedTest
    @CsvSource("true, false")
    void associateToken(final Boolean single) throws Exception {
        // Given
        final var notAssociatedAccount = persistAccountEntity();

        final var tokenEntity =
                domainBuilder.entity().customize(e -> e.type(EntityType.TOKEN)).persist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(TokenTypeEnum.FUNGIBLE_COMMON))
                .persist();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = single
                ? contract.call_associateTokenExternal(
                        getAddressFromEntity(notAssociatedAccount), getAddressFromEntity(tokenEntity))
                : contract.call_associateTokensExternal(
                        getAddressFromEntity(notAssociatedAccount), List.of(getAddressFromEntity(tokenEntity)));

        functionCall.send();

        // Then
        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void associateTokenHRC() throws Exception {
        // Given
        final var tokenEntity =
                domainBuilder.entity().customize(e -> e.type(EntityType.TOKEN)).persist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(TokenTypeEnum.FUNGIBLE_COMMON))
                .persist();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_associateWithRedirect(getAddressFromEntity(tokenEntity));
        functionCall.send();

        // Then
        testEstimateGas(functionCall, contract, 0L);
    }

    @ParameterizedTest
    @CsvSource("true, false")
    void dissociateToken(final Boolean single) throws Exception {
        // Given
        final var associatedAccount = persistAccountEntity();

        final var tokenEntity =
                domainBuilder.entity().customize(e -> e.type(EntityType.TOKEN)).persist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(TokenTypeEnum.FUNGIBLE_COMMON))
                .persist();

        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(associatedAccount.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .associated(true))
                .persist();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = single
                ? contract.call_dissociateTokenExternal(
                        getAddressFromEntity(associatedAccount), getAddressFromEntity(tokenEntity))
                : contract.call_dissociateTokensExternal(
                        getAddressFromEntity(associatedAccount), List.of(getAddressFromEntity(tokenEntity)));
        functionCall.send();

        // Then
        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void dissociateTokenHRC() throws Exception {
        // Given
        final var tokenEntity =
                domainBuilder.entity().customize(e -> e.type(EntityType.TOKEN)).persist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(TokenTypeEnum.FUNGIBLE_COMMON))
                .persist();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var contractId = testWeb3jService.getEntityId();
        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(contractId)
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .associated(true))
                .persist();

        // When
        final var functionCall = contract.call_dissociateWithRedirect(getAddressFromEntity(tokenEntity));
        functionCall.send();

        // Then
        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void mintFungibleToken() throws Exception {
        // Given
        final var treasury = persistAccountEntity();
        final var tokenEntity = persistTokenEntity();
        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .treasuryAccountId(treasury.toEntityId()))
                .persist();
        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(treasury.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .associated(true))
                .persist();

        final var totalSupply = token.getTotalSupply();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_mintTokenExternal(
                getAddressFromEntity(tokenEntity), BigInteger.valueOf(30), new ArrayList<>());

        final var result = functionCall.send();
        assertThat(result.component2()).isEqualTo(BigInteger.valueOf(totalSupply + 30L));

        // Then
        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void mintNFT() throws Exception {
        // Given
        final var treasury = persistAccountEntity();
        final var tokenEntity = persistTokenEntity();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(treasury.toEntityId()))
                .persist();
        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(treasury.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .associated(true))
                .persist();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_mintTokenExternal(
                getAddressFromEntity(tokenEntity), BigInteger.ZERO, List.of(domainBuilder.nonZeroBytes(12)));

        final var result = functionCall.send();

        assertThat(result.component3().getFirst()).isEqualTo(BigInteger.ONE);

        // Then
        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void burnFungibleToken() throws Exception {
        // Given
        final var treasury = persistAccountEntity();
        final var tokenEntity = persistTokenEntity();
        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .treasuryAccountId(treasury.toEntityId()))
                .persist();
        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(treasury.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .associated(true))
                .persist();

        final var totalSupply = token.getTotalSupply();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_burnTokenExternal(
                getAddressFromEntity(tokenEntity), BigInteger.valueOf(4), new ArrayList<>());

        final var result = functionCall.send();

        // Then
        assertThat(result.component2()).isEqualTo(BigInteger.valueOf(totalSupply - 4L));
        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void burnNFT() throws Exception {
        // Given
        final var treasury = persistAccountEntity();
        final var tokenEntity = persistTokenEntity();
        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(treasury.toEntityId()))
                .persist();
        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .freezeStatus(TokenFreezeStatusEnum.UNFROZEN)
                        .accountId(treasury.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .associated(true))
                .persist();
        final var totalSupply = token.getTotalSupply();

        domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId()).serialNumber(1L).accountId(treasury.toEntityId()))
                .persist();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var functionCall = contract.call_burnTokenExternal(
                getAddressFromEntity(tokenEntity), BigInteger.ZERO, List.of(BigInteger.ONE));

        final var result = functionCall.send();

        // When
        assertThat(result.component2()).isEqualTo(BigInteger.valueOf(totalSupply - 1));

        // Then
        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void wipeFungibleToken() throws Exception {
        // Given
        final var owner = persistAccountEntity();

        final var tokenEntity = persistTokenEntity();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(TokenTypeEnum.FUNGIBLE_COMMON))
                .persist();

        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(owner.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .associated(true)
                        .balance(100L))
                .persist();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_wipeTokenAccountExternal(
                getAddressFromEntity(tokenEntity), getAliasFromEntity(owner), BigInteger.valueOf(4));
        functionCall.send();

        // Then
        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void wipeNFT() throws Exception {
        // Given
        final var owner = persistAccountEntity();

        final var tokenEntity = persistTokenEntity();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE))
                .persist();

        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(owner.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .associated(true))
                .persist();
        domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId()).serialNumber(1L).accountId(owner.toEntityId()))
                .persist();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_wipeTokenAccountNFTExternal(
                getAddressFromEntity(tokenEntity), getAliasFromEntity(owner), List.of(BigInteger.ONE));
        functionCall.send();

        // Then
        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void grantTokenKyc() throws Exception {
        // Given
        final var accountWithoutGrant = persistAccountEntity();

        final var tokenEntity = persistTokenEntity();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(TokenTypeEnum.FUNGIBLE_COMMON))
                .persist();

        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(accountWithoutGrant.getId())
                        .associated(true)
                        .balance(100L))
                .persist();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_grantTokenKycExternal(
                getAddressFromEntity(tokenEntity), getAliasFromEntity(accountWithoutGrant));
        functionCall.send();

        // Then
        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void revokeTokenKyc() throws Exception {
        // Given
        final var accountWithGrant = persistAccountEntity();

        final var tokenEntity = persistTokenEntity();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(TokenTypeEnum.FUNGIBLE_COMMON))
                .persist();

        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(accountWithGrant.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .associated(true)
                        .balance(100L))
                .persist();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_revokeTokenKycExternal(
                getAddressFromEntity(tokenEntity), getAliasFromEntity(accountWithGrant));
        functionCall.send();

        // Then
        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void deleteToken() throws Exception {
        // Given
        final var tokenEntity = persistTokenEntity();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(TokenTypeEnum.FUNGIBLE_COMMON))
                .persist();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_deleteTokenExternal(getAddressFromEntity(tokenEntity));
        functionCall.send();

        // Then
        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void freezeToken() throws Exception {
        // Given
        final var accountWithoutFreeze = persistAccountEntity();

        final var tokenEntity = persistFungibleToken();

        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(accountWithoutFreeze.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .associated(true)
                        .balance(100L))
                .persist();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_freezeTokenExternal(
                getAddressFromEntity(tokenEntity), getAliasFromEntity(accountWithoutFreeze));
        functionCall.send();

        // Then
        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void unfreezeToken() throws Exception {
        // Given
        final var accountWithFreeze = persistAccountEntity();

        final var tokenEntity = persistFungibleToken();

        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(accountWithFreeze.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .freezeStatus(TokenFreezeStatusEnum.FROZEN)
                        .associated(true)
                        .balance(100L))
                .persist();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_unfreezeTokenExternal(
                getAddressFromEntity(tokenEntity), getAliasFromEntity(accountWithFreeze));
        functionCall.send();

        // Then
        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void pauseToken() throws Exception {
        // Given
        final var tokenEntity = persistTokenEntity();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(TokenTypeEnum.FUNGIBLE_COMMON))
                .persist();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_pauseTokenExternal(getAddressFromEntity(tokenEntity));
        functionCall.send();

        // Then
        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void unpauseToken() throws Exception {
        // Given
        final var sender = persistAccountEntity();

        final var tokenEntity = persistTokenEntity();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .treasuryAccountId(sender.toEntityId())
                        .pauseStatus(TokenPauseStatusEnum.PAUSED))
                .persist();

        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(sender.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .associated(true))
                .persist();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_unpauseTokenExternal(getAddressFromEntity(tokenEntity));
        functionCall.send();

        // Then
        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void createFungibleToken() throws Exception {
        // Given
        var initialSupply = BigInteger.valueOf(10L);
        var decimals = BigInteger.valueOf(10L);
        var value = BigInteger.valueOf(10000L * 100_000_000L);

        final var sender = persistAccountEntity();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var treasuryAccount = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.ACCOUNT).deleted(false).evmAddress(null))
                .persist();
        final var token = populateHederaToken(
                contract.getContractAddress(), TokenTypeEnum.FUNGIBLE_COMMON, treasuryAccount.toEntityId());

        // When
        final var functionCall = contract.send_createFungibleTokenExternal(token, initialSupply, decimals, value);

        functionCall.send();
        final var decodedResult = DefaultFunctionReturnDecoder.decode(
                testWeb3jService.getTransactionResult(), CREATE_TOKEN_FUNCTION_OUTPUT_PARAMETERS);

        // Then
        assertThat(decodedResult.get(1).getValue()).isNotEqualTo(Address.ZERO);

        testEstimateGasForSend(functionCall, contract, sender, value.longValueExact());
    }

    @Test
    void createFungibleTokenWithCustomFees() throws Exception {
        // Given
        var initialSupply = BigInteger.valueOf(10L);
        var decimals = BigInteger.valueOf(10L);
        var value = BigInteger.valueOf(10000L * 100_000_000L);

        final var sender = persistAccountEntity();
        final var tokenForDenomination = persistFungibleToken();
        final var feeCollector = persistAccountEntity();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var treasuryAccount = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.ACCOUNT).deleted(false).evmAddress(null))
                .persist();
        final var token = populateHederaToken(
                contract.getContractAddress(), TokenTypeEnum.FUNGIBLE_COMMON, treasuryAccount.toEntityId());

        final var fixedFee = new ModificationPrecompileTestContract.FixedFee(
                BigInteger.valueOf(100L),
                getAddressFromEntityId(tokenForDenomination.toEntityId()),
                false,
                false,
                getAliasFromEntity(feeCollector));
        final var fractionalFee = new ModificationPrecompileTestContract.FractionalFee(
                BigInteger.valueOf(1L),
                BigInteger.valueOf(100L),
                BigInteger.valueOf(10L),
                BigInteger.valueOf(1000L),
                false,
                getAliasFromEntity(feeCollector));

        // When
        final var functionCall = contract.send_createFungibleTokenWithCustomFeesExternal(
                token, initialSupply, decimals, List.of(fixedFee), List.of(fractionalFee), value);

        functionCall.send();
        final var decodedResult = DefaultFunctionReturnDecoder.decode(
                testWeb3jService.getTransactionResult(), CREATE_TOKEN_FUNCTION_OUTPUT_PARAMETERS);

        // Then
        assertThat(decodedResult.get(1).getValue()).isNotEqualTo(Address.ZERO);

        testEstimateGasForSend(functionCall, contract, sender, value.longValueExact());
    }

    @Test
    void createNonFungibleToken() throws Exception {
        // Given
        var value = BigInteger.valueOf(10000L * 100_000_000L);

        final var sender = persistAccountEntity();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var treasuryAccount = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.ACCOUNT).deleted(false).evmAddress(null))
                .persist();
        final var token = populateHederaToken(
                contract.getContractAddress(), TokenTypeEnum.NON_FUNGIBLE_UNIQUE, treasuryAccount.toEntityId());

        // When
        final var functionCall = contract.send_createNonFungibleTokenExternal(token, value);

        functionCall.send();
        final var decodedResult = DefaultFunctionReturnDecoder.decode(
                testWeb3jService.getTransactionResult(), CREATE_TOKEN_FUNCTION_OUTPUT_PARAMETERS);

        // Then
        assertThat(decodedResult.get(1).getValue()).isNotEqualTo(Address.ZERO);

        testEstimateGasForSend(functionCall, contract, sender, value.longValueExact());
    }

    @Test
    void createNonFungibleTokenWithCustomFees() throws Exception {
        // Given
        var value = BigInteger.valueOf(10000L * 100_000_000L);

        final var sender = persistAccountEntity();
        final var tokenForDenomination = persistFungibleToken();
        final var feeCollector = persistAccountEntity();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var treasuryAccount = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.ACCOUNT).deleted(false).evmAddress(null))
                .persist();
        final var token = populateHederaToken(
                contract.getContractAddress(), TokenTypeEnum.NON_FUNGIBLE_UNIQUE, treasuryAccount.toEntityId());

        final var fixedFee = new ModificationPrecompileTestContract.FixedFee(
                BigInteger.valueOf(100L),
                getAddressFromEntityId(tokenForDenomination.toEntityId()),
                false,
                false,
                getAliasFromEntity(feeCollector));
        final var royaltyFee = new ModificationPrecompileTestContract.RoyaltyFee(
                BigInteger.valueOf(1L),
                BigInteger.valueOf(100L),
                BigInteger.valueOf(10L),
                getAddressFromEntity(tokenForDenomination),
                false,
                getAliasFromEntity(feeCollector));

        // When
        final var functionCall = contract.send_createNonFungibleTokenWithCustomFeesExternal(
                token, List.of(fixedFee), List.of(royaltyFee), value);

        functionCall.send();
        final var decodedResult = DefaultFunctionReturnDecoder.decode(
                testWeb3jService.getTransactionResult(), CREATE_TOKEN_FUNCTION_OUTPUT_PARAMETERS);

        // Then
        assertThat(decodedResult.get(1).getValue()).isNotEqualTo(Address.ZERO);

        testEstimateGasForSend(functionCall, contract, sender, value.longValueExact());
    }

    @Test
    void create2ContractAndTransferFromIt() throws Exception {
        // Given
        final var sponsor = persistAccountEntity();
        final var receiver = persistAccountEntity();
        final var token = persistFungibleToken();
        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(token.getId())
                        .accountId(sponsor.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .associated(true))
                .persist();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_createContractViaCreate2AndTransferFromIt(
                getAddressFromEntity(token),
                getAliasFromEntity(sponsor),
                getAliasFromEntity(receiver),
                BigInteger.valueOf(10L));

        functionCall.send();

        // Then
        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void nftInfoForInvalidSerialNo() {
        // Given
        final var nftEntity = persistTokenEntity();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(nftEntity.getId()).type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE))
                .persist();
        domainBuilder
                .nft()
                .customize(n -> n.tokenId(nftEntity.getId()).serialNumber(1L))
                .persist();
        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_getInformationForNonFungibleToken(
                getAddressFromEntity(nftEntity), BigInteger.valueOf(3L));

        // Then
        assertThatThrownBy(functionCall::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void tokenInfoForNonTokenAccount() {
        // Given
        final var account = persistAccountEntity();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_getInformationForToken(getAddressFromEntity(account));

        // Then
        assertThatThrownBy(functionCall::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void notExistingPrecompileCallFails() {
        // Given
        final var token = persistFungibleToken();
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.send_callNotExistingPrecompile(getAddressFromEntity(token));

        // Then
        assertThatThrownBy(functionCall::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void createFungibleTokenWithInheritKeysCall() throws Exception {
        // Given
        final var value = BigInteger.valueOf(10000 * 100_000_000L);
        final var sender = persistAccountEntity();
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.send_createFungibleTokenWithInheritKeysExternal(value);

        // Then
        testWeb3jService.setSender(getAliasFromEntity(sender));

        functionCall.send();
        final var result = testWeb3jService.getTransactionResult();
        assertThat(result).isNotEqualTo(EMPTY_UNTRIMMED_ADDRESS);

        testEstimateGasForSend(functionCall, contract, sender, value.longValueExact());
    }

    @Test
    void createTokenWithInvalidMemoFails() {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var function = contract.send_createFungibleTokenExternal(
                convertTokenEntityToHederaToken(domainBuilder
                        .token()
                        .customize(t -> t.metadata(new byte[mirrorNodeEvmProperties.getMaxMemoUtf8Bytes() + 1]))
                        .get()),
                BigInteger.valueOf(10L),
                BigInteger.valueOf(10L),
                BigInteger.valueOf(10000L * 100_000_000L));

        // Then
        assertThatThrownBy(function::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void createTokenWithInvalidNameFails() {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var function = contract.send_createFungibleTokenExternal(
                convertTokenEntityToHederaToken(domainBuilder
                        .token()
                        .customize(t -> t.name(new String(
                                new byte[mirrorNodeEvmProperties.getMaxTokenNameUtf8Bytes() + 1],
                                StandardCharsets.UTF_8)))
                        .get()),
                BigInteger.valueOf(10L),
                BigInteger.valueOf(10L),
                BigInteger.valueOf(10000L * 100_000_000L));

        // Then
        assertThatThrownBy(function::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void createTokenWithInvalidSymbolFails() {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var function = contract.send_createFungibleTokenExternal(
                convertTokenEntityToHederaToken(domainBuilder
                        .token()
                        .customize(t -> t.symbol(new String(
                                new byte[mirrorNodeEvmProperties.getMaxTokenNameUtf8Bytes() + 1],
                                StandardCharsets.UTF_8)))
                        .get()),
                BigInteger.valueOf(10L),
                BigInteger.valueOf(10L),
                BigInteger.valueOf(10000L * 100_000_000L));

        // Then
        assertThatThrownBy(function::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void createTokenWithInvalidDecimalsFails() {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var function = contract.send_createFungibleTokenExternal(
                convertTokenEntityToHederaToken(
                        domainBuilder.token().customize(t -> t.decimals(-1)).get()),
                BigInteger.valueOf(10L),
                BigInteger.valueOf(10L),
                BigInteger.valueOf(10000L * 100_000_000L));

        // Then
        assertThatThrownBy(function::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void createTokenWithInvalidInitialSupplyFails() {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var function = contract.send_createFungibleTokenExternal(
                convertTokenEntityToHederaToken(domainBuilder
                        .token()
                        .customize(t -> t.initialSupply(-1L))
                        .get()),
                BigInteger.valueOf(10L),
                BigInteger.valueOf(10L),
                BigInteger.valueOf(10000L * 100_000_000L));

        // Then
        assertThatThrownBy(function::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void createTokenWithInvalidInitialSupplyGreaterThanMaxSupplyFails() {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var function = contract.send_createFungibleTokenExternal(
                convertTokenEntityToHederaToken(domainBuilder
                        .token()
                        .customize(t -> t.initialSupply(10_000_001L))
                        .get()),
                BigInteger.valueOf(10L),
                BigInteger.valueOf(10L),
                BigInteger.valueOf(10000L * 100_000_000L));

        // Then
        assertThatThrownBy(function::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void createNftWithNoSupplyKeyFails() {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var function = contract.send_createNonFungibleTokenExternal(
                convertTokenEntityToHederaToken(domainBuilder
                        .token()
                        .customize(t -> t.supplyKey(new byte[0]))
                        .get()),
                BigInteger.valueOf(10000L * 100_000_000L));

        // Then
        assertThatThrownBy(function::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void createNftWithNoTreasuryFails() {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var function = contract.send_createNonFungibleTokenExternal(
                convertTokenEntityToHederaToken(domainBuilder
                        .token()
                        .customize(t -> t.treasuryAccountId(null))
                        .get()),
                BigInteger.valueOf(10000L * 100_000_000L));

        // Then
        assertThatThrownBy(function::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void createNftWithDefaultFreezeWithNoKeyFails() {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var function = contract.send_createNonFungibleTokenExternal(
                convertTokenEntityToHederaToken(domainBuilder
                        .token()
                        .customize(t -> t.freezeDefault(true).freezeKey(new byte[0]))
                        .get()),
                BigInteger.valueOf(10000L * 100_000_000L));

        // Then
        assertThatThrownBy(function::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void createNftWithInvalidAutoRenewAccountFails() {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var function = contract.send_createNonFungibleTokenExternal(
                convertTokenEntityToHederaToken(
                        domainBuilder.token().get(),
                        domainBuilder
                                .entity()
                                .customize(e -> e.autoRenewPeriod(10L))
                                .get()),
                BigInteger.valueOf(10000L * 100_000_000L));

        // Then
        assertThatThrownBy(function::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @ParameterizedTest
    @CsvSource({"single", "multiple"})
    void transferToken(final String type) throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);
        final var tokenEntity = persistFungibleToken();
        final var sender = persistAccountEntity();
        final var receiver = persistAccountEntity();
        final var payer = persistAccountEntity();

        domainBuilder
                .tokenAccount()
                .customize(ta ->
                        ta.tokenId(tokenEntity.getId()).accountId(payer.getId()).kycStatus(TokenKycStatusEnum.GRANTED))
                .persist();
        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(sender.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED))
                .persist();
        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(receiver.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED))
                .persist();

        // When
        testWeb3jService.setSender(getAliasFromEntity(payer));
        final var functionCall = "single".equals(type)
                ? contract.call_transferTokenExternal(
                        getAddressFromEntity(tokenEntity),
                        getAliasFromEntity(sender),
                        getAliasFromEntity(receiver),
                        BigInteger.valueOf(1L))
                : contract.call_transferTokensExternal(
                        getAddressFromEntity(tokenEntity),
                        List.of(getAliasFromEntity(sender), getAliasFromEntity(receiver)),
                        List.of(BigInteger.ONE, BigInteger.valueOf(-1L)));
        functionCall.send();

        // Then
        testEstimateGas(functionCall, contract, 0L);
    }

    @ParameterizedTest
    @CsvSource({"single", "multiple"})
    void transferNft(final String type) throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);
        final var sender = persistAccountEntity();
        final var tokenEntity = persistTokenEntity();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE))
                .persist();
        domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId()).serialNumber(1L).accountId(sender.toEntityId()))
                .persist();
        final var receiver = persistAccountEntity();
        final var payer = persistAccountEntity();

        domainBuilder
                .tokenAccount()
                .customize(ta ->
                        ta.tokenId(tokenEntity.getId()).accountId(payer.getId()).kycStatus(TokenKycStatusEnum.GRANTED))
                .persist();
        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(sender.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED))
                .persist();
        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(receiver.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED))
                .persist();

        // When
        testWeb3jService.setSender(getAliasFromEntity(payer));
        final var functionCall = "single".equals(type)
                ? contract.call_transferNFTExternal(
                        getAddressFromEntity(tokenEntity),
                        getAliasFromEntity(sender),
                        getAliasFromEntity(receiver),
                        BigInteger.ONE)
                : contract.call_transferNFTsExternal(
                        getAddressFromEntity(tokenEntity),
                        List.of(getAliasFromEntity(sender)),
                        List.of(getAliasFromEntity(receiver)),
                        List.of(BigInteger.ONE));
        functionCall.send();

        // Then
        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void cryptoTransferHbars() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);
        final var sender = persistAccountEntity();
        final var receiver = persistAccountEntity();
        final var payer = persistAccountEntity();

        // When
        testWeb3jService.setSender(getAliasFromEntity(payer));
        final var transferList = new TransferList(List.of(
                new AccountAmount(getAliasFromEntity(sender), BigInteger.valueOf(-5L), false),
                new AccountAmount(getAliasFromEntity(receiver), BigInteger.valueOf(5L), false)));

        final var functionCall = contract.call_cryptoTransferExternal(transferList, new ArrayList<>());
        functionCall.send();

        // Then
        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void cryptoTransferToken() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);
        final var sender = persistAccountEntity();
        final var tokenEntity = persistFungibleToken();
        final var receiver = persistAccountEntity();
        final var payer = persistAccountEntity();

        domainBuilder
                .tokenAccount()
                .customize(ta ->
                        ta.tokenId(tokenEntity.getId()).accountId(payer.getId()).kycStatus(TokenKycStatusEnum.GRANTED))
                .persist();
        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(sender.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED))
                .persist();
        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(receiver.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED))
                .persist();

        // When
        testWeb3jService.setSender(getAliasFromEntity(payer));
        final var tokenTransferList = new TokenTransferList(
                getAddressFromEntity(tokenEntity),
                List.of(
                        new AccountAmount(getAliasFromEntity(sender), BigInteger.valueOf(5L), false),
                        new AccountAmount(getAliasFromEntity(receiver), BigInteger.valueOf(-5L), false)),
                new ArrayList<>());

        final var functionCall =
                contract.call_cryptoTransferExternal(new TransferList(new ArrayList<>()), List.of(tokenTransferList));
        functionCall.send();

        // Then
        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void cryptoTransferHbarsAndToken() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);
        final var sender = persistAccountEntity();
        final var tokenEntity = persistFungibleToken();
        final var receiver = persistAccountEntity();
        final var payer = persistAccountEntity();

        domainBuilder
                .tokenAccount()
                .customize(ta ->
                        ta.tokenId(tokenEntity.getId()).accountId(payer.getId()).kycStatus(TokenKycStatusEnum.GRANTED))
                .persist();
        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(sender.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED))
                .persist();
        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(receiver.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED))
                .persist();

        // When
        testWeb3jService.setSender(getAliasFromEntity(payer));
        final var transferList = new TransferList(List.of(
                new AccountAmount(getAliasFromEntity(sender), BigInteger.valueOf(-5L), false),
                new AccountAmount(getAliasFromEntity(receiver), BigInteger.valueOf(5L), false)));

        final var tokenTransferList = new TokenTransferList(
                getAddressFromEntity(tokenEntity),
                List.of(
                        new AccountAmount(getAliasFromEntity(sender), BigInteger.valueOf(5L), false),
                        new AccountAmount(getAliasFromEntity(receiver), BigInteger.valueOf(-5L), false)),
                new ArrayList<>());

        final var functionCall = contract.call_cryptoTransferExternal(transferList, List.of(tokenTransferList));
        functionCall.send();

        // Then
        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void cryptoTransferNft() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);
        final var sender = persistAccountEntity();
        final var tokenEntity = persistTokenEntity();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE))
                .persist();
        domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId()).serialNumber(1L).accountId(sender.toEntityId()))
                .persist();
        final var receiver = persistAccountEntity();
        final var payer = persistAccountEntity();

        domainBuilder
                .tokenAccount()
                .customize(ta ->
                        ta.tokenId(tokenEntity.getId()).accountId(payer.getId()).kycStatus(TokenKycStatusEnum.GRANTED))
                .persist();
        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(sender.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED))
                .persist();
        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(receiver.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED))
                .persist();

        // When
        testWeb3jService.setSender(getAliasFromEntity(payer));
        final var tokenTransferList = new TokenTransferList(
                getAddressFromEntity(tokenEntity),
                new ArrayList<>(),
                List.of(new NftTransfer(
                        getAliasFromEntity(sender), getAliasFromEntity(receiver), BigInteger.ONE, false)));

        final var functionCall =
                contract.call_cryptoTransferExternal(new TransferList(new ArrayList<>()), List.of(tokenTransferList));
        functionCall.send();

        // Then
        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void updateTokenInfo() throws Exception {
        // Given
        final var autoRenewAccount = persistAccountEntity();
        final var treasuryAccountId = persistAccountEntity();
        final var tokenToUpdateEntity = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.TOKEN).autoRenewAccountId(autoRenewAccount.getId()))
                .persist();
        final var tokenToUpdate = domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenToUpdateEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .treasuryAccountId(treasuryAccountId.toEntityId()))
                .persist();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var token = populateHederaToken(
                contract.getContractAddress(), TokenTypeEnum.FUNGIBLE_COMMON, tokenToUpdate.getTreasuryAccountId());

        // When
        final var functionCall =
                contract.call_updateTokenInfoExternal(getAddressFromEntity(tokenToUpdateEntity), token);

        functionCall.send();

        // Then
        testEstimateGas(functionCall, contract, 0L);
    }

    @Test
    void updateTokenExpiry() throws Exception {
        // Given
        final var autoRenewAccount = persistAccountEntity();
        final var treasuryAccountId = persistAccountEntity();
        final var tokenToUpdateEntity = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.TOKEN).autoRenewAccountId(autoRenewAccount.getId()))
                .persist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenToUpdateEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .treasuryAccountId(treasuryAccountId.toEntityId()))
                .persist();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var tokenExpiry = new Expiry(
                BigInteger.valueOf(Time.getCurrent().currentTimeMillis() + 1_000_000_000),
                getAliasFromEntity(autoRenewAccount),
                BigInteger.valueOf(8_000_000));

        // When
        final var functionCall =
                contract.call_updateTokenExpiryInfoExternal(getAddressFromEntity(tokenToUpdateEntity), tokenExpiry);

        functionCall.send();

        // Then
        testEstimateGas(functionCall, contract, 0L);
    }

    @ParameterizedTest
    @CsvSource({
        """
        FUNGIBLE_COMMON, ED25519
        FUNGIBLE_COMMON, ECDSA_SECPK256K1
        FUNGIBLE_COMMON, CONTRACT_ID
        FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID
        NON_FUNGIBLE_UNIQUE, ED25519
        NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1
        NON_FUNGIBLE_UNIQUE, CONTRACT_ID
        NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID
        """
    })
    void updateTokenKey(final TokenTypeEnum tokenTypeEnum, final KeyValueType keyValueType) throws Exception {
        // Given
        final var allCasesKeyType = 0b1111111;
        final var autoRenewAccount = persistAccountEntity();
        final var treasuryAccountId = persistAccountEntity();
        final var tokenEntityToUpdate = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.TOKEN).autoRenewAccountId(autoRenewAccount.getId()))
                .persist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntityToUpdate.getId())
                        .type(
                                TokenTypeEnum.FUNGIBLE_COMMON.equals(tokenTypeEnum)
                                        ? TokenTypeEnum.FUNGIBLE_COMMON
                                        : TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(treasuryAccountId.toEntityId()))
                .persist();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var tokenKeys = new ArrayList<TokenKey>();
        tokenKeys.add(new TokenKey(
                BigInteger.valueOf(allCasesKeyType),
                getKeyValueForTypeModificationContract(keyValueType, contract.getContractAddress())));

        // When
        final var functionCall =
                contract.call_updateTokenKeysExternal(getAddressFromEntity(tokenEntityToUpdate), tokenKeys);

        functionCall.send();

        // Then
        testEstimateGas(functionCall, contract, 0L);
    }

    private void testEstimateGas(final RemoteFunctionCall<?> functionCall, final Contract contract, final Long value) {
        // Given
        final var estimateGasUsedResult = longValueOf.applyAsLong(testWeb3jService.getEstimatedGas());

        // When
        final var actualGasUsed = gasUsedAfterExecution(getContractExecutionParameters(functionCall, contract, value));

        // Then
        assertThat(isWithinExpectedGasRange(estimateGasUsedResult, actualGasUsed))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimateGasUsedResult, actualGasUsed)
                .isTrue();
    }

    private void testEstimateGasForSend(
            final RemoteFunctionCall<?> functionCall, final Contract contract, final Entity sender, final Long value)
            throws Exception {
        // Given
        testWeb3jService.setEstimateGas(true);
        functionCall.send();
        final var estimateGasUsedResult = longValueOf.applyAsLong(testWeb3jService.getEstimatedGas());

        // When
        final var actualGasUsed =
                gasUsedAfterExecution(getContractExecutionParameters(functionCall, contract, sender, value));

        // Then
        assertThat(isWithinExpectedGasRange(estimateGasUsedResult, actualGasUsed))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimateGasUsedResult, actualGasUsed)
                .isTrue();
    }

    private HederaToken populateHederaToken(
            final String contractAddres, final TokenTypeEnum tokenType, final EntityId treasuryAccountId) {
        final var autoRenewAccount = persistAccountEntity();
        final var tokenEntity = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.TOKEN).autoRenewAccountId(autoRenewAccount.getId()))
                .persist();
        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(tokenType).treasuryAccountId(treasuryAccountId))
                .persist();

        final var supplyKey = new ModificationPrecompileTestContract.KeyValue(
                Boolean.FALSE, contractAddres, new byte[0], new byte[0], Address.ZERO.toHexString());
        final var keys = new ArrayList<TokenKey>();
        keys.add(new TokenKey(AbstractContractCallServiceTest.KeyType.SUPPLY_KEY.getKeyTypeNumeric(), supplyKey));
        return new HederaToken(
                token.getName(),
                token.getSymbol(),
                getAddressFromEntityId(treasuryAccountId),
                tokenEntity.getMemo(),
                true,
                BigInteger.valueOf(10_000L),
                false,
                keys,
                new Expiry(
                        BigInteger.valueOf(Time.getCurrent().currentTimeMillis() + 1_000_000_000),
                        getAliasFromEntity(autoRenewAccount),
                        BigInteger.valueOf(8_000_000)));
    }

    private ContractExecutionParameters getContractExecutionParameters(
            final RemoteFunctionCall<?> functionCall, final Contract contract, final Long value) {
        return ContractExecutionParameters.builder()
                .block(BlockType.LATEST)
                .callData(Bytes.fromHexString(functionCall.encodeFunctionCall()))
                .callType(CallServiceParameters.CallType.ETH_CALL)
                .gas(TRANSACTION_GAS_LIMIT)
                .isEstimate(false)
                .isStatic(false)
                .receiver(Address.fromHexString(contract.getContractAddress()))
                //                .sender(new HederaEvmAccount(Address.wrap(Bytes.wrap(domainBuilder.evmAddress()))))
                .sender(new HederaEvmAccount(testWeb3jService.getSender()))
                .value(value)
                .build();
    }

    private ContractExecutionParameters getContractExecutionParameters(
            final RemoteFunctionCall<?> functionCall, final Contract contract, final Entity sender, final Long value) {
        return ContractExecutionParameters.builder()
                .block(BlockType.LATEST)
                .callData(Bytes.fromHexString(functionCall.encodeFunctionCall()))
                .callType(CallServiceParameters.CallType.ETH_CALL)
                .gas(TRANSACTION_GAS_LIMIT)
                .isEstimate(false)
                .isStatic(false)
                .receiver(Address.fromHexString(contract.getContractAddress()))
                .sender(new HederaEvmAccount(Address.fromHexString(getAliasFromEntity(sender))))
                .value(value)
                .build();
    }

    private Entity getTokenWithKey(
            final TokenTypeEnum tokenType,
            final KeyValueType keyValueType,
            final AbstractContractCallServiceTest.KeyType keyType,
            final Contract contract) {
        final Key key;
        switch (keyValueType) {
            case ECDSA_SECPK256K1:
                key = KEY_WITH_ECDSA_TYPE;
                break;
            case ED25519:
                key = KEY_WITH_ED_25519_TYPE;
                break;
            case CONTRACT_ID:
                key = getKeyWithContractId(contract);
                break;
            case DELEGATABLE_CONTRACT_ID:
                key = getKeyWithDelegatableContractId(contract);
                break;
            default:
                throw new IllegalArgumentException("Invalid key type");
        }

        final var tokenEntity = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.TOKEN).key(key.toByteArray()))
                .persist();
        final var tokenBuilder = domainBuilder.token().customize(t -> t.tokenId(tokenEntity.getId())
                .type(tokenType));

        switch (keyType) {
            case AbstractContractCallServiceTest.KeyType.ADMIN_KEY:
                break;
            case AbstractContractCallServiceTest.KeyType.KYC_KEY:
                tokenBuilder.customize(t -> t.kycKey(key.toByteArray()));
                break;
            case AbstractContractCallServiceTest.KeyType.FREEZE_KEY:
                tokenBuilder.customize(t -> t.freezeKey(key.toByteArray()));
                break;
            case AbstractContractCallServiceTest.KeyType.WIPE_KEY:
                tokenBuilder.customize(t -> t.wipeKey(key.toByteArray()));
                break;
            case AbstractContractCallServiceTest.KeyType.SUPPLY_KEY:
                tokenBuilder.customize(t -> t.supplyKey(key.toByteArray()));
                break;
            case AbstractContractCallServiceTest.KeyType.FEE_SCHEDULE_KEY:
                tokenBuilder.customize(t -> t.feeScheduleKey(key.toByteArray()));
                break;
            case AbstractContractCallServiceTest.KeyType.PAUSE_KEY:
                tokenBuilder.customize(t -> t.pauseKey(key.toByteArray()));
                break;
            default:
                throw new IllegalArgumentException("Invalid key type");
        }

        tokenBuilder.persist();
        return tokenEntity;
    }

    private KeyValue getKeyValueForType(final KeyValueType keyValueType, String contractAddress) {
        return switch (keyValueType) {
            case CONTRACT_ID -> new KeyValue(
                    Boolean.FALSE, contractAddress, new byte[0], new byte[0], Address.ZERO.toHexString());
            case ED25519 -> new KeyValue(
                    Boolean.FALSE, Address.ZERO.toHexString(), ED25519_KEY, new byte[0], Address.ZERO.toHexString());
            case ECDSA_SECPK256K1 -> new KeyValue(
                    Boolean.FALSE, Address.ZERO.toHexString(), new byte[0], ECDSA_KEY, Address.ZERO.toHexString());
            case DELEGATABLE_CONTRACT_ID -> new KeyValue(
                    Boolean.FALSE, Address.ZERO.toHexString(), new byte[0], new byte[0], contractAddress);
            default -> throw new RuntimeException("Unsupported key type: " + keyValueType.name());
        };
    }

    private ModificationPrecompileTestContract.KeyValue getKeyValueForTypeModificationContract(
            final KeyValueType keyValueType, String contractAddress) {
        final var ed25519Key =
                Bytes.wrap(domainBuilder.key(KeyCase.ED25519)).slice(2).toArray();

        return switch (keyValueType) {
            case CONTRACT_ID -> new ModificationPrecompileTestContract.KeyValue(
                    Boolean.FALSE, contractAddress, new byte[0], new byte[0], Address.ZERO.toHexString());
            case ED25519 -> new ModificationPrecompileTestContract.KeyValue(
                    Boolean.FALSE, Address.ZERO.toHexString(), ed25519Key, new byte[0], Address.ZERO.toHexString());
            case ECDSA_SECPK256K1 -> new ModificationPrecompileTestContract.KeyValue(
                    Boolean.FALSE, Address.ZERO.toHexString(), new byte[0], ECDSA_KEY, Address.ZERO.toHexString());
            case DELEGATABLE_CONTRACT_ID -> new ModificationPrecompileTestContract.KeyValue(
                    Boolean.FALSE, Address.ZERO.toHexString(), new byte[0], new byte[0], contractAddress);
            default -> throw new RuntimeException("Unsupported key type: " + keyValueType.name());
        };
    }

    private Entity persistAccountEntity() {
        return domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.ACCOUNT).deleted(false).balance(1_000_000_000_000L))
                .persist();
    }

    private Entity persistTokenEntity() {
        return domainBuilder.entity().customize(e -> e.type(EntityType.TOKEN)).persist();
    }

    private Entity persistFungibleToken() {
        final var tokenEntity = persistTokenEntity();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(TokenTypeEnum.FUNGIBLE_COMMON))
                .persist();

        return tokenEntity;
    }

    private Entity persistNft() {
        final var tokenEntity = persistTokenEntity();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE))
                .persist();
        domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId()).serialNumber(1L))
                .persist();

        return tokenEntity;
    }

    private CustomFee persistCustomFeesWithFeeCollector(
            final Entity feeCollector, final Entity tokenEntity, final TokenTypeEnum tokenType) {
        final var fixedFee = com.hedera.mirror.common.domain.token.FixedFee.builder()
                .allCollectorsAreExempt(true)
                .amount(domainBuilder.number())
                .collectorAccountId(feeCollector.toEntityId())
                .denominatingTokenId(tokenEntity.toEntityId())
                .build();

        final var fractionalFee = TokenTypeEnum.FUNGIBLE_COMMON.equals(tokenType)
                ? FractionalFee.builder()
                        .allCollectorsAreExempt(true)
                        .collectorAccountId(feeCollector.toEntityId())
                        .denominator(domainBuilder.number())
                        .maximumAmount(domainBuilder.number())
                        .minimumAmount(1L)
                        .numerator(domainBuilder.number())
                        .netOfTransfers(true)
                        .build()
                : null;

        final var fallbackFee = FallbackFee.builder()
                .amount(domainBuilder.number())
                .denominatingTokenId(tokenEntity.toEntityId())
                .build();

        final var royaltyFee = TokenTypeEnum.NON_FUNGIBLE_UNIQUE.equals(tokenType)
                ? RoyaltyFee.builder()
                        .allCollectorsAreExempt(true)
                        .collectorAccountId(feeCollector.toEntityId())
                        .denominator(domainBuilder.number())
                        .fallbackFee(fallbackFee)
                        .numerator(domainBuilder.number())
                        .build()
                : null;

        if (TokenTypeEnum.FUNGIBLE_COMMON.equals(tokenType)) {
            return domainBuilder
                    .customFee()
                    .customize(f -> f.tokenId(tokenEntity.getId())
                            .fixedFees(List.of(fixedFee))
                            .fractionalFees(List.of(fractionalFee))
                            .royaltyFees(new ArrayList<>()))
                    .persist();
        } else if (TokenTypeEnum.NON_FUNGIBLE_UNIQUE.equals(tokenType)) {
            return domainBuilder
                    .customFee()
                    .customize(f -> f.tokenId(tokenEntity.getId())
                            .fixedFees(List.of(fixedFee))
                            .royaltyFees(List.of(royaltyFee))
                            .fractionalFees(new ArrayList<>()))
                    .persist();
        }

        return CustomFee.builder().build();
    }

    private List<PrecompileTestContract.TokenKey> getExpectedTokenKeys(final Entity tokenEntity, final Token token) {
        final var expectedTokenKeys = new ArrayList<PrecompileTestContract.TokenKey>();
        expectedTokenKeys.add(new PrecompileTestContract.TokenKey(BigInteger.ONE, getKeyValue(tokenEntity.getKey())));
        expectedTokenKeys.add(
                new PrecompileTestContract.TokenKey(BigInteger.valueOf(2), getKeyValue(token.getKycKey())));
        expectedTokenKeys.add(
                new PrecompileTestContract.TokenKey(BigInteger.valueOf(4), getKeyValue(token.getFreezeKey())));
        expectedTokenKeys.add(
                new PrecompileTestContract.TokenKey(BigInteger.valueOf(8), getKeyValue(token.getWipeKey())));
        expectedTokenKeys.add(
                new PrecompileTestContract.TokenKey(BigInteger.valueOf(16), getKeyValue(token.getSupplyKey())));
        expectedTokenKeys.add(
                new PrecompileTestContract.TokenKey(BigInteger.valueOf(32), getKeyValue(token.getFeeScheduleKey())));
        expectedTokenKeys.add(
                new PrecompileTestContract.TokenKey(BigInteger.valueOf(64), getKeyValue(token.getPauseKey())));

        return expectedTokenKeys;
    }

    private String getAliasFromEntity(Entity entity) {
        return Address.fromHexString(Bytes.wrap(entity.getEvmAddress()).toHexString())
                .toHexString();
    }

    private String getAddressFromEntity(Entity entity) {
        return EntityIdUtils.asHexedEvmAddress(new Id(entity.getShard(), entity.getRealm(), entity.getNum()));
    }

    private String getAddressFromEntityId(EntityId entity) {
        return EntityIdUtils.asHexedEvmAddress(new Id(entity.getShard(), entity.getRealm(), entity.getNum()));
    }

    private String getAddressFromEvmAddress(byte[] evmAddress) {
        return Address.wrap(Bytes.wrap(evmAddress)).toHexString();
    }

    private KeyValue getKeyValue(byte[] serializedKey) {
        try {
            final var key = Key.parseFrom(serializedKey);
            return new KeyValue(
                    false,
                    key.getContractID().hasContractNum()
                            ? EntityIdUtils.asTypedEvmAddress(key.getContractID())
                                    .toHexString()
                            : Address.ZERO.toHexString(),
                    key.getEd25519().toByteArray(),
                    key.getECDSASecp256K1().toByteArray(),
                    key.getDelegatableContractId().hasContractNum()
                            ? EntityIdUtils.asTypedEvmAddress(key.getDelegatableContractId())
                                    .toHexString()
                            : Address.ZERO.toHexString());
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Unable to parse key", e);
        }
    }

    private HederaToken convertTokenEntityToHederaToken(final Token token) {
        final var tokenEntity =
                domainBuilder.entity().customize(e -> e.id(token.getTokenId())).get();
        final var treasuryAccountId = token.getTreasuryAccountId();
        final var keys = new ArrayList<TokenKey>();
        final var entityRenewAccountId = tokenEntity.getAutoRenewAccountId();

        return new HederaToken(
                token.getName(),
                token.getSymbol(),
                treasuryAccountId != null
                        ? EntityIdUtils.asHexedEvmAddress(new Id(
                                treasuryAccountId.getShard(), treasuryAccountId.getRealm(), treasuryAccountId.getNum()))
                        : Address.ZERO.toHexString(),
                new String(token.getMetadata(), StandardCharsets.UTF_8),
                token.getSupplyType().equals(TokenSupplyTypeEnum.FINITE),
                BigInteger.valueOf(token.getMaxSupply()),
                token.getFreezeDefault(),
                keys,
                new Expiry(
                        BigInteger.valueOf(tokenEntity.getEffectiveExpiration()),
                        EntityIdUtils.asHexedEvmAddress(new Id(0, 0, entityRenewAccountId.longValue())),
                        BigInteger.valueOf(tokenEntity.getEffectiveExpiration())));
    }

    private HederaToken convertTokenEntityToHederaToken(final Token token, final Entity entity) {
        final var treasuryAccountId = token.getTreasuryAccountId();
        final var keys = new ArrayList<TokenKey>();
        final var entityRenewAccountId = entity.getAutoRenewAccountId();

        return new HederaToken(
                token.getName(),
                token.getSymbol(),
                EntityIdUtils.asHexedEvmAddress(
                        new Id(treasuryAccountId.getShard(), treasuryAccountId.getRealm(), treasuryAccountId.getNum())),
                new String(token.getMetadata(), StandardCharsets.UTF_8),
                token.getSupplyType().equals(TokenSupplyTypeEnum.FINITE),
                BigInteger.valueOf(token.getMaxSupply()),
                token.getFreezeDefault(),
                keys,
                new Expiry(
                        BigInteger.valueOf(entity.getEffectiveExpiration()),
                        EntityIdUtils.asHexedEvmAddress(new Id(0, 0, entityRenewAccountId.longValue())),
                        BigInteger.valueOf(entity.getEffectiveExpiration())));
    }

    private PrecompileTestContract.FixedFee getFixedFee(
            final com.hedera.mirror.common.domain.token.FixedFee fixedFee, final Entity feeCollector) {
        return new PrecompileTestContract.FixedFee(
                BigInteger.valueOf(fixedFee.getAmount()),
                getAddressFromEntityId(fixedFee.getDenominatingTokenId()),
                false,
                false,
                getAliasFromEntity(feeCollector));
    }

    private PrecompileTestContract.FractionalFee getFractionalFee(
            final com.hedera.mirror.common.domain.token.FractionalFee fractionalFee, final Entity feeCollector) {
        return new PrecompileTestContract.FractionalFee(
                BigInteger.valueOf(fractionalFee.getNumerator()),
                BigInteger.valueOf(fractionalFee.getDenominator()),
                BigInteger.valueOf(fractionalFee.getMinimumAmount()),
                BigInteger.valueOf(fractionalFee.getMaximumAmount()),
                true,
                getAliasFromEntity(feeCollector));
    }

    private PrecompileTestContract.RoyaltyFee getRoyaltyFee(
            final com.hedera.mirror.common.domain.token.RoyaltyFee royaltyFee, final Entity feeCollector) {
        return new PrecompileTestContract.RoyaltyFee(
                BigInteger.valueOf(royaltyFee.getNumerator()),
                BigInteger.valueOf(royaltyFee.getDenominator()),
                BigInteger.valueOf(royaltyFee.getFallbackFee().getAmount()),
                getAddressFromEntityId(royaltyFee.getFallbackFee().getDenominatingTokenId()),
                false,
                getAddressFromEvmAddress(feeCollector.getEvmAddress()));
    }
}
