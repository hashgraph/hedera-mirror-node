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

import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.EMPTY_UNTRIMMED_ADDRESS;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.KEY_WITH_ECDSA_TYPE;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.KEY_WITH_ED_25519_TYPE;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.LEDGER_ID;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.isWithinExpectedGasRange;
import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hederahashgraph.api.proto.java.CustomFee.FeeCase.FIXED_FEE;
import static com.hederahashgraph.api.proto.java.CustomFee.FeeCase.FRACTIONAL_FEE;
import static com.hederahashgraph.api.proto.java.CustomFee.FeeCase.ROYALTY_FEE;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
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
import com.hedera.mirror.common.domain.token.TokenSupplyTypeEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.web3.exception.BlockNumberNotFoundException;
import com.hedera.mirror.web3.exception.BlockNumberOutOfRangeException;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.utils.ContractFunctionProviderEnum;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.mirror.web3.web3j.generated.ModificationPrecompileTestContract;
import com.hedera.mirror.web3.web3j.generated.ModificationPrecompileTestContract.Expiry;
import com.hedera.mirror.web3.web3j.generated.ModificationPrecompileTestContract.HederaToken;
import com.hedera.mirror.web3.web3j.generated.ModificationPrecompileTestContract.TokenKey;
import com.hedera.mirror.web3.web3j.generated.PrecompileTestContract;
import com.hedera.mirror.web3.web3j.generated.PrecompileTestContract.FixedFee;
import com.hedera.mirror.web3.web3j.generated.PrecompileTestContract.FungibleTokenInfo;
import com.hedera.mirror.web3.web3j.generated.PrecompileTestContract.KeyValue;
import com.hedera.mirror.web3.web3j.generated.PrecompileTestContract.NonFungibleTokenInfo;
import com.hedera.mirror.web3.web3j.generated.PrecompileTestContract.TokenInfo;
import com.hedera.services.store.contracts.precompile.codec.KeyValueWrapper.KeyValueType;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.Key;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.tx.Contract;

class ContractCallServicePrecompileTest extends AbstractContractCallServiceTest {

    private static Stream<Arguments> htsContractFunctionArgumentsProviderHistoricalReadOnly() {
        List<String> blockNumbers = List.of(String.valueOf(EVM_V_34_BLOCK - 1), String.valueOf(EVM_V_34_BLOCK));

        return Arrays.stream(ContractReadFunctionsHistorical.values()).flatMap(htsFunction -> blockNumbers.stream()
                .map(blockNumber -> Arguments.of(htsFunction, blockNumber)));
    }

    private static long getValue(SupportedContractModificationFunctions contractFunc) {
        return switch (contractFunc) {
            case CREATE_FUNGIBLE_TOKEN,
                    CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES,
                    CREATE_NON_FUNGIBLE_TOKEN,
                    CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES -> 10000 * 100_000_000L;
            default -> 0L;
        };
    }

    @Test
    void isTokenFrozen() throws Exception {
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
        final var result = contract.call_isTokenFrozen(getAddressFromEntity(tokenEntity), getAddressFromEntity(account))
                .send();

        assertThat(result).isTrue();

        final var functionCall =
                contract.send_isTokenFrozen(getAddressFromEntity(tokenEntity), getAddressFromEntity(account));
        testEstimateGas(functionCall, contract);
    }

    @Test
    void isTokenFrozenWithAlias() throws Exception {
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
        final var result = contract.call_isTokenFrozen(getAddressFromEntity(tokenEntity), getAliasFromEntity(account))
                .send();

        assertThat(result).isTrue();

        final var functionCall =
                contract.send_isTokenFrozen(getAddressFromEntity(tokenEntity), getAliasFromEntity(account));
        testEstimateGas(functionCall, contract);
    }

    @Test
    void isKycGranted() throws Exception {
        final var account = persistAccountEntity();
        final var tokenEntity = persistFungibleToken();
        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(account.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED))
                .persist();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);
        final var result = contract.call_isKycGranted(getAddressFromEntity(tokenEntity), getAddressFromEntity(account))
                .send();

        assertThat(result).isTrue();

        final var functionCall =
                contract.send_isKycGranted(getAddressFromEntity(tokenEntity), getAddressFromEntity(account));
        testEstimateGas(functionCall, contract);
    }

    @Test
    void isKycGrantedWithAlias() throws Exception {
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
        final var result = contract.call_isKycGranted(getAddressFromEntity(tokenEntity), getAliasFromEntity(account))
                .send();

        assertThat(result).isTrue();

        final var functionCall =
                contract.send_isKycGranted(getAddressFromEntity(tokenEntity), getAliasFromEntity(account));
        testEstimateGas(functionCall, contract);
    }

    @Test
    void isKycGrantedForNFT() throws Exception {
        final var account = persistAccountEntity();
        final var tokenEntity = persistNft();
        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(account.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED))
                .persist();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);
        final var result = contract.call_isKycGranted(getAddressFromEntity(tokenEntity), getAddressFromEntity(account))
                .send();

        assertThat(result).isTrue();

        final var functionCall =
                contract.send_isKycGranted(getAddressFromEntity(tokenEntity), getAddressFromEntity(account));
        testEstimateGas(functionCall, contract);
    }

    @Test
    void isKycGrantedForNFTWithAlias() throws Exception {
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
        final var result = contract.call_isKycGranted(getAddressFromEntity(tokenEntity), getAliasFromEntity(account))
                .send();

        assertThat(result).isTrue();

        final var functionCall =
                contract.send_isKycGranted(getAddressFromEntity(tokenEntity), getAliasFromEntity(account));
        testEstimateGas(functionCall, contract);
    }

    @Test
    void isTokenAddress() throws Exception {
        final var tokenEntity = persistFungibleToken();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);
        final var result =
                contract.call_isTokenAddress(getAddressFromEntity(tokenEntity)).send();

        assertThat(result).isTrue();

        final var functionCall = contract.send_isTokenAddress(getAddressFromEntity(tokenEntity));
        testEstimateGas(functionCall, contract);
    }

    @Test
    void isTokenAddressNFT() throws Exception {
        final var tokenEntity = persistNft();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);
        final var result =
                contract.call_isTokenAddress(getAddressFromEntity(tokenEntity)).send();

        assertThat(result).isTrue();

        final var functionCall = contract.send_isTokenAddress(getAddressFromEntity(tokenEntity));
        testEstimateGas(functionCall, contract);
    }

    @Test
    void getDefaultKycToken() throws Exception {
        domainBuilder.recordFile().customize(f -> f.index(0L)).persist();
        final var tokenEntity = persistTokenEntity();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .type(TokenTypeEnum.FUNGIBLE_COMMON))
                .persist();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);
        final var result = contract.call_getTokenDefaultKyc(getAddressFromEntity(tokenEntity))
                .send();

        assertThat(result).isTrue();

        final var functionCall = contract.send_getTokenDefaultKyc(getAddressFromEntity(tokenEntity));
        testEstimateGas(functionCall, contract);
    }

    @Test
    void getDefaultKycNFT() throws Exception {
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
        final var result = contract.call_getTokenDefaultKyc(getAddressFromEntity(tokenEntity))
                .send();

        assertThat(result).isTrue();

        final var functionCall = contract.send_getTokenDefaultKyc(getAddressFromEntity(tokenEntity));
        testEstimateGas(functionCall, contract);
    }

    @Test
    void getTokenType() throws Exception {
        final var tokenEntity = persistFungibleToken();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);
        final var result =
                contract.call_getType(getAddressFromEntity(tokenEntity)).send();

        assertThat(result).isEqualTo(BigInteger.ZERO);

        final var functionCall = contract.send_getType(getAddressFromEntity(tokenEntity));
        testEstimateGas(functionCall, contract);
    }

    @Test
    void getTokenTypeNFT() throws Exception {
        final var tokenEntity = persistNft();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);
        final var result =
                contract.call_getType(getAddressFromEntity(tokenEntity)).send();

        assertThat(result).isEqualTo(BigInteger.ONE);

        final var functionCall = contract.send_getType(getAddressFromEntity(tokenEntity));
        testEstimateGas(functionCall, contract);
    }

    @Test
    void getTokenDefaultFreeze() throws Exception {
        final var tokenEntity = persistTokenEntity();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .freezeDefault(true))
                .persist();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);
        final var result = contract.call_getTokenDefaultFreeze(getAddressFromEntity(tokenEntity))
                .send();

        assertThat(result).isTrue();

        final var functionCall = contract.send_getTokenDefaultFreeze(getAddressFromEntity(tokenEntity));
        testEstimateGas(functionCall, contract);
    }

    @Test
    void getNFTDefaultFreeze() throws Exception {
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
        final var result = contract.call_getTokenDefaultFreeze(getAddressFromEntity(tokenEntity))
                .send();

        assertThat(result).isTrue();

        final var functionCall = contract.send_getTokenDefaultFreeze(getAddressFromEntity(tokenEntity));
        testEstimateGas(functionCall, contract);
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
    void getTokenKey(final TokenTypeEnum tokenType, final KeyValueType keyValueType, final KeyType keyType)
            throws Exception {
        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        final var tokenEntity = getTokenWithKey(tokenType, keyValueType, keyType, contract);

        final var result = contract.call_getTokenKeyPublic(
                        getAddressFromEntity(tokenEntity), keyType.getKeyTypeNumeric())
                .send();

        final var expectedKey = getKeyValueForType(keyValueType, contract.getContractAddress());

        assertThat(result).isEqualTo(expectedKey);

        final var functionCall =
                contract.send_getTokenKeyPublic(getAddressFromEntity(tokenEntity), keyType.getKeyTypeNumeric());
        testEstimateGas(functionCall, contract);
    }

    private Entity getTokenWithKey(
            final TokenTypeEnum tokenType,
            final KeyValueType keyValueType,
            final KeyType keyType,
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
            case KeyType.ADMIN_KEY:
                break;
            case KeyType.KYC_KEY:
                tokenBuilder.customize(t -> t.kycKey(key.toByteArray()));
                break;
            case KeyType.FREEZE_KEY:
                tokenBuilder.customize(t -> t.freezeKey(key.toByteArray()));
                break;
            case KeyType.WIPE_KEY:
                tokenBuilder.customize(t -> t.wipeKey(key.toByteArray()));
                break;
            case KeyType.SUPPLY_KEY:
                tokenBuilder.customize(t -> t.supplyKey(key.toByteArray()));
                break;
            case KeyType.FEE_SCHEDULE_KEY:
                tokenBuilder.customize(t -> t.feeScheduleKey(key.toByteArray()));
                break;
            case KeyType.PAUSE_KEY:
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

    @Test
    void getCustomFeesForTokenWithFixedFee() throws Exception {
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
        final var result = contract.call_getCustomFeesForToken(getAddressFromEntity(tokenEntity))
                .send();

        final var expectedFee = new FixedFee(
                BigInteger.valueOf(100L),
                getAddressFromEntity(tokenEntity),
                false,
                false,
                Address.fromHexString(
                                Bytes.wrap(collectorAccount.getEvmAddress()).toHexString())
                        .toHexString());

        assertThat(result.component1().getFirst()).isEqualTo(expectedFee);

        final var functionCall = contract.send_getCustomFeesForToken(getAddressFromEntity(tokenEntity));
        testEstimateGas(functionCall, contract);
    }

    @Test
    void getCustomFeesForTokenWithFractionalFee() throws Exception {
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
        final var result = contract.call_getCustomFeesForToken(getAddressFromEntity(tokenEntity))
                .send();

        final var expectedFee = new PrecompileTestContract.FractionalFee(
                BigInteger.valueOf(100L),
                BigInteger.valueOf(10L),
                BigInteger.valueOf(1L),
                BigInteger.valueOf(1000L),
                true,
                Address.fromHexString(
                                Bytes.wrap(collectorAccount.getEvmAddress()).toHexString())
                        .toHexString());

        assertThat(result.component2().getFirst()).isEqualTo(expectedFee);

        final var functionCall = contract.send_getCustomFeesForToken(getAddressFromEntity(tokenEntity));
        testEstimateGas(functionCall, contract);
    }

    @Test
    void getCustomFeesForTokenWithRoyaltyFee() throws Exception {
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
        final var result = contract.call_getCustomFeesForToken(getAddressFromEntity(tokenEntity))
                .send();

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

        assertThat(result.component3().getFirst()).isEqualTo(expectedFee);

        final var functionCall = contract.send_getCustomFeesForToken(getAddressFromEntity(tokenEntity));
        testEstimateGas(functionCall, contract);
    }

    @Test
    void getExpiryForToken() throws Exception {
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
        final var result = contract.call_getExpiryInfoForToken(getAddressFromEntity(tokenEntity))
                .send();

        final var expectedExpiry = new PrecompileTestContract.Expiry(
                BigInteger.valueOf(expiryPeriod).divide(BigInteger.valueOf(1_000_000_000L)),
                Address.fromHexString(
                                Bytes.wrap(autoRenewAccount.getEvmAddress()).toHexString())
                        .toHexString(),
                BigInteger.valueOf(autoRenewExpiry));

        assertThat(result).isEqualTo(expectedExpiry);

        final var functionCall = contract.send_getExpiryInfoForToken(getAddressFromEntity(tokenEntity));
        testEstimateGas(functionCall, contract);
    }

    @Test
    void getAllowanceForToken() throws Exception {
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
        final var result = contract.call_htsAllowance(
                        getAddressFromEntity(tokenEntity), getAliasFromEntity(owner), getAliasFromEntity(spender))
                .send();

        assertThat(result).isEqualTo(BigInteger.valueOf(amountGranted));

        final var functionCall = contract.send_htsAllowance(
                getAddressFromEntity(tokenEntity), getAliasFromEntity(owner), getAliasFromEntity(spender));
        testEstimateGas(functionCall, contract);
    }

    @Test
    void isApprovedForAllNFT() throws Exception {
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
        final var result = contract.call_htsIsApprovedForAll(
                        getAddressFromEntity(tokenEntity), getAliasFromEntity(owner), getAliasFromEntity(spender))
                .send();

        assertThat(result).isEqualTo(Boolean.TRUE);

        final var functionCall = contract.send_htsIsApprovedForAll(
                getAddressFromEntity(tokenEntity), getAliasFromEntity(owner), getAliasFromEntity(spender));
        testEstimateGas(functionCall, contract);
    }

    @Test
    void getFungibleTokenInfo() throws Exception {
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
        final var result = contract.call_getInformationForFungibleToken(getAddressFromEntity(tokenEntity))
                .send();

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

        assertThat(result).isEqualTo(expectedFungibleTokenInfo);

        final var functionCall = contract.send_getInformationForFungibleToken(getAddressFromEntity(tokenEntity));
        testEstimateGas(functionCall, contract);
    }

    @Test
    void getNonFungibleTokenInfo() throws Exception {
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
        final var result = contract.call_getInformationForNonFungibleToken(
                        getAddressFromEntity(tokenEntity), BigInteger.ONE)
                .send();

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

        assertThat(result).isEqualTo(expectedNonFungibleTokenInfo);

        final var functionCall =
                contract.send_getInformationForNonFungibleToken(getAddressFromEntity(tokenEntity), BigInteger.ONE);
        testEstimateGas(functionCall, contract);
    }

    @ParameterizedTest
    @CsvSource({"FUNGIBLE_COMMON", "NON_FUNGIBLE_UNIQUE"})
    void getTokenInfo(final TokenTypeEnum tokenType) throws Exception {
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
        final var result = contract.call_getInformationForToken(getAddressFromEntity(tokenEntity))
                .send();

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

        assertThat(result).isEqualTo(expectedTokenInfo);

        final var functionCall = contract.send_getInformationForToken(getAddressFromEntity(tokenEntity));
        testEstimateGas(functionCall, contract);
    }

    private Entity persistAccountEntity() {
        return domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.ACCOUNT).deleted(false))
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
        return EntityIdUtils.asHexedEvmAddress(new Id(entity.getShard(), entity.getRealm(), entity.getId()));
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
        final var entity = domainBuilder.entity().get();
        final var treasuryAccountId = token.getTreasuryAccountId();
        final var keys = new ArrayList<TokenKey>();
        final var entityRenewAccountId = entity.getAutoRenewAccountId();

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
                        BigInteger.valueOf(entity.getEffectiveExpiration()),
                        EntityIdUtils.asHexedEvmAddress(new Id(0, 0, entityRenewAccountId.longValue())),
                        BigInteger.valueOf(entity.getEffectiveExpiration())));
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

    @ParameterizedTest
    @MethodSource("htsContractFunctionArgumentsProviderHistoricalReadOnly")
    void evmPrecompileReadOnlyTokenFunctionsTestEthCallHistorical(
            final ContractReadFunctionsHistorical contractFunc, final String blockNumber) {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                contractFunc.name, PRECOMPILE_TEST_CONTRACT_ABI_PATH, contractFunc.functionParameters);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, PRECOMPILE_TEST_CONTRACT_ADDRESS, ETH_CALL, 0L, BlockType.of(blockNumber));
        switch (contractFunc) {
            case GET_CUSTOM_FEES_FOR_TOKEN_WITH_FIXED_FEE -> customFeePersistHistorical(
                    FIXED_FEE,
                    Range.closedOpen(
                            recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd()));
            case GET_CUSTOM_FEES_FOR_TOKEN_WITH_FRACTIONAL_FEE,
                    GET_INFORMATION_FOR_TOKEN_FUNGIBLE,
                    GET_INFORMATION_FOR_TOKEN_NFT,
                    GET_FUNGIBLE_TOKEN_INFO,
                    GET_NFT_INFO -> customFeePersistHistorical(
                    FRACTIONAL_FEE,
                    Range.closedOpen(
                            recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd()));
            case GET_CUSTOM_FEES_FOR_TOKEN_WITH_ROYALTY_FEE -> customFeePersistHistorical(
                    ROYALTY_FEE,
                    Range.closedOpen(
                            recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd()));
        }
        final var successfulResponse = functionEncodeDecoder.encodedResultFor(
                contractFunc.name, PRECOMPILE_TEST_CONTRACT_ABI_PATH, contractFunc.expectedResultFields);

        if (Long.parseLong(blockNumber) < EVM_V_34_BLOCK) {
            switch (contractFunc) {
                    // These are the only cases where an exception is not thrown. There are custom
                    // precompiles for these cases and an exception is thrown there but the top
                    // stack frame overrides the result with the one from Besu and the zero address
                    // is returned.
                case HTS_GET_APPROVED, HTS_ALLOWANCE, HTS_IS_APPROVED_FOR_ALL -> {
                    assertThat(contractCallService.processCall(serviceParameters))
                            .isEqualTo(String.valueOf(Bytes32.ZERO));
                    return;
                }
            }
            assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                    .isInstanceOf(MirrorEvmTransactionException.class);
        } else {
            assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {51, Long.MAX_VALUE - 1})
    void evmPrecompileReadOnlyTokenFunctionsEthCallHistoricalNotExistingBlockTest(final long blockNumber) {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                "isTokenFrozen",
                PRECOMPILE_TEST_CONTRACT_ABI_PATH,
                FUNGIBLE_TOKEN_ADDRESS_HISTORICAL,
                SENDER_ADDRESS_HISTORICAL);
        final var serviceParameters = serviceParametersForExecution(
                functionHash,
                PRECOMPILE_TEST_CONTRACT_ADDRESS,
                ETH_CALL,
                0L,
                BlockType.of(String.valueOf(blockNumber)));

        final var latestBlockNumber = recordFileRepository.findLatestIndex().orElse(Long.MAX_VALUE);

        // Block number (Long.MAX_VALUE - 1) does not exist in the DB and is after the
        // latest block available in the DB => returning error
        if (blockNumber > latestBlockNumber) {
            assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                    .isInstanceOf(BlockNumberOutOfRangeException.class);
        } else if (blockNumber == 51) {
            // Block number 51 = (EVM_V_34_BLOCK + 1) does not exist in the DB but it is before the latest
            // block available in the DB => throw an exception
            assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                    .isInstanceOf(BlockNumberNotFoundException.class);
        }
    }

    @ParameterizedTest
    @EnumSource(SupportedContractModificationFunctions.class)
    void evmPrecompileSupportedModificationTokenFunctionsTestEstimateGas(
            final SupportedContractModificationFunctions contractFunc) {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                contractFunc.name, MODIFICATION_CONTRACT_ABI_PATH, contractFunc.functionParameters);
        final long value = getValue(contractFunc);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, MODIFICATION_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, value, BlockType.LATEST);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();
    }

    @ParameterizedTest
    @EnumSource(NestedContractModificationFunctions.class)
    void nestedContractModificationFunctionsTest(final NestedContractModificationFunctions contractFunc) {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                contractFunc.name, MODIFICATION_CONTRACT_ABI_PATH, contractFunc.functionParameters);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, MODIFICATION_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 0L, BlockType.LATEST);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();
    }

    @ParameterizedTest
    @EnumSource(
            value = SupportedContractModificationFunctions.class,
            mode = Mode.INCLUDE,
            names = {
                "MINT_TOKEN",
                "MINT_NFT_TOKEN",
                "BURN_TOKEN",
                "BURN_NFT_TOKEN",
                "CREATE_FUNGIBLE_TOKEN",
                "CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES",
                "CREATE_NON_FUNGIBLE_TOKEN",
                "CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES"
            })
    void supportedContractModificationFunctionsResponseBodyTest(
            final SupportedContractModificationFunctions contractFunc) {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                contractFunc.name, MODIFICATION_CONTRACT_ABI_PATH, contractFunc.functionParameters);
        final long value = getValue(contractFunc);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, MODIFICATION_CONTRACT_ADDRESS, ETH_CALL, value, BlockType.LATEST);
        final var expectedResult = functionEncodeDecoder.encodedResultFor(
                contractFunc.name, MODIFICATION_CONTRACT_ABI_PATH, contractFunc.expectedResult);
        final var result = contractCallService.processCall(serviceParameters);
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    void nftInfoForInvalidSerialNo() {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                "getInformationForNonFungibleToken", PRECOMPILE_TEST_CONTRACT_ABI_PATH, NFT_ADDRESS, 4L);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, PRECOMPILE_TEST_CONTRACT_ADDRESS, ETH_CALL, 0L, BlockType.LATEST);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void tokenInfoForNonTokenAccount() {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                "getInformationForFungibleToken", PRECOMPILE_TEST_CONTRACT_ABI_PATH, SENDER_ADDRESS);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, PRECOMPILE_TEST_CONTRACT_ADDRESS, ETH_CALL, 0L, BlockType.LATEST);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void notExistingPrecompileCallFails() {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                "callNotExistingPrecompile", MODIFICATION_CONTRACT_ABI_PATH, FUNGIBLE_TOKEN_ADDRESS);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, MODIFICATION_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 0L, BlockType.LATEST);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void createFungibleTokenWithInheritKeysEstimateGas() {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                "createFungibleTokenWithInheritKeysExternal", MODIFICATION_CONTRACT_ABI_PATH);
        final var serviceParameters = serviceParametersForExecution(
                functionHash,
                MODIFICATION_WITHOUT_KEY_CONTRACT_ADDRESS,
                ETH_ESTIMATE_GAS,
                10000 * 100_000_000L,
                BlockType.LATEST);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();
    }

    @Test
    void createFungibleTokenWithInheritKeysCall() throws Exception {
        domainBuilder.recordFile().customize(f -> f.index(0L)).persist();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);
        contract.send_createFungibleTokenWithInheritKeysExternal(BigInteger.valueOf(10000 * 100_000_000L))
                .send();

        final var result = testWeb3jService.getOutput();
        assertThat(result).isNotEqualTo(EMPTY_UNTRIMMED_ADDRESS);
    }

    @ParameterizedTest
    @EnumSource(value = TokenCreateNegativeCases.class)
    void invalidTokenCreateCases(final TokenCreateNegativeCases tokenCreateNegativeCase) throws Exception {
        var initialSupply = BigInteger.valueOf(10L);
        var decimals = BigInteger.valueOf(10L);
        var value = BigInteger.valueOf(10000L * 100_000_000);

        domainBuilder.recordFile().customize(f -> f.index(0L)).persist();
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);
        RemoteFunctionCall function = null;

        switch (tokenCreateNegativeCase) {
            case INVALID_MEMO -> function = contract.send_createFungibleTokenExternal(
                    convertTokenEntityToHederaToken(domainBuilder
                            .token()
                            .customize(t -> t.metadata(new byte[mirrorNodeEvmProperties.getMaxMemoUtf8Bytes() + 1]))
                            .get()),
                    initialSupply,
                    decimals,
                    value);
            case INVALID_NAME -> function = contract.send_createFungibleTokenExternal(
                    convertTokenEntityToHederaToken(domainBuilder
                            .token()
                            .customize(t -> t.name(new String(
                                    new byte[mirrorNodeEvmProperties.getMaxTokenNameUtf8Bytes() + 1],
                                    StandardCharsets.UTF_8)))
                            .get()),
                    initialSupply,
                    decimals,
                    value);
            case INVALID_SYMBOL -> function = contract.send_createFungibleTokenExternal(
                    convertTokenEntityToHederaToken(domainBuilder
                            .token()
                            .customize(t -> t.symbol(new String(
                                    new byte[mirrorNodeEvmProperties.getMaxTokenNameUtf8Bytes() + 1],
                                    StandardCharsets.UTF_8)))
                            .get()),
                    initialSupply,
                    decimals,
                    value);
            case INVALID_DECIMALS -> function = contract.send_createFungibleTokenExternal(
                    convertTokenEntityToHederaToken(
                            domainBuilder.token().customize(t -> t.decimals(-1)).get()),
                    initialSupply,
                    decimals,
                    value);
            case INVALID_INITIAL_SUPPLY -> function = contract.send_createFungibleTokenExternal(
                    convertTokenEntityToHederaToken(domainBuilder
                            .token()
                            .customize(t -> t.initialSupply(-1L))
                            .get()),
                    initialSupply,
                    decimals,
                    value);
            case INITIAL_SUPPLY_GREATER_THAN_MAX_SUPPLY -> function = contract.send_createFungibleTokenExternal(
                    convertTokenEntityToHederaToken(domainBuilder
                            .token()
                            .customize(t -> t.initialSupply(10_000_001L))
                            .get()),
                    initialSupply,
                    decimals,
                    value);
            case NFT_NO_SUPPLY_KEY -> function = contract.send_createNonFungibleTokenExternal(
                    convertTokenEntityToHederaToken(domainBuilder
                            .token()
                            .customize(t -> t.supplyKey(new byte[0]))
                            .get()),
                    value);
            case NFT_NO_TREASURY -> function = contract.send_createNonFungibleTokenExternal(
                    convertTokenEntityToHederaToken(domainBuilder
                            .token()
                            .customize(t -> t.treasuryAccountId(null))
                            .get()),
                    value);
            case NFT_FREEZE_DEFAULT_NO_KEY -> function = contract.send_createNonFungibleTokenExternal(
                    convertTokenEntityToHederaToken(domainBuilder
                            .token()
                            .customize(t -> t.freezeDefault(true).freezeKey(new byte[0]))
                            .get()),
                    value);
            case NFT_INVALID_AUTORENEW_ACCOUNT -> function = contract.send_createNonFungibleTokenExternal(
                    convertTokenEntityToHederaToken(
                            domainBuilder.token().get(),
                            domainBuilder
                                    .entity()
                                    .customize(e -> e.autoRenewPeriod(10L))
                                    .get()),
                    value);
        }

        final var functionToCall = function;
        assertThatThrownBy(functionToCall::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Getter
    @RequiredArgsConstructor
    enum ContractReadFunctionsHistorical implements ContractFunctionProviderEnum {
        IS_FROZEN(
                "isTokenFrozen",
                new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL, SENDER_ADDRESS_HISTORICAL},
                new Boolean[] {true}),
        IS_FROZEN_WITH_ALIAS(
                "isTokenFrozen",
                new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL, SENDER_ALIAS_HISTORICAL},
                new Boolean[] {true}),
        IS_KYC(
                "isKycGranted",
                new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL, SENDER_ADDRESS_HISTORICAL},
                new Boolean[] {true}),
        IS_KYC_WITH_ALIAS(
                "isKycGranted",
                new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL, SENDER_ALIAS_HISTORICAL},
                new Boolean[] {true}),
        IS_KYC_FOR_NFT(
                "isKycGranted", new Address[] {NFT_ADDRESS_HISTORICAL, SENDER_ADDRESS_HISTORICAL}, new Boolean[] {true
                }),
        IS_KYC_FOR_NFT_WITH_ALIAS(
                "isKycGranted", new Address[] {NFT_ADDRESS_HISTORICAL, SENDER_ALIAS_HISTORICAL}, new Boolean[] {true}),
        IS_TOKEN_PRECOMPILE("isTokenAddress", new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL}, new Boolean[] {true}),
        IS_TOKEN_PRECOMPILE_NFT("isTokenAddress", new Address[] {NFT_ADDRESS_HISTORICAL}, new Boolean[] {true}),
        GET_TOKEN_DEFAULT_KYC(
                "getTokenDefaultKyc", new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL}, new Boolean[] {true}),
        GET_TOKEN_DEFAULT_KYC_NFT("getTokenDefaultKyc", new Address[] {NFT_ADDRESS_HISTORICAL}, new Boolean[] {true}),
        GET_TOKEN_TYPE("getType", new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL}, new Long[] {0L}),
        GET_TOKEN_TYPE_FOR_NFT("getType", new Address[] {NFT_ADDRESS_HISTORICAL}, new Long[] {1L}),
        GET_TOKEN_DEFAULT_FREEZE(
                "getTokenDefaultFreeze", new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL}, new Boolean[] {true}),
        GET_TOKEN_DEFAULT_FREEZE_FOR_NFT(
                "getTokenDefaultFreeze", new Address[] {NFT_ADDRESS_HISTORICAL}, new Boolean[] {true}),
        GET_TOKEN_ADMIN_KEY_WITH_CONTRACT_ADDRESS(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_CONTRACT_ADDRESS_HISTORICAL, 1L},
                new Object[] {false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
        GET_TOKEN_FREEZE_KEY_WITH_CONTRACT_ADDRESS(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_CONTRACT_ADDRESS_HISTORICAL, 4L},
                new Object[] {false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
        GET_TOKEN_WIPE_KEY_WITH_CONTRACT_ADDRESS(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_CONTRACT_ADDRESS_HISTORICAL, 8L},
                new Object[] {false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
        GET_TOKEN_SUPPLY_KEY_WITH_CONTRACT_ADDRESS(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_CONTRACT_ADDRESS_HISTORICAL, 16L},
                new Object[] {false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
        GET_TOKEN_ADMIN_KEY_WITH_ED25519_KEY(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ED25519_KEY_HISTORICAL, 1L},
                new Object[] {false, Address.ZERO, ED25519_KEY, new byte[0], Address.ZERO}),
        GET_TOKEN_FREEZE_KEY_WITH_ED25519_KEY(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ED25519_KEY_HISTORICAL, 4L},
                new Object[] {false, Address.ZERO, ED25519_KEY, new byte[0], Address.ZERO}),
        GET_TOKEN_WIPE_KEY_WITH_ED25519_KEY(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ED25519_KEY_HISTORICAL, 8L},
                new Object[] {false, Address.ZERO, ED25519_KEY, new byte[0], Address.ZERO}),
        GET_TOKEN_SUPPLY_KEY_WITH_ED25519_KEY(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ED25519_KEY_HISTORICAL, 16L},
                new Object[] {false, Address.ZERO, ED25519_KEY, new byte[0], Address.ZERO}),
        GET_TOKEN_ADMIN_KEY_WITH_ECDSA_KEY(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ECDSA_KEY_HISTORICAL, 1L},
                new Object[] {false, Address.ZERO, new byte[0], ECDSA_KEY, Address.ZERO}),
        GET_TOKEN_FREEZE_KEY_WITH_ECDSA_KEY(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ECDSA_KEY_HISTORICAL, 4L},
                new Object[] {false, Address.ZERO, new byte[0], ECDSA_KEY, Address.ZERO}),
        GET_TOKEN_WIPE_KEY_WITH_ECDSA_KEY(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ECDSA_KEY_HISTORICAL, 8L},
                new Object[] {false, Address.ZERO, new byte[0], ECDSA_KEY, Address.ZERO}),
        GET_TOKEN_SUPPLY_KEY_WITH_ECDSA_KEY(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ECDSA_KEY_HISTORICAL, 16L},
                new Object[] {false, Address.ZERO, new byte[0], ECDSA_KEY, Address.ZERO}),
        GET_TOKEN_ADMIN_KEY_WITH_DELEGATABLE_CONTRACT_ID(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_DELEGATABLE_CONTRACT_ID_HISTORICAL, 1L},
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS}),
        GET_TOKEN_FREEZE_KEY_WITH_DELEGATABLE_CONTRACT_ID(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_DELEGATABLE_CONTRACT_ID_HISTORICAL, 4L},
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS}),
        GET_TOKEN_WIPE_KEY_WITH_DELEGATABLE_CONTRACT_ID(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_DELEGATABLE_CONTRACT_ID_HISTORICAL, 8L},
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS}),
        GET_TOKEN_SUPPLY_KEY_WITH_DELEGATABLE_CONTRACT_ID(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_DELEGATABLE_CONTRACT_ID_HISTORICAL, 16L},
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS}),
        GET_TOKEN_KYC_KEY_FOR_NFT_WITH_CONTRACT_ADDRESS(
                "getTokenKeyPublic",
                new Object[] {NFT_ADDRESS_GET_KEY_WITH_CONTRACT_ADDRESS_HISTORICAL, 2L},
                new Object[] {false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
        GET_TOKEN_FEE_KEY_FOR_NFT_WITH_CONTRACT_ADDRESS(
                "getTokenKeyPublic",
                new Object[] {NFT_ADDRESS_GET_KEY_WITH_CONTRACT_ADDRESS_HISTORICAL, 32L},
                new Object[] {false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
        GET_TOKEN_PAUSE_KEY_FOR_NFT_WITH_CONTRACT_ADDRESS(
                "getTokenKeyPublic",
                new Object[] {NFT_ADDRESS_GET_KEY_WITH_CONTRACT_ADDRESS_HISTORICAL, 64L},
                new Object[] {false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
        GET_TOKEN_KYC_KEY_FOR_NFT_WITH_ED25519_KEY(
                "getTokenKeyPublic",
                new Object[] {NFT_ADDRESS_GET_KEY_WITH_ED25519_KEY_HISTORICAL, 2L},
                new Object[] {false, Address.ZERO, ED25519_KEY, new byte[0], Address.ZERO}),
        GET_TOKEN_FEE_KEY_FOR_NFT_WITH_ED25519_KEY(
                "getTokenKeyPublic",
                new Object[] {NFT_ADDRESS_GET_KEY_WITH_ED25519_KEY_HISTORICAL, 32L},
                new Object[] {false, Address.ZERO, ED25519_KEY, new byte[0], Address.ZERO}),
        GET_TOKEN_PAUSE_KEY_FOR_NFT_WITH_ED25519_KEY(
                "getTokenKeyPublic",
                new Object[] {NFT_ADDRESS_GET_KEY_WITH_ED25519_KEY_HISTORICAL, 64L},
                new Object[] {false, Address.ZERO, ED25519_KEY, new byte[0], Address.ZERO}),
        GET_TOKEN_KYC_KEY_FOR_NFT_WITH_ECDSA_KEY(
                "getTokenKeyPublic",
                new Object[] {NFT_ADDRESS_GET_KEY_WITH_ECDSA_KEY_HISTORICAL, 2L},
                new Object[] {false, Address.ZERO, new byte[0], ECDSA_KEY, Address.ZERO}),
        GET_TOKEN_FEE_KEY_FOR_NFT_WITH_ECDSA_KEY(
                "getTokenKeyPublic",
                new Object[] {NFT_ADDRESS_GET_KEY_WITH_ECDSA_KEY_HISTORICAL, 32L},
                new Object[] {false, Address.ZERO, new byte[0], ECDSA_KEY, Address.ZERO}),
        GET_TOKEN_PAUSE_KEY_FOR_NFT_WITH_ECDSA_KEY(
                "getTokenKeyPublic",
                new Object[] {NFT_ADDRESS_GET_KEY_WITH_ECDSA_KEY_HISTORICAL, 64L},
                new Object[] {false, Address.ZERO, new byte[0], ECDSA_KEY, Address.ZERO}),
        GET_TOKEN_KYC_KEY_FOR_NFT_WITH_DELEGATABLE_CONTRACT_ID(
                "getTokenKeyPublic",
                new Object[] {NFT_ADDRESS_GET_KEY_WITH_DELEGATABLE_CONTRACT_ID_HISTORICAL, 2L},
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS}),
        GET_TOKEN_FEE_KEY_FOR_NFT_WITH_DELEGATABLE_CONTRACT_ID(
                "getTokenKeyPublic",
                new Object[] {NFT_ADDRESS_GET_KEY_WITH_DELEGATABLE_CONTRACT_ID_HISTORICAL, 32L},
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS}),
        GET_TOKEN_PAUSE_KEY_FOR_NFT_WITH_DELEGATABLE_CONTRACT_ID(
                "getTokenKeyPublic",
                new Object[] {NFT_ADDRESS_GET_KEY_WITH_DELEGATABLE_CONTRACT_ID_HISTORICAL, 64L},
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS}),
        GET_CUSTOM_FEES_FOR_TOKEN_WITH_FIXED_FEE(
                "getCustomFeesForToken", new Object[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL}, new Object[] {
                    new Object[] {100L, FUNGIBLE_TOKEN_ADDRESS_HISTORICAL, false, false, SENDER_ALIAS_HISTORICAL},
                    new Object[0],
                    new Object[0]
                }),
        GET_CUSTOM_FEES_FOR_TOKEN_WITH_FRACTIONAL_FEE(
                "getCustomFeesForToken", new Object[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL}, new Object[] {
                    new Object[0], new Object[] {100L, 10L, 1L, 1000L, true, SENDER_ALIAS_HISTORICAL}, new Object[0]
                }),
        GET_CUSTOM_FEES_FOR_TOKEN_WITH_ROYALTY_FEE(
                "getCustomFeesForToken", new Object[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL}, new Object[] {
                    new Object[0],
                    new Object[0],
                    new Object[] {20L, 10L, 100L, FUNGIBLE_TOKEN_ADDRESS_HISTORICAL, SENDER_ALIAS_HISTORICAL}
                }),
        GET_TOKEN_EXPIRY(
                "getExpiryInfoForToken",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_WITH_EXPIRY_HISTORICAL},
                new Object[] {1000L, AUTO_RENEW_ACCOUNT_ADDRESS_HISTORICAL, 8_000_000L}),
        HTS_GET_APPROVED(
                "htsGetApproved", new Object[] {NFT_ADDRESS_HISTORICAL, 1L}, new Object[] {SPENDER_ALIAS_HISTORICAL}),
        HTS_ALLOWANCE(
                "htsAllowance",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL, SENDER_ADDRESS_HISTORICAL, SPENDER_ADDRESS_HISTORICAL},
                new Object[] {13L}),
        HTS_IS_APPROVED_FOR_ALL(
                "htsIsApprovedForAll",
                new Object[] {NFT_ADDRESS_HISTORICAL, SENDER_ADDRESS_HISTORICAL, SPENDER_ADDRESS_HISTORICAL},
                new Object[] {true}),
        GET_FUNGIBLE_TOKEN_INFO(
                "getInformationForFungibleToken", new Object[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL}, new Object[] {
                    new Object[] {
                        FUNGIBLE_HBAR_TOKEN_AND_KEYS_HISTORICAL,
                        12345L,
                        false,
                        false,
                        true,
                        new Object[] {100L, 10L, 1L, 1000L, true, SENDER_ALIAS_HISTORICAL},
                        LEDGER_ID
                    },
                    12
                }),
        GET_NFT_INFO("getInformationForNonFungibleToken", new Object[] {NFT_ADDRESS_HISTORICAL, 1L}, new Object[] {
            new Object[] {
                NFT_HBAR_TOKEN_AND_KEYS_HISTORICAL,
                2L,
                false,
                false,
                true,
                new Object[] {0L, 0L, 0L, 0L, false, SENDER_ALIAS_HISTORICAL},
                LEDGER_ID
            },
            1L,
            OWNER_ADDRESS_HISTORICAL,
            1475067194L,
            "NFT_METADATA_URI".getBytes(),
            SPENDER_ADDRESS_HISTORICAL
        }),
        GET_INFORMATION_FOR_TOKEN_FUNGIBLE(
                "getInformationForToken", new Object[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL}, new Object[] {
                    FUNGIBLE_HBAR_TOKEN_AND_KEYS_HISTORICAL,
                    12345L,
                    false,
                    false,
                    true,
                    new Object[] {100L, 10L, 1L, 1000L, true, SENDER_ALIAS_HISTORICAL},
                    LEDGER_ID
                }),
        GET_INFORMATION_FOR_TOKEN_NFT("getInformationForToken", new Object[] {NFT_ADDRESS_HISTORICAL}, new Object[] {
            NFT_HBAR_TOKEN_AND_KEYS_HISTORICAL,
            2L,
            false,
            false,
            true,
            new Object[] {0L, 0L, 0L, 0L, false, SENDER_ALIAS_HISTORICAL},
            LEDGER_ID
        });

        private final String name;
        private final Object[] functionParameters;
        private final Object[] expectedResultFields;
    }

    @RequiredArgsConstructor
    enum TokenCreateNegativeCases {
        INVALID_NAME,
        INVALID_MEMO,
        INVALID_SYMBOL,
        INVALID_DECIMALS,
        INVALID_INITIAL_SUPPLY,
        INITIAL_SUPPLY_GREATER_THAN_MAX_SUPPLY,
        NFT_NO_SUPPLY_KEY,
        NFT_NO_TREASURY,
        NFT_FREEZE_DEFAULT_NO_KEY,
        NFT_INVALID_AUTORENEW_ACCOUNT
    }

    @Getter
    @RequiredArgsConstructor
    enum SupportedContractModificationFunctions implements ContractFunctionProviderEnum {
        TRANSFER_FROM(
                "transferFromExternal",
                new Object[] {TRANSFRER_FROM_TOKEN_ADDRESS, SENDER_ALIAS, SPENDER_ALIAS, 1L},
                new Object[] {}),
        APPROVE("approveExternal", new Object[] {FUNGIBLE_TOKEN_ADDRESS, SPENDER_ALIAS, 1L}, new Object[] {}),
        DELETE_ALLOWANCE(
                "approveExternal", new Object[] {FUNGIBLE_TOKEN_ADDRESS, SPENDER_ADDRESS, 0L}, new Object[] {}),
        DELETE_ALLOWANCE_NFT("approveNFTExternal", new Object[] {NFT_ADDRESS, Address.ZERO, 1L}, new Object[] {}),
        APPROVE_NFT("approveNFTExternal", new Object[] {NFT_ADDRESS, TREASURY_ADDRESS, 1L}, new Object[] {}),
        SET_APPROVAL_FOR_ALL(
                "setApprovalForAllExternal", new Object[] {NFT_ADDRESS, TREASURY_ADDRESS, true}, new Object[] {}),
        ASSOCIATE_TOKEN(
                "associateTokenExternal", new Object[] {SPENDER_ALIAS, FUNGIBLE_TOKEN_ADDRESS}, new Object[] {}),
        ASSOCIATE_TOKENS(
                "associateTokensExternal",
                new Object[] {SPENDER_ALIAS, new Address[] {FUNGIBLE_TOKEN_ADDRESS}},
                new Object[] {}),
        HRC_ASSOCIATE_REDIRECT(
                "associateWithRedirect", new Address[] {FUNGIBLE_TOKEN_ADDRESS_NOT_ASSOCIATED}, new Object[] {}),
        MINT_TOKEN(
                "mintTokenExternal",
                new Object[] {NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS, 100L, new byte[0][0]},
                new Object[] {SUCCESS_RESULT, 12445L, new long[0]}),
        MINT_NFT_TOKEN(
                "mintTokenExternal",
                new Object[] {
                    NFT_ADDRESS,
                    0L,
                    new byte[][] {ByteString.copyFromUtf8("firstMeta").toByteArray()}
                },
                new Object[] {SUCCESS_RESULT, 1_000_000_000L + 1, new long[] {1L}}),
        DISSOCIATE_TOKEN(
                "dissociateTokenExternal", new Object[] {SPENDER_ALIAS, TREASURY_TOKEN_ADDRESS}, new Object[] {}),
        DISSOCIATE_TOKENS(
                "dissociateTokensExternal",
                new Object[] {SPENDER_ALIAS, new Address[] {TREASURY_TOKEN_ADDRESS}},
                new Object[] {}),
        HRC_DISSOCIATE_REDIRECT("dissociateWithRedirect", new Address[] {FUNGIBLE_TOKEN_ADDRESS}, new Object[] {}),
        BURN_TOKEN(
                "burnTokenExternal",
                new Object[] {NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS, 1L, new long[0]},
                new Object[] {SUCCESS_RESULT, 12345L - 1}),
        BURN_NFT_TOKEN("burnTokenExternal", new Object[] {NFT_ADDRESS, 0L, new long[] {1}}, new Object[] {
            SUCCESS_RESULT, 1_000_000_000L - 1
        }),
        WIPE_TOKEN(
                "wipeTokenAccountExternal",
                new Object[] {NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS, SENDER_ALIAS, 1L},
                new Object[] {}),
        WIPE_NFT_TOKEN(
                "wipeTokenAccountNFTExternal",
                new Object[] {NFT_ADDRESS_WITH_DIFFERENT_OWNER_AND_TREASURY, SENDER_ALIAS, new long[] {1}},
                new Object[] {}),
        REVOKE_TOKEN_KYC(
                "revokeTokenKycExternal", new Object[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ALIAS}, new Object[] {}),
        GRANT_TOKEN_KYC("grantTokenKycExternal", new Object[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ALIAS}, new Object[] {}),
        DELETE_TOKEN("deleteTokenExternal", new Object[] {FUNGIBLE_TOKEN_ADDRESS}, new Object[] {}),
        FREEZE_TOKEN(
                "freezeTokenExternal",
                new Object[] {NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS, SPENDER_ALIAS},
                new Object[] {}),
        UNFREEZE_TOKEN(
                "unfreezeTokenExternal", new Object[] {FROZEN_FUNGIBLE_TOKEN_ADDRESS, SPENDER_ALIAS}, new Object[] {}),
        PAUSE_TOKEN("pauseTokenExternal", new Object[] {FUNGIBLE_TOKEN_ADDRESS}, new Object[] {}),
        UNPAUSE_TOKEN("unpauseTokenExternal", new Object[] {FUNGIBLE_TOKEN_ADDRESS}, new Object[] {}),
        CREATE_FUNGIBLE_TOKEN("createFungibleTokenExternal", new Object[] {FUNGIBLE_TOKEN, 10L, 10}, new Object[] {
            SUCCESS_RESULT, MODIFICATION_CONTRACT_ADDRESS
        }),
        CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES(
                "createFungibleTokenWithCustomFeesExternal",
                new Object[] {FUNGIBLE_TOKEN, 10L, 10, FIXED_FEE_WRAPPER, FRACTIONAL_FEE_WRAPPER},
                new Object[] {SUCCESS_RESULT, MODIFICATION_CONTRACT_ADDRESS}),
        CREATE_NON_FUNGIBLE_TOKEN("createNonFungibleTokenExternal", new Object[] {NON_FUNGIBLE_TOKEN}, new Object[] {
            SUCCESS_RESULT, MODIFICATION_CONTRACT_ADDRESS
        }),
        CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES(
                "createNonFungibleTokenWithCustomFeesExternal",
                new Object[] {NON_FUNGIBLE_TOKEN, FIXED_FEE_WRAPPER, ROYALTY_FEE_WRAPPER},
                new Object[] {SUCCESS_RESULT, MODIFICATION_CONTRACT_ADDRESS}),
        TRANSFER_TOKEN_WITH(
                "transferTokenExternal",
                new Object[] {TREASURY_TOKEN_ADDRESS, SPENDER_ALIAS, SENDER_ALIAS, 1L},
                new Object[] {}),
        TRANSFER_TOKENS(
                "transferTokensExternal",
                new Object[] {TREASURY_TOKEN_ADDRESS, new Address[] {OWNER_ADDRESS, SPENDER_ALIAS}, new long[] {1L, -1L}
                },
                new Object[] {}),
        TRANSFER_TOKENS_WITH_ALIAS(
                "transferTokensExternal",
                new Object[] {TREASURY_TOKEN_ADDRESS, new Address[] {SPENDER_ALIAS, SENDER_ALIAS}, new long[] {1L, -1L}
                },
                new Object[] {}),
        CRYPTO_TRANSFER_TOKENS(
                "cryptoTransferExternal",
                new Object[] {
                    new Object[] {}, new Object[] {TREASURY_TOKEN_ADDRESS, SENDER_ALIAS, OWNER_ADDRESS, 5L, false}
                },
                new Object[] {}),
        CRYPTO_TRANSFER_TOKENS_WITH_ALIAS(
                "cryptoTransferExternal",
                new Object[] {
                    new Object[] {}, new Object[] {TREASURY_TOKEN_ADDRESS, SENDER_ALIAS, SPENDER_ALIAS, 5L, false}
                },
                new Object[] {}),
        CRYPTO_TRANSFER_HBARS_AND_TOKENS(
                "cryptoTransferExternal",
                new Object[] {
                    new Object[] {SENDER_ALIAS, OWNER_ADDRESS, 5L},
                    new Object[] {TREASURY_TOKEN_ADDRESS, SENDER_ALIAS, OWNER_ADDRESS, 5L, false}
                },
                new Object[] {}),
        CRYPTO_TRANSFER_HBARS(
                "cryptoTransferExternal",
                new Object[] {
                    new Object[] {SENDER_ALIAS, OWNER_ADDRESS, 5L},
                    new Object[] {}
                },
                new Object[] {}),
        CRYPTO_TRANSFER_NFT(
                "cryptoTransferExternal",
                new Object[] {
                    new Object[] {}, new Object[] {NFT_TRANSFER_ADDRESS, OWNER_ADDRESS, SPENDER_ALIAS, 1L, true}
                },
                new Object[] {}),
        TRANSFER_NFT_TOKENS(
                "transferNFTsExternal",
                new Object[] {
                    NFT_TRANSFER_ADDRESS, new Address[] {OWNER_ADDRESS}, new Address[] {SPENDER_ALIAS}, new long[] {1}
                },
                new Object[] {}),
        TRANSFER_NFT_TOKEN(
                "transferNFTExternal",
                new Object[] {NFT_TRANSFER_ADDRESS, OWNER_ADDRESS, SPENDER_ALIAS, 1L},
                new Object[] {}),
        TRANSFER_FROM_NFT(
                "transferFromNFTExternal",
                new Object[] {NFT_TRANSFER_ADDRESS, OWNER_ADDRESS, SPENDER_ALIAS, 1L},
                new Object[] {}),
        UPDATE_TOKEN_INFO(
                "updateTokenInfoExternal",
                new Object[] {UNPAUSED_FUNGIBLE_TOKEN_ADDRESS, FUNGIBLE_TOKEN2},
                new Object[] {}),
        UPDATE_TOKEN_EXPIRY(
                "updateTokenExpiryInfoExternal",
                new Object[] {UNPAUSED_FUNGIBLE_TOKEN_ADDRESS, TOKEN_EXPIRY_WRAPPER},
                new Object[] {}),
        UPDATE_TOKEN_KEYS_CONTRACT_ADDRESS(
                "updateTokenKeysExternal",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {
                            АLL_CASES_KEY_TYPE,
                            new Object[] {
                                false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO
                            }
                        }
                    }
                },
                new Object[] {}),
        UPDATE_TOKEN_KEYS_DELEGATABLE_CONTRACT_ID(
                "updateTokenKeysExternal",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {
                            АLL_CASES_KEY_TYPE,
                            new Object[] {
                                false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS
                            }
                        }
                    }
                },
                new Object[] {}),
        UPDATE_TOKEN_KEYS_ED25519(
                "updateTokenKeysExternal",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {
                            АLL_CASES_KEY_TYPE,
                            new Object[] {false, Address.ZERO, NEW_ED25519_KEY, new byte[0], Address.ZERO}
                        }
                    }
                },
                new Object[] {}),
        UPDATE_TOKEN_KEYS_ECDSA(
                "updateTokenKeysExternal",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {
                            АLL_CASES_KEY_TYPE,
                            new Object[] {false, Address.ZERO, new byte[0], NEW_ECDSA_KEY, Address.ZERO}
                        }
                    }
                },
                new Object[] {});

        private final String name;
        private final Object[] functionParameters;
        private final Object[] expectedResult;
    }

    @Getter
    @RequiredArgsConstructor
    enum NestedContractModificationFunctions implements ContractFunctionProviderEnum {
        CREATE_CONTRACT_VIA_CREATE2_AND_TRANSFER_FROM_IT(
                "createContractViaCreate2AndTransferFromIt",
                new Object[] {TREASURY_TOKEN_ADDRESS_WITH_ALL_KEYS, SENDER_ALIAS, RECEIVER_ADDRESS, 1L});
        private final String name;
        private final Object[] functionParameters;
    }
}
