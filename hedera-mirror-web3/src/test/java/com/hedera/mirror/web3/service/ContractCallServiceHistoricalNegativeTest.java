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

import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.entityIdFromEvmAddress;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.EVM_V_34_BLOCK;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.EVM_V_38_BLOCK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.web3j.generated.EvmCodesHistorical;
import com.hedera.mirror.web3.web3j.generated.ModificationPrecompileTestContract;
import com.hedera.mirror.web3.web3j.generated.ModificationPrecompileTestContract.AccountAmount;
import com.hedera.mirror.web3.web3j.generated.ModificationPrecompileTestContract.TransferList;
import com.hedera.mirror.web3.web3j.generated.PrecompileTestContractHistorical;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ContractCallServiceHistoricalNegativeTest extends AbstractContractCallServiceHistoricalTest {

    @Test
    void contractNotPersistedYetThrowsException() {
        // Given
        final var evm30RecordFile = recordFilePersist(EVM_V_34_BLOCK - 1);
        final var historicalRangeAfterEvm34 = setUpHistoricalContext(EVM_V_34_BLOCK);
        final var tokenEntity = nftPersistHistorical(historicalRangeAfterEvm34);

        // Deploy the contract against block X, make the contract call against block (X-1) -> throw an exception as the
        // contract does not exist yet.
        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);
        setupHistoricalStateInService(EVM_V_34_BLOCK - 1, evm30RecordFile);

        // When
        final var functionCall = contract.call_isTokenAddress(getAddressFromEntity(tokenEntity));
        // Then
        final var expectedErrorMessage = mirrorNodeEvmProperties.isModularizedServices()
                ? INVALID_CONTRACT_ID.name()
                : INVALID_TRANSACTION.name();
        assertThatThrownBy(functionCall::send)
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage(expectedErrorMessage);
    }

    // Tests TokenRepository and NftRepository
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void tokenNotPersistedYet(final boolean isNft) throws Exception {
        // Given
        final var evm30RecordFile = recordFilePersist(EVM_V_34_BLOCK - 1);
        final var historicalRangeAfterEvm34 = setUpHistoricalContext(EVM_V_34_BLOCK);
        final var tokenEntity = isNft
                ? nftPersistHistorical(historicalRangeAfterEvm34)
                : fungibleTokenPersistHistorical(historicalRangeAfterEvm34);

        setupHistoricalStateInService(EVM_V_34_BLOCK - 1, evm30RecordFile);
        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // Persist the token against block X, make the call against block (X-1) -> throw an exception as the token does
        // not exist yet.
        // When
        final var functionCall = contract.call_isTokenAddress(getAddressFromEntity(tokenEntity));

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            // Modularized services just tries to fetch the token by id from the state
            // and simply returns false if it does not exist without throwing an exception
            assertThat(functionCall.send()).isFalse();
        } else {
            assertThatThrownBy(functionCall::send).isInstanceOf(MirrorEvmTransactionException.class);
        }
    }

    // Tests TokenAccountRepository
    @Test
    void tokenAccountRelationshipNotPersistedYetReturnsFalse() throws Exception {
        // Given
        final var evm30RecordFile = recordFilePersist(EVM_V_34_BLOCK - 1);
        final var evm30HistoricalRange = setupHistoricalStateInService(EVM_V_34_BLOCK - 1, evm30RecordFile);
        final var historicalRangeAfterEvm34 = setUpHistoricalContext(EVM_V_34_BLOCK);
        final var tokenEntity = tokenEntityPersistHistorical(evm30HistoricalRange);
        fungibleTokenPersistHistorical(tokenEntity, evm30HistoricalRange);
        final var accountEntity = accountEntityNoEvmAddressPersistHistorical(evm30HistoricalRange);
        tokenAccountFrozenRelationshipPersistHistorical(tokenEntity, accountEntity, historicalRangeAfterEvm34);

        setupHistoricalStateInService(EVM_V_34_BLOCK - 1, evm30RecordFile);
        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // Persist the token and the account in block (X-1), but the relationship in block X. The call against block
        // (X-1)
        // should fail as the association is not available yet.
        // When
        final var functionCall =
                contract.call_isTokenFrozen(getAddressFromEntity(tokenEntity), getAddressFromEntity(accountEntity));

        // Then
        assertThat(functionCall.send()).isFalse();
    }

    // Tests TokenAllowanceRepository
    @Test
    void getAllowanceForFungibleTokenNotPersistedYetReturnsZero() throws Exception {
        // Given
        final var evm30RecordFile = recordFilePersist(EVM_V_34_BLOCK - 1);
        final var evm30HistoricalRange = setupHistoricalStateInService(EVM_V_34_BLOCK - 1, evm30RecordFile);
        final var historicalRangeAfterEvm34 = setUpHistoricalContext(EVM_V_34_BLOCK);
        final var owner = accountEntityPersistHistorical(evm30HistoricalRange);
        final var spender = accountEntityPersistHistorical(evm30HistoricalRange);
        final var tokenEntity = tokenEntityPersistHistorical(evm30HistoricalRange);
        fungibleTokenPersistHistorical(tokenEntity, evm30HistoricalRange);

        // Persist the token and the accounts in block (X-1), but the token allowance in block X. The call against block
        // (X-1) should fail as the allowance is not available yet.
        tokenAllowancePersistHistorical(tokenEntity, owner, spender, historicalRangeAfterEvm34);

        setupHistoricalStateInService(EVM_V_34_BLOCK - 1, evm30RecordFile);
        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall = contract.call_htsAllowance(
                getAddressFromEntity(tokenEntity), getAddressFromEntity(owner), getAddressFromEntity(spender));

        // Then
        assertThat(functionCall.send()).isEqualTo(BigInteger.ZERO);
    }

    // Tests NftAllowanceRepository
    @Test
    void getAllowanceForNftNotPersistedYetReturnsZero() throws Exception {
        // Given
        final var evm30RecordFile = recordFilePersist(EVM_V_34_BLOCK - 1);
        final var evm30HistoricalRange = setupHistoricalStateInService(EVM_V_34_BLOCK - 1, evm30RecordFile);
        final var historicalRangeAfterEvm34 = setUpHistoricalContext(EVM_V_34_BLOCK);
        final var owner = accountEntityPersistHistorical(evm30HistoricalRange);
        final var spender = accountEntityPersistHistorical(evm30HistoricalRange);
        final var tokenEntity = nftPersistHistorical(evm30HistoricalRange);

        // Persist the token and the accounts in block (X-1), but the nft allowance in block X. The call against block
        // (X-1)
        // should fail as the allowance is not available yet.
        nftAllowancePersistHistorical(tokenEntity, owner, spender, historicalRangeAfterEvm34);

        setupHistoricalStateInService(EVM_V_34_BLOCK - 1, evm30RecordFile);
        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall = contract.call_htsAllowance(
                getAddressFromEntity(tokenEntity), getAddressFromEntity(owner), getAddressFromEntity(spender));

        // Then
        assertThat(functionCall.send()).isEqualTo(BigInteger.ZERO);
    }

    // Tests CustomFeeRepository
    @Test
    void getFungibleTokenInfoCustomFeesNotPersistedYetReturnsZero() throws Exception {
        // Given
        final var evm30RecordFile = recordFilePersist(EVM_V_34_BLOCK - 1);
        final var evm30HistoricalRange = setupHistoricalStateInService(EVM_V_34_BLOCK - 1, evm30RecordFile);
        final var historicalRangeAfterEvm34 = setUpHistoricalContext(EVM_V_34_BLOCK);

        final var tokenEntity = fungibleTokenPersistHistorical(evm30HistoricalRange);
        final var feeCollector = accountEntityPersistHistorical(evm30HistoricalRange);

        // Persist the token and the account in block (X-1), but the custom fees in block X. The call against block
        // (X-1)
        // should fail as the custom fees are not available yet.
        customFeesWithFeeCollectorPersistHistorical(
                feeCollector, tokenEntity, TokenTypeEnum.FUNGIBLE_COMMON, historicalRangeAfterEvm34);

        setupHistoricalStateInService(EVM_V_34_BLOCK - 1, evm30RecordFile);
        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var result = contract.call_getInformationForFungibleToken(getAddressFromEntity(tokenEntity))
                .send();

        // Then
        assertThat(result.tokenInfo.fixedFees).usingRecursiveComparison().isEqualTo(List.of());
        assertThat(result.tokenInfo.fractionalFees).usingRecursiveComparison().isEqualTo(List.of());
        assertThat(result.tokenInfo.royaltyFees).usingRecursiveComparison().isEqualTo(List.of());
    }

    // Tests CustomFeeRepository
    @Test
    void getNftInfoCustomFeesNotPersistedYetReturnsZero() throws Exception {
        // Given
        final var evm30RecordFile = recordFilePersist(EVM_V_34_BLOCK - 1);
        final var evm30HistoricalRange = setupHistoricalStateInService(EVM_V_34_BLOCK - 1, evm30RecordFile);
        final var historicalRangeAfterEvm34 = setUpHistoricalContext(EVM_V_34_BLOCK);

        final var tokenEntity = nftPersistHistorical(evm30HistoricalRange);
        final var feeCollector = accountEntityPersistHistorical(evm30HistoricalRange);

        // Persist the token and the account in block (X-1), but the custom fees in block X. The call against block
        // (X-1)
        // should fail as the custom fees are not available yet.
        customFeesWithFeeCollectorPersistHistorical(
                feeCollector, tokenEntity, TokenTypeEnum.NON_FUNGIBLE_UNIQUE, historicalRangeAfterEvm34);

        setupHistoricalStateInService(EVM_V_34_BLOCK - 1, evm30RecordFile);
        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var result = contract.call_getInformationForToken(getAddressFromEntity(tokenEntity))
                .send();

        // Then
        assertThat(result.fixedFees).usingRecursiveComparison().isEqualTo(List.of());
        assertThat(result.fractionalFees).usingRecursiveComparison().isEqualTo(List.of());
        assertThat(result.royaltyFees).usingRecursiveComparison().isEqualTo(List.of());
    }

    // Tests TokenBalanceRepository
    @Test
    void tokenBalanceNotPersistedYetThrowsException() {
        // Given
        final var evm38RecordFile = recordFilePersist(EVM_V_38_BLOCK);
        final var evm38HistoricalRange = setupHistoricalStateInService(EVM_V_38_BLOCK, evm38RecordFile);
        final var historicalRangeAfterEvm38 = setUpHistoricalContext(EVM_V_38_BLOCK + 1);

        final var accountEntity = accountEntityPersistHistorical(evm38HistoricalRange);
        final var receiverEntity = accountEntityPersistHistorical(evm38HistoricalRange);
        final var tokenEntity = tokenEntityPersistHistorical(evm38HistoricalRange);
        domainBuilder
                .tokenHistory()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .kycKey(null)
                        .treasuryAccountId(accountEntity.toEntityId())
                        .timestampRange(evm38HistoricalRange))
                .persist();
        tokenBalancePersistHistorical(accountEntity.toEntityId(), tokenEntity.toEntityId(), 1L, evm38HistoricalRange);

        // Persist the token, the account, balance1 in block (X-1) and balance2 in block X, where balance1 < balance2.
        // The call against block (X-1)
        // should fail with balance2, since the bigger balance is not available at this point.
        tokenBalancePersistHistorical(
                accountEntity.toEntityId(), tokenEntity.toEntityId(), 2L, historicalRangeAfterEvm38);

        setupHistoricalStateInService(EVM_V_38_BLOCK, evm38HistoricalRange);
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_transferTokenExternal(
                getAddressFromEntity(tokenEntity),
                getAddressFromEntity(accountEntity),
                getAddressFromEntity(receiverEntity),
                BigInteger.valueOf(2));

        // Then
        assertThatThrownBy(functionCall::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void transferWithNoPersistedCryptoAllowanceThrowsException() {
        // Given
        final var evm38RecordFile = recordFilePersist(EVM_V_38_BLOCK);
        final var evm38HistoricalRange = setupHistoricalStateInService(EVM_V_38_BLOCK, evm38RecordFile);
        final var historicalRangeAfterEvm38 = setUpHistoricalContext(EVM_V_38_BLOCK + 1);

        final var sender = accountEntityPersistHistorical(evm38HistoricalRange);
        final var receiver = accountEntityPersistHistorical(evm38HistoricalRange);

        setupHistoricalStateInService(EVM_V_38_BLOCK, evm38HistoricalRange);
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        // The accounts are persisted against block (X-1) but the crypto allowance is persisted in
        // block X, so the call against block (X-1) fails as there is no allowance yet.
        cryptoAllowancePersistHistorical(sender, contractEntityId, 5L, historicalRangeAfterEvm38);

        testWeb3jService.setSender(getAddressFromEntity(sender));
        final var transferList = new TransferList(List.of(
                new AccountAmount(getAddressFromEntity(sender), BigInteger.valueOf(-5L), true),
                new AccountAmount(getAddressFromEntity(receiver), BigInteger.valueOf(5L), true)));

        // When
        final var functionCall = contract.call_cryptoTransferExternal(transferList, new ArrayList<>());

        // Then
        assertThatThrownBy(functionCall::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    // Tests AccountBalanceRepository
    @Test
    void accountBalanceNotPersistedYetReturnsPreviousBalance() throws Exception {
        // Given
        final var evm30RecordFile = recordFilePersist(EVM_V_34_BLOCK - 1);
        final var evm30HistoricalRange = setupHistoricalStateInService(EVM_V_34_BLOCK - 1, evm30RecordFile);
        final var historicalRangeAfterEvm34 = setUpHistoricalContext(EVM_V_34_BLOCK);
        final var feeCollector = accountEntityNoEvmAddressPersistHistorical(evm30HistoricalRange);
        accountBalancePersistHistorical(feeCollector.toEntityId(), DEFAULT_ACCOUNT_BALANCE, evm30HistoricalRange);

        setupHistoricalStateInService(EVM_V_34_BLOCK - 1, evm30RecordFile);
        final var contract = testWeb3jService.deploy(EvmCodesHistorical::deploy);

        // Persist the token, the account and the account balance in block 49. In block 50 enter another
        // account balance. The call against block 49 should return the first balance as the second
        // balance is not available at that point yet.
        accountBalancePersistHistorical(
                feeCollector.toEntityId(), DEFAULT_ACCOUNT_BALANCE * 2, historicalRangeAfterEvm34);
        // When
        final var result = contract.call_getAccountBalance(getAddressFromEntity(feeCollector))
                .send();

        // Then
        assertThat(result).isEqualTo(BigInteger.valueOf(DEFAULT_ACCOUNT_BALANCE));
    }
}
