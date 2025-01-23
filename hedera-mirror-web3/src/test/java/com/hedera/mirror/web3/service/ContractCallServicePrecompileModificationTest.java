/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.entityIdFromEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.EMPTY_UNTRIMMED_ADDRESS;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.ESTIMATE_GAS_ERROR_MESSAGE;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.NEW_ECDSA_KEY;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.ZERO_VALUE;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.isWithinExpectedGasRange;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.longValueOf;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.common.domain.token.TokenPauseStatusEnum;
import com.hedera.mirror.common.domain.token.TokenSupplyTypeEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.utils.ContractFunctionProviderRecord;
import com.hedera.mirror.web3.web3j.generated.ModificationPrecompileTestContract;
import com.hedera.mirror.web3.web3j.generated.ModificationPrecompileTestContract.AccountAmount;
import com.hedera.mirror.web3.web3j.generated.ModificationPrecompileTestContract.Expiry;
import com.hedera.mirror.web3.web3j.generated.ModificationPrecompileTestContract.FixedFee;
import com.hedera.mirror.web3.web3j.generated.ModificationPrecompileTestContract.FractionalFee;
import com.hedera.mirror.web3.web3j.generated.ModificationPrecompileTestContract.HederaToken;
import com.hedera.mirror.web3.web3j.generated.ModificationPrecompileTestContract.KeyValue;
import com.hedera.mirror.web3.web3j.generated.ModificationPrecompileTestContract.NftTransfer;
import com.hedera.mirror.web3.web3j.generated.ModificationPrecompileTestContract.RoyaltyFee;
import com.hedera.mirror.web3.web3j.generated.ModificationPrecompileTestContract.TokenKey;
import com.hedera.mirror.web3.web3j.generated.ModificationPrecompileTestContract.TokenTransferList;
import com.hedera.mirror.web3.web3j.generated.ModificationPrecompileTestContract.TransferList;
import com.hedera.services.store.contracts.precompile.codec.KeyValueWrapper.KeyValueType;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.Key.KeyCase;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.tx.Contract;

class ContractCallServicePrecompileModificationTest extends AbstractContractCallServiceOpcodeTracerTest {

    @Test
    void transferFrom() throws Exception {
        // Given
        final var owner = accountEntityPersist();
        final var spender = accountEntityWithEvmAddressPersist();
        final var recipient = accountEntityWithEvmAddressPersist();

        final var tokenEntity =
                domainBuilder.entity().customize(e -> e.type(EntityType.TOKEN)).persist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .treasuryAccountId(owner.toEntityId()))
                .persist();

        tokenAccountPersist(tokenEntity, spender);
        tokenAccountPersist(tokenEntity, recipient);

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAccountPersist(tokenEntity, contractEntityId.getId());

        domainBuilder
                .tokenAllowance()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .spender(contractEntityId.getId())
                        .amount(10L)
                        .owner(spender.getId()))
                .persist();

        // When
        final var functionCall = contract.call_transferFrom(
                getAddressFromEntity(tokenEntity),
                getAliasFromEntity(spender),
                getAliasFromEntity(recipient),
                BigInteger.ONE);

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @ParameterizedTest
    @CsvSource({"1", "0"})
    void approve(final BigInteger allowance) throws Exception {
        // Given
        final var spender = accountEntityPersist();
        final var tokenEntity = persistFungibleToken();

        tokenAccountPersist(tokenEntity, spender);

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAccountPersist(tokenEntity, contractEntityId.getId());

        // When
        final var functionCall = contract.call_approveExternal(
                getAddressFromEntity(tokenEntity), getAddressFromEntity(spender), allowance);

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void approveNFT(final Boolean approve) throws Exception {
        // Given
        final var owner = accountEntityPersist();
        final var spender = accountEntityPersist();

        final var tokenEntity =
                domainBuilder.entity().customize(e -> e.type(EntityType.TOKEN)).persist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE))
                .persist();

        tokenAccountPersist(tokenEntity, owner);
        tokenAccountPersist(tokenEntity, spender);

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAccountPersist(tokenEntity, contractEntityId.getId());

        domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId()).serialNumber(1L).accountId(contractEntityId))
                .persist();

        // When
        final var functionCall = contract.call_approveNFTExternal(
                getAddressFromEntity(tokenEntity),
                approve ? getAddressFromEntity(spender) : Address.ZERO.toHexString(),
                BigInteger.ONE);

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void setApprovalForAll() throws Exception {
        // Given
        final var spender = accountEntityPersist();

        final var tokenEntity =
                domainBuilder.entity().customize(e -> e.type(EntityType.TOKEN)).persist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE))
                .persist();

        tokenAccountPersist(tokenEntity, spender);

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAccountPersist(tokenEntity, contractEntityId.getId());

        domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId()).serialNumber(1L).accountId(contractEntityId))
                .persist();

        // When
        final var functionCall = contract.call_setApprovalForAllExternal(
                getAddressFromEntity(tokenEntity), getAddressFromEntity(spender), Boolean.TRUE);

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void associateToken(final Boolean single) throws Exception {
        // Given
        final var notAssociatedAccount = accountEntityPersist();

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

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
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

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void dissociateToken(final Boolean single) throws Exception {
        // Given
        final var associatedAccount = accountEntityWithEvmAddressPersist();

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
                        .freezeStatus(TokenFreezeStatusEnum.UNFROZEN)
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .balance(0L)
                        .associated(true))
                .persist();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = single
                ? contract.call_dissociateTokenExternal(
                getAliasFromEntity(associatedAccount), getAddressFromEntity(tokenEntity))
                : contract.call_dissociateTokensExternal(
                        getAliasFromEntity(associatedAccount), List.of(getAddressFromEntity(tokenEntity)));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
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

        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAccountPersist(tokenEntity, contractEntityId.getId());

        // When
        final var functionCall = contract.call_dissociateWithRedirect(getAddressFromEntity(tokenEntity));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void mintFungibleToken() throws Exception {
        // Given
        final var treasury = accountEntityPersist();
        final var tokenEntity = tokenEntityPersist();
        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .treasuryAccountId(treasury.toEntityId()))
                .persist();
        tokenAccountPersist(tokenEntity, treasury);

        final var totalSupply = token.getTotalSupply();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_mintTokenExternal(
                getAddressFromEntity(tokenEntity), BigInteger.valueOf(30), new ArrayList<>());
        final var result = functionCall.send();

        // Then
        assertThat(result.component2()).isEqualTo(BigInteger.valueOf(totalSupply + 30L));
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void mintNFT() throws Exception {
        // Given
        final var treasury = accountEntityPersist();
        final var tokenEntity = tokenEntityPersist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(treasury.toEntityId()))
                .persist();
        tokenAccountPersist(tokenEntity, treasury);

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_mintTokenExternal(
                getAddressFromEntity(tokenEntity), BigInteger.ZERO, List.of(domainBuilder.nonZeroBytes(12)));

        final var result = functionCall.send();

        // Then
        assertThat(result.component3().getFirst()).isEqualTo(BigInteger.ONE);
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void burnFungibleToken() throws Exception {
        // Given
        final var treasury = accountEntityPersist();
        final var tokenEntity = tokenEntityPersist();
        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .treasuryAccountId(treasury.toEntityId()))
                .persist();
        tokenAccountPersist(tokenEntity, treasury);

        final var totalSupply = token.getTotalSupply();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_burnTokenExternal(
                getAddressFromEntity(tokenEntity), BigInteger.valueOf(4), new ArrayList<>());

        final var result = functionCall.send();

        // Then
        assertThat(result.component2()).isEqualTo(BigInteger.valueOf(totalSupply - 4L));
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void burnNFT() throws Exception {
        // Given
        final var treasury = accountEntityPersist();
        final var tokenEntity = tokenEntityPersist();
        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(treasury.toEntityId()))
                .persist();
        tokenAccountPersist(tokenEntity, treasury);
        final var totalSupply = token.getTotalSupply();

        domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId()).serialNumber(1L).accountId(treasury.toEntityId()))
                .persist();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_burnTokenExternal(
                getAddressFromEntity(tokenEntity), BigInteger.ZERO, List.of(BigInteger.ONE));

        final var result = functionCall.send();

        // Then
        assertThat(result.component2()).isEqualTo(BigInteger.valueOf(totalSupply - 1));
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void wipeFungibleToken() throws Exception {
        // Given
        final var owner = accountEntityWithEvmAddressPersist();

        final var tokenEntity = tokenEntityPersist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(TokenTypeEnum.FUNGIBLE_COMMON))
                .persist();

        tokenAccountPersist(tokenEntity, owner);

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_wipeTokenAccountExternal(
                getAddressFromEntity(tokenEntity), getAliasFromEntity(owner), BigInteger.valueOf(4));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void wipeNFT() throws Exception {
        // Given
        final var owner = accountEntityWithEvmAddressPersist();
        final var tokenTreasury = accountEntityPersist();
        final var tokenEntity = tokenEntityPersist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(tokenTreasury.toEntityId()))
                .persist();

        tokenAccountPersist(tokenEntity, owner);
        domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId()).serialNumber(1L).accountId(owner.toEntityId()))
                .persist();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_wipeTokenAccountNFTExternal(
                getAddressFromEntity(tokenEntity), getAliasFromEntity(owner), List.of(BigInteger.ONE));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void grantTokenKyc() throws Exception {
        // Given
        final var accountWithoutGrant = accountEntityWithEvmAddressPersist();

        final var tokenEntity = tokenEntityPersist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(TokenTypeEnum.FUNGIBLE_COMMON))
                .persist();

        tokenAccountPersist(tokenEntity, accountWithoutGrant);

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_grantTokenKycExternal(
                getAddressFromEntity(tokenEntity), getAliasFromEntity(accountWithoutGrant));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void revokeTokenKyc() throws Exception {
        // Given
        final var accountWithGrant = accountEntityWithEvmAddressPersist();

        final var tokenEntity = tokenEntityPersist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(TokenTypeEnum.FUNGIBLE_COMMON))
                .persist();

        tokenAccountPersist(tokenEntity, accountWithGrant);

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_revokeTokenKycExternal(
                getAddressFromEntity(tokenEntity), getAliasFromEntity(accountWithGrant));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void deleteToken() throws Exception {
        // Given
        final var treasury = accountEntityPersist();
        final var tokenEntity = tokenEntityPersist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .treasuryAccountId(treasury.toEntityId()))
                .persist();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_deleteTokenExternal(getAddressFromEntity(tokenEntity));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void freezeToken() throws Exception {
        // Given
        final var accountWithoutFreeze = accountEntityWithEvmAddressPersist();
        final var tokenEntity = persistFungibleToken();
        tokenAccountPersist(tokenEntity, accountWithoutFreeze);

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_freezeTokenExternal(
                getAddressFromEntity(tokenEntity), getAliasFromEntity(accountWithoutFreeze));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void unfreezeToken() throws Exception {
        // Given
        final var accountWithFreeze = accountEntityWithEvmAddressPersist();

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

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void pauseToken() throws Exception {
        // Given
        final var treasury = accountEntityPersist();
        final var tokenEntity = tokenEntityPersist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .treasuryAccountId(treasury.toEntityId()))
                .persist();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_pauseTokenExternal(getAddressFromEntity(tokenEntity));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void unpauseToken() throws Exception {
        // Given
        final var sender = accountEntityPersist();

        final var tokenEntity = tokenEntityPersist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .treasuryAccountId(sender.toEntityId())
                        .pauseStatus(TokenPauseStatusEnum.PAUSED))
                .persist();

        tokenAccountPersist(tokenEntity, sender);

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_unpauseTokenExternal(getAddressFromEntity(tokenEntity));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void createFungibleToken() throws Exception {
        // Given
        final var value = 10000L * 100_000_000_000L;
        final var sender = accountEntityPersist();

        accountBalanceRecordsPersist(sender);

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        testWeb3jService.setValue(value);

        testWeb3jService.setSender(toAddress(sender.toEntityId()).toHexString());

        final var treasuryAccount = accountEntityPersist();

        final var token = populateHederaToken(
                contract.getContractAddress(), TokenTypeEnum.FUNGIBLE_COMMON, treasuryAccount.toEntityId());
        final var initialTokenSupply = BigInteger.valueOf(10L);
        final var decimalPlacesSupportedByToken = BigInteger.valueOf(10L); // e.g. 1.0123456789

        // When
        final var functionCall =
                contract.call_createFungibleTokenExternal(token, initialTokenSupply, decimalPlacesSupportedByToken);
        final var result = functionCall.send();

        final var contractFunctionProvider = ContractFunctionProviderRecord.builder()
                .contractAddress(Address.fromHexString(contract.getContractAddress()))
                .sender(toAddress(sender.toEntityId()))
                .value(value)
                .build();

        // Then
        assertThat(result.component2()).isNotEqualTo(Address.ZERO.toHexString());

        verifyEthCallAndEstimateGas(functionCall, contract, value);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contractFunctionProvider);
    }

    @Test
    void createFungibleTokenWithCustomFees() throws Exception {
        // Given
        var initialSupply = BigInteger.valueOf(10L);
        var decimals = BigInteger.valueOf(10L);
        var value = 10000L * 100_000_000L;

        final var sender = accountEntityPersist();

        accountBalanceRecordsPersist(sender);

        final var treasuryAccount = accountEntityPersist();

        final var tokenForDenomination = persistFungibleToken();
        final var feeCollector = accountEntityWithEvmAddressPersist();

        tokenAccountPersist(tokenForDenomination, feeCollector);

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        testWeb3jService.setSender(toAddress(sender.toEntityId()).toHexString());
        testWeb3jService.setValue(value);

        final var token = populateHederaToken(
                contract.getContractAddress(), TokenTypeEnum.FUNGIBLE_COMMON, treasuryAccount.toEntityId());

        final var fixedFee = new FixedFee(
                BigInteger.valueOf(100L),
                getAddressFromEntityId(tokenForDenomination.toEntityId()),
                false,
                false,
                getAliasFromEntity(feeCollector));
        final var fractionalFee = new FractionalFee(
                BigInteger.valueOf(1L),
                BigInteger.valueOf(100L),
                BigInteger.valueOf(10L),
                BigInteger.valueOf(1000L),
                false,
                getAliasFromEntity(feeCollector));

        // When
        final var functionCall = contract.call_createFungibleTokenWithCustomFeesExternal(
                token, initialSupply, decimals, List.of(fixedFee), List.of(fractionalFee));
        final var result = functionCall.send();

        final var contractFunctionProvider = ContractFunctionProviderRecord.builder()
                .contractAddress(Address.fromHexString(contract.getContractAddress()))
                .sender(toAddress(sender.toEntityId()))
                .value(value)
                .build();

        // Then
        assertThat(result.component2()).isNotEqualTo(Address.ZERO.toHexString());

        verifyEthCallAndEstimateGas(functionCall, contract, value);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contractFunctionProvider);
    }

    @Test
    void createNonFungibleToken() throws Exception {
        // Given
        var value = 10000L * 100_000_000L;
        final var sender = accountEntityPersist();

        accountBalanceRecordsPersist(sender);

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        testWeb3jService.setSender(toAddress(sender.toEntityId()).toHexString());

        final var treasuryAccount = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.ACCOUNT).deleted(false).evmAddress(null))
                .persist();
        final var token = populateHederaToken(
                contract.getContractAddress(), TokenTypeEnum.NON_FUNGIBLE_UNIQUE, treasuryAccount.toEntityId());

        // When
        testWeb3jService.setValue(value);
        final var functionCall = contract.call_createNonFungibleTokenExternal(token);
        final var result = functionCall.send();

        final var contractFunctionProvider = ContractFunctionProviderRecord.builder()
                .contractAddress(Address.fromHexString(contract.getContractAddress()))
                .sender(toAddress(sender.toEntityId()))
                .value(value)
                .build();

        // Then
        assertThat(result.component2()).isNotEqualTo(Address.ZERO.toHexString());

        verifyEthCallAndEstimateGas(functionCall, contract, value);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contractFunctionProvider);
    }

    @Test
    void createNonFungibleTokenWithCustomFees() throws Exception {
        // Given
        var value = 10000L * 100_000_000L;
        final var sender = accountEntityPersist();

        accountBalanceRecordsPersist(sender);

        final var tokenForDenomination = persistFungibleToken();
        final var feeCollector = accountEntityWithEvmAddressPersist();

        tokenAccountPersist(tokenForDenomination, feeCollector);

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        testWeb3jService.setSender(toAddress(sender.toEntityId()).toHexString());
        testWeb3jService.setValue(value);

        final var treasuryAccount = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.ACCOUNT).deleted(false).evmAddress(null))
                .persist();
        final var token = populateHederaToken(
                contract.getContractAddress(), TokenTypeEnum.NON_FUNGIBLE_UNIQUE, treasuryAccount.toEntityId());

        final var fixedFee = new FixedFee(
                BigInteger.valueOf(100L),
                getAddressFromEntityId(tokenForDenomination.toEntityId()),
                false,
                false,
                getAliasFromEntity(feeCollector));
        final var royaltyFee = new RoyaltyFee(
                BigInteger.valueOf(1L),
                BigInteger.valueOf(100L),
                BigInteger.valueOf(10L),
                getAddressFromEntity(tokenForDenomination),
                false,
                getAliasFromEntity(feeCollector));

        // When
        testWeb3jService.setValue(value);
        final var functionCall = contract.call_createNonFungibleTokenWithCustomFeesExternal(
                token, List.of(fixedFee), List.of(royaltyFee));
        final var result = functionCall.send();

        final var contractFunctionProvider = ContractFunctionProviderRecord.builder()
                .contractAddress(Address.fromHexString(contract.getContractAddress()))
                .sender(toAddress(sender.toEntityId()))
                .value(value)
                .build();
        // Then
        assertThat(result.component2()).isNotEqualTo(Address.ZERO.toHexString());

        verifyEthCallAndEstimateGas(functionCall, contract, value);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contractFunctionProvider);
    }

    @Test
    void create2ContractAndTransferFromIt() throws Exception {
        // Given
        final var sponsor = accountEntityWithEvmAddressPersist();
        final var receiver = accountEntityWithEvmAddressPersist();
        final var token = persistFungibleToken();
        tokenAccountPersist(token, sponsor);

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_createContractViaCreate2AndTransferFromIt(
                getAddressFromEntity(token),
                getAliasFromEntity(sponsor),
                getAliasFromEntity(receiver),
                BigInteger.valueOf(10L));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, 0L);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void notExistingPrecompileCallFails() {
        // Given
        final var token = persistFungibleToken();
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);
        // When
        final var functionCall = contract.send_callNotExistingPrecompile(getAddressFromEntity(token));
        // Then
        assertThatThrownBy(functionCall::send)
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage(INVALID_TOKEN_ID.name());
    }

    @Test
    void createFungibleTokenWithInheritKeysCall() throws Exception {
        // Given
        final var value = 10000 * 100_000_000L;
        final var sender = accountEntityWithEvmAddressPersist();
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        testWeb3jService.setValue(value);
        final var functionCall = contract.call_createFungibleTokenWithInheritKeysExternal();

        // Then
        testWeb3jService.setSender(getAliasFromEntity(sender));

        functionCall.send();
        final var result = testWeb3jService.getTransactionResult();
        assertThat(result).isNotEqualTo(EMPTY_UNTRIMMED_ADDRESS);

        verifyEthCallAndEstimateGas(functionCall, contract, value);
    }

    @Test
    void createTokenWithInvalidMemoFails() {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var tokenToCreate = convertTokenEntityToHederaToken(domainBuilder
                .token()
                .customize(t -> t.metadata(new byte[mirrorNodeEvmProperties.getMaxMemoUtf8Bytes() + 1]))
                .get());

        // When
        testWeb3jService.setValue(10000L * 100_000_000L);
        final var function = contract.call_createFungibleTokenExternal(
                tokenToCreate, BigInteger.valueOf(10L), BigInteger.valueOf(10L));

        // Then
        assertThatThrownBy(function::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void createTokenWithInvalidNameFails() {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var tokenToCreate = convertTokenEntityToHederaToken(domainBuilder
                .token()
                .customize(t -> t.name(new String(
                        new byte[mirrorNodeEvmProperties.getMaxTokenNameUtf8Bytes() + 1], StandardCharsets.UTF_8)))
                .get());

        // When
        testWeb3jService.setValue(10000L * 100_000_000L);
        final var function = contract.call_createFungibleTokenExternal(
                tokenToCreate, BigInteger.valueOf(10L), BigInteger.valueOf(10L));

        // Then
        assertThatThrownBy(function::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void createTokenWithInvalidSymbolFails() {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var tokenToCreate = convertTokenEntityToHederaToken(domainBuilder
                .token()
                .customize(t -> t.symbol(new String(
                        new byte[mirrorNodeEvmProperties.getMaxTokenNameUtf8Bytes() + 1], StandardCharsets.UTF_8)))
                .get());

        // When
        testWeb3jService.setValue(10000L * 100_000_000L);
        final var function = contract.call_createFungibleTokenExternal(
                tokenToCreate, BigInteger.valueOf(10L), BigInteger.valueOf(10L));

        // Then
        assertThatThrownBy(function::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void createTokenWithInvalidDecimalsFails() {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var tokenToCreate = convertTokenEntityToHederaToken(
                domainBuilder.token().customize(t -> t.decimals(-1)).get());

        // When
        testWeb3jService.setValue(10000L * 100_000_000L);
        final var function = contract.call_createFungibleTokenExternal(
                tokenToCreate, BigInteger.valueOf(10L), BigInteger.valueOf(10L));

        // Then
        assertThatThrownBy(function::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void createTokenWithInvalidInitialSupplyFails() {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var tokenToCreate = convertTokenEntityToHederaToken(
                domainBuilder.token().customize(t -> t.initialSupply(-1L)).get());

        // When
        testWeb3jService.setValue(10000L * 100_000_000L);
        final var function = contract.call_createFungibleTokenExternal(
                tokenToCreate, BigInteger.valueOf(10L), BigInteger.valueOf(10L));

        // Then
        assertThatThrownBy(function::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void createTokenWithInvalidInitialSupplyGreaterThanMaxSupplyFails() {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var tokenToCreate = convertTokenEntityToHederaToken(domainBuilder
                .token()
                .customize(t -> t.initialSupply(10_000_001L))
                .get());

        // When
        testWeb3jService.setValue(10000L * 100_000_000L);
        final var function = contract.call_createFungibleTokenExternal(
                tokenToCreate, BigInteger.valueOf(10L), BigInteger.valueOf(10L));

        // Then
        assertThatThrownBy(function::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void createNftWithNoSupplyKeyFails() {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var tokenToCreate = convertTokenEntityToHederaToken(
                domainBuilder.token().customize(t -> t.supplyKey(new byte[0])).get());

        // When
        testWeb3jService.setValue(10000L * 100_000_000L);
        final var function = contract.call_createNonFungibleTokenExternal(tokenToCreate);

        // Then
        assertThatThrownBy(function::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void createNftWithNoTreasuryFails() {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var tokenToCreate = convertTokenEntityToHederaToken(
                domainBuilder.token().customize(t -> t.treasuryAccountId(null)).get());

        // When
        testWeb3jService.setValue(10000L * 100_000_000L);
        final var function = contract.call_createNonFungibleTokenExternal(tokenToCreate);

        // Then
        assertThatThrownBy(function::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void createNftWithDefaultFreezeWithNoKeyFails() {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var tokenToCreate = convertTokenEntityToHederaToken(domainBuilder
                .token()
                .customize(t -> t.freezeDefault(true).freezeKey(new byte[0]))
                .get());

        // When
        testWeb3jService.setValue(10000L * 100_000_000L);
        final var function = contract.call_createNonFungibleTokenExternal(tokenToCreate);

        // Then
        assertThatThrownBy(function::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void createNftWithInvalidAutoRenewAccountFails() {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var tokenToCreate = convertTokenEntityToHederaToken(
                domainBuilder.token().get(),
                domainBuilder.entity().customize(e -> e.autoRenewPeriod(10L)).get());

        // When
        testWeb3jService.setValue(10000L * 100_000_000L);
        final var function = contract.call_createNonFungibleTokenExternal(tokenToCreate);

        // Then
        assertThatThrownBy(function::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @ParameterizedTest
    @CsvSource({"single", "multiple"})
    void transferToken(final String type) throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);
        final var tokenEntity = persistFungibleToken();
        final var sender = accountEntityWithEvmAddressPersist();
        final var receiver = accountEntityWithEvmAddressPersist();
        final var payer = accountEntityWithEvmAddressPersist();

        tokenAccountPersist(tokenEntity, payer);
        tokenAccountPersist(tokenEntity, sender);
        tokenAccountPersist(tokenEntity, receiver);

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

        final var contractFunctionProvider = ContractFunctionProviderRecord.builder()
                .contractAddress(Address.fromHexString(contract.getContractAddress()))
                .sender(Address.fromHexString(getAliasFromEntity(payer)))
                .build();

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contractFunctionProvider);
    }

    @ParameterizedTest
    @CsvSource({"single", "multiple"})
    void transferNft(final String type) throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);
        final var sender = accountEntityWithEvmAddressPersist();
        final var tokenEntity = tokenEntityPersist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE))
                .persist();
        domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId()).serialNumber(1L).accountId(sender.toEntityId()))
                .persist();
        final var receiver = accountEntityWithEvmAddressPersist();
        final var payer = accountEntityWithEvmAddressPersist();

        tokenAccountPersist(tokenEntity, payer);
        tokenAccountPersist(tokenEntity, sender);
        tokenAccountPersist(tokenEntity, receiver);

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

        final var contractFunctionProvider = ContractFunctionProviderRecord.builder()
                .contractAddress(Address.fromHexString(contract.getContractAddress()))
                .sender(Address.fromHexString(getAliasFromEntity(payer)))
                .build();

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contractFunctionProvider);
    }

    @Test
    void transferFromNft() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);
        final var sender = accountEntityWithEvmAddressPersist();
        final var tokenEntity = tokenEntityPersist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE))
                .persist();
        domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId()).serialNumber(1L).accountId(sender.toEntityId()))
                .persist();
        final var receiver = accountEntityWithEvmAddressPersist();
        final var payer = accountEntityWithEvmAddressPersist();

        tokenAccountPersist(tokenEntity, payer);
        tokenAccountPersist(tokenEntity, sender);
        tokenAccountPersist(tokenEntity, receiver);

        // When
        testWeb3jService.setSender(getAliasFromEntity(payer));
        final var functionCall = contract.call_transferFromNFTExternal(
                getAddressFromEntity(tokenEntity),
                getAliasFromEntity(sender),
                getAliasFromEntity(receiver),
                BigInteger.ONE);

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        final var callData = functionCall.encodeFunctionCall();
        final var contractFunctionProvider = ContractFunctionProviderRecord.builder()
                .contractAddress(Address.fromHexString(contract.getContractAddress()))
                .sender(Address.fromHexString(getAliasFromEntity(payer)))
                .build();
        verifyOpcodeTracerCall(callData, contractFunctionProvider);
    }

    @Test
    void cryptoTransferHbars() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);
        final var sender = accountEntityWithEvmAddressPersist();
        final var receiver = accountEntityWithEvmAddressPersist();
        final var payer = accountEntityWithEvmAddressPersist();

        long timestampForBalances = payer.getCreatedTimestamp();
        persistAccountBalance(payer, payer.getBalance());
        persistAccountBalance(sender, sender.getBalance(), timestampForBalances);
        persistAccountBalance(treasuryEntity, treasuryEntity.getBalance(), timestampForBalances);

        // When
        testWeb3jService.setSender(getAliasFromEntity(payer));
        final var transferList = new TransferList(List.of(
                new AccountAmount(getAliasFromEntity(sender), BigInteger.valueOf(-5L), false),
                new AccountAmount(getAliasFromEntity(receiver), BigInteger.valueOf(5L), false)));

        final var functionCall = contract.call_cryptoTransferExternal(transferList, new ArrayList<>());

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(
                functionCall.encodeFunctionCall(),
                getContractFunctionProviderWithSender(contract.getContractAddress(), payer));
    }

    @Test
    void cryptoTransferToken() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);
        final var sender = accountEntityWithEvmAddressPersist();
        final var tokenEntity = persistFungibleToken();
        final var receiver = accountEntityWithEvmAddressPersist();
        final var payer = accountEntityWithEvmAddressPersist();

        long timestampForBalances = payer.getCreatedTimestamp();
        persistAccountBalance(payer, payer.getBalance());
        persistTokenBalance(payer, tokenEntity, timestampForBalances);

        persistAccountBalance(sender, sender.getBalance(), timestampForBalances);
        persistTokenBalance(sender, tokenEntity, timestampForBalances);

        persistTokenBalance(receiver, tokenEntity, timestampForBalances);
        persistAccountBalance(treasuryEntity, treasuryEntity.getBalance(), timestampForBalances);

        tokenAccountPersist(tokenEntity, sender);
        tokenAccountPersist(tokenEntity, receiver);
        tokenAccountPersist(tokenEntity, payer);
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

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(
                functionCall.encodeFunctionCall(),
                getContractFunctionProviderWithSender(contract.getContractAddress(), payer));
    }

    @Test
    void cryptoTransferHbarsAndToken() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);
        final var sender = accountEntityWithEvmAddressPersist();
        final var tokenEntity = persistFungibleToken();
        final var receiver = accountEntityWithEvmAddressPersist();
        final var payer = accountEntityWithEvmAddressPersist();

        long timestampForBalances = payer.getCreatedTimestamp();
        persistAccountBalance(payer, payer.getBalance());
        persistTokenBalance(payer, tokenEntity, timestampForBalances);

        persistAccountBalance(sender, sender.getBalance(), timestampForBalances);
        persistTokenBalance(sender, tokenEntity, timestampForBalances);

        persistTokenBalance(receiver, tokenEntity, timestampForBalances);

        persistAccountBalance(treasuryEntity, treasuryEntity.getBalance(), timestampForBalances);

        tokenAccountPersist(tokenEntity, sender);
        tokenAccountPersist(tokenEntity, receiver);
        tokenAccountPersist(tokenEntity, payer);

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

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(
                functionCall.encodeFunctionCall(),
                getContractFunctionProviderWithSender(contract.getContractAddress(), payer));
    }

    @Test
    void cryptoTransferNft() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);
        final var sender = accountEntityWithEvmAddressPersist();
        final var tokenTreasury = accountEntityPersist();
        final var receiver = accountEntityWithEvmAddressPersist();
        final var payer = accountEntityWithEvmAddressPersist();
        final var tokenEntity = tokenEntityPersist();
        accountBalanceRecordsPersist(payer);
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(tokenTreasury.toEntityId()))
                .persist();
        domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId()).serialNumber(1L).accountId(sender.toEntityId()))
                .persist();

        tokenAccountPersist(tokenEntity, payer);
        tokenAccountPersist(tokenEntity, sender);
        tokenAccountPersist(tokenEntity, receiver);

        // When
        testWeb3jService.setSender(getAliasFromEntity(payer));
        final var tokenTransferList = new TokenTransferList(
                getAddressFromEntity(tokenEntity),
                new ArrayList<>(),
                List.of(new NftTransfer(
                        getAliasFromEntity(sender), getAliasFromEntity(receiver), BigInteger.ONE, false)));

        final var functionCall =
                contract.call_cryptoTransferExternal(new TransferList(new ArrayList<>()), List.of(tokenTransferList));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(
                functionCall.encodeFunctionCall(),
                getContractFunctionProviderWithSender(contract.getContractAddress(), payer));
    }

    @Test
    void updateTokenInfo() throws Exception {
        // Given
        final var treasuryAccount = accountEntityPersist();
        final var tokenWithAutoRenewPair =
                persistTokenWithAutoRenewAndTreasuryAccounts(TokenTypeEnum.FUNGIBLE_COMMON, treasuryAccount, false);

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var token = populateHederaToken(
                contract.getContractAddress(), TokenTypeEnum.FUNGIBLE_COMMON, treasuryAccount.toEntityId());

        // When
        final var functionCall =
                contract.call_updateTokenInfoExternal(getAddressFromEntity(tokenWithAutoRenewPair.getLeft()), token);

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void updateTokenExpiry() throws Exception {
        // Given
        final var treasuryAccount = accountEntityPersist();
        final var tokenWithAutoRenewPair =
                persistTokenWithAutoRenewAndTreasuryAccounts(TokenTypeEnum.FUNGIBLE_COMMON, treasuryAccount, false);

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var tokenExpiry = new Expiry(
                BigInteger.valueOf(Instant.now().getEpochSecond() + 8_000_000L),
                toAddress(tokenWithAutoRenewPair.getRight().toEntityId()).toHexString(),
                BigInteger.valueOf(8_000_000));

        // When
        final var functionCall = contract.call_updateTokenExpiryInfoExternal(
                getAddressFromEntity(tokenWithAutoRenewPair.getLeft()), tokenExpiry);

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                            FUNGIBLE_COMMON, ED25519
                            FUNGIBLE_COMMON, ECDSA_SECPK256K1
                            FUNGIBLE_COMMON, CONTRACT_ID
                            FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID
                            NON_FUNGIBLE_UNIQUE, ED25519
                            NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1
                            NON_FUNGIBLE_UNIQUE, CONTRACT_ID
                            NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID
                            """)
    void updateTokenKey(final TokenTypeEnum tokenTypeEnum, final KeyValueType keyValueType) throws Exception {
        // Given
        final var allCasesKeyType = 0b1111111;
        final var treasuryAccount = accountEntityPersist();
        final var tokenWithAutoRenewPair = persistTokenWithAutoRenewAndTreasuryAccounts(tokenTypeEnum, treasuryAccount,
                tokenTypeEnum == TokenTypeEnum.NON_FUNGIBLE_UNIQUE);

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var tokenKeys = new ArrayList<TokenKey>();
        tokenKeys.add(new TokenKey(
                BigInteger.valueOf(allCasesKeyType), getKeyValueForType(keyValueType, contract.getContractAddress())));

        // When
        final var functionCall = contract.call_updateTokenKeysExternal(
                getAddressFromEntity(tokenWithAutoRenewPair.getLeft()), tokenKeys);

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    private void verifyEthCallAndEstimateGas(
            final RemoteFunctionCall<?> functionCall, final Contract contract, final Long value) throws Exception {
        // Given
        testWeb3jService.setEstimateGas(true);
        functionCall.send();

        final var estimateGasUsedResult = longValueOf.applyAsLong(testWeb3jService.getEstimatedGas());

        // When
        final var actualGasUsed = gasUsedAfterExecution(getContractExecutionParameters(functionCall, contract, value));

        // Then
        assertThat(isWithinExpectedGasRange(estimateGasUsedResult, actualGasUsed))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimateGasUsedResult, actualGasUsed)
                .isTrue();
        testWeb3jService.setEstimateGas(false);
    }

    private HederaToken populateHederaToken(
            final String contractAddress, final TokenTypeEnum tokenType, final EntityId treasuryAccountId) {
        final var autoRenewAccount =
                accountEntityWithEvmAddressPersist(); // the account that is going to be charged for token renewal upon
        // expiration
        final var tokenEntity = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.TOKEN).autoRenewAccountId(autoRenewAccount.getId()))
                .persist();
        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(tokenType).treasuryAccountId(treasuryAccountId))
                .persist();

        final var supplyKey = new KeyValue(
                Boolean.FALSE,
                contractAddress,
                new byte[0],
                new byte[0],
                Address.ZERO.toHexString()); // the key needed for token minting or burning
        final var keys = new ArrayList<TokenKey>();
        keys.add(new TokenKey(AbstractContractCallServiceTest.KeyType.SUPPLY_KEY.getKeyTypeNumeric(), supplyKey));

        return new HederaToken(
                token.getName(),
                token.getSymbol(),
                getAddressFromEntityId(treasuryAccountId), // id of the account holding the initial token supply
                tokenEntity.getMemo(), // token description encoded in UTF-8 format
                true,
                BigInteger.valueOf(10_000L),
                false,
                keys,
                new Expiry(
                        BigInteger.valueOf(Instant.now().getEpochSecond() + 8_000_000L),
                        getAliasFromEntity(autoRenewAccount),
                        BigInteger.valueOf(8_000_000)));
    }

    private KeyValue getKeyValueForType(final KeyValueType keyValueType, String contractAddress) {
        final var ed25519Key =
                Bytes.wrap(domainBuilder.key(KeyCase.ED25519)).slice(2).toArray();

        return switch (keyValueType) {
            case CONTRACT_ID -> new KeyValue(
                    Boolean.FALSE, contractAddress, new byte[0], new byte[0], Address.ZERO.toHexString());
            case ED25519 -> new KeyValue(
                    Boolean.FALSE, Address.ZERO.toHexString(), ed25519Key, new byte[0], Address.ZERO.toHexString());
            case ECDSA_SECPK256K1 -> new KeyValue(
                    Boolean.FALSE, Address.ZERO.toHexString(), new byte[0], NEW_ECDSA_KEY, Address.ZERO.toHexString());
            case DELEGATABLE_CONTRACT_ID -> new KeyValue(
                    Boolean.FALSE, Address.ZERO.toHexString(), new byte[0], new byte[0], contractAddress);
            default -> throw new RuntimeException("Unsupported key type: " + keyValueType.name());
        };
    }

    private Entity persistFungibleToken() {
        final var tokenEntity = tokenEntityPersist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(TokenTypeEnum.FUNGIBLE_COMMON))
                .persist();

        return tokenEntity;
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
                        EntityIdUtils.asHexedEvmAddress(new Id(0, 0, entityRenewAccountId)),
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
                        EntityIdUtils.asHexedEvmAddress(new Id(0, 0, entityRenewAccountId)),
                        BigInteger.valueOf(entity.getEffectiveExpiration())));
    }
}
