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

import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.entityIdFromEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.exception.BlockNumberNotFoundException.UNKNOWN_BLOCK_NUMBER;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.EVM_V_34_BLOCK;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.SENDER_ALIAS;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.SENDER_PUBLIC_KEY;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.SPENDER_ALIAS;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.SPENDER_PUBLIC_KEY;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.balance.TokenBalance;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.mirror.web3.web3j.generated.ERCTestContractHistorical;
import java.math.BigInteger;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ContractCallServiceERCTokenHistoricalTest extends AbstractContractCallServiceTest {
    private Range<Long> historicalRangeAfterEvm34;

    @Nested
    class beforeEvm34Tests {
        @BeforeEach
        void beforeEach() {
            historicalRangeAfterEvm34 = setUpHistoricalContextBeforeEvm34();
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void getApprovedEmptySpenderBeforeEvmV34(final boolean isStatic) {
            // Given
            final var tokenEntity = persistTokenEntityHistorical(historicalRangeAfterEvm34);
            domainBuilder
                    .token()
                    .customize(t -> t.tokenId(tokenEntity.getId())
                            .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                            .timestampRange(historicalRangeAfterEvm34))
                    .persist();
            domainBuilder
                    .nft()
                    .customize(n ->
                            n.tokenId(tokenEntity.getId()).serialNumber(1L).timestampRange(historicalRangeAfterEvm34))
                    .persist();
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_getApproved(getAddressFromEntity(tokenEntity), BigInteger.valueOf(1L))
                    : contract.call_getApprovedNonStatic(getAddressFromEntity(tokenEntity), BigInteger.valueOf(1L));
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void getApprovedBeforeEvmV34(final boolean isStatic) throws Exception {
            // Given
            final var owner = persistAccountEntityHistorical(historicalRangeAfterEvm34);
            final var spender = persistAccountEntityHistorical(historicalRangeAfterEvm34);
            final var nftToken = persistNftHistorical(
                    historicalRangeAfterEvm34, owner.toEntityId(), owner.toEntityId(), spender.toEntityId());
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_getApproved(getAddressFromEntity(nftToken), BigInteger.valueOf(1L))
                    : contract.call_getApprovedNonStatic(getAddressFromEntity(nftToken), BigInteger.valueOf(1L));
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void isApproveForAllBeforeEvmV34(final boolean isStatic) {
            // Given
            final var owner = persistAccountEntityHistorical(historicalRangeAfterEvm34);
            final var spender = persistAccountEntityHistorical(historicalRangeAfterEvm34);
            final var nftToken = persistNftHistorical(
                    historicalRangeAfterEvm34, owner.toEntityId(), owner.toEntityId(), spender.toEntityId());
            domainBuilder
                    .nftAllowance()
                    .customize(a -> a.tokenId(nftToken.getId())
                            .owner(owner.getNum())
                            .spender(spender.getNum())
                            .timestampRange(historicalRangeAfterEvm34)
                            .approvedForAll(true))
                    .persist();
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_isApprovedForAll(
                            getAddressFromEntity(nftToken), getAddressFromEntity(owner), getAddressFromEntity(spender))
                    : contract.call_isApprovedForAllNonStatic(
                            getAddressFromEntity(nftToken), getAddressFromEntity(owner), getAddressFromEntity(spender));
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void isApproveForAllBeforeEvmV34WithAlias(final boolean isStatic) {
            // Given
            final var owner =
                    persistAccountEntityWithAliasHistorical(historicalRangeAfterEvm34, SENDER_ALIAS, SENDER_PUBLIC_KEY);
            final var spender = persistAccountEntityWithAliasHistorical(
                    historicalRangeAfterEvm34, SPENDER_ALIAS, SPENDER_PUBLIC_KEY);
            final var nftToken = persistNftHistorical(
                    historicalRangeAfterEvm34, owner.toEntityId(), owner.toEntityId(), spender.toEntityId());
            domainBuilder
                    .nftAllowance()
                    .customize(a -> a.tokenId(nftToken.getId())
                            .owner(owner.getNum())
                            .spender(spender.getNum())
                            .timestampRange(historicalRangeAfterEvm34)
                            .approvedForAll(true))
                    .persist();
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_isApprovedForAll(
                            getAddressFromEntity(nftToken), SENDER_ALIAS.toHexString(), SPENDER_ALIAS.toHexString())
                    : contract.call_isApprovedForAllNonStatic(
                            getAddressFromEntity(nftToken), SENDER_ALIAS.toHexString(), SPENDER_ALIAS.toHexString());
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void allowanceBeforeEvmV34(final boolean isStatic) {
            // Given
            final var owner = persistAccountEntityHistorical(historicalRangeAfterEvm34);
            final var spender = persistAccountEntityHistorical(historicalRangeAfterEvm34);
            final var token = persistFungibleTokenHistorical(historicalRangeAfterEvm34);
            final var amountGranted = 10L;

            domainBuilder
                    .tokenAllowance()
                    .customize(a -> a.tokenId(token.getId())
                            .owner(owner.getNum())
                            .spender(spender.getNum())
                            .amount(amountGranted)
                            .amountGranted(amountGranted)
                            .timestampRange(historicalRangeAfterEvm34))
                    .persist();
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_allowance(
                            getAddressFromEntity(token), getAddressFromEntity(owner), getAddressFromEntity(spender))
                    : contract.call_allowanceNonStatic(
                            getAddressFromEntity(token), getAddressFromEntity(owner), getAddressFromEntity(spender));
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void allowanceBeforeEvmV34WithAlias(final boolean isStatic) {
            // Given
            final var owner =
                    persistAccountEntityWithAliasHistorical(historicalRangeAfterEvm34, SENDER_ALIAS, SENDER_PUBLIC_KEY);
            final var spender = persistAccountEntityWithAliasHistorical(
                    historicalRangeAfterEvm34, SPENDER_ALIAS, SPENDER_PUBLIC_KEY);
            final var token = persistFungibleTokenHistorical(historicalRangeAfterEvm34);
            final var amountGranted = 10L;

            domainBuilder
                    .tokenAllowance()
                    .customize(a -> a.tokenId(token.getId())
                            .owner(owner.getNum())
                            .spender(spender.getNum())
                            .amount(amountGranted)
                            .amountGranted(amountGranted)
                            .timestampRange(historicalRangeAfterEvm34))
                    .persist();
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_allowance(
                            getAddressFromEntity(token), SENDER_ALIAS.toHexString(), SPENDER_ALIAS.toHexString())
                    : contract.call_allowanceNonStatic(
                            getAddressFromEntity(token), SENDER_ALIAS.toHexString(), SPENDER_ALIAS.toHexString());
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void decimalsBeforeEvmV34(final boolean isStatic) {
            // Given
            final var decimals = 12;
            final var tokenEntity = persistTokenEntityHistorical(historicalRangeAfterEvm34);
            domainBuilder
                    .token()
                    .customize(t -> t.tokenId(tokenEntity.getId())
                            .type(TokenTypeEnum.FUNGIBLE_COMMON)
                            .decimals(decimals)
                            .timestampRange(historicalRangeAfterEvm34)
                            .createdTimestamp(historicalRangeAfterEvm34.lowerEndpoint()))
                    .persist();
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_decimals(getAddressFromEntity(tokenEntity))
                    : contract.call_decimalsNonStatic(getAddressFromEntity(tokenEntity));
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void totalSupplyBeforeEvmV34(final boolean isStatic) {
            // Given
            final var totalSupply = 12345L;
            final var spender = persistAccountEntityHistorical(historicalRangeAfterEvm34);
            final var tokenEntity = persistTokenEntityHistorical(historicalRangeAfterEvm34);
            domainBuilder
                    .token()
                    .customize(t -> t.tokenId(tokenEntity.getId())
                            .type(TokenTypeEnum.FUNGIBLE_COMMON)
                            .totalSupply(totalSupply)
                            .timestampRange(historicalRangeAfterEvm34)
                            .createdTimestamp(historicalRangeAfterEvm34.lowerEndpoint()))
                    .persist();
            balancePersistHistorical(
                    toAddress(tokenEntity.getId()), historicalRangeAfterEvm34, toAddress(spender.getId()), 12L);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_totalSupply(getAddressFromEntity(tokenEntity))
                    : contract.call_totalSupplyNonStatic(getAddressFromEntity(tokenEntity));
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void symbolBeforeEvmV34(final boolean isStatic) {
            // Given
            final var symbol = "HBAR";
            final var tokenEntity = persistTokenEntityHistorical(historicalRangeAfterEvm34);
            domainBuilder
                    .token()
                    .customize(t -> t.tokenId(tokenEntity.getId())
                            .type(TokenTypeEnum.FUNGIBLE_COMMON)
                            .symbol(symbol)
                            .timestampRange(historicalRangeAfterEvm34)
                            .createdTimestamp(historicalRangeAfterEvm34.lowerEndpoint()))
                    .persist();
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_symbol(getAddressFromEntity(tokenEntity))
                    : contract.call_symbolNonStatic(getAddressFromEntity(tokenEntity));
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void balanceOfBeforeEvmV34(final boolean isStatic) {
            // Given
            final var owner = persistAccountEntityHistorical(historicalRangeAfterEvm34);
            final var token = persistFungibleTokenHistorical(historicalRangeAfterEvm34);
            domainBuilder
                    .tokenAccountHistory()
                    .customize(e -> e.freezeStatus(TokenFreezeStatusEnum.FROZEN)
                            .accountId(owner.getId())
                            .tokenId(token.getId())
                            .kycStatus(TokenKycStatusEnum.GRANTED)
                            .associated(true)
                            .timestampRange(historicalRangeAfterEvm34))
                    .persist();
            final var balance = 10L;
            balancePersistHistorical(
                    toAddress(token.getId()), historicalRangeAfterEvm34, toAddress(owner.getId()), balance);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_balanceOf(getAddressFromEntity(token), getAddressFromEntity(owner))
                    : contract.call_balanceOfNonStatic(getAddressFromEntity(token), getAddressFromEntity(owner));
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void balanceOfBeforeEvmV34WithAlias(final boolean isStatic) {
            // Given
            final var owner =
                    persistAccountEntityWithAliasHistorical(historicalRangeAfterEvm34, SENDER_ALIAS, SENDER_PUBLIC_KEY);
            final var token = persistFungibleTokenHistorical(historicalRangeAfterEvm34);
            domainBuilder
                    .tokenAccountHistory()
                    .customize(e -> e.freezeStatus(TokenFreezeStatusEnum.FROZEN)
                            .accountId(owner.getId())
                            .tokenId(token.getId())
                            .kycStatus(TokenKycStatusEnum.GRANTED)
                            .associated(true)
                            .timestampRange(historicalRangeAfterEvm34))
                    .persist();
            final var balance = 10L;
            balancePersistHistorical(
                    toAddress(token.getId()), historicalRangeAfterEvm34, toAddress(owner.getId()), balance);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_balanceOf(getAddressFromEntity(token), SENDER_ALIAS.toHexString())
                    : contract.call_balanceOfNonStatic(getAddressFromEntity(token), SENDER_ALIAS.toHexString());
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void nameBeforeEvmV34(final boolean isStatic) {
            // Given
            final var name = "Hbars";
            final var tokenEntity = persistTokenEntityHistorical(historicalRangeAfterEvm34);
            domainBuilder
                    .token()
                    .customize(t -> t.tokenId(tokenEntity.getId())
                            .type(TokenTypeEnum.FUNGIBLE_COMMON)
                            .name(name)
                            .timestampRange(historicalRangeAfterEvm34)
                            .createdTimestamp(historicalRangeAfterEvm34.lowerEndpoint()))
                    .persist();
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_name(getAddressFromEntity(tokenEntity))
                    : contract.call_nameNonStatic(getAddressFromEntity(tokenEntity));
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void ownerOfBeforeEvmV34(final boolean isStatic) {
            // Given
            final var owner = persistAccountEntityHistorical(historicalRangeAfterEvm34);
            final var nftToken = persistNftHistorical(historicalRangeAfterEvm34, owner.toEntityId());
            domainBuilder
                    .tokenAccountHistory()
                    .customize(e -> e.freezeStatus(TokenFreezeStatusEnum.FROZEN)
                            .accountId(owner.getId())
                            .tokenId(nftToken.getId())
                            .kycStatus(TokenKycStatusEnum.GRANTED)
                            .associated(true)
                            .timestampRange(historicalRangeAfterEvm34))
                    .persist();
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_getOwnerOf(getAddressFromEntity(nftToken), BigInteger.valueOf(1L))
                    : contract.call_getOwnerOfNonStatic(getAddressFromEntity(nftToken), BigInteger.valueOf(1L));
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void emptyOwnerOfBeforeEvmV34(final boolean isStatic) {
            // Given
            final var owner = persistAccountEntityHistorical(historicalRangeAfterEvm34);
            final var nftToken = persistNftHistorical(historicalRangeAfterEvm34, owner.toEntityId());
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_getOwnerOf(getAddressFromEntity(nftToken), BigInteger.valueOf(2L))
                    : contract.call_getOwnerOfNonStatic(getAddressFromEntity(nftToken), BigInteger.valueOf(2L));
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void tokenURIBeforeEvmV34(final boolean isStatic) {
            // Given
            final var owner = persistAccountEntityHistorical(historicalRangeAfterEvm34);
            final byte[] kycKey = domainBuilder.key();
            final var metadata = "NFT_METADATA_URI";
            final var tokenEntity = persistTokenEntityHistorical(historicalRangeAfterEvm34);
            domainBuilder
                    .token()
                    .customize(t -> t.tokenId(tokenEntity.getId())
                            .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                            .treasuryAccountId(owner.toEntityId())
                            .timestampRange(historicalRangeAfterEvm34)
                            .kycKey(kycKey))
                    .persist();
            domainBuilder
                    .nft()
                    .customize(n -> n.tokenId(tokenEntity.getId())
                            .serialNumber(1L)
                            .accountId(owner.toEntityId())
                            .metadata(metadata.getBytes())
                            .timestampRange(historicalRangeAfterEvm34))
                    .persist();
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_tokenURI(getAddressFromEntity(tokenEntity), BigInteger.valueOf(1L))
                    : contract.call_tokenURINonStatic(getAddressFromEntity(tokenEntity), BigInteger.valueOf(1L));
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }
    }

    @Nested
    class afterEvm34Tests {
        @BeforeEach
        void beforeEach() {
            historicalRangeAfterEvm34 = setUpHistoricalContextAfterEvm34();
        }

        @ParameterizedTest // ОК
        @ValueSource(booleans = {true, false})
        void getApprovedEmptySpenderAfterEvmV34Test(final boolean isStatic) throws Exception {
            // Given
            final var tokenEntity = persistTokenEntityHistorical(historicalRangeAfterEvm34);
            domainBuilder
                    .token()
                    .customize(t -> t.tokenId(tokenEntity.getId())
                            .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                            .timestampRange(historicalRangeAfterEvm34))
                    .persist();
            domainBuilder
                    .nft()
                    .customize(n ->
                            n.tokenId(tokenEntity.getId()).serialNumber(1L).timestampRange(historicalRangeAfterEvm34))
                    .persist();
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_getApproved(getAddressFromEntity(tokenEntity), BigInteger.valueOf(1L))
                            .send()
                    : contract.call_getApprovedNonStatic(getAddressFromEntity(tokenEntity), BigInteger.valueOf(1L))
                            .send();
            // Then
            assertThat(result).isEqualTo(Address.ZERO.toHexString());
        }

        @ParameterizedTest // ОК
        @ValueSource(booleans = {true, false})
        void getApprovedAfterEvmV34(final boolean isStatic) throws Exception {
            // Given
            final var owner = persistAccountEntityHistorical(historicalRangeAfterEvm34);
            final var spender = persistAccountEntityHistorical(historicalRangeAfterEvm34);
            final var nftToken = persistNftHistorical(
                    historicalRangeAfterEvm34, owner.toEntityId(), owner.toEntityId(), spender.toEntityId());
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_getApproved(getAddressFromEntity(nftToken), BigInteger.valueOf(1L))
                            .send()
                    : contract.call_getApprovedNonStatic(getAddressFromEntity(nftToken), BigInteger.valueOf(1L))
                            .send();
            // Then
            assertThat(result).isEqualTo(getAliasFromEntity(spender));
        }

        @ParameterizedTest // OK
        @ValueSource(booleans = {true, false})
        void isApproveForAllAfterEvmV34(final boolean isStatic) throws Exception {
            // Given
            final var owner = persistAccountEntityHistorical(historicalRangeAfterEvm34);
            final var spender = persistAccountEntityHistorical(historicalRangeAfterEvm34);
            final var nftToken = persistNftHistorical(
                    historicalRangeAfterEvm34, owner.toEntityId(), owner.toEntityId(), spender.toEntityId());
            domainBuilder
                    .nftAllowance()
                    .customize(a -> a.tokenId(nftToken.getId())
                            .owner(owner.getNum())
                            .spender(spender.getNum())
                            .timestampRange(historicalRangeAfterEvm34)
                            .approvedForAll(true))
                    .persist();
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_isApprovedForAll(
                                    getAddressFromEntity(nftToken),
                                    getAddressFromEntity(owner),
                                    getAddressFromEntity(spender))
                            .send()
                    : contract.call_isApprovedForAllNonStatic(
                                    getAddressFromEntity(nftToken),
                                    getAddressFromEntity(owner),
                                    getAddressFromEntity(spender))
                            .send();
            // Then
            assertThat(result).isEqualTo(true);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void isApproveForAllAfterEvmV34WithAlias(final boolean isStatic) throws Exception {
            // Given
            final var owner =
                    persistAccountEntityWithAliasHistorical(historicalRangeAfterEvm34, SENDER_ALIAS, SENDER_PUBLIC_KEY);
            final var spender = persistAccountEntityWithAliasHistorical(
                    historicalRangeAfterEvm34, SPENDER_ALIAS, SPENDER_PUBLIC_KEY);
            final var nftToken = persistNftHistorical(
                    historicalRangeAfterEvm34, owner.toEntityId(), owner.toEntityId(), spender.toEntityId());
            domainBuilder
                    .nftAllowance()
                    .customize(a -> a.tokenId(nftToken.getId())
                            .owner(owner.getNum())
                            .spender(spender.getNum())
                            .timestampRange(historicalRangeAfterEvm34)
                            .approvedForAll(true))
                    .persist();
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_isApprovedForAll(
                                    getAddressFromEntity(nftToken),
                                    SENDER_ALIAS.toHexString(),
                                    SPENDER_ALIAS.toHexString())
                            .send()
                    : contract.call_isApprovedForAllNonStatic(
                                    getAddressFromEntity(nftToken),
                                    SENDER_ALIAS.toHexString(),
                                    SPENDER_ALIAS.toHexString())
                            .send();
            // Then
            assertThat(result).isEqualTo(true);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void allowanceAfterEvmV34(final boolean isStatic) throws Exception {
            // Given
            final var owner = persistAccountEntityHistorical(historicalRangeAfterEvm34);
            final var spender = persistAccountEntityHistorical(historicalRangeAfterEvm34);
            final var token = persistFungibleTokenHistorical(historicalRangeAfterEvm34);
            final var amountGranted = 10L;
            domainBuilder
                    .tokenAllowance()
                    .customize(a -> a.tokenId(token.getId())
                            .owner(owner.getNum())
                            .spender(spender.getNum())
                            .amount(amountGranted)
                            .amountGranted(amountGranted)
                            .timestampRange(historicalRangeAfterEvm34))
                    .persist();
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_allowance(
                                    getAddressFromEntity(token),
                                    getAddressFromEntity(owner),
                                    getAddressFromEntity(spender))
                            .send()
                    : contract.call_allowanceNonStatic(
                                    getAddressFromEntity(token),
                                    getAddressFromEntity(owner),
                                    getAddressFromEntity(spender))
                            .send();
            // Then
            assertThat(result).isEqualTo(BigInteger.valueOf(amountGranted));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void allowanceAfterEvmV34WithAlias(final boolean isStatic) throws Exception {
            // Given
            final var owner =
                    persistAccountEntityWithAliasHistorical(historicalRangeAfterEvm34, SENDER_ALIAS, SENDER_PUBLIC_KEY);
            final var spender = persistAccountEntityWithAliasHistorical(
                    historicalRangeAfterEvm34, SPENDER_ALIAS, SPENDER_PUBLIC_KEY);
            final var token = persistFungibleTokenHistorical(historicalRangeAfterEvm34);
            final var amountGranted = 10L;
            domainBuilder
                    .tokenAllowance()
                    .customize(a -> a.tokenId(token.getId())
                            .owner(owner.getNum())
                            .spender(spender.getNum())
                            .amount(amountGranted)
                            .amountGranted(amountGranted)
                            .timestampRange(historicalRangeAfterEvm34))
                    .persist();
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_allowance(
                                    getAddressFromEntity(token),
                                    SENDER_ALIAS.toHexString(),
                                    SPENDER_ALIAS.toHexString())
                            .send()
                    : contract.call_allowanceNonStatic(
                                    getAddressFromEntity(token),
                                    SENDER_ALIAS.toHexString(),
                                    SPENDER_ALIAS.toHexString())
                            .send();
            // Then
            assertThat(result).isEqualTo(BigInteger.valueOf(amountGranted));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void decimalsAfterEvmV34(final boolean isStatic) throws Exception {
            // Given
            final var decimals = 12;
            final var tokenEntity = persistTokenEntityHistorical(historicalRangeAfterEvm34);
            domainBuilder
                    .token()
                    .customize(t -> t.tokenId(tokenEntity.getId())
                            .type(TokenTypeEnum.FUNGIBLE_COMMON)
                            .decimals(decimals)
                            .timestampRange(historicalRangeAfterEvm34)
                            .createdTimestamp(historicalRangeAfterEvm34.lowerEndpoint()))
                    .persist();
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_decimals(getAddressFromEntity(tokenEntity)).send()
                    : contract.call_decimalsNonStatic(getAddressFromEntity(tokenEntity))
                            .send();
            // Then
            assertThat(result).isEqualTo(BigInteger.valueOf(decimals));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void totalSupplyAfterEvmV34(final boolean isStatic) throws Exception {
            // Given
            final var totalSupply = 12345L;
            final var spender = persistAccountEntityHistorical(historicalRangeAfterEvm34);
            final var tokenEntity = persistTokenEntityHistorical(historicalRangeAfterEvm34);
            domainBuilder
                    .token()
                    .customize(t -> t.tokenId(tokenEntity.getId())
                            .type(TokenTypeEnum.FUNGIBLE_COMMON)
                            .totalSupply(totalSupply)
                            .timestampRange(historicalRangeAfterEvm34)
                            .createdTimestamp(historicalRangeAfterEvm34.lowerEndpoint()))
                    .persist();
            balancePersistHistorical(
                    toAddress(tokenEntity.getId()), historicalRangeAfterEvm34, toAddress(spender.getId()), 12L);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_totalSupply(getAddressFromEntity(tokenEntity))
                            .send()
                    : contract.call_totalSupplyNonStatic(getAddressFromEntity(tokenEntity))
                            .send();
            // Then
            assertThat(result).isEqualTo(BigInteger.valueOf(totalSupply));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void symbolAfterEvmV34(final boolean isStatic) throws Exception {
            // Given
            final var symbol = "HBAR";
            final var tokenEntity = persistTokenEntityHistorical(historicalRangeAfterEvm34);
            domainBuilder
                    .token()
                    .customize(t -> t.tokenId(tokenEntity.getId())
                            .type(TokenTypeEnum.FUNGIBLE_COMMON)
                            .symbol(symbol)
                            .timestampRange(historicalRangeAfterEvm34)
                            .createdTimestamp(historicalRangeAfterEvm34.lowerEndpoint()))
                    .persist();
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_symbol(getAddressFromEntity(tokenEntity)).send()
                    : contract.call_symbolNonStatic(getAddressFromEntity(tokenEntity))
                            .send();
            // Then
            assertThat(result).isEqualTo(symbol);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void balanceOfAfterEvmV34(final boolean isStatic) throws Exception {
            // Given
            final var owner = persistAccountEntityHistorical(historicalRangeAfterEvm34);
            final var token = persistFungibleTokenHistorical(historicalRangeAfterEvm34);
            domainBuilder
                    .tokenAccountHistory()
                    .customize(e -> e.freezeStatus(TokenFreezeStatusEnum.FROZEN)
                            .accountId(owner.getId())
                            .tokenId(token.getId())
                            .kycStatus(TokenKycStatusEnum.GRANTED)
                            .associated(true)
                            .timestampRange(historicalRangeAfterEvm34))
                    .persist();
            final var balance = 10L;
            balancePersistHistorical(
                    toAddress(token.getId()), historicalRangeAfterEvm34, toAddress(owner.getId()), balance);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_balanceOf(getAddressFromEntity(token), getAddressFromEntity(owner))
                            .send()
                    : contract.call_balanceOfNonStatic(getAddressFromEntity(token), getAddressFromEntity(owner))
                            .send();
            // Then
            assertThat(result).isEqualTo(BigInteger.valueOf(balance));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void balanceOfAfterEvmV34WithAlias(final boolean isStatic) throws Exception {
            // Given
            final var owner =
                    persistAccountEntityWithAliasHistorical(historicalRangeAfterEvm34, SENDER_ALIAS, SENDER_PUBLIC_KEY);
            final var token = persistFungibleTokenHistorical(historicalRangeAfterEvm34);
            domainBuilder
                    .tokenAccountHistory()
                    .customize(e -> e.freezeStatus(TokenFreezeStatusEnum.FROZEN)
                            .accountId(owner.getId())
                            .tokenId(token.getId())
                            .kycStatus(TokenKycStatusEnum.GRANTED)
                            .associated(true)
                            .timestampRange(historicalRangeAfterEvm34))
                    .persist();
            final var balance = 10L;
            balancePersistHistorical(
                    toAddress(token.getId()), historicalRangeAfterEvm34, toAddress(owner.getId()), balance);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_balanceOf(getAddressFromEntity(token), SENDER_ALIAS.toHexString())
                            .send()
                    : contract.call_balanceOfNonStatic(getAddressFromEntity(token), SENDER_ALIAS.toHexString())
                            .send();
            // Then
            assertThat(result).isEqualTo(BigInteger.valueOf(balance));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void nameAfterEvmV34(final boolean isStatic) throws Exception {
            // Given
            final var name = "Hbars";
            final var tokenEntity = persistTokenEntityHistorical(historicalRangeAfterEvm34);
            domainBuilder
                    .token()
                    .customize(t -> t.tokenId(tokenEntity.getId())
                            .type(TokenTypeEnum.FUNGIBLE_COMMON)
                            .name(name)
                            .timestampRange(historicalRangeAfterEvm34)
                            .createdTimestamp(historicalRangeAfterEvm34.lowerEndpoint()))
                    .persist();
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_name(getAddressFromEntity(tokenEntity)).send()
                    : contract.call_nameNonStatic(getAddressFromEntity(tokenEntity))
                            .send();
            // Then
            assertThat(result).isEqualTo(name);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void ownerOfAfterEvmV34(final boolean isStatic) throws Exception {
            // Given
            final var owner = persistAccountEntityHistorical(historicalRangeAfterEvm34);
            final var nftToken = persistNftHistorical(historicalRangeAfterEvm34, owner.toEntityId());
            domainBuilder
                    .tokenAccountHistory()
                    .customize(e -> e.freezeStatus(TokenFreezeStatusEnum.FROZEN)
                            .accountId(owner.getId())
                            .tokenId(nftToken.getId())
                            .kycStatus(TokenKycStatusEnum.GRANTED)
                            .associated(true)
                            .timestampRange(historicalRangeAfterEvm34))
                    .persist();
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_getOwnerOf(getAddressFromEntity(nftToken), BigInteger.valueOf(1L))
                            .send()
                    : contract.call_getOwnerOfNonStatic(getAddressFromEntity(nftToken), BigInteger.valueOf(1L))
                            .send();
            // Then
            assertThat(result).isEqualTo(getAliasFromEntity(owner));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void emptyOwnerOfAfterEvmV34(final boolean isStatic) throws Exception {
            // Given
            final var owner = persistAccountEntityHistorical(historicalRangeAfterEvm34);
            final var nftToken = persistNftHistorical(historicalRangeAfterEvm34, owner.toEntityId());
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_getOwnerOf(getAddressFromEntity(nftToken), BigInteger.valueOf(2L))
                            .send()
                    : contract.call_getOwnerOfNonStatic(getAddressFromEntity(nftToken), BigInteger.valueOf(2L))
                            .send();
            // Then
            assertThat(result).isEqualTo(Address.ZERO.toHexString());
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void tokenURIAfterEvmV34(final boolean isStatic) throws Exception {
            // Given
            final var owner = persistAccountEntityHistorical(historicalRangeAfterEvm34);
            final byte[] kycKey = domainBuilder.key();
            final var metadata = "NFT_METADATA_URI";
            final var tokenEntity = persistTokenEntityHistorical(historicalRangeAfterEvm34);
            domainBuilder
                    .token()
                    .customize(t -> t.tokenId(tokenEntity.getId())
                            .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                            .treasuryAccountId(owner.toEntityId())
                            .timestampRange(historicalRangeAfterEvm34)
                            .kycKey(kycKey))
                    .persist();
            domainBuilder
                    .nft()
                    .customize(n -> n.tokenId(tokenEntity.getId())
                            .serialNumber(1L)
                            .accountId(owner.toEntityId())
                            .metadata(metadata.getBytes())
                            .timestampRange(historicalRangeAfterEvm34))
                    .persist();
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_tokenURI(getAddressFromEntity(tokenEntity), BigInteger.valueOf(1L))
                            .send()
                    : contract.call_tokenURINonStatic(getAddressFromEntity(tokenEntity), BigInteger.valueOf(1L))
                            .send();
            // Then
            assertThat(result).isEqualTo(metadata);
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {51, Long.MAX_VALUE - 1})
    void ercReadOnlyPrecompileHistoricalNotExistingBlockTest(final long blockNumber) {
        // When
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(blockNumber)));
        // Then
        assertThatThrownBy(() -> testWeb3jService.deploy(ERCTestContractHistorical::deploy))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining(UNKNOWN_BLOCK_NUMBER);
    }

    private void balancePersistHistorical(
            final Address tokenAddress, final Range<Long> historicalBlock, Address senderAddress, Long balance) {
        final var tokenEntityId = entityIdFromEvmAddress(tokenAddress);
        final var accountId = entityIdFromEvmAddress(senderAddress);
        final var tokenId = entityIdFromEvmAddress(tokenAddress);
        // hardcoded treasury account id is mandatory
        final long lowerTimestamp = historicalBlock.lowerEndpoint();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(lowerTimestamp, EntityId.of(2))))
                .persist();
        domainBuilder
                .tokenBalance()
                .customize(tb -> tb.id(new TokenBalance.Id(lowerTimestamp, accountId, tokenId))
                        .balance(balance))
                .persist();
        domainBuilder
                .tokenBalance()
                // Expected total supply is 12345
                .customize(tb -> tb.balance(12345L - balance)
                        .id(new TokenBalance.Id(lowerTimestamp, domainBuilder.entityId(), tokenEntityId)))
                .persist();
    }

    private Entity persistFungibleTokenHistorical(final Range<Long> timestampRange) {
        final var tokenEntity = persistTokenEntityHistorical(historicalRangeAfterEvm34);
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .timestampRange(timestampRange)
                        .createdTimestamp(timestampRange.lowerEndpoint()))
                .persist();
        return tokenEntity;
    }

    private Entity persistNftHistorical(final Range<Long> timestampRange, final EntityId treasury) {
        return persistNftHistorical(timestampRange, treasury, treasury);
    }

    private Entity persistNftHistorical(
            final Range<Long> timestampRange, final EntityId treasury, final EntityId owner) {
        return persistNftHistorical(timestampRange, treasury, owner, owner);
    }

    private Entity persistNftHistorical(
            final Range<Long> timestampRange, final EntityId treasury, final EntityId owner, final EntityId spender) {
        return persistNftHistorical(timestampRange, treasury, owner, spender, domainBuilder.key());
    }

    private Entity persistNftHistorical(
            final Range<Long> timestampRange,
            EntityId treasury,
            EntityId owner,
            EntityId spender,
            final byte[] kycKey) {
        final var tokenEntity = persistTokenEntityHistorical(timestampRange);
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(treasury)
                        .timestampRange(timestampRange)
                        .kycKey(kycKey))
                .persist();
        domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId())
                        .serialNumber(1L)
                        .spender(spender)
                        .accountId(owner)
                        .timestampRange(timestampRange))
                .persist();
        return tokenEntity;
    }

    private Entity persistTokenEntityHistorical(final Range<Long> timestampRange) {
        return domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.TOKEN).timestampRange(timestampRange))
                .persist();
    }

    private Range<Long> setUpHistoricalContextAfterEvm34() {
        final var recordFile = domainBuilder
                .recordFile()
                .customize(f -> f.index(EVM_V_34_BLOCK))
                .persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK)));
        final var historicalRange = Range.closedOpen(recordFile.getConsensusStart(), recordFile.getConsensusEnd());
        testWeb3jService.setHistoricalRange(historicalRange);
        return historicalRange;
    }

    private Range<Long> setUpHistoricalContextBeforeEvm34() {
        final var recordFileBeforeEvm34 = domainBuilder
                .recordFile()
                .customize(f -> f.index(EVM_V_34_BLOCK - 1))
                .persist();
        final var recordFileAfterEvm34 = domainBuilder
                .recordFile()
                .customize(f -> f.index(EVM_V_34_BLOCK))
                .persist();
        final var historicalRange =
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd());
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK - 1)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd()));
        return historicalRange;
    }

    private Entity persistAccountEntityHistorical(final Range<Long> timestampRange) {
        return domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.ACCOUNT)
                        .deleted(false)
                        .balance(1_000_000_000_000L)
                        .timestampRange(timestampRange)
                        .createdTimestamp(timestampRange.lowerEndpoint()))
                .persist();
    }

    private Entity persistAccountEntityWithAliasHistorical(
            final Range<Long> timestampRange, Address alias, ByteString publicKey) {
        return domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.ACCOUNT)
                        .deleted(false)
                        .evmAddress(alias.toArray())
                        .alias(publicKey.toByteArray())
                        .balance(1_000_000_000_000L)
                        .timestampRange(timestampRange)
                        .createdTimestamp(timestampRange.lowerEndpoint()))
                .persist();
    }
}
