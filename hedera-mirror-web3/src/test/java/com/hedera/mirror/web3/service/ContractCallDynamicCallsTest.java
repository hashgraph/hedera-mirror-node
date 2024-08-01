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
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.entityIdFromEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.service.ContractCallTestUtil.ESTIMATE_GAS_ERROR_MESSAGE;
import static com.hedera.mirror.web3.service.ContractCallTestUtil.SENDER_ALIAS;
import static com.hedera.mirror.web3.service.ContractCallTestUtil.SENDER_PUBLIC_KEY;
import static com.hedera.mirror.web3.service.ContractCallTestUtil.SPENDER_ALIAS;
import static com.hedera.mirror.web3.service.ContractCallTestUtil.SPENDER_PUBLIC_KEY;
import static com.hedera.mirror.web3.service.ContractCallTestUtil.isWithinExpectedGasRange;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.web3j.TestWeb3jService;
import com.hedera.mirror.web3.web3j.TestWeb3jService.Web3jTestConfiguration;
import com.hedera.mirror.web3.web3j.generated.DynamicEthCalls;
import com.hedera.mirror.web3.web3j.generated.DynamicEthCalls.AccountAmount;
import com.hedera.mirror.web3.web3j.generated.DynamicEthCalls.NftTransfer;
import com.hedera.mirror.web3.web3j.generated.DynamicEthCalls.TokenTransferList;
import com.hedera.mirror.web3.web3j.generated.DynamicEthCalls.TransferList;
import java.math.BigInteger;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.Import;

@Import(Web3jTestConfiguration.class)
@SuppressWarnings("unchecked")
@RequiredArgsConstructor
@TestInstance(PER_CLASS)
class ContractCallDynamicCallsTest extends Web3IntegrationTest {

    private final TestWeb3jService testWeb3jService;

    @BeforeEach
    void setup() {
        recordFilePersist();
    }

    @AfterEach
    void cleanup() {
        testWeb3jService.setEstimateGas(false);
        testWeb3jService.setReceiver(null);
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                FUNGIBLE_COMMON,        100,
                NON_FUNGIBLE_UNIQUE,    0,      NftMetadata
                """)
    void mintTokenGetTotalSupplyAndBalanceOfTreasuryEthCall(
            final TokenTypeEnum tokenType, final long amount, final String metadata) {
        // Given
        final var treasuryEntityId = accountPersist();
        final var treasuryAddress = toAddress(treasuryEntityId.getId());

        final var tokenEntity = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                ? fungibleTokenPersist(treasuryEntityId)
                : nftPersist(treasuryEntityId);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());

        tokenAccountPersist(entityIdFromEvmAddress(tokenAddress), treasuryEntityId);

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);

        // When
        final var functionCall = contract.send_mintTokenGetTotalSupplyAndBalanceOfTreasury(
                tokenAddress.toHexString(),
                BigInteger.valueOf(amount),
                metadata == null ? List.of() : List.of(metadata.getBytes()),
                treasuryAddress.toHexString());

        // Then
        assertDoesNotThrow(() -> {
            functionCall.send();
        });
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                FUNGIBLE_COMMON,        100,
                NON_FUNGIBLE_UNIQUE,    0,      NftMetadata
                """)
    void mintTokenGetTotalSupplyAndBalanceOfTreasuryEstimateGas(
            final TokenTypeEnum tokenType, final long amount, final String metadata) throws Exception {
        // Given
        final var treasuryEntityId = accountPersist();
        final var treasuryAddress = toAddress(treasuryEntityId.getId());

        final var tokenEntity = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                ? fungibleTokenPersist(treasuryEntityId)
                : nftPersist(treasuryEntityId);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());

        tokenAccountPersist(entityIdFromEvmAddress(tokenAddress), treasuryEntityId);

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);

        // When
        final var functionCall = contract.send_mintTokenGetTotalSupplyAndBalanceOfTreasury(
                tokenAddress.toHexString(),
                BigInteger.valueOf(amount),
                metadata == null ? List.of() : List.of(metadata.getBytes()),
                treasuryAddress.toHexString());

        testWeb3jService.setEstimateGas(true);
        final var estimateGasUsedResult = functionCall.send().getGasUsed().longValue();

        testWeb3jService.setEstimateGas(false);
        final var actualGasUsed =
                gasUsedAfterExecution(getContractExecutionParameters(functionCall, contract, treasuryAddress));

        // Then
        assertThat(isWithinExpectedGasRange(estimateGasUsedResult, actualGasUsed))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimateGasUsedResult, actualGasUsed)
                .isTrue();
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                FUNGIBLE_COMMON,        1,    0
                NON_FUNGIBLE_UNIQUE,    0,    1
                """)
    void burnTokenGetTotalSupplyAndBalanceOfTreasuryEthCall(
            final TokenTypeEnum tokenType, final long amount, final long serialNumber) {
        // Given
        final var treasuryEntityId = accountPersist();
        final var treasuryAddress = toAddress(treasuryEntityId.getId());

        final var tokenEntity = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                ? fungibleTokenPersist(treasuryEntityId)
                : nftPersist(treasuryEntityId);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());

        tokenAccountPersist(entityIdFromEvmAddress(tokenAddress), treasuryEntityId);

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);

        // When
        final var functionCall = contract.send_burnTokenGetTotalSupplyAndBalanceOfTreasury(
                tokenAddress.toHexString(),
                BigInteger.valueOf(amount),
                serialNumber == 0 ? List.of() : List.of(BigInteger.valueOf(serialNumber)),
                treasuryAddress.toHexString());

        // Then
        assertDoesNotThrow(() -> {
            functionCall.send();
        });
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                FUNGIBLE_COMMON,        1,    0
                NON_FUNGIBLE_UNIQUE,    0,    1
                """)
    void burnTokenGetTotalSupplyAndBalanceOfTreasuryEthEstimateGas(
            final TokenTypeEnum tokenType, final long amount, final long serialNumber) throws Exception {
        // Given
        final var treasuryEntityId = accountPersist();
        final var treasuryAddress = toAddress(treasuryEntityId.getId());

        final var tokenEntity = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                ? fungibleTokenPersist(treasuryEntityId)
                : nftPersist(treasuryEntityId);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());

        tokenAccountPersist(entityIdFromEvmAddress(tokenAddress), treasuryEntityId);

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);

        // When
        final var functionCall = contract.send_burnTokenGetTotalSupplyAndBalanceOfTreasury(
                tokenAddress.toHexString(),
                BigInteger.valueOf(amount),
                serialNumber == 0 ? List.of() : List.of(BigInteger.valueOf(serialNumber)),
                treasuryAddress.toHexString());

        testWeb3jService.setEstimateGas(true);
        final var estimateGasUsedResult = functionCall.send().getGasUsed().longValue();

        testWeb3jService.setEstimateGas(false);
        final var actualGasUsed =
                gasUsedAfterExecution(getContractExecutionParameters(functionCall, contract, treasuryAddress));

        // Then
        assertThat(isWithinExpectedGasRange(estimateGasUsedResult, actualGasUsed))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimateGasUsedResult, actualGasUsed)
                .isTrue();
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                FUNGIBLE_COMMON,        1,    0
                NON_FUNGIBLE_UNIQUE,    0,    1
                """)
    void wipeTokenGetTotalSupplyAndBalanceOfTreasuryEthCall(
            final TokenTypeEnum tokenType, final long amount, final long serialNumber) {
        // Given
        final var treasuryEntityId = accountPersist();
        final var senderEntityId = accountPersist();
        final var senderAddress = toAddress(senderEntityId.getId());

        final var tokenEntity = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                ? fungibleTokenPersist(treasuryEntityId)
                : nftPersist(treasuryEntityId, senderEntityId);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());

        tokenAccountPersist(entityIdFromEvmAddress(tokenAddress), treasuryEntityId);
        tokenAccountPersist(entityIdFromEvmAddress(tokenAddress), senderEntityId);

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);

        // When
        final var functionCall = contract.send_wipeTokenGetTotalSupplyAndBalanceOfTreasury(
                tokenAddress.toHexString(),
                BigInteger.valueOf(amount),
                serialNumber == 0 ? List.of() : List.of(BigInteger.valueOf(serialNumber)),
                senderAddress.toHexString());

        // Then
        assertDoesNotThrow(() -> {
            functionCall.send();
        });
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                FUNGIBLE_COMMON,        1,    0
                NON_FUNGIBLE_UNIQUE,    0,    1
                """)
    void wipeTokenGetTotalSupplyAndBalanceOfTreasuryEstimateGas(
            final TokenTypeEnum tokenType, final long amount, final long serialNumber) throws Exception {
        // Given
        final var treasuryEntityId = accountPersist();
        final var treasuryAddress = toAddress(treasuryEntityId.getId());
        final var senderEntityId = accountPersist();
        final var senderAddress = toAddress(senderEntityId.getId());

        final var tokenEntity = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                ? fungibleTokenPersist(treasuryEntityId)
                : nftPersist(treasuryEntityId, senderEntityId);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());

        tokenAccountPersist(entityIdFromEvmAddress(tokenAddress), treasuryEntityId);
        tokenAccountPersist(entityIdFromEvmAddress(tokenAddress), senderEntityId);

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);

        // When
        final var functionCall = contract.send_wipeTokenGetTotalSupplyAndBalanceOfTreasury(
                tokenAddress.toHexString(),
                BigInteger.valueOf(amount),
                serialNumber == 0 ? List.of() : List.of(BigInteger.valueOf(serialNumber)),
                senderAddress.toHexString());

        testWeb3jService.setEstimateGas(true);
        final var estimateGasUsedResult = functionCall.send().getGasUsed().longValue();

        testWeb3jService.setEstimateGas(false);
        final var actualGasUsed =
                gasUsedAfterExecution(getContractExecutionParameters(functionCall, contract, treasuryAddress));

        // Then
        assertThat(isWithinExpectedGasRange(estimateGasUsedResult, actualGasUsed))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimateGasUsedResult, actualGasUsed)
                .isTrue();
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
                FUNGIBLE_COMMON,
                NON_FUNGIBLE_UNIQUE
                """)
    void pauseTokenGetPauseStatusUnpauseGetPauseStatusEthCall(final TokenTypeEnum tokenType) {
        // Given
        final var treasuryEntityId = accountPersist();

        final var tokenEntity = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                ? fungibleTokenPersist(treasuryEntityId)
                : nftPersist(treasuryEntityId);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());

        tokenAccountPersist(entityIdFromEvmAddress(tokenAddress), treasuryEntityId);

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);

        // When
        final var functionCall =
                contract.send_pauseTokenGetPauseStatusUnpauseGetPauseStatus(tokenAddress.toHexString());

        // Then
        assertDoesNotThrow(() -> {
            functionCall.send();
        });
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
                FUNGIBLE_COMMON,
                NON_FUNGIBLE_UNIQUE
                """)
    void pauseTokenGetPauseStatusUnpauseGetPauseStatusEstimateGas(final TokenTypeEnum tokenType) throws Exception {
        // Given
        final var treasuryEntityId = accountPersist();
        final var treasuryAddress = toAddress(treasuryEntityId.getId());

        final var tokenEntity = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                ? fungibleTokenPersist(treasuryEntityId)
                : nftPersist(treasuryEntityId);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());

        tokenAccountPersist(entityIdFromEvmAddress(tokenAddress), treasuryEntityId);

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);

        // When
        final var functionCall =
                contract.send_pauseTokenGetPauseStatusUnpauseGetPauseStatus(tokenAddress.toHexString());

        testWeb3jService.setEstimateGas(true);
        final var estimateGasUsedResult = functionCall.send().getGasUsed().longValue();

        testWeb3jService.setEstimateGas(false);
        final var actualGasUsed =
                gasUsedAfterExecution(getContractExecutionParameters(functionCall, contract, treasuryAddress));

        // Then
        assertThat(isWithinExpectedGasRange(estimateGasUsedResult, actualGasUsed))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimateGasUsedResult, actualGasUsed)
                .isTrue();
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
                FUNGIBLE_COMMON,
                NON_FUNGIBLE_UNIQUE
                """)
    void freezeTokenGetPauseStatusUnpauseGetPauseStatusEthCall(final TokenTypeEnum tokenType) {
        // Given
        final var treasuryEntityId = accountPersist();
        final var treasuryAddress = toAddress(treasuryEntityId.getId());

        final var tokenEntity = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                ? fungibleTokenPersist(treasuryEntityId)
                : nftPersist(treasuryEntityId);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());

        tokenAccountPersist(entityIdFromEvmAddress(tokenAddress), treasuryEntityId);

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);

        // When
        final var functionCall = contract.send_freezeTokenGetPauseStatusUnpauseGetPauseStatus(
                tokenAddress.toHexString(), treasuryAddress.toHexString());

        // Then
        assertDoesNotThrow(() -> {
            functionCall.send();
        });
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
                FUNGIBLE_COMMON,
                NON_FUNGIBLE_UNIQUE
                """)
    void freezeTokenGetPauseStatusUnpauseGetPauseStatusEstimateGas(final TokenTypeEnum tokenType) throws Exception {
        // Given
        final var treasuryEntityId = accountPersist();
        final var treasuryAddress = toAddress(treasuryEntityId.getId());

        final var tokenEntity = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                ? fungibleTokenPersist(treasuryEntityId)
                : nftPersist(treasuryEntityId);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());

        tokenAccountPersist(entityIdFromEvmAddress(tokenAddress), treasuryEntityId);

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);

        // When
        final var functionCall = contract.send_freezeTokenGetPauseStatusUnpauseGetPauseStatus(
                tokenAddress.toHexString(), treasuryAddress.toHexString());

        testWeb3jService.setEstimateGas(true);
        final var estimateGasUsedResult = functionCall.send().getGasUsed().longValue();

        testWeb3jService.setEstimateGas(false);
        final var actualGasUsed =
                gasUsedAfterExecution(getContractExecutionParameters(functionCall, contract, treasuryAddress));

        // Then
        assertThat(isWithinExpectedGasRange(estimateGasUsedResult, actualGasUsed))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimateGasUsedResult, actualGasUsed)
                .isTrue();
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                FUNGIBLE_COMMON,        1,      0
                NON_FUNGIBLE_UNIQUE,    0,      1
                """)
    void associateTokenTransferEthCall(final TokenTypeEnum tokenType, final long amount, final long serialNumber) {
        // Given
        final var treasuryEntityId = accountPersist();
        final var treasuryAddress = toAddress(treasuryEntityId.getId());
        final var senderEntityId = accountPersist();
        final var senderAddress = toAddress(senderEntityId.getId());

        final var tokenEntity = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                ? fungibleTokenPersist(treasuryEntityId, null)
                : nftPersist(treasuryEntityId, treasuryEntityId, treasuryEntityId, null);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());

        tokenAccountPersist(entityIdFromEvmAddress(tokenAddress), treasuryEntityId);

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);

        // When
        final var functionCall = contract.send_associateTokenTransfer(
                tokenAddress.toHexString(),
                treasuryAddress.toHexString(),
                senderAddress.toHexString(),
                BigInteger.valueOf(amount),
                BigInteger.valueOf(serialNumber));

        // Then
        assertDoesNotThrow(() -> {
            functionCall.send();
        });
    }

    @Test
    void associateTokenTransferEthCallFail() {
        // Given
        final var treasuryEntityId = accountPersist();
        final var treasuryAddress = toAddress(treasuryEntityId.getId());
        final var senderEntityId = accountPersist();
        final var senderAddress = toAddress(senderEntityId.getId());

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);

        // When
        final var functionCall = contract.send_associateTokenTransfer(
                toAddress(EntityId.of(21496934L)).toHexString(), // Not existing address
                treasuryAddress.toHexString(),
                senderAddress.toHexString(),
                BigInteger.ZERO,
                BigInteger.ONE);

        // Then
        assertThatThrownBy(functionCall::send)
                .isInstanceOf(MirrorEvmTransactionException.class)
                .satisfies(ex -> {
                    MirrorEvmTransactionException exception = (MirrorEvmTransactionException) ex;
                    assertEquals("Failed to associate tokens", exception.getDetail());
                });
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                FUNGIBLE_COMMON,        1,      0
                NON_FUNGIBLE_UNIQUE,    0,      1
                """)
    void associateTokenTransferEstimateGas(final TokenTypeEnum tokenType, final long amount, final long serialNumber)
            throws Exception {
        // Given
        final var treasuryEntityId = accountPersist();
        final var treasuryAddress = toAddress(treasuryEntityId.getId());
        final var senderEntityId = accountPersist();
        final var senderAddress = toAddress(senderEntityId.getId());

        final var tokenEntity = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                ? fungibleTokenPersist(treasuryEntityId, null)
                : nftPersist(treasuryEntityId, treasuryEntityId, treasuryEntityId, null);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());

        tokenAccountPersist(entityIdFromEvmAddress(tokenAddress), treasuryEntityId);

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);

        // When
        final var functionCall = contract.send_associateTokenTransfer(
                tokenAddress.toHexString(),
                treasuryAddress.toHexString(),
                senderAddress.toHexString(),
                BigInteger.valueOf(amount),
                BigInteger.valueOf(serialNumber));
        // Then
        testWeb3jService.setEstimateGas(true);
        final var estimateGasUsedResult = functionCall.send().getGasUsed().longValue();

        testWeb3jService.setEstimateGas(false);
        final var actualGasUsed =
                gasUsedAfterExecution(getContractExecutionParameters(functionCall, contract, treasuryAddress));

        assertThat(isWithinExpectedGasRange(estimateGasUsedResult, actualGasUsed))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimateGasUsedResult, actualGasUsed)
                .isTrue();
    }

    @Test
    void associateTokenDissociateFailTransferFungibleTokenEthCall() {
        // Given
        final var treasuryEntityId = accountPersist();
        final var ownerEntityId = accountPersist();
        final var ownerAddress = toAddress(ownerEntityId.getId());
        final var senderEntityId = accountPersist();
        final var senderAddress = toAddress(senderEntityId.getId());

        final var tokenEntity = fungibleTokenPersist(treasuryEntityId, null);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);

        // When
        final var functionCall = contract.send_associateTokenDissociateFailTransfer(
                tokenAddress.toHexString(),
                ownerAddress.toHexString(),
                senderAddress.toHexString(),
                BigInteger.ONE,
                BigInteger.ZERO);

        // Then
        assertThatThrownBy(functionCall::send)
                .isInstanceOf(MirrorEvmTransactionException.class)
                .satisfies(ex -> {
                    MirrorEvmTransactionException exception = (MirrorEvmTransactionException) ex;
                    assertEquals("IERC20: failed to transfer", exception.getDetail());
                });
    }

    @Test
    void associateTokenDissociateFailTransferNftEthCall() {
        // Given
        final var treasuryEntityId = accountPersist();
        final var ownerEntityId = accountPersist();
        final var ownerAddress = toAddress(ownerEntityId.getId());
        final var senderEntityId = accountPersist();
        final var senderAddress = toAddress(senderEntityId.getId());

        final var tokenEntity = nftPersist(treasuryEntityId, ownerEntityId);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);

        // When
        final var functionCall = contract.send_associateTokenDissociateFailTransfer(
                tokenAddress.toHexString(),
                ownerAddress.toHexString(),
                senderAddress.toHexString(),
                BigInteger.ZERO,
                BigInteger.ONE);

        // Then
        assertThatThrownBy(functionCall::send)
                .isInstanceOf(MirrorEvmTransactionException.class)
                .satisfies(ex -> {
                    MirrorEvmTransactionException exception = (MirrorEvmTransactionException) ex;
                    assertEquals("IERC721: failed to transfer", exception.getDetail());
                });
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                FUNGIBLE_COMMON,        1,      0
                NON_FUNGIBLE_UNIQUE,    0,      1
                """)
    void approveTokenGetAllowanceEthCall(final TokenTypeEnum tokenType, final long amount, final long serialNumber) {
        // Given
        final var treasuryEntityId = accountPersist();
        final var ownerEntityId = accountPersist();
        final var ownerAddress = toAddress(ownerEntityId);
        final var spenderEntityId = accountPersist();

        final var tokenEntity = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                ? fungibleTokenPersist(treasuryEntityId)
                : nftPersist(treasuryEntityId, ownerEntityId, spenderEntityId);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var tokenEntityId = entityIdFromEvmAddress(toAddress(tokenEntity.getTokenId()));

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        tokenAccountPersist(tokenEntityId, contractEntityId);
        tokenAccountPersist(tokenEntityId, ownerEntityId);

        if (tokenType == TokenTypeEnum.NON_FUNGIBLE_UNIQUE) {
            nftAllowancePersist(tokenEntityId, contractEntityId, ownerEntityId, true);
        }

        // When
        final var spenderAddress =
                tokenType == TokenTypeEnum.FUNGIBLE_COMMON ? ownerAddress : toAddress(spenderEntityId);
        final var functionCall = contract.send_approveTokenGetAllowance(
                tokenAddress.toHexString(),
                spenderAddress.toHexString(),
                BigInteger.valueOf(amount),
                BigInteger.valueOf(serialNumber));

        // Then
        assertDoesNotThrow(() -> {
            functionCall.send();
        });
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                FUNGIBLE_COMMON,        1,      0
                NON_FUNGIBLE_UNIQUE,    0,      1
                """)
    void approveTokenGetAllowanceEstimateGas(final TokenTypeEnum tokenType, final long amount, final long serialNumber)
            throws Exception {
        // Given
        final var treasuryEntityId = accountPersist();
        final var ownerEntityId = accountPersist();
        final var ownerAddress = toAddress(ownerEntityId);
        final var spenderEntityId = accountPersist();

        final var tokenEntity = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                ? fungibleTokenPersist(treasuryEntityId)
                : nftPersist(treasuryEntityId, ownerEntityId, spenderEntityId);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var tokenEntityId = entityIdFromEvmAddress(toAddress(tokenEntity.getTokenId()));

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        tokenAccountPersist(tokenEntityId, contractEntityId);
        tokenAccountPersist(tokenEntityId, ownerEntityId);

        if (tokenType == TokenTypeEnum.NON_FUNGIBLE_UNIQUE) {
            nftAllowancePersist(tokenEntityId, contractEntityId, ownerEntityId, true);
        }

        // When
        final var spenderAddress =
                tokenType == TokenTypeEnum.FUNGIBLE_COMMON ? ownerAddress : toAddress(spenderEntityId);
        final var functionCall = contract.send_approveTokenGetAllowance(
                tokenAddress.toHexString(),
                spenderAddress.toHexString(),
                BigInteger.valueOf(amount),
                BigInteger.valueOf(serialNumber));

        // Then
        testWeb3jService.setEstimateGas(true);
        final var estimateGasUsedResult = functionCall.send().getGasUsed().longValue();

        testWeb3jService.setEstimateGas(false);
        final var actualGasUsed =
                gasUsedAfterExecution(getContractExecutionParameters(functionCall, contract, ownerAddress));

        assertThat(isWithinExpectedGasRange(estimateGasUsedResult, actualGasUsed))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimateGasUsedResult, actualGasUsed)
                .isTrue();
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                FUNGIBLE_COMMON,        1,      0
                NON_FUNGIBLE_UNIQUE,    0,      1
                """)
    void approveTokenTransferFromGetAllowanceGetBalanceEthCall(
            final TokenTypeEnum tokenType, final long amount, final long serialNumber) {
        // Given
        final var treasuryEntityId = accountPersist();
        final var ownerEntityId = accountPersist();
        final var spenderEntityId = spenderEntityPersistWithAlias();

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        final var tokenEntity = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                ? fungibleTokenPersist(treasuryEntityId)
                : nftPersist(treasuryEntityId, contractEntityId, spenderEntityId);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var tokenEntityId = entityIdFromEvmAddress(tokenAddress);

        tokenAccountPersist(tokenEntityId, contractEntityId);
        tokenAccountPersist(tokenEntityId, spenderEntityId);
        tokenAccountPersist(tokenEntityId, ownerEntityId);

        if (tokenType == TokenTypeEnum.NON_FUNGIBLE_UNIQUE) {
            nftAllowancePersist(tokenEntityId, contractEntityId, ownerEntityId, true);
        }

        // When
        final var functionCall = contract.send_approveTokenTransferFromGetAllowanceGetBalance(
                tokenAddress.toHexString(),
                SPENDER_ALIAS.toHexString(),
                BigInteger.valueOf(amount),
                BigInteger.valueOf(serialNumber));

        // Then
        assertDoesNotThrow(() -> {
            functionCall.send();
        });
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                FUNGIBLE_COMMON,        1,      0
                NON_FUNGIBLE_UNIQUE,    0,      1
                """)
    void approveTokenTransferFromGetAllowanceGetBalanceEstimateGas(
            final TokenTypeEnum tokenType, final long amount, final long serialNumber) throws Exception {
        // Given
        final var treasuryEntityId = accountPersist();
        final var ownerEntityId = accountPersist();
        final var spenderEntityId = spenderEntityPersistWithAlias();

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        final var tokenEntity = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                ? fungibleTokenPersist(treasuryEntityId)
                : nftPersist(treasuryEntityId, contractEntityId, spenderEntityId);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var tokenEntityId = entityIdFromEvmAddress(tokenAddress);

        tokenAccountPersist(tokenEntityId, contractEntityId);
        tokenAccountPersist(tokenEntityId, spenderEntityId);
        tokenAccountPersist(tokenEntityId, ownerEntityId);

        if (tokenType == TokenTypeEnum.NON_FUNGIBLE_UNIQUE) {
            nftAllowancePersist(tokenEntityId, contractEntityId, ownerEntityId, true);
        }

        // When
        final var functionCall = contract.send_approveTokenTransferFromGetAllowanceGetBalance(
                tokenAddress.toHexString(),
                SPENDER_ALIAS.toHexString(),
                BigInteger.valueOf(amount),
                BigInteger.valueOf(serialNumber));

        // Then
        testWeb3jService.setEstimateGas(true);
        final var estimateGasUsedResult = functionCall.send().getGasUsed().longValue();

        testWeb3jService.setEstimateGas(false);
        final var actualGasUsed = gasUsedAfterExecution(
                getContractExecutionParameters(functionCall, contract, toAddress(treasuryEntityId)));

        assertThat(isWithinExpectedGasRange(estimateGasUsedResult, actualGasUsed))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimateGasUsedResult, actualGasUsed)
                .isTrue();
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                FUNGIBLE_COMMON,        1,      0
                NON_FUNGIBLE_UNIQUE,    0,      1
                """)
    void approveTokenTransferGetAllowanceGetBalanceEthCall(
            final TokenTypeEnum tokenType, final long amount, final long serialNumber) {
        // Given
        final var treasuryEntityId = accountPersist();
        final var senderEntityId = senderEntityPersistWithAlias();

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        final var tokenEntity = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                ? fungibleTokenPersist(treasuryEntityId)
                : nftPersist(treasuryEntityId, senderEntityId);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var tokenEntityId = entityIdFromEvmAddress(tokenAddress);

        tokenAccountPersist(tokenEntityId, senderEntityId);
        tokenAccountPersist(tokenEntityId, contractEntityId);

        // When
        final var functionCall = contract.send_approveTokenTransferGetAllowanceGetBalance(
                tokenAddress.toHexString(),
                SENDER_ALIAS.toHexString(),
                BigInteger.valueOf(amount),
                BigInteger.valueOf(serialNumber));

        // Then
        assertDoesNotThrow(() -> {
            functionCall.send();
        });
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                FUNGIBLE_COMMON,        1,      0
                NON_FUNGIBLE_UNIQUE,    0,      1
                """)
    void approveTokenTransferGetAllowanceGetBalanceEstimateGas(
            final TokenTypeEnum tokenType, final long amount, final long serialNumber) throws Exception {
        // Given
        final var treasuryEntityId = accountPersist();
        final var senderEntityId = senderEntityPersistWithAlias();

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        final var tokenEntity = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                ? fungibleTokenPersist(treasuryEntityId)
                : nftPersist(treasuryEntityId, senderEntityId);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var tokenEntityId = entityIdFromEvmAddress(tokenAddress);

        tokenAccountPersist(tokenEntityId, senderEntityId);
        tokenAccountPersist(tokenEntityId, contractEntityId);

        // When
        final var functionCall = contract.send_approveTokenTransferGetAllowanceGetBalance(
                tokenAddress.toHexString(),
                SENDER_ALIAS.toHexString(),
                BigInteger.valueOf(amount),
                BigInteger.valueOf(serialNumber));

        // Then
        testWeb3jService.setEstimateGas(true);
        final var estimateGasUsedResult = functionCall.send().getGasUsed().longValue();

        testWeb3jService.setEstimateGas(false);
        final var actualGasUsed =
                gasUsedAfterExecution(getContractExecutionParameters(functionCall, contract, contractAddress));

        assertThat(isWithinExpectedGasRange(estimateGasUsedResult, actualGasUsed))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimateGasUsedResult, actualGasUsed)
                .isTrue();
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                FUNGIBLE_COMMON,        1,      0
                NON_FUNGIBLE_UNIQUE,    0,      1
                """)
    void approveTokenCryptoTransferGetAllowanceGetBalanceEthCall(
            final TokenTypeEnum tokenType, final long amount, final long serialNumber) {
        // Given
        final var treasuryEntityId = accountPersist();
        final var spenderEntityId = spenderEntityPersistWithAlias();

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        final var tokenEntity = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                ? fungibleTokenPersist(treasuryEntityId)
                : nftPersist(treasuryEntityId, spenderEntityId);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var tokenEntityId = entityIdFromEvmAddress(tokenAddress);

        tokenAccountPersist(tokenEntityId, spenderEntityId);
        tokenAccountPersist(tokenEntityId, contractEntityId);

        TokenTransferList tokenTransferList;
        if (tokenType == TokenTypeEnum.FUNGIBLE_COMMON) {
            tokenTransferList = new TokenTransferList(
                    tokenAddress.toHexString(),
                    List.of(
                            new AccountAmount(contractAddress.toHexString(), BigInteger.valueOf(-amount), false),
                            new AccountAmount(SPENDER_ALIAS.toHexString(), BigInteger.valueOf(amount), false)),
                    List.of());
        } else {
            tokenTransferList = new TokenTransferList(
                    tokenAddress.toHexString(),
                    List.of(),
                    List.of(new NftTransfer(
                            contractAddress.toHexString(),
                            SPENDER_ALIAS.toHexString(),
                            BigInteger.valueOf(serialNumber),
                            Boolean.FALSE)));
        }

        // When
        final var functionCall = contract.send_approveTokenCryptoTransferGetAllowanceGetBalance(
                new TransferList(List.of()), List.of(tokenTransferList));

        // Then
        assertDoesNotThrow(() -> {
            functionCall.send();
        });
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                FUNGIBLE_COMMON,        1,      0
                NON_FUNGIBLE_UNIQUE,    0,      1
                """)
    void approveTokenCryptoTransferGetAllowanceGetBalanceEstimateGas(
            final TokenTypeEnum tokenType, final long amount, final long serialNumber) throws Exception {
        // Given
        final var treasuryEntityId = accountPersist();
        final var treasuryAddress = toAddress(treasuryEntityId.getId());
        final var spenderEntityId = spenderEntityPersistWithAlias();

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        final var tokenEntity = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                ? fungibleTokenPersist(treasuryEntityId)
                : nftPersist(treasuryEntityId, spenderEntityId);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var tokenEntityId = entityIdFromEvmAddress(tokenAddress);

        tokenAccountPersist(tokenEntityId, spenderEntityId);
        tokenAccountPersist(tokenEntityId, contractEntityId);

        TokenTransferList tokenTransferList;
        if (tokenType == TokenTypeEnum.FUNGIBLE_COMMON) {
            tokenTransferList = new TokenTransferList(
                    tokenAddress.toHexString(),
                    List.of(
                            new AccountAmount(contractAddress.toHexString(), BigInteger.valueOf(-amount), false),
                            new AccountAmount(SPENDER_ALIAS.toHexString(), BigInteger.valueOf(amount), false)),
                    List.of());
        } else {
            tokenTransferList = new TokenTransferList(
                    tokenAddress.toHexString(),
                    List.of(),
                    List.of(new NftTransfer(
                            contractAddress.toHexString(),
                            SPENDER_ALIAS.toHexString(),
                            BigInteger.valueOf(serialNumber),
                            Boolean.FALSE)));
        }

        // When
        final var functionCall = contract.send_approveTokenCryptoTransferGetAllowanceGetBalance(
                new TransferList(List.of()), List.of(tokenTransferList));

        // Then
        testWeb3jService.setEstimateGas(true);
        final var estimateGasUsedResult = functionCall.send().getGasUsed().longValue();

        testWeb3jService.setEstimateGas(false);
        final var actualGasUsed =
                gasUsedAfterExecution(getContractExecutionParameters(functionCall, contract, treasuryAddress));

        assertThat(isWithinExpectedGasRange(estimateGasUsedResult, actualGasUsed))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimateGasUsedResult, actualGasUsed)
                .isTrue();
    }

    @Test
    void approveForAllTokenTransferFromGetAllowanceEthCall() {
        // Given
        final var treasuryEntityId = accountPersist();
        final var spenderEntityId = spenderEntityPersistWithAlias();

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        final var tokenEntity = nftPersist(treasuryEntityId, spenderEntityId);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var tokenEntityId = entityIdFromEvmAddress(tokenAddress);

        tokenAccountPersist(tokenEntityId, spenderEntityId);
        tokenAccountPersist(tokenEntityId, contractEntityId);

        // When
        final var functionCall = contract.send_approveForAllTokenTransferGetAllowance(
                tokenAddress.toHexString(), SPENDER_ALIAS.toHexString(), BigInteger.ONE);

        // Then
        assertDoesNotThrow(() -> {
            functionCall.send();
        });
    }

    @Test
    void approveForAllTokenTransferFromGetAllowanceEstimateGas() throws Exception {
        // Given
        final var treasuryEntityId = accountPersist();
        final var treasuryAddress = toAddress(treasuryEntityId.getId());
        final var spenderEntityId = spenderEntityPersistWithAlias();

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        final var tokenEntity = nftPersist(treasuryEntityId, spenderEntityId);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var tokenEntityId = entityIdFromEvmAddress(tokenAddress);

        tokenAccountPersist(tokenEntityId, spenderEntityId);
        tokenAccountPersist(tokenEntityId, contractEntityId);

        // When
        final var functionCall = contract.send_approveForAllTokenTransferGetAllowance(
                tokenAddress.toHexString(), SPENDER_ALIAS.toHexString(), BigInteger.ONE);

        // Then
        testWeb3jService.setEstimateGas(true);
        final var estimateGasUsedResult = functionCall.send().getGasUsed().longValue();

        testWeb3jService.setEstimateGas(false);
        final var actualGasUsed =
                gasUsedAfterExecution(getContractExecutionParameters(functionCall, contract, treasuryAddress));

        assertThat(isWithinExpectedGasRange(estimateGasUsedResult, actualGasUsed))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimateGasUsedResult, actualGasUsed)
                .isTrue();
    }

    @Test
    void approveForAllCryptoTransferGetAllowanceEthCall() {
        // Given
        final var treasuryEntityId = accountPersist();
        final var spenderEntityId = spenderEntityPersistWithAlias();

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        final var tokenEntity = nftPersist(treasuryEntityId, spenderEntityId);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var tokenEntityId = entityIdFromEvmAddress(tokenAddress);

        tokenAccountPersist(tokenEntityId, spenderEntityId);
        tokenAccountPersist(tokenEntityId, contractEntityId);

        var tokenTransferList = new TokenTransferList(
                tokenAddress.toHexString(),
                List.of(),
                List.of(new NftTransfer(
                        contractAddress.toHexString(), SPENDER_ALIAS.toHexString(), BigInteger.ONE, Boolean.TRUE)));

        // When
        final var functionCall = contract.send_approveForAllCryptoTransferGetAllowance(
                new TransferList(List.of()), List.of(tokenTransferList));

        // Then
        assertDoesNotThrow(() -> {
            functionCall.send();
        });
    }

    @Test
    void approveForAllCryptoTransferGetAllowanceEstimateGas() throws Exception {
        // Given
        final var treasuryEntityId = accountPersist();
        final var treasuryAddress = toAddress(treasuryEntityId.getId());
        final var spenderEntityId = spenderEntityPersistWithAlias();

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        final var tokenEntity = nftPersist(treasuryEntityId, spenderEntityId);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var tokenEntityId = entityIdFromEvmAddress(tokenAddress);

        tokenAccountPersist(tokenEntityId, spenderEntityId);
        tokenAccountPersist(tokenEntityId, contractEntityId);

        var tokenTransferList = new TokenTransferList(
                tokenAddress.toHexString(),
                List.of(),
                List.of(new NftTransfer(
                        contractAddress.toHexString(), SPENDER_ALIAS.toHexString(), BigInteger.ONE, Boolean.TRUE)));

        // When
        final var functionCall = contract.send_approveForAllCryptoTransferGetAllowance(
                new TransferList(List.of()), List.of(tokenTransferList));

        // Then
        testWeb3jService.setEstimateGas(true);
        final var estimateGasUsedResult = functionCall.send().getGasUsed().longValue();

        testWeb3jService.setEstimateGas(false);
        final var actualGasUsed =
                gasUsedAfterExecution(getContractExecutionParameters(functionCall, contract, treasuryAddress));

        assertThat(isWithinExpectedGasRange(estimateGasUsedResult, actualGasUsed))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimateGasUsedResult, actualGasUsed)
                .isTrue();
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                FUNGIBLE_COMMON,        1,      0,      false
                NON_FUNGIBLE_UNIQUE,    0,      1,      true
                """)
    void cryptoTransferFromGetAllowanceGetBalanceEthCall(
            final TokenTypeEnum tokenType, final long amount, final long serialNumber, final boolean approvalForAll) {
        // Given
        final var treasuryEntityId = accountPersist();
        final var spenderEntityId = spenderEntityPersistWithAlias();

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        final var tokenEntity = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                ? fungibleTokenPersist(treasuryEntityId)
                : nftPersist(treasuryEntityId, spenderEntityId);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var tokenEntityId = entityIdFromEvmAddress(tokenAddress);

        tokenAccountPersist(tokenEntityId, spenderEntityId);
        tokenAccountPersist(tokenEntityId, contractEntityId);

        TokenTransferList tokenTransferList;
        if (tokenType == TokenTypeEnum.FUNGIBLE_COMMON) {
            tokenTransferList = new TokenTransferList(
                    tokenAddress.toHexString(),
                    List.of(
                            new AccountAmount(
                                    contractAddress.toHexString(), BigInteger.valueOf(-amount), approvalForAll),
                            new AccountAmount(SPENDER_ALIAS.toHexString(), BigInteger.valueOf(amount), approvalForAll)),
                    List.of());
        } else {
            tokenTransferList = new TokenTransferList(
                    tokenAddress.toHexString(),
                    List.of(),
                    List.of(new NftTransfer(
                            contractAddress.toHexString(),
                            SPENDER_ALIAS.toHexString(),
                            BigInteger.valueOf(serialNumber),
                            approvalForAll)));
        }

        // When
        final var functionCall = contract.send_cryptoTransferFromGetAllowanceGetBalance(
                new TransferList(List.of()), List.of(tokenTransferList));

        // Then
        assertDoesNotThrow(() -> {
            functionCall.send();
        });
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                FUNGIBLE_COMMON,        1,      0,      false
                NON_FUNGIBLE_UNIQUE,    0,      1,      true
                """)
    void cryptoTransferFromGetAllowanceGetBalanceEstimateGas(
            final TokenTypeEnum tokenType, final long amount, final long serialNumber, final boolean approvalForAll)
            throws Exception {
        // Given
        final var treasuryEntityId = accountPersist();
        final var treasuryAddress = toAddress(treasuryEntityId.getId());
        final var spenderEntityId = spenderEntityPersistWithAlias();

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        final var tokenEntity = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                ? fungibleTokenPersist(treasuryEntityId)
                : nftPersist(treasuryEntityId, spenderEntityId);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var tokenEntityId = entityIdFromEvmAddress(tokenAddress);

        tokenAccountPersist(tokenEntityId, spenderEntityId);
        tokenAccountPersist(tokenEntityId, contractEntityId);

        TokenTransferList tokenTransferList;
        if (tokenType == TokenTypeEnum.FUNGIBLE_COMMON) {
            tokenTransferList = new TokenTransferList(
                    tokenAddress.toHexString(),
                    List.of(
                            new AccountAmount(
                                    contractAddress.toHexString(), BigInteger.valueOf(-amount), approvalForAll),
                            new AccountAmount(SPENDER_ALIAS.toHexString(), BigInteger.valueOf(amount), approvalForAll)),
                    List.of());
        } else {
            tokenTransferList = new TokenTransferList(
                    tokenAddress.toHexString(),
                    List.of(),
                    List.of(new NftTransfer(
                            contractAddress.toHexString(),
                            SPENDER_ALIAS.toHexString(),
                            BigInteger.valueOf(serialNumber),
                            approvalForAll)));
        }

        // When
        final var functionCall = contract.send_cryptoTransferFromGetAllowanceGetBalance(
                new TransferList(List.of()), List.of(tokenTransferList));

        // Then
        testWeb3jService.setEstimateGas(true);
        final var estimateGasUsedResult = functionCall.send().getGasUsed().longValue();

        testWeb3jService.setEstimateGas(false);
        final var actualGasUsed =
                gasUsedAfterExecution(getContractExecutionParameters(functionCall, contract, treasuryAddress));

        assertThat(isWithinExpectedGasRange(estimateGasUsedResult, actualGasUsed))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimateGasUsedResult, actualGasUsed)
                .isTrue();
    }

    @Test
    void transferFromNFTGetAllowanceEthCall() {
        // Given
        final var treasuryEntityId = accountPersist();
        final var spenderEntityId = accountPersist();

        final var tokenEntity = nftPersist(treasuryEntityId, spenderEntityId);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        tokenAccountPersist(entityIdFromEvmAddress(tokenAddress), spenderEntityId);
        tokenAccountPersist(entityIdFromEvmAddress(tokenAddress), contractEntityId);

        // When
        final var functionCall = contract.send_transferFromNFTGetAllowance(tokenAddress.toHexString(), BigInteger.ONE);

        // Then
        assertDoesNotThrow(() -> {
            functionCall.send();
        });
    }

    @Test
    void transferFromNFTGetAllowanceEstimateGas() throws Exception {
        // Given
        final var treasuryEntityId = accountPersist();
        final var treasuryAddress = toAddress(treasuryEntityId.getId());
        final var spenderEntityId = accountPersist();

        final var tokenEntity = nftPersist(treasuryEntityId, spenderEntityId);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        tokenAccountPersist(entityIdFromEvmAddress(tokenAddress), spenderEntityId);
        tokenAccountPersist(entityIdFromEvmAddress(tokenAddress), contractEntityId);

        // When
        final var functionCall = contract.send_transferFromNFTGetAllowance(tokenAddress.toHexString(), BigInteger.ONE);

        // Then
        testWeb3jService.setEstimateGas(true);
        final var estimateGasUsedResult = functionCall.send().getGasUsed().longValue();

        testWeb3jService.setEstimateGas(false);
        final var actualGasUsed =
                gasUsedAfterExecution(getContractExecutionParameters(functionCall, contract, treasuryAddress));

        assertThat(isWithinExpectedGasRange(estimateGasUsedResult, actualGasUsed))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimateGasUsedResult, actualGasUsed)
                .isTrue();
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                FUNGIBLE_COMMON,        1,      0
                NON_FUNGIBLE_UNIQUE,    0,      1
                """)
    void transferFromGetAllowanceGetBalanceEthCall(
            final TokenTypeEnum tokenType, final long amount, final long serialNumber) {
        // Given
        final var treasuryEntityId = accountPersist();
        final var spenderEntityId = spenderEntityPersistWithAlias();

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        final var tokenEntity = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                ? fungibleTokenPersist(treasuryEntityId)
                : nftPersist(treasuryEntityId, treasuryEntityId);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var tokenEntityId = entityIdFromEvmAddress(tokenAddress);

        tokenAccountPersist(tokenEntityId, treasuryEntityId);
        tokenAccountPersist(tokenEntityId, spenderEntityId);
        tokenAccountPersist(tokenEntityId, contractEntityId);

        // When
        final var functionCall = contract.send_transferFromGetAllowanceGetBalance(
                tokenAddress.toHexString(),
                SPENDER_ALIAS.toHexString(),
                BigInteger.valueOf(amount),
                BigInteger.valueOf(serialNumber));

        // Then
        assertDoesNotThrow(() -> {
            functionCall.send();
        });
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                FUNGIBLE_COMMON,        1,      0
                NON_FUNGIBLE_UNIQUE,    0,      1
                """)
    void transferFromGetAllowanceGetBalanceEstimateGas(
            final TokenTypeEnum tokenType, final long amount, final long serialNumber) throws Exception {
        // Given
        final var treasuryEntityId = accountPersist();
        final var treasuryAddress = toAddress(treasuryEntityId);
        final var spenderEntityId = spenderEntityPersistWithAlias();

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        final var tokenEntity = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                ? fungibleTokenPersist(treasuryEntityId)
                : nftPersist(treasuryEntityId, treasuryEntityId);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var tokenEntityId = entityIdFromEvmAddress(tokenAddress);

        tokenAccountPersist(tokenEntityId, treasuryEntityId);
        tokenAccountPersist(tokenEntityId, spenderEntityId);
        tokenAccountPersist(tokenEntityId, contractEntityId);

        // When
        final var functionCall = contract.send_transferFromGetAllowanceGetBalance(
                tokenAddress.toHexString(),
                SPENDER_ALIAS.toHexString(),
                BigInteger.valueOf(amount),
                BigInteger.valueOf(serialNumber));

        // Then
        testWeb3jService.setEstimateGas(true);
        final var estimateGasUsedResult = functionCall.send().getGasUsed().longValue();

        testWeb3jService.setEstimateGas(false);
        final var actualGasUsed =
                gasUsedAfterExecution(getContractExecutionParameters(functionCall, contract, treasuryAddress));

        assertThat(isWithinExpectedGasRange(estimateGasUsedResult, actualGasUsed))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimateGasUsedResult, actualGasUsed)
                .isTrue();
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
                FUNGIBLE_COMMON
                NON_FUNGIBLE_UNIQUE
                """)
    void grantKycRevokeKycEthCall(final TokenTypeEnum tokenType) {
        // Given
        final var treasuryEntityId = accountPersist();
        final var spenderEntityId = spenderEntityPersistWithAlias();

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);

        final var tokenEntity = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                ? fungibleTokenPersist(treasuryEntityId)
                : nftPersist(treasuryEntityId, treasuryEntityId);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var tokenEntityId = entityIdFromEvmAddress(tokenAddress);

        tokenAccountPersist(tokenEntityId, spenderEntityId);

        // When
        final var functionCall =
                contract.send_grantKycRevokeKyc(tokenAddress.toHexString(), SPENDER_ALIAS.toHexString());

        // Then
        assertDoesNotThrow(() -> {
            functionCall.send();
        });
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
                FUNGIBLE_COMMON
                NON_FUNGIBLE_UNIQUE
                """)
    void grantKycRevokeKycEstimateGas(final TokenTypeEnum tokenType) throws Exception {
        // Given
        final var treasuryEntityId = accountPersist();
        final var treasuryAddress = toAddress(treasuryEntityId);
        final var spenderEntityId = spenderEntityPersistWithAlias();

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);

        final var tokenEntity = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                ? fungibleTokenPersist(treasuryEntityId)
                : nftPersist(treasuryEntityId, treasuryEntityId);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var tokenEntityId = entityIdFromEvmAddress(tokenAddress);

        tokenAccountPersist(tokenEntityId, spenderEntityId);

        // When
        final var functionCall =
                contract.send_grantKycRevokeKyc(tokenAddress.toHexString(), SPENDER_ALIAS.toHexString());

        // Then
        testWeb3jService.setEstimateGas(true);
        final var estimateGasUsedResult = functionCall.send().getGasUsed().longValue();

        testWeb3jService.setEstimateGas(false);
        final var actualGasUsed =
                gasUsedAfterExecution(getContractExecutionParameters(functionCall, contract, treasuryAddress));

        assertThat(isWithinExpectedGasRange(estimateGasUsedResult, actualGasUsed))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimateGasUsedResult, actualGasUsed)
                .isTrue();
    }

    @Test
    void getAddressThisEthCall() {
        // Given
        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);

        // When
        final var functionCall = contract.send_getAddressThis();

        // Then
        assertDoesNotThrow(() -> {
            functionCall.send();
        });
    }

    @Test
    void getAddressThisEstimateGas() throws Exception {
        // Given
        final var treasuryEntityId = accountPersist();
        final var treasuryAddress = toAddress(treasuryEntityId.getId());

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);

        // When
        final var functionCall = contract.send_getAddressThis();

        // Then
        testWeb3jService.setEstimateGas(true);
        final var estimateGasUsedResult = functionCall.send().getGasUsed().longValue();

        testWeb3jService.setEstimateGas(false);
        final var actualGasUsed =
                gasUsedAfterExecution(getContractExecutionParameters(functionCall, contract, treasuryAddress));

        // Then
        assertThat(isWithinExpectedGasRange(estimateGasUsedResult, actualGasUsed))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimateGasUsedResult, actualGasUsed)
                .isTrue();
    }

    @Test
    void getAddressThisWithEvmAliasRecipient() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);

        final var contractAlias = ContractCallContext.run(ctx -> {
            ctx.initializeStackFrames(store.getStackedStateFrames());
            final var contractAccount =
                    store.getAccount(Address.fromHexString(contract.getContractAddress()), OnMissing.THROW);
            return contractAccount.canonicalAddress();
        });

        testWeb3jService.setReceiver(contractAlias);

        // When
        final String result = contract.call_getAddressThis().send();

        // Then
        assertEquals(contractAlias.toHexString(), result);
    }

    @Test
    void getAddressThisWithLongZeroRecipientThatHasEvmAlias() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);

        final var contractAlias = ContractCallContext.run(ctx -> {
            ctx.initializeStackFrames(store.getStackedStateFrames());
            final var contractAccount =
                    store.getAccount(Address.fromHexString(contract.getContractAddress()), OnMissing.THROW);
            return contractAccount.canonicalAddress();
        });

        // When
        final String result = contract.call_getAddressThis().send();

        // Then
        assertEquals(contractAlias.toHexString(), result);
    }

    private void recordFilePersist() {
        domainBuilder.recordFile().persist();
    }

    private Token fungibleTokenPersist(final EntityId treasuryEntityId) {
        return fungibleTokenPersist(treasuryEntityId, domainBuilder.key());
    }

    private Token fungibleTokenPersist(final EntityId treasuryEntityId, final byte[] kycKey) {
        final var tokenEntity =
                domainBuilder.entity().customize(e -> e.type(TOKEN)).persist();

        return domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .treasuryAccountId(treasuryEntityId)
                        .kycKey(kycKey))
                .persist();
    }

    private Token nftPersist(final EntityId treasuryEntityId) {
        return nftPersist(treasuryEntityId, treasuryEntityId);
    }

    private Token nftPersist(final EntityId treasuryEntityId, final EntityId ownerEntityId) {
        return nftPersist(treasuryEntityId, ownerEntityId, ownerEntityId);
    }

    private Token nftPersist(
            final EntityId treasuryEntityId, final EntityId ownerEntityId, final EntityId spenderEntityId) {
        return nftPersist(treasuryEntityId, ownerEntityId, spenderEntityId, domainBuilder.key());
    }

    private Token nftPersist(
            final EntityId treasuryEntityId,
            final EntityId ownerEntityId,
            final EntityId spenderEntityId,
            final byte[] kycKey) {
        final var nftEntity =
                domainBuilder.entity().customize(e -> e.type(TOKEN)).persist();

        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(nftEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(treasuryEntityId)
                        .kycKey(kycKey))
                .persist();

        domainBuilder
                .nft()
                .customize(n -> n.accountId(treasuryEntityId)
                        .spender(spenderEntityId)
                        .accountId(ownerEntityId)
                        .tokenId(nftEntity.getId())
                        .serialNumber(1))
                .persist();
        return token;
    }

    private EntityId accountPersist() {
        return domainBuilder
                .entity()
                .customize(e -> e.evmAddress(null))
                .persist()
                .toEntityId();
    }

    private EntityId accountPersistWithAlias(final Address alias, final ByteString publicKey) {
        return domainBuilder
                .entity()
                .customize(e -> e.evmAddress(alias.toArray()).alias(publicKey.toByteArray()))
                .persist()
                .toEntityId();
    }

    private EntityId senderEntityPersistWithAlias() {
        return accountPersistWithAlias(SENDER_ALIAS, SENDER_PUBLIC_KEY);
    }

    private EntityId spenderEntityPersistWithAlias() {
        return accountPersistWithAlias(SPENDER_ALIAS, SPENDER_PUBLIC_KEY);
    }

    private void tokenAccountPersist(final EntityId tokenEntityId, final EntityId accountId) {
        domainBuilder
                .tokenAccount()
                .customize(e -> e.accountId(accountId.getId())
                        .tokenId(tokenEntityId.getId())
                        .associated(true)
                        .kycStatus(TokenKycStatusEnum.GRANTED))
                .persist();
    }

    private void nftAllowancePersist(
            final EntityId tokenEntityId,
            final EntityId spenderEntityId,
            final EntityId ownerEntityId,
            final boolean approvedForAll) {
        domainBuilder
                .nftAllowance()
                .customize(a -> a.tokenId(tokenEntityId.getId())
                        .spender(spenderEntityId.getId())
                        .owner(ownerEntityId.getId())
                        .payerAccountId(ownerEntityId)
                        .approvedForAll(approvedForAll))
                .persist();
    }
}
