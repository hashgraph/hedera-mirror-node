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
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.SENDER_ALIAS;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.SENDER_PUBLIC_KEY;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.SPENDER_ALIAS;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.SPENDER_PUBLIC_KEY;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.web3.utils.BytecodeUtils;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.mirror.web3.viewmodel.ContractCallRequest;
import com.hedera.mirror.web3.web3j.generated.ERCTestContract;
import com.hedera.mirror.web3.web3j.generated.RedirectTestContract;
import jakarta.annotation.Resource;
import java.math.BigInteger;
import lombok.SneakyThrows;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
class ContractCallServiceERCTokenModificationFunctionsTest extends AbstractContractCallServiceTest {

    private static final String CALL_URI = "/api/v1/contracts/call";

    @Resource
    private MockMvc mockMvc;

    @Resource
    private ObjectMapper objectMapper;

    @SneakyThrows
    private ResultActions contractCall(ContractCallRequest request) {
        return mockMvc.perform(post(CALL_URI)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .content(convert(request)));
    }

    @Test
    void approveFungibleToken() {
        // Given
        final var spender = accountEntityPersist();
        final var token = persistFungibleToken();
        final var amountGranted = 13L;
        final var tokenAddress = toAddress(token.toEntityId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAssociateAccountPersist(contractEntityId, token.toEntityId());
        // When
        final var functionCall = contract.send_approve(
                tokenAddress.toHexString(),
                toAddress(spender.getId()).toHexString(),
                BigInteger.valueOf(amountGranted));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void approveNFT() {
        // Given
        final var spender = accountEntityPersist();
        final var owner = accountEntityPersist();
        final var treasury = accountEntityPersist();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        final var tokenEntity =
                domainBuilder.entity().customize(e -> e.type(EntityType.TOKEN)).persist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE))
                .persist();

        domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId()).serialNumber(1L).accountId(contractEntityId))
                .persist();

        //        final var token = nftPersist(treasury.toEntityId(), contractEntityId);
        //        final var tokenAddress = toAddress(token.getTokenId());
        //        tokenAssociateAccountPersist(contractEntityId, entityIdFromEvmAddress(tokenAddress));
        //        tokenAssociateAccountPersist(spender.toEntityId(), entityIdFromEvmAddress(tokenAddress));
        //        tokenAssociateAccountPersist(owner.toEntityId(), entityIdFromEvmAddress(tokenAddress));
        //        // When
        //        final var functionCall = contract.send_approve(
        //                tokenAddress.toHexString(), toAddress(spender.getId()).toHexString(), BigInteger.ONE);

        tokenAssociateAccountPersist(contractEntityId, tokenEntity.toEntityId());
        tokenAssociateAccountPersist(spender.toEntityId(), tokenEntity.toEntityId());
        tokenAssociateAccountPersist(owner.toEntityId(), tokenEntity.toEntityId());
        // When
        final var functionCall = contract.send_approve(
                toAddress(tokenEntity.toEntityId()).toHexString(),
                toAddress(spender.getId()).toHexString(),
                BigInteger.ONE);
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void deleteAllowanceNFT() {
        // Given
        final var treasury = accountEntityPersist();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var token = nftPersist(treasury.toEntityId(), contractEntityId);
        final var tokenAddress = toAddress(token.getTokenId());
        tokenAssociateAccountPersist(contractEntityId, entityIdFromEvmAddress(tokenAddress));
        // When
        final var functionCall =
                contract.send_approve(tokenAddress.toHexString(), Address.ZERO.toHexString(), BigInteger.ONE);
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void contractDeployNonPayableWithoutValue() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var request = new ContractCallRequest();
        request.setBlock(BlockType.LATEST);
        request.setData(contract.getContractBinary());
        request.setFrom(Address.ZERO.toHexString());
        // When
        contractCall(request)
                // Then
                .andExpect(status().isOk())
                .andExpect(result -> {
                    final var response = result.getResponse().getContentAsString();
                    assertThat(response).contains(BytecodeUtils.extractRuntimeBytecode(contract.getContractBinary()));
                });
    }

    @Test
    void contractDeployNonPayableWithValue() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var request = new ContractCallRequest();
        request.setBlock(BlockType.LATEST);
        request.setData(contract.getContractBinary());
        request.setFrom(Address.ZERO.toHexString());
        request.setValue(10);
        // When
        contractCall(request)
                // Then
                .andExpect(status().isBadRequest());
    }

    @Test
    void approveFungibleTokenWithAlias() {
        // Given
        final var spender = accountPersistWithAlias(SPENDER_ALIAS, SPENDER_PUBLIC_KEY);
        final var token = persistFungibleToken();
        final var amountGranted = 13L;
        final var tokenAddress = toAddress(token.toEntityId());
        tokenAssociateAccountPersist(spender, token.toEntityId());

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAssociateAccountPersist(contractEntityId, token.toEntityId());
        // When
        final var functionCall = contract.send_approve(
                tokenAddress.toHexString(), SPENDER_ALIAS.toHexString(), BigInteger.valueOf(amountGranted));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void approveNFTWithAlias() {
        // Given
        final var treasury = accountEntityPersist();
        accountPersistWithAlias(SPENDER_ALIAS, SPENDER_PUBLIC_KEY);
        final var serialNo = 1L;
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var token = nftPersist(treasury.toEntityId(), contractEntityId);

        final var tokenAddress = toAddress(token.getTokenId());
        tokenAssociateAccountPersist(contractEntityId, entityIdFromEvmAddress(tokenAddress));
        // When
        final var functionCall = contract.send_approve(
                tokenAddress.toHexString(), SPENDER_ALIAS.toHexString(), BigInteger.valueOf(serialNo));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transfer() {
        // Given
        final var recipient = accountEntityPersist();
        final var token = persistFungibleToken();
        final var tokenAddress = toAddress(token.toEntityId().getId());
        tokenAssociateAccountPersist(recipient.toEntityId(), token.toEntityId());

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        tokenAssociateAccountPersist(contractEntityId, token.toEntityId());
        final var amount = 10L;
        // When
        final var functionCall = contract.send_transfer(
                tokenAddress.toHexString(), toAddress(recipient.getId()).toHexString(), BigInteger.valueOf(amount));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFrom() {
        // Given
        final var owner = accountEntityPersist();
        final var recipient = accountEntityPersist();

        final var token = persistFungibleToken();
        tokenAssociateAccountPersist(owner.toEntityId(), token.toEntityId());
        tokenAssociateAccountPersist(recipient.toEntityId(), token.toEntityId());

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAssociateAccountPersist(contractEntityId, token.toEntityId());
        final var amount = 10L;
        fungibleTokenAllowancePersist(contractEntityId, owner.toEntityId(), token.toEntityId(), amount);
        // When
        final var functionCall = contract.send_transferFrom(
                toAddress(token.toEntityId().getId()).toHexString(),
                toAddress(owner.getId()).toHexString(),
                toAddress(recipient.getId()).toHexString(),
                BigInteger.valueOf(amount));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromToHollowAccount() {
        // Given
        final var treasury = accountEntityPersist();
        final var owner = accountEntityPersist();
        final var token = tokenEntityPersist();

        domainBuilder
                .token()
                .customize(t -> t.tokenId(token.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .treasuryAccountId(treasury.toEntityId())
                        .kycKey(new byte[0]))
                .persist();

        tokenAssociateAccountPersist(owner.toEntityId(), token.toEntityId());

        final var hollowAccount = domainBuilder
                .entity()
                .customize(e -> e.key(null).maxAutomaticTokenAssociations(10).receiverSigRequired(false))
                .persist();

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAssociateAccountPersist(contractEntityId, token.toEntityId());
        tokenAssociateAccountPersist(hollowAccount.toEntityId(), token.toEntityId());

        final var amount = 10L;
        fungibleTokenAllowancePersist(contractEntityId, owner.toEntityId(), token.toEntityId(), amount);
        // When
        final var functionCall = contract.send_transferFrom(
                toAddress(token.getId()).toHexString(),
                toAddress(owner.toEntityId()).toHexString(),
                getAliasFromEntity(hollowAccount),
                BigInteger.valueOf(amount));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromNFT() {
        // Given
        final var treasury = accountEntityPersist();
        final var owner = accountEntityPersist();
        final var recipient = accountEntityPersist();
        final var serialNumber = 1L;

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        final var token = nftPersist(treasury.toEntityId(), owner.toEntityId());
        final var tokenAddress = toAddress(token.getTokenId());

        tokenAssociateAccountPersist(owner.toEntityId(), entityIdFromEvmAddress(toAddress(token.getTokenId())));
        tokenAssociateAccountPersist(recipient.toEntityId(), entityIdFromEvmAddress(toAddress(token.getTokenId())));
        tokenAssociateAccountPersist(contractEntityId, entityIdFromEvmAddress(tokenAddress));
        nftTokenAllowancePersist(contractEntityId, owner.toEntityId(), entityIdFromEvmAddress(tokenAddress));
        // When
        final var functionCall = contract.send_transferFromNFT(
                tokenAddress.toHexString(),
                toAddress(owner.toEntityId()).toHexString(),
                toAddress(recipient.toEntityId()).toHexString(),
                BigInteger.valueOf(serialNumber));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferWithAlias() {
        // Given
        final var recipient = accountPersistWithAlias(SPENDER_ALIAS, SPENDER_PUBLIC_KEY);
        final var token = persistFungibleToken();
        tokenAssociateAccountPersist(recipient, token.toEntityId());

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAssociateAccountPersist(contractEntityId, token.toEntityId());

        final var amount = 10L;
        // When
        final var functionCall = contract.send_transfer(
                toAddress(token.toEntityId()).toHexString(), SPENDER_ALIAS.toHexString(), BigInteger.valueOf(amount));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromWithAlias() {
        // Given
        final var owner = accountPersistWithAlias(SENDER_ALIAS, SENDER_PUBLIC_KEY);
        final var recipient = accountPersistWithAlias(SPENDER_ALIAS, SPENDER_PUBLIC_KEY);
        final var token = persistFungibleToken();
        tokenAssociateAccountPersist(owner, token.toEntityId());
        tokenAssociateAccountPersist(recipient, token.toEntityId());

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        tokenAssociateAccountPersist(contractEntityId, token.toEntityId());

        final var amount = 10L;
        fungibleTokenAllowancePersist(contractEntityId, owner, token.toEntityId(), amount);
        // When
        final var functionCall = contract.send_transferFrom(
                toAddress(token.toEntityId().getId()).toHexString(),
                SENDER_ALIAS.toHexString(),
                SPENDER_ALIAS.toHexString(),
                BigInteger.valueOf(amount));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromNFTWithAlias() {
        // Given
        final var treasury = accountEntityPersist();
        final var owner = accountPersistWithAlias(SENDER_ALIAS, SENDER_PUBLIC_KEY);
        final var recipient = accountPersistWithAlias(SPENDER_ALIAS, SPENDER_PUBLIC_KEY);
        final var serialNumber = 1L;
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var token = nftPersist(treasury.toEntityId(), owner);
        tokenAssociateAccountPersist(owner, entityIdFromEvmAddress(toAddress(token.getTokenId())));
        tokenAssociateAccountPersist(recipient, entityIdFromEvmAddress(toAddress(token.getTokenId())));
        tokenAssociateAccountPersist(contractEntityId, entityIdFromEvmAddress(toAddress(token.getTokenId())));
        nftTokenAllowancePersist(contractEntityId, owner, entityIdFromEvmAddress(toAddress(token.getTokenId())));
        // When
        final var functionCall = contract.send_transferFromNFT(
                toAddress(token.getTokenId()).toHexString(),
                SENDER_ALIAS.toHexString(),
                SPENDER_ALIAS.toHexString(),
                BigInteger.valueOf(serialNumber));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void approveFungibleTokenRedirect() {
        // Given
        final var spender = accountEntityPersist();
        final var token = persistFungibleToken();
        final var amountGranted = 13L;
        final var tokenAddress = toAddress(token.toEntityId());
        tokenAssociateAccountPersist(spender.toEntityId(), token.toEntityId());

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAssociateAccountPersist(contractEntityId, token.toEntityId());
        // When
        final var functionCall = contract.send_approveRedirect(
                tokenAddress.toHexString(),
                toAddress(spender.toEntityId()).toHexString(),
                BigInteger.valueOf(amountGranted));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void approveNFTRedirect() {
        // Given
        final var spender = accountEntityPersist();
        final var treasury = accountEntityPersist();
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var token =
                nftPersist(treasury.toEntityId(), entityIdFromEvmAddress(toAddress(contractEntityId.getId())));
        final var tokenAddress = toAddress(token.getTokenId());
        tokenAssociateAccountPersist(contractEntityId, entityIdFromEvmAddress(toAddress(token.getTokenId())));
        // When
        final var functionCall = contract.send_approveRedirect(
                tokenAddress.toHexString(), toAddress(spender.toEntityId()).toHexString(), BigInteger.ONE);
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void deleteAllowanceNFTRedirect() {
        // Given
        final var treasury = accountEntityPersist();
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var token =
                nftPersist(treasury.toEntityId(), entityIdFromEvmAddress(toAddress(contractEntityId.getId())));
        final var tokenAddress = toAddress(token.getTokenId());
        tokenAssociateAccountPersist(contractEntityId, entityIdFromEvmAddress(tokenAddress));
        // When
        final var functionCall =
                contract.send_approveRedirect(tokenAddress.toHexString(), Address.ZERO.toHexString(), BigInteger.ONE);
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void approveFungibleTokenWithAliasRedirect() {
        // Given
        final var spender = accountPersistWithAlias(SPENDER_ALIAS, SPENDER_PUBLIC_KEY);
        final var token = persistFungibleToken();
        final var amountGranted = 13L;
        final var tokenAddress = toAddress(token.toEntityId());
        tokenAssociateAccountPersist(spender, token.toEntityId());

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAssociateAccountPersist(contractEntityId, token.toEntityId());
        // When
        final var functionCall = contract.send_approveRedirect(
                tokenAddress.toHexString(), SPENDER_ALIAS.toHexString(), BigInteger.valueOf(amountGranted));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void approveNFTWithAliasRedirect() {
        // Given
        final var treasury = accountEntityPersist();
        accountPersistWithAlias(SPENDER_ALIAS, SPENDER_PUBLIC_KEY);
        final var serialNo = 1L;

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        final var token = nftPersist(treasury.toEntityId(), contractEntityId);
        final var tokenEntity = entityIdFromEvmAddress(toAddress(token.getTokenId()));
        final var tokenAddress = toAddress(token.getTokenId());
        tokenAssociateAccountPersist(contractEntityId, tokenEntity);
        // When
        final var functionCall = contract.send_approveRedirect(
                tokenAddress.toHexString(), SPENDER_ALIAS.toHexString(), BigInteger.valueOf(serialNo));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferRedirect() {
        // Given
        final var recipient = accountEntityPersist();
        final var treasury = accountEntityPersist();
        final var token = persistFungibleToken();
        final var amount = 10L;

        final var tokenAddress = toAddress(token.toEntityId().getId());
        tokenAssociateAccountPersist(recipient.toEntityId(), token.toEntityId());

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAssociateAccountPersist(contractEntityId, token.toEntityId());
        // When
        final var functionCall = contract.send_transferRedirect(
                tokenAddress.toHexString(),
                toAddress(recipient.toEntityId()).toHexString(),
                BigInteger.valueOf(amount));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromRedirect() {
        // Given
        final var treasury = accountEntityPersist();
        final var owner = accountEntityPersist();
        final var recipient = accountEntityPersist();

        final var token = persistFungibleToken();
        tokenAssociateAccountPersist(owner.toEntityId(), token.toEntityId());
        tokenAssociateAccountPersist(recipient.toEntityId(), token.toEntityId());

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAssociateAccountPersist(contractEntityId, token.toEntityId());
        final var amount = 10L;
        fungibleTokenAllowancePersist(contractEntityId, owner.toEntityId(), token.toEntityId(), amount);
        // When
        final var functionCall = contract.send_transferFromRedirect(
                toAddress(token.toEntityId().getId()).toHexString(),
                toAddress(owner.toEntityId()).toHexString(),
                toAddress(recipient.toEntityId()).toHexString(),
                BigInteger.valueOf(amount));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromToHollowAccountRedirect() {
        // Given
        final var treasury = accountEntityPersist();
        final var owner = accountEntityPersist();
        final var tokenEntity = tokenEntityPersist();

        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .treasuryAccountId(treasury.toEntityId())
                        .kycKey(new byte[0]))
                .persist();

        tokenAssociateAccountPersist(owner.toEntityId(), entityIdFromEvmAddress(toAddress(tokenEntity.getId())));

        final var hollowAccount = domainBuilder
                .entity()
                .customize(e -> e.key(null).maxAutomaticTokenAssociations(10).receiverSigRequired(false))
                .persist();

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAssociateAccountPersist(contractEntityId, entityIdFromEvmAddress(toAddress(tokenEntity.getId())));
        tokenAssociateAccountPersist(hollowAccount.toEntityId(), tokenEntity.toEntityId());

        final var amount = 10L;
        fungibleTokenAllowancePersist(
                contractEntityId, owner.toEntityId(), entityIdFromEvmAddress(toAddress(tokenEntity.getId())), amount);
        // When
        final var functionCall = contract.send_transferFromRedirect(
                toAddress(tokenEntity.getId()).toHexString(),
                toAddress(owner.toEntityId()).toHexString(),
                getAliasFromEntity(hollowAccount),
                BigInteger.valueOf(amount));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromNFTRedirect() {
        // Given
        final var treasury = accountEntityPersist();
        final var owner = accountEntityPersist();
        final var recipient = accountEntityPersist();
        final var serialNumber = 1L;

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        final var token = nftPersist(treasury.toEntityId(), owner.toEntityId());
        final var tokenAddress = toAddress(token.getTokenId());

        tokenAssociateAccountPersist(owner.toEntityId(), entityIdFromEvmAddress(toAddress(token.getTokenId())));
        tokenAssociateAccountPersist(recipient.toEntityId(), entityIdFromEvmAddress(toAddress(token.getTokenId())));
        tokenAssociateAccountPersist(contractEntityId, entityIdFromEvmAddress(toAddress(token.getTokenId())));
        nftTokenAllowancePersist(
                contractEntityId, owner.toEntityId(), entityIdFromEvmAddress(toAddress(token.getTokenId())));
        // When
        final var functionCall = contract.send_transferFromNFTRedirect(
                tokenAddress.toHexString(),
                toAddress(owner.toEntityId()).toHexString(),
                toAddress(recipient.toEntityId()).toHexString(),
                BigInteger.valueOf(serialNumber));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferWithAliasRedirect() {
        // Given
        final var recipient = accountPersistWithAlias(SPENDER_ALIAS, SPENDER_PUBLIC_KEY);
        final var treasury = accountPersistWithAlias(SENDER_ALIAS, SENDER_PUBLIC_KEY);
        final var token = persistFungibleToken();
        tokenAssociateAccountPersist(recipient, token.toEntityId());

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAssociateAccountPersist(contractEntityId, token.toEntityId());

        final var amount = 10L;
        // When
        final var functionCall = contract.send_transferRedirect(
                toAddress(token.toEntityId()).toHexString(), SPENDER_ALIAS.toHexString(), BigInteger.valueOf(amount));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromWithAliasRedirect() {
        // Given
        final var treasury = accountEntityPersist();
        final var owner = accountPersistWithAlias(SENDER_ALIAS, SENDER_PUBLIC_KEY);
        final var recipient = accountPersistWithAlias(SPENDER_ALIAS, SPENDER_PUBLIC_KEY);
        final var token = persistFungibleToken();
        tokenAssociateAccountPersist(owner, token.toEntityId());
        tokenAssociateAccountPersist(recipient, token.toEntityId());

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAssociateAccountPersist(contractEntityId, token.toEntityId());
        final var amount = 10L;
        fungibleTokenAllowancePersist(contractEntityId, owner, token.toEntityId(), amount);
        // When
        final var functionCall = contract.send_transferFromRedirect(
                toAddress(token.toEntityId().getId()).toHexString(),
                SENDER_ALIAS.toHexString(),
                SPENDER_ALIAS.toHexString(),
                BigInteger.valueOf(amount));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromNFTWithAliasRedirect() {
        // Given
        final var treasury = accountEntityPersist();
        final var owner = accountPersistWithAlias(SENDER_ALIAS, SENDER_PUBLIC_KEY);
        final var recipient = accountPersistWithAlias(SPENDER_ALIAS, SPENDER_PUBLIC_KEY);
        final var serialNumber = 1L;
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var token = nftPersist(treasury.toEntityId(), owner);
        tokenAssociateAccountPersist(owner, entityIdFromEvmAddress(toAddress(token.getTokenId())));
        tokenAssociateAccountPersist(recipient, entityIdFromEvmAddress(toAddress(token.getTokenId())));
        tokenAssociateAccountPersist(contractEntityId, entityIdFromEvmAddress(toAddress(token.getTokenId())));
        nftTokenAllowancePersist(contractEntityId, owner, entityIdFromEvmAddress(toAddress(token.getTokenId())));
        // When
        final var functionCall = contract.send_transferFromNFTRedirect(
                toAddress(token.getTokenId()).toHexString(),
                SENDER_ALIAS.toHexString(),
                SPENDER_ALIAS.toHexString(),
                BigInteger.valueOf(serialNumber));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void delegateTransferDoesNotExecuteAndReturnEmpty() throws Exception {
        // Given
        final var recipient = accountEntityPersist();
        final var treasury = accountEntityPersist();
        final var token = persistFungibleToken();
        final var tokenAddress = toAddress(token.toEntityId().getId());
        tokenAssociateAccountPersist(recipient.toEntityId(), token.toEntityId());

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        tokenAssociateAccountPersist(contractEntityId, token.toEntityId());
        final var amount = 10L;
        // When
        contract.send_delegateTransfer(
                        tokenAddress.toHexString(),
                        toAddress(recipient.toEntityId()).toHexString(),
                        BigInteger.valueOf(amount))
                .send();
        final var result = testWeb3jService.getTransactionResult();
        // Then
        assertThat(result).isEqualTo("0x");
    }

    private EntityId accountPersistWithAlias(final Address alias, final ByteString publicKey) {
        return domainBuilder
                .entity()
                .customize(e -> e.evmAddress(alias.toArray()).alias(publicKey.toByteArray()))
                .persist()
                .toEntityId();
    }

    // Same as tokenAccountPersist() except method parameters
    protected void tokenAssociateAccountPersist(final EntityId account, final EntityId tokenEntityId) {
        domainBuilder
                .tokenAccount()
                .customize(e -> e.accountId(account.getId())
                        .tokenId(tokenEntityId.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .freezeStatus(TokenFreezeStatusEnum.UNFROZEN)
                        .associated(true))
                .persist();
    }

    protected void nftTokenAllowancePersist(
            final EntityId spender, final EntityId owner, final EntityId tokenEntityId) {
        domainBuilder
                .nftAllowance()
                .customize(ta -> ta.tokenId(tokenEntityId.getId())
                        .spender(spender.getId())
                        .approvedForAll(true)
                        .owner(owner.getId())
                        .payerAccountId(owner))
                .persist();
    }

    protected void fungibleTokenAllowancePersist(
            final EntityId spender, final EntityId owner, final EntityId tokenEntityId, final Long amount) {
        domainBuilder
                .tokenAllowance()
                .customize(ta -> ta.tokenId(tokenEntityId.getId())
                        .spender(spender.getId())
                        .amount(amount)
                        .owner(owner.getId()))
                .persist();
    }

    @SneakyThrows
    private String convert(Object object) {
        return objectMapper.writeValueAsString(object);
    }
}
