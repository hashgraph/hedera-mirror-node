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
import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.entityIdFromEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.exception.BlockNumberNotFoundException.UNKNOWN_BLOCK_NUMBER;
import static com.hedera.mirror.web3.service.ContractCallTestSetup.NETWORK_TREASURY_ACCOUNT_ID;

import static com.hedera.mirror.web3.utils.ContractCallTestUtil.SENDER_ALIAS;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.SENDER_PUBLIC_KEY;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.SPENDER_ALIAS;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.SPENDER_PUBLIC_KEY;

import static com.hedera.mirror.web3.utils.ContractCallTestUtil.EVM_V_34_BLOCK;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.KEY_PROTO;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.balance.TokenBalance;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.common.domain.token.TokenPauseStatusEnum;
import com.hedera.mirror.common.domain.token.TokenSupplyTypeEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.mirror.web3.web3j.generated.ERCTestContractHistorical;
import java.math.BigInteger;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ContractCallServiceERCTokenHistoricalTest extends AbstractContractCallServiceTest {

    private RecordFile recordFileBeforeEvm34;
    protected static RecordFile recordFileAfterEvm34;

    @ParameterizedTest
@ValueSource(booleans = {true, false})
void getApprovedEmptySpenderBeforeEvmV34(final boolean isStatic) {
    // Given
    recordFileBeforeEvmV34Persist();
    recordFileAfterEvmV34Persist();
    testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK - 1)));
    testWeb3jService.setHistoricalRange(
            Range.closedOpen(recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd()));
    final var tokenAddress = toAddress(1063);
    final var ownerAddress = toAddress(1065);
    final var ownerEntityId = ownerEntityPersistHistoricalAfterEvm34(ownerAddress);
    final var autoRenewAddress = toAddress(1078);
        final var spenderAddress = toAddress(1041);
    final var spenderEntityPersist = spenderEntityPersistHistorical(spenderAddress);
    nftPersistHistorical(
            tokenAddress,
            autoRenewAddress,
            ownerEntityId,
            spenderEntityPersist,
            ownerEntityId,
            KEY_PROTO,
            TokenPauseStatusEnum.PAUSED,
            true,
            Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
    final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
    // When
    final var result = isStatic
            ? contract.call_getApproved(tokenAddress.toHexString(), BigInteger.valueOf(2L))
            : contract.call_getApprovedNonStatic(tokenAddress.toHexString(), BigInteger.valueOf(2L));
    // Then
    assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
}
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void getApprovedEmptySpenderAfterEvmV34(final boolean isStatic) throws Exception {
        // Given
        recordFileAfterEvmV34Persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        final var tokenAddress = toAddress(1063);
        final var ownerAddress = toAddress(1065);
        final var ownerEntityId = ownerEntityPersistHistorical(ownerAddress);
        final var autoRenewAddress = toAddress(1078);
        final var spenderAddress = toAddress(1041);
        final var spenderEntityPersist = spenderEntityPersistHistorical(spenderAddress);
        nftPersistHistorical(
                tokenAddress,
                autoRenewAddress,
                ownerEntityId,
                spenderEntityPersist,
                ownerEntityId,
                KEY_PROTO,
                TokenPauseStatusEnum.PAUSED,
                true,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
        // When
        final var result = isStatic
                ? contract.call_getApproved(tokenAddress.toHexString(), BigInteger.valueOf(2L)).send()
                : contract.call_getApprovedNonStatic(tokenAddress.toHexString(), BigInteger.valueOf(2L)).send();
        // Then
        assertThat(result).isEqualTo(Address.ZERO.toHexString());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void getApprovedBeforeEvmV34(final boolean isStatic) {
        // Given
        recordFileBeforeEvmV34Persist();
        recordFileAfterEvmV34Persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK - 1)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd()));
        final var tokenAddress = toAddress(1063);
        final var ownerAddress = toAddress(1065);
        final var ownerEntityId = ownerEntityPersistHistoricalAfterEvm34(ownerAddress);
        final var autoRenewAddress = toAddress(1078);
        final var spenderAddress = toAddress(1041);
        final var spenderEntityPersist = spenderEntityPersistHistorical(spenderAddress);
        nftPersistHistorical(
                tokenAddress,
                autoRenewAddress,
                ownerEntityId,
                spenderEntityPersist,
                ownerEntityId,
                KEY_PROTO,
                TokenPauseStatusEnum.PAUSED,
                true,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
        // When
        final var result = isStatic
                ? contract.call_getApproved(tokenAddress.toHexString(), BigInteger.valueOf(3L))
                : contract.call_getApprovedNonStatic(tokenAddress.toHexString(), BigInteger.valueOf(3L));
        // Then
        assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void getApprovedAfterEvmV34(final boolean isStatic) throws Exception {
        // Given
        recordFileAfterEvmV34Persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        final var tokenAddress = toAddress(1063);
        final var ownerAddress = toAddress(1065);
        final var ownerEntityId = ownerEntityPersistHistorical(ownerAddress);
        final var autoRenewAddress = toAddress(1078);
        final var spenderAddress = toAddress(1041);
        final var spenderEntityPersist = spenderEntityPersistHistorical(spenderAddress);
        nftPersistHistorical(
                tokenAddress,
                autoRenewAddress,
                ownerEntityId,
                spenderEntityPersist,
                ownerEntityId,
                KEY_PROTO,
                TokenPauseStatusEnum.PAUSED,
                true,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
        // When
        final var result = isStatic
                ? contract.call_getApproved(tokenAddress.toHexString(), BigInteger.valueOf(3L)).send()
                : contract.call_getApprovedNonStatic(tokenAddress.toHexString(), BigInteger.valueOf(3L)).send();
        // Then
        assertThat(result).isEqualTo(SPENDER_ALIAS.toHexString());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void isApproveForAllBeforeEvmV34(final boolean isStatic) {
        // Given
        recordFileBeforeEvmV34Persist();
        recordFileAfterEvmV34Persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK - 1)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd()));
        final var tokenAddress = toAddress(1063);
        final var ownerAddress = toAddress(1065);
        final var ownerEntityId = ownerEntityPersistHistoricalAfterEvm34(ownerAddress);
        final var autoRenewAddress = toAddress(1078);
        final var spenderAddress = toAddress(1041);
        final var spenderEntityPersist = spenderEntityPersistHistorical(spenderAddress);
        nftPersistHistorical(
                tokenAddress,
                autoRenewAddress,
                ownerEntityId,
                spenderEntityPersist,
                ownerEntityId,
                KEY_PROTO,
                TokenPauseStatusEnum.PAUSED,
                true,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
        // When
        final var result = isStatic
                ? contract.call_isApprovedForAll(tokenAddress.toHexString(), ownerAddress.toHexString(), spenderAddress.toHexString())
                : contract.call_isApprovedForAllNonStatic(tokenAddress.toHexString(), ownerAddress.toHexString(), spenderAddress.toHexString());
        // Then
        assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void isApproveForAllAfterEvmV34(final boolean isStatic) throws Exception {
        // Given
        recordFileAfterEvmV34Persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        final var tokenAddress = toAddress(1063);
        final var ownerAddress = toAddress(1065);
        final var ownerEntityId = ownerEntityPersistHistoricalAfterEvm34(ownerAddress);
        final var autoRenewAddress = toAddress(1078);
        final var spenderAddress = toAddress(1041);
        final var spenderEntityPersist = spenderEntityPersistHistorical(spenderAddress);
        nftPersistHistorical(
                tokenAddress,
                autoRenewAddress,
                ownerEntityId,
                spenderEntityPersist,
                ownerEntityId,
                KEY_PROTO,
                TokenPauseStatusEnum.PAUSED,
                true,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        nftAllowancePersistHistorical(entityIdFromEvmAddress(tokenAddress), ownerEntityId, ownerEntityId, spenderEntityPersist);
        final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
        // When
        final var result = isStatic
                ? contract.call_isApprovedForAll(tokenAddress.toHexString(), ownerAddress.toHexString(), spenderAddress.toHexString()).send()
                : contract.call_isApprovedForAllNonStatic(tokenAddress.toHexString(), ownerAddress.toHexString(), spenderAddress.toHexString()).send();
        // Then
        assertThat(result).isEqualTo(true);
    }
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void isApproveForAllBeforeEvmV34WithAlias(final boolean isStatic) {
        // Given
        recordFileBeforeEvmV34Persist();
        recordFileAfterEvmV34Persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK - 1)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd()));
        final var tokenAddress = toAddress(1063);
        final var autoRenewAddress = toAddress(1078);
        final var senderAddress = toAddress(1014);
        final var senderEntityPersist = senderEntityPersistHistorical(senderAddress);
        final var spenderAddress = toAddress(1041);
        final var spenderEntityPersist = spenderEntityPersistHistorical(spenderAddress);
        nftPersistHistorical(
                tokenAddress,
                autoRenewAddress,
                senderEntityPersist,
                spenderEntityPersist,
                senderEntityPersist,
                KEY_PROTO,
                TokenPauseStatusEnum.PAUSED,
                true,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        nftAllowancePersistHistorical(entityIdFromEvmAddress(tokenAddress), senderEntityPersist, senderEntityPersist, spenderEntityPersist);
        final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
        // When
        final var result = isStatic
                ? contract.call_isApprovedForAll(tokenAddress.toHexString(), SENDER_ALIAS.toHexString(), SPENDER_ALIAS.toHexString())
                : contract.call_isApprovedForAllNonStatic(tokenAddress.toHexString(), SENDER_ALIAS.toHexString(), SPENDER_ALIAS.toHexString());
        // Then
        assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
    }
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void isApproveForAllAfterEvmV34WithAlias(final boolean isStatic) throws Exception {
        // Given
        recordFileAfterEvmV34Persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        final var tokenAddress = toAddress(1063);
        final var autoRenewAddress = toAddress(1078);
        final var senderAddress = toAddress(1014);
        final var senderEntityPersist = senderEntityPersistHistorical(senderAddress);
        final var spenderAddress = toAddress(1041);
        final var spenderEntityPersist = spenderEntityPersistHistorical(spenderAddress);
        nftPersistHistorical(
                tokenAddress,
                autoRenewAddress,
                senderEntityPersist,
                spenderEntityPersist,
                senderEntityPersist,
                KEY_PROTO,
                TokenPauseStatusEnum.PAUSED,
                true,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        nftAllowancePersistHistorical(entityIdFromEvmAddress(tokenAddress), senderEntityPersist, senderEntityPersist, spenderEntityPersist);
        final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
        // When
        final var result = isStatic
                ? contract.call_isApprovedForAll(tokenAddress.toHexString(), SENDER_ALIAS.toHexString(), SPENDER_ALIAS.toHexString()).send()
                : contract.call_isApprovedForAllNonStatic(tokenAddress.toHexString(), SENDER_ALIAS.toHexString(), SPENDER_ALIAS.toHexString()).send();
        // Then
        assertThat(result).isEqualTo(true);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void allowanceBeforeEvmV34(final boolean isStatic) {
        // Given
        recordFileBeforeEvmV34Persist();
        recordFileAfterEvmV34Persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK - 1)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd()));
        final var tokenAddress = toAddress(1062);
        final var ownerAddress = toAddress(1065);
        final var ownerEntityId = ownerEntityPersistHistoricalAfterEvm34(ownerAddress);
        final var autoRenewAddress = toAddress(1078);
        final var spenderAddress = toAddress(1041);
        final var spenderEntityPersist = spenderEntityPersistHistorical(spenderAddress);
        fungibleTokenPersistHistorical(
                ownerEntityId,
                KEY_PROTO,
                tokenAddress,
                autoRenewAddress,
                9999999999999L,
                TokenPauseStatusEnum.UNPAUSED,
                true,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        final var amountGranted = 10L;
        fungibleTokenAllowancePersistHistorical(entityIdFromEvmAddress(tokenAddress), ownerEntityId, ownerEntityId, spenderEntityPersist, amountGranted);
        final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
        // When
        final var result = isStatic
                ? contract.call_allowance(tokenAddress.toHexString(), ownerAddress.toHexString(), spenderAddress.toHexString())
                : contract.call_allowanceNonStatic(tokenAddress.toHexString(), ownerAddress.toHexString(), spenderAddress.toHexString());
        // Then
        assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void allowanceAfterEvmV34(final boolean isStatic) throws Exception {
        // Given
        recordFileAfterEvmV34Persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        final var tokenAddress = toAddress(1062);
        final var ownerAddress = toAddress(1065);
        final var ownerEntityId = ownerEntityPersistHistoricalAfterEvm34(ownerAddress);
        final var autoRenewAddress = toAddress(1078);
        final var spenderAddress = toAddress(1041);
        final var spenderEntityPersist = spenderEntityPersistHistorical(spenderAddress);
        fungibleTokenPersistHistorical(
                ownerEntityId,
                KEY_PROTO,
                tokenAddress,
                autoRenewAddress,
                9999999999999L,
                TokenPauseStatusEnum.UNPAUSED,
                true,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        final var amountGranted = 10L;
        fungibleTokenAllowancePersistHistorical(entityIdFromEvmAddress(tokenAddress), ownerEntityId, ownerEntityId, spenderEntityPersist, amountGranted);
        final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
        // When
        final var result = isStatic
                ? contract.call_allowance(tokenAddress.toHexString(), ownerAddress.toHexString(), spenderAddress.toHexString()).send()
                : contract.call_allowanceNonStatic(tokenAddress.toHexString(), ownerAddress.toHexString(), spenderAddress.toHexString()).send();
        // Then
        assertThat(result).isEqualTo(BigInteger.valueOf(amountGranted));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void allowanceBeforeEvmV34WithAlias(final boolean isStatic) {
        // Given
        recordFileBeforeEvmV34Persist();
        recordFileAfterEvmV34Persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK - 1)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd()));
        final var tokenAddress = toAddress(1062);
        final var autoRenewAddress = toAddress(1078);
        final var senderAddress = toAddress(1014);
        final var senderEntityPersist = senderEntityPersistHistorical(senderAddress);
        final var spenderAddress = toAddress(1041);
        final var spenderEntityPersist = spenderEntityPersistHistorical(spenderAddress);
        fungibleTokenPersistHistorical(
                senderEntityPersist,
                KEY_PROTO,
                tokenAddress,
                autoRenewAddress,
                9999999999999L,
                TokenPauseStatusEnum.UNPAUSED,
                true,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        final var amountGranted = 10L;
        fungibleTokenAllowancePersistHistorical(entityIdFromEvmAddress(tokenAddress), senderEntityPersist, senderEntityPersist, spenderEntityPersist, amountGranted);
        final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
        // When
        final var result = isStatic
                ? contract.call_allowance(tokenAddress.toHexString(), SENDER_ALIAS.toHexString(), SPENDER_ALIAS.toHexString())
                : contract.call_allowanceNonStatic(tokenAddress.toHexString(), SENDER_ALIAS.toHexString(), SPENDER_ALIAS.toHexString());
        // Then
        assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void allowanceAfterEvmV34WithAlias(final boolean isStatic) throws Exception {
        // Given
        recordFileAfterEvmV34Persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        final var tokenAddress = toAddress(1062);
        final var autoRenewAddress = toAddress(1078);
        final var senderAddress = toAddress(1014);
        final var senderEntityPersist = senderEntityPersistHistorical(senderAddress);
        final var spenderAddress = toAddress(1041);
        final var spenderEntityPersist = spenderEntityPersistHistorical(spenderAddress);
        fungibleTokenPersistHistorical(
                senderEntityPersist,
                KEY_PROTO,
                tokenAddress,
                autoRenewAddress,
                9999999999999L,
                TokenPauseStatusEnum.UNPAUSED,
                true,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        final var amountGranted = 10L;
        fungibleTokenAllowancePersistHistorical(entityIdFromEvmAddress(tokenAddress), senderEntityPersist, senderEntityPersist, spenderEntityPersist, amountGranted);
        final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
        // When
        final var result = isStatic
                ? contract.call_allowance(tokenAddress.toHexString(), SENDER_ALIAS.toHexString(), SPENDER_ALIAS.toHexString()).send()
                : contract.call_allowanceNonStatic(tokenAddress.toHexString(), SENDER_ALIAS.toHexString(), SPENDER_ALIAS.toHexString()).send();
        // Then
        assertThat(result).isEqualTo(BigInteger.valueOf(amountGranted));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void decimalsBeforeEvmV34(final boolean isStatic) {
        // Given
        recordFileBeforeEvmV34Persist();
        recordFileAfterEvmV34Persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK - 1)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd()));
        final var tokenAddress = toAddress(1062);
        final var ownerAddress = toAddress(1065);
        final var ownerEntityId = ownerEntityPersistHistoricalAfterEvm34(ownerAddress);
        final var autoRenewAddress = toAddress(1078);
        fungibleTokenPersistHistorical(
                ownerEntityId,
                KEY_PROTO,
                tokenAddress,
                autoRenewAddress,
                9999999999999L,
                TokenPauseStatusEnum.UNPAUSED,
                true,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
        // When
        final var result = isStatic
                ? contract.call_decimals(tokenAddress.toHexString())
                : contract.call_decimalsNonStatic(tokenAddress.toHexString());
        // Then
        assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
    }
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void decimalsAfterEvmV34(final boolean isStatic) throws Exception {
        // Given
        recordFileAfterEvmV34Persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        final var tokenAddress = toAddress(1062);
        final var ownerAddress = toAddress(1065);
        final var ownerEntityId = ownerEntityPersistHistorical(ownerAddress);
        final var autoRenewAddress = toAddress(1078);
        fungibleTokenPersistHistorical(
                ownerEntityId,
                KEY_PROTO,
                tokenAddress,
                autoRenewAddress,
                9999999999999L,
                TokenPauseStatusEnum.PAUSED,
                true,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
        // When
        final var result = isStatic
                ? contract.call_decimals(tokenAddress.toHexString()).send()
                : contract.call_decimalsNonStatic(tokenAddress.toHexString()).send();
        // Then
        assertThat(result).isEqualTo(BigInteger.valueOf(12));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void totalSupplyBeforeEvmV34(final boolean isStatic) {
        // Given
        recordFileBeforeEvmV34Persist();
        recordFileAfterEvmV34Persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK - 1)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd()));
        final var tokenAddress = toAddress(1062);
        final var ownerAddress = toAddress(1065);
        final var ownerEntityPersist = ownerEntityPersistHistorical(ownerAddress);
        final var autoRenewAddress = toAddress(1078);
        fungibleTokenPersistHistorical(
                ownerEntityPersist,
                KEY_PROTO,
                tokenAddress,
                autoRenewAddress,
                9999999999999L,
                TokenPauseStatusEnum.PAUSED,
                true,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        balancePersistHistorical(tokenAddress, Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()), ownerAddress, 12L);
        final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
        // When
        final var result = isStatic
                ? contract.call_totalSupply(tokenAddress.toHexString())
                : contract.call_totalSupplyNonStatic(tokenAddress.toHexString());
        // Then
        assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
    }
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void totalSupplyAfterEvmV34(final boolean isStatic) throws Exception {
        // Given
        recordFileAfterEvmV34Persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        final var tokenAddress = toAddress(1062);
        final var ownerAddress = toAddress(1065);
        final var ownerEntityPersist = ownerEntityPersistHistorical(ownerAddress);
        final var autoRenewAddress = toAddress(1078);
        fungibleTokenPersistHistorical(
                ownerEntityPersist,
                KEY_PROTO,
                tokenAddress,
                autoRenewAddress,
                9999999999999L,
                TokenPauseStatusEnum.PAUSED,
                true,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        balancePersistHistorical(tokenAddress, Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()), ownerAddress, 12L);
        final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
        // When
        final var result = isStatic
                ? contract.call_totalSupply(tokenAddress.toHexString()).send()
                : contract.call_totalSupplyNonStatic(tokenAddress.toHexString()).send();
        // Then
        assertThat(result).isEqualTo(BigInteger.valueOf(12345L));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void symbolBeforeEvmV34(final boolean isStatic) {
        // Given
        recordFileBeforeEvmV34Persist();
        recordFileAfterEvmV34Persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK - 1)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd()));
        final var tokenAddress = toAddress(1062);
        final var ownerAddress = toAddress(1065);
        final var ownerEntityId = ownerEntityPersistHistoricalAfterEvm34(ownerAddress);
        final var autoRenewAddress = toAddress(1078);
        fungibleTokenPersistHistorical(
                ownerEntityId,
                KEY_PROTO,
                tokenAddress,
                autoRenewAddress,
                9999999999999L,
                TokenPauseStatusEnum.UNPAUSED,
                true,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
        // When
        final var result = isStatic
                ? contract.call_symbol(tokenAddress.toHexString())
                : contract.call_symbolNonStatic(tokenAddress.toHexString());
        // Then
        assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void symbolAfterEvmV34(final boolean isStatic) throws Exception {
        // Given
        recordFileAfterEvmV34Persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        final var tokenAddress = toAddress(1062);
        final var ownerAddress = toAddress(1065);
        final var ownerEntityId = ownerEntityPersistHistoricalAfterEvm34(ownerAddress);
        final var autoRenewAddress = toAddress(1078);
        fungibleTokenPersistHistorical(
                ownerEntityId,
                KEY_PROTO,
                tokenAddress,
                autoRenewAddress,
                9999999999999L,
                TokenPauseStatusEnum.PAUSED,
                true,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
        // When
        final var result = isStatic
                ? contract.call_symbol(tokenAddress.toHexString()).send()
                : contract.call_symbolNonStatic(tokenAddress.toHexString()).send();
        // Then
        assertThat(result).isEqualTo("HBAR");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void balanceOfBeforeEvmV34(final boolean isStatic) {
        // Given
    recordFileBeforeEvmV34Persist();
        recordFileAfterEvmV34Persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK - 1)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd()));
        final var tokenAddress = toAddress(1062);
        final var tokenEntity = entityIdFromEvmAddress(tokenAddress);
        final var autoRenewAddress = toAddress(1078);
        final var senderAddress = toAddress(1014);
        final var senderEntityPersist = senderEntityPersistHistorical(senderAddress);
        fungibleTokenPersistHistorical(
                senderEntityPersist,
                KEY_PROTO,
                tokenAddress,
                autoRenewAddress,
                9999999999999L,
                TokenPauseStatusEnum.PAUSED,
                true,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        tokenAccountPersistHistorical(senderEntityPersist, tokenEntity, TokenFreezeStatusEnum.FROZEN);
        final var balance = 10L;
        balancePersistHistorical(tokenAddress, Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()), senderAddress, balance);
        final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
        // When
        final var result = isStatic
                ? contract.call_balanceOf(tokenAddress.toHexString(), senderAddress.toHexString())
                : contract.call_balanceOfNonStatic(tokenAddress.toHexString(), senderAddress.toHexString());
        // Then
        assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
    }
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void balanceOfAfterEvmV34(final boolean isStatic) throws Exception {
        // Given
        recordFileAfterEvmV34Persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        final var tokenAddress = toAddress(1062);
        final var tokenEntity = entityIdFromEvmAddress(tokenAddress);
        final var autoRenewAddress = toAddress(1078);
        final var senderAddress = toAddress(1014);
        final var senderEntityPersist = senderEntityPersistHistorical(senderAddress);
        fungibleTokenPersistHistorical(
                senderEntityPersist,
                KEY_PROTO,
                tokenAddress,
                autoRenewAddress,
                9999999999999L,
                TokenPauseStatusEnum.PAUSED,
                true,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        tokenAccountPersistHistorical(senderEntityPersist, tokenEntity, TokenFreezeStatusEnum.FROZEN);
        final var balance = 10L;
        balancePersistHistorical(tokenAddress, Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()), senderAddress, balance);
        final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
        // When
        final var result = isStatic
                ? contract.call_balanceOf(tokenAddress.toHexString(), senderAddress.toHexString()).send()
                : contract.call_balanceOfNonStatic(tokenAddress.toHexString(), senderAddress.toHexString()).send();
        // Then
        assertThat(result).isEqualTo(BigInteger.valueOf(balance));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void balanceOfBeforeEvmV34WithAlias(final boolean isStatic) {
        // Given
    recordFileBeforeEvmV34Persist();
        recordFileAfterEvmV34Persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK - 1)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd()));
        final var tokenAddress = toAddress(1062);
        final var tokenEntity = entityIdFromEvmAddress(tokenAddress);
        final var autoRenewAddress = toAddress(1078);
        final var senderAddress = toAddress(1014);
        final var senderEntityPersist = senderEntityPersistHistorical(senderAddress);
        fungibleTokenPersistHistorical(
                senderEntityPersist,
                KEY_PROTO,
                tokenAddress,
                autoRenewAddress,
                9999999999999L,
                TokenPauseStatusEnum.PAUSED,
                true,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        tokenAccountPersistHistorical(senderEntityPersist, tokenEntity, TokenFreezeStatusEnum.FROZEN);
        final var balance = 10L;
        balancePersistHistorical(tokenAddress, Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()), senderAddress, balance);
        final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
        // When
        final var result = isStatic
                ? contract.call_balanceOf(tokenAddress.toHexString(), SENDER_ALIAS.toHexString())
                : contract.call_balanceOfNonStatic(tokenAddress.toHexString(), SENDER_ALIAS.toHexString());
        // Then
        assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void balanceOfAfterEvmV34WithAlias(final boolean isStatic) throws Exception {
        // Given
        recordFileAfterEvmV34Persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        final var tokenAddress = toAddress(1062);
        final var tokenEntity = entityIdFromEvmAddress(tokenAddress);
        final var autoRenewAddress = toAddress(1078);
        final var senderAddress = toAddress(1014);
        final var senderEntityPersist = senderEntityPersistHistorical(senderAddress);
        fungibleTokenPersistHistorical(
                senderEntityPersist,
                KEY_PROTO,
                tokenAddress,
                autoRenewAddress,
                9999999999999L,
                TokenPauseStatusEnum.PAUSED,
                true,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        tokenAccountPersistHistorical(senderEntityPersist, tokenEntity, TokenFreezeStatusEnum.FROZEN);
        final var balance = 10L;
        balancePersistHistorical(tokenAddress, Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()), senderAddress, balance);
        final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
        // When
        final var result = isStatic
                ? contract.call_balanceOf(tokenAddress.toHexString(), SENDER_ALIAS.toHexString()).send()
                : contract.call_balanceOfNonStatic(tokenAddress.toHexString(), SENDER_ALIAS.toHexString()).send();
        // Then
        assertThat(result).isEqualTo(BigInteger.valueOf(balance));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void nameBeforeEvmV34(final boolean isStatic) {
        // Given
        recordFileBeforeEvmV34Persist();
        recordFileAfterEvmV34Persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK - 1)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd()));
        final var tokenAddress = toAddress(1062);
        final var ownerAddress = toAddress(1065);
        final var ownerEntityId = ownerEntityPersistHistoricalAfterEvm34(ownerAddress);
        final var autoRenewAddress = toAddress(1078);
        fungibleTokenPersistHistorical(
                ownerEntityId,
                KEY_PROTO,
                tokenAddress,
                autoRenewAddress,
                9999999999999L,
                TokenPauseStatusEnum.PAUSED,
                true,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
        // When
        final var result = isStatic
                ? contract.call_name(tokenAddress.toHexString())
                : contract.call_nameNonStatic(tokenAddress.toHexString());
        // Then
        assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
    }
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void nameAfterEvmV34(final boolean isStatic) throws Exception {
        // Given
        recordFileAfterEvmV34Persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        final var tokenAddress = toAddress(1062);
        final var ownerAddress = toAddress(1065);
        final var ownerEntityId = ownerEntityPersistHistoricalAfterEvm34(ownerAddress);
        final var autoRenewAddress = toAddress(1078);
        fungibleTokenPersistHistorical(
                ownerEntityId,
                KEY_PROTO,
                tokenAddress,
                autoRenewAddress,
                9999999999999L,
                TokenPauseStatusEnum.PAUSED,
                true,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
        // When
        final var result = isStatic
                ? contract.call_name(tokenAddress.toHexString()).send()
                : contract.call_nameNonStatic(tokenAddress.toHexString()).send();
        // Then
        assertThat(result).isEqualTo("Hbars");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void ownerOfBeforeEvmV34(final boolean isStatic) {
        // Given
        recordFileBeforeEvmV34Persist();
        recordFileAfterEvmV34Persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK - 1)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd()));
        final var tokenAddress = toAddress(1063);
        final var ownerAddress = toAddress(1065);
        final var ownerEntityId = ownerEntityPersistHistorical(ownerAddress);
        final var autoRenewAddress = toAddress(1078);
        final var spenderAddress = toAddress(1041);
        final var spenderEntityPersist = spenderEntityPersistHistorical(spenderAddress);
        nftPersistHistorical(
                tokenAddress,
                autoRenewAddress,
                ownerEntityId,
                spenderEntityPersist,
                ownerEntityId,
                KEY_PROTO,
                TokenPauseStatusEnum.PAUSED,
                true,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        tokenAccountPersistHistorical(ownerEntityId, entityIdFromEvmAddress(tokenAddress), TokenFreezeStatusEnum.FROZEN);
        final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
        // When
        final var result = isStatic
                ? contract.call_getOwnerOf(tokenAddress.toHexString(), BigInteger.valueOf(1L))
                : contract.call_getOwnerOfNonStatic(tokenAddress.toHexString(), BigInteger.valueOf(1L));
        // Then
        assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
    }
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void ownerOfAfterEvmV34(final boolean isStatic) throws Exception {
        // Given
        recordFileAfterEvmV34Persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        final var tokenAddress = toAddress(1063);
        final var ownerAddress = toAddress(1065);
        final var ownerEntityId = ownerEntityPersistHistorical(ownerAddress);
        final var autoRenewAddress = toAddress(1078);
        final var spenderAddress = toAddress(1041);
        final var spenderEntityPersist = spenderEntityPersistHistorical(spenderAddress);
        nftPersistHistorical(
                tokenAddress,
                autoRenewAddress,
                ownerEntityId,
                spenderEntityPersist,
                ownerEntityId,
                KEY_PROTO,
                TokenPauseStatusEnum.PAUSED,
                true,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        tokenAccountPersistHistorical(ownerEntityId, entityIdFromEvmAddress(tokenAddress), TokenFreezeStatusEnum.FROZEN);
        final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
        // When
        final var result = isStatic
                ? contract.call_getOwnerOf(tokenAddress.toHexString(), BigInteger.valueOf(1L)).send()
                : contract.call_getOwnerOfNonStatic(tokenAddress.toHexString(), BigInteger.valueOf(1L)).send();
        // Then
        assertThat(result).isEqualTo(ownerAddress.toHexString());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void emptyOwnerOfBeforeEvmV34(final boolean isStatic) {
        // Given
    recordFileBeforeEvmV34Persist();
        recordFileAfterEvmV34Persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK - 1)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd()));
        final var tokenAddress = toAddress(1063);
        final var ownerAddress = toAddress(1065);
        final var ownerEntityId = ownerEntityPersistHistoricalAfterEvm34(ownerAddress);
        final var autoRenewAddress = toAddress(1078);
        final var spenderAddress = toAddress(1041);
        final var spenderEntityPersist = spenderEntityPersistHistorical(spenderAddress);
        nftPersistHistorical(
                tokenAddress,
                autoRenewAddress,
                ownerEntityId,
                spenderEntityPersist,
                ownerEntityId,
                KEY_PROTO,
                TokenPauseStatusEnum.PAUSED,
                true,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
        // When
        final var result = isStatic
                ? contract.call_getOwnerOf(tokenAddress.toHexString(), BigInteger.valueOf(2L))
                : contract.call_getOwnerOfNonStatic(tokenAddress.toHexString(), BigInteger.valueOf(2L));
        // Then
        assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
    }
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void emptyOwnerOfAfterEvmV34(final boolean isStatic) throws Exception {
        // Given
        recordFileAfterEvmV34Persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        final var tokenAddress = toAddress(1063);
        final var ownerAddress = toAddress(1065);
        final var ownerEntityId = ownerEntityPersistHistoricalAfterEvm34(ownerAddress);
        final var autoRenewAddress = toAddress(1078);
        final var spenderAddress = toAddress(1041);
        final var spenderEntityPersist = spenderEntityPersistHistorical(spenderAddress);
        nftPersistHistorical(
                tokenAddress,
                autoRenewAddress,
                ownerEntityId,
                spenderEntityPersist,
                ownerEntityId,
                KEY_PROTO,
                TokenPauseStatusEnum.PAUSED,
                true,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
        // When
        final var result = isStatic
                ? contract.call_getOwnerOf(tokenAddress.toHexString(), BigInteger.valueOf(2L)).send()
                : contract.call_getOwnerOfNonStatic(tokenAddress.toHexString(), BigInteger.valueOf(2L)).send();
        // Then
        assertThat(result).isEqualTo(Address.ZERO.toHexString());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void tokenURIBeforeEvmV34(final boolean isStatic) {
        // Given
    recordFileBeforeEvmV34Persist();
        recordFileAfterEvmV34Persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK - 1)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd()));
        final var tokenAddress = toAddress(1063);
        final var ownerAddress = toAddress(1065);
        final var ownerEntityId = ownerEntityPersistHistoricalAfterEvm34(ownerAddress);
        final var autoRenewAddress = toAddress(1078);
        final var spenderAddress = toAddress(1041);
        final var spenderEntityPersist = spenderEntityPersistHistorical(spenderAddress);
        nftPersistHistorical(
                tokenAddress,
                autoRenewAddress,
                ownerEntityId,
                spenderEntityPersist,
                ownerEntityId,
                KEY_PROTO,
                TokenPauseStatusEnum.PAUSED,
                true,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
        // When
        final var result = isStatic
                ? contract.call_tokenURI(tokenAddress.toHexString(), BigInteger.valueOf(1L))
                : contract.call_tokenURINonStatic(tokenAddress.toHexString(), BigInteger.valueOf(1L));
        // Then
        assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
    }
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void tokenURIAfterEvmV34(final boolean isStatic) throws Exception {
        // Given
        recordFileAfterEvmV34Persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        final var tokenAddress = toAddress(1063);
        final var ownerAddress = toAddress(1065);
        final var ownerEntityId = ownerEntityPersistHistoricalAfterEvm34(ownerAddress);
        final var autoRenewAddress = toAddress(1078);
        final var spenderAddress = toAddress(1041);
        final var spenderEntityPersist = spenderEntityPersistHistorical(spenderAddress);
        nftPersistHistorical(
                tokenAddress,
                autoRenewAddress,
                ownerEntityId,
                spenderEntityPersist,
                ownerEntityId,
                KEY_PROTO,
                TokenPauseStatusEnum.PAUSED,
                true,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
        // When
        final var result = isStatic
                ? contract.call_tokenURI(tokenAddress.toHexString(), BigInteger.valueOf(1L)).send()
                : contract.call_tokenURINonStatic(tokenAddress.toHexString(), BigInteger.valueOf(1L)).send();
        // Then
        assertThat(result).isEqualTo("NFT_METADATA_URI");
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

    private void recordFileBeforeEvmV34Persist(){
        recordFileBeforeEvm34 = domainBuilder
                .recordFile()
                .customize(f -> f.index(EVM_V_34_BLOCK - 1))
                .persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK - 1)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd()));
    }

    private void recordFileAfterEvmV34Persist(){
        recordFileAfterEvm34 = domainBuilder
                .recordFile()
                .customize(f -> f.index(EVM_V_34_BLOCK))
                .persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
    }

    private EntityId ownerEntityPersistHistoricalAfterEvm34(Address address) {
        final var ownerEntityId = entityIdFromEvmAddress(address);
        domainBuilder
                .entity()
                .customize(e -> e.id(ownerEntityId.getId())
                        .num(ownerEntityId.getNum())
                        .alias(toEvmAddress(ownerEntityId))
                        .timestampRange(Range.closedOpen(
                                recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd())))
                .persist();
        return ownerEntityId;
    }

    private EntityId ownerEntityPersistHistoricalBeforeEvm34(Address address) {
        final var ownerEntityId = entityIdFromEvmAddress(address);
        domainBuilder
                .entity()
                .customize(e -> e.id(ownerEntityId.getId())
                        .num(ownerEntityId.getNum())
                        .alias(toEvmAddress(ownerEntityId))
                        .timestampRange(Range.closedOpen(
                                recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd())))
                .persist();
        return ownerEntityId;
    }


    private EntityId nftPersistHistorical(
            final Address nftAddress,
            final Address autoRenewAddress,
            final EntityId ownerEntityId,
            final EntityId spenderEntityId,
            final EntityId treasuryId,
            final byte[] key,
            final TokenPauseStatusEnum pauseStatus,
            final boolean freezeDefault,
            final Range<Long> historicalBlock) {
        final var nftEntityId = entityIdFromEvmAddress(nftAddress);
        final var autoRenewEntityId = entityIdFromEvmAddress(autoRenewAddress);
        final var nftEvmAddress = toEvmAddress(nftEntityId);
        final var ownerEntity = EntityId.of(ownerEntityId.getId());

        domainBuilder
                .entity()
                .customize(e -> e.id(nftEntityId.getId())
                        .autoRenewAccountId(autoRenewEntityId.getId())
                        .num(nftEntityId.getNum())
                        .evmAddress(nftEvmAddress)
                        .type(TOKEN)
                        .balance(1500L)
                        .key(key)
                        .expirationTimestamp(9999999999999L)
                        .memo("TestMemo")
                        .deleted(false)
                        .timestampRange(historicalBlock))
                .persist();

        domainBuilder
                .tokenHistory()
                .customize(t -> t.tokenId(nftEntityId.getId())
                        .treasuryAccountId(treasuryId)
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .kycKey(key)
                        .freezeDefault(freezeDefault)
                        .feeScheduleKey(key)
                        .totalSupply(2L)
                        .maxSupply(2_000_000_000L)
                        .name("Hbars")
                        .supplyType(TokenSupplyTypeEnum.FINITE)
                        .freezeKey(key)
                        .pauseKey(key)
                        .pauseStatus(pauseStatus)
                        .wipeKey(key)
                        .supplyKey(key)
                        .symbol("HBAR")
                        .wipeKey(key)
                        .decimals(0)
                        .timestampRange(historicalBlock))
                .persist();

        domainBuilder
                .nftHistory()
                .customize(n -> n.accountId(spenderEntityId)
                        .createdTimestamp(1475067194949034022L)
                        .serialNumber(1L)
                        .metadata("NFT_METADATA_URI".getBytes())
                        .accountId(ownerEntity)
                        .tokenId(nftEntityId.getId())
                        .deleted(false)
                        .timestampRange(
                                Range.openClosed(historicalBlock.lowerEndpoint(), historicalBlock.upperEndpoint() + 1)))
                .persist();

        domainBuilder
                .nftHistory()
                .customize(n -> n.accountId(spenderEntityId)
                        .spender(spenderEntityId)
                        .createdTimestamp(1475067194949034022L)
                        .serialNumber(3L)
                        .metadata("NFT_METADATA_URI".getBytes())
                        //
                        .tokenId(nftEntityId.getId())
                        .deleted(false)
                        .timestampRange(Range.openClosed(
                                historicalBlock.lowerEndpoint() - 1, historicalBlock.upperEndpoint() + 1)))
                .persist();

        // nft table
        domainBuilder
                .nft()
                .customize(n -> n.accountId(spenderEntityId)
                        .createdTimestamp(1475067194949034022L)
                        .serialNumber(1L)
                        .metadata("NFT_METADATA_URI".getBytes())
                        .accountId(ownerEntity)
                        .tokenId(nftEntityId.getId())
                        .deleted(false)
                        .timestampRange(Range.atLeast(historicalBlock.upperEndpoint() + 1)))
                .persist();

        domainBuilder
                .nft()
                .customize(n -> n.accountId(spenderEntityId)
                        .createdTimestamp(1475067194949034022L)
                        .serialNumber(3L)
                        .metadata("NFT_METADATA_URI".getBytes())
                        .accountId(ownerEntity)
                        .tokenId(nftEntityId.getId())
                        .deleted(false)
                        .timestampRange(Range.atLeast(historicalBlock.upperEndpoint() + 1)))
                .persist();

        return nftEntityId;
    }

    private void nftAllowancePersistHistorical(
            final EntityId tokenEntityId,
            final EntityId payerAccountId,
            final EntityId ownerEntityId,
            final EntityId spenderEntityId) {
        domainBuilder
                .nftAllowanceHistory()
                .customize(a -> a.tokenId(tokenEntityId.getId())
                        .payerAccountId(payerAccountId)
                        .owner(ownerEntityId.getNum())
                        .spender(spenderEntityId.getNum())
                        .approvedForAll(true)
                        .timestampRange(Range.closedOpen(
                                recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd())))
                .persist();
    }

    private void fungibleTokenPersistHistorical(
            final EntityId treasuryId,
            final byte[] key,
            final Address tokenAddress,
            final Address autoRenewAddress,
            final long tokenExpiration,
            final TokenPauseStatusEnum pauseStatus,
            final boolean freezeDefault,
            final Range<Long> historicalBlock) {
        final var tokenEntityId = entityIdFromEvmAddress(tokenAddress);
        final var autoRenewEntityId = entityIdFromEvmAddress(autoRenewAddress);
        final long lowerTimestamp = historicalBlock.lowerEndpoint();

        domainBuilder
                .entity()
                .customize(e -> e.id(tokenEntityId.getId())
                        .autoRenewAccountId(autoRenewEntityId.getId())
                        .num(tokenEntityId.getNum())
                        .evmAddress(tokenAddress.toArrayUnsafe())
                        .type(TOKEN)
                        .balance(1500L)
                        .key(key)
                        .expirationTimestamp(tokenExpiration)
                        .memo("TestMemo")
                        .timestampRange(Range.atLeast(lowerTimestamp))
                        .deleted(false))
                .persist();

        domainBuilder
                .tokenHistory()
                .customize(t -> t.tokenId(tokenEntityId.getId())
                        .treasuryAccountId(treasuryId)
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .kycKey(key)
                        .freezeDefault(freezeDefault)
                        .feeScheduleKey(key)
                        .supplyType(TokenSupplyTypeEnum.INFINITE)
                        .maxSupply(2525L)
                        .initialSupply(10_000_000L)
                        .name("Hbars")
                        .totalSupply(12345L)
                        .decimals(12)
                        .wipeKey(key)
                        .freezeKey(key)
                        .pauseStatus(pauseStatus)
                        .pauseKey(key)
                        .supplyKey(key)
                        .symbol("HBAR")
                        .timestampRange(Range.openClosed(lowerTimestamp, historicalBlock.upperEndpoint() + 1)))
                .persist();
    }

    private void fungibleTokenAllowancePersistHistorical(
            final EntityId tokenEntityId,
            final EntityId payerAccountId,
            final EntityId ownerEntityId,
            final EntityId spenderEntityId,
            final long amount) {
        domainBuilder
                .tokenAllowanceHistory()
                .customize(a -> a.tokenId(tokenEntityId.getId())
                        .payerAccountId(payerAccountId)
                        .owner(ownerEntityId.getNum())
                        .spender(spenderEntityId.getNum())
                        .amount(amount)
                        .amountGranted(amount)
                        .timestampRange(Range.closed(
                                recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd())))
                .persist();
    }

    private EntityId balancePersistHistorical(final Address tokenAddress, final Range<Long> historicalBlock, Address senderAddress, Long balance) {
        final var tokenEntityId = entityIdFromEvmAddress(tokenAddress);
        final var accountId = entityIdFromEvmAddress(senderAddress);
        final var tokenId = entityIdFromEvmAddress(tokenAddress);
        // hardcoded treasury account id is mandatory
        final long lowerTimestamp = historicalBlock.lowerEndpoint();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(lowerTimestamp, NETWORK_TREASURY_ACCOUNT_ID)))
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

        return tokenEntityId;
    }

    private EntityId senderEntityPersistHistorical(Address senderAddress) {
        final var senderEntityId = entityIdFromEvmAddress(senderAddress);
        domainBuilder
                .entity()
                .customize(e -> e.id(senderEntityId.getId())
                        .num(senderEntityId.getNum())
                        .evmAddress(SENDER_ALIAS.toArray())
                        .deleted(false)
                        .alias(SENDER_PUBLIC_KEY.toByteArray())
                        .balance(10000 * 100_000_000L)
                        .createdTimestamp(recordFileAfterEvm34.getConsensusStart())
                        .timestampRange(Range.closedOpen(
                                recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd())))
                .persist();
        return senderEntityId;
    }

    private EntityId spenderEntityPersistHistorical(Address spenderAddress) {
        final var spenderEntityId = entityIdFromEvmAddress(spenderAddress);
        domainBuilder
                .entity()
                .customize(e -> e.id(spenderEntityId.getId())
                        .num(spenderEntityId.getNum())
                        .evmAddress(SPENDER_ALIAS.toArray())
                        .alias(SPENDER_PUBLIC_KEY.toByteArray())
                        .deleted(false)
                        .createdTimestamp(recordFileAfterEvm34.getConsensusStart())
                        .timestampRange(Range.closedOpen(
                                recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd())))
                .persist();
        return spenderEntityId;
    }

    private EntityId ownerEntityPersistHistorical(Address ownerAddress) {
        final var ownerEntityId = entityIdFromEvmAddress(ownerAddress);

        domainBuilder
                .entity()
                .customize(e -> e.id(ownerEntityId.getId())
                        .num(ownerEntityId.getNum())
                        .evmAddress(null)
                        .alias(toEvmAddress(ownerEntityId))
                        .balance(20000L)
                        .timestampRange(Range.closedOpen(
                                recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd())))
                .persist();

        return ownerEntityId;
    }

    private Entity accountPersistWithBalance(final long balance) {
        final var entity = domainBuilder
                .entity()
                .customize(e -> e.evmAddress(null).balance(balance))
                .persist();

        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(entity.getCreatedTimestamp(), EntityId.of(2)))
                        .balance(balance))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(entity.getCreatedTimestamp(), entity.toEntityId()))
                        .balance(balance))
                .persist();

        return entity;
    }

    private void tokenAccountPersistHistorical(
            final EntityId senderEntityId, final EntityId tokenEntityId, final TokenFreezeStatusEnum freezeStatus) {
        domainBuilder
                .tokenAccountHistory()
                .customize(e -> e.freezeStatus(freezeStatus)
                        .accountId(senderEntityId.getId())
                        .tokenId(tokenEntityId.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .associated(true)
                        .timestampRange(Range.closedOpen(
                                recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd())))
                .persist();
    }
}
