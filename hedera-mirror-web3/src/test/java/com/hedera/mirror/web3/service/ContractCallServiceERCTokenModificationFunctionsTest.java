/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.SENDER_ALIAS;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.SENDER_PUBLIC_KEY;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.SPENDER_ALIAS;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.SPENDER_PUBLIC_KEY;

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.web3.web3j.generated.ERCTestContract;
import com.hedera.mirror.web3.web3j.generated.RedirectTestContract;
import java.math.BigInteger;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;

class ContractCallServiceERCTokenModificationFunctionsTest extends AbstractContractCallServiceTest {

    @Test
    void approveFungibleToken() {
        final var spender = accountPersist();
        final var token = fungibleTokenPersist();
        final var amountGranted = 13L;
        final var tokenEntity = entityIdFromEvmAddress(toAddress(token.getTokenId()));
        final var tokenAddress = toAddress(token.getTokenId());

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        tokenAssociateAccountPersist(contractEntityId, tokenEntity);
        final var functionCall = contract.send_approve(
                tokenAddress.toHexString(), toAddress(spender).toHexString(), BigInteger.valueOf(amountGranted));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void approveNFT() {
        final var spender = accountPersist();
        final var treasury = accountPersist();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var token = nftPersist(treasury, entityIdFromEvmAddress(toAddress(contractEntityId.getId())));
        final var tokenAddress = toAddress(token.getTokenId());
        tokenAssociateAccountPersist(contractEntityId, entityIdFromEvmAddress(tokenAddress));

        final var functionCall = contract.send_approve(
                tokenAddress.toHexString(), toAddress(spender).toHexString(), BigInteger.ONE);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void deleteAllowanceNFT() {
        final var treasury = accountPersist();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var token = nftPersist(treasury, entityIdFromEvmAddress(toAddress(contractEntityId.getId())));
        final var tokenAddress = toAddress(token.getTokenId());
        tokenAssociateAccountPersist(contractEntityId, entityIdFromEvmAddress(tokenAddress));

        final var functionCall =
                contract.send_approve(tokenAddress.toHexString(), Address.ZERO.toHexString(), BigInteger.ONE);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void approveFungibleTokenWithAlias() {
        final var spender = accountPersistWithAlias(SPENDER_ALIAS, SPENDER_PUBLIC_KEY);
        final var treasury = accountPersist();
        final var token = fungibleTokenPersist(treasury);
        final var amountGranted = 13L;
        final var tokenEntity = entityIdFromEvmAddress(toAddress(token.getTokenId()));
        final var tokenAddress = toAddress(token.getTokenId());
        tokenAssociateAccountPersist(spender, entityIdFromEvmAddress(toAddress(token.getTokenId())));

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAssociateAccountPersist(contractEntityId, tokenEntity);

        final var functionCall = contract.send_approve(
                tokenAddress.toHexString(), SPENDER_ALIAS.toHexString(), BigInteger.valueOf(amountGranted));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void approveNFTWithAlias() {
        final var treasury = accountPersist();
        accountPersistWithAlias(SPENDER_ALIAS, SPENDER_PUBLIC_KEY);
        final var serialNo = 1L;
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var token = nftPersist(treasury, entityIdFromEvmAddress(toAddress(contractEntityId.getId())));

        final var tokenAddress = toAddress(token.getTokenId());
        tokenAssociateAccountPersist(contractEntityId, entityIdFromEvmAddress(tokenAddress));

        final var functionCall = contract.send_approve(
                tokenAddress.toHexString(), SPENDER_ALIAS.toHexString(), BigInteger.valueOf(serialNo));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transfer() {
        final var recipient = accountPersist();
        final var treasury = accountPersist();
        final var token = fungibleTokenPersist(treasury);
        final var tokenEntity = entityIdFromEvmAddress(toAddress(token.getTokenId()));
        final var tokenAddress = toAddress(tokenEntity.getId());
        tokenAssociateAccountPersist(recipient, entityIdFromEvmAddress(toAddress(tokenEntity.getId())));

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        tokenAssociateAccountPersist(contractEntityId, tokenEntity);
        final var amount = 10L;
        final var functionCall = contract.send_transfer(
                tokenAddress.toHexString(), toAddress(recipient).toHexString(), BigInteger.valueOf(amount));

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFrom() {
        final var treasury = accountPersist();
        final var owner = accountPersist();
        final var recipient = accountPersist();

        final var token = fungibleTokenPersist(treasury);
        final var tokenEntity = entityIdFromEvmAddress(toAddress(token.getTokenId()));
        tokenAssociateAccountPersist(owner, tokenEntity);
        tokenAssociateAccountPersist(recipient, tokenEntity);

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAssociateAccountPersist(contractEntityId, tokenEntity);
        final var amount = 10L;
        fungibleTokenAllowancePersist(
                contractEntityId, owner, entityIdFromEvmAddress(toAddress(tokenEntity.getId())), amount);

        final var functionCall = contract.send_transferFrom(
                toAddress(tokenEntity.getId()).toHexString(),
                toAddress(owner).toHexString(),
                toAddress(recipient).toHexString(),
                BigInteger.valueOf(amount));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromToHollowAccount() {
        final var treasury = accountPersist();
        final var owner = accountPersist();
        final var token = domainBuilder.entity().customize(e -> e.type(TOKEN)).persist();

        domainBuilder
                .token()
                .customize(t -> t.tokenId(token.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .treasuryAccountId(treasury)
                        .kycKey(new byte[0]))
                .persist();

        tokenAssociateAccountPersist(owner, token.toEntityId());

        final var hollowAccount = domainBuilder
                .entity()
                .customize(e -> e.key(null).maxAutomaticTokenAssociations(10))
                .persist()
                .toEntityId();

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAssociateAccountPersist(contractEntityId, token.toEntityId());

        final var amount = 10L;
        fungibleTokenAllowancePersist(contractEntityId, owner, token.toEntityId(), amount);

        final var functionCall = contract.send_transferFrom(
                toAddress(token.getId()).toHexString(),
                toAddress(owner).toHexString(),
                toAddress(hollowAccount.getId()).toHexString(),
                BigInteger.valueOf(amount));

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromNFT() {
        final var treasury = accountPersist();
        final var owner = accountPersist();
        final var recipient = accountPersist();
        final var serialNumber = 1L;

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        final var token = nftPersist(treasury, owner);
        final var tokenAddress = toAddress(token.getTokenId());

        tokenAssociateAccountPersist(owner, entityIdFromEvmAddress(toAddress(token.getTokenId())));
        tokenAssociateAccountPersist(recipient, entityIdFromEvmAddress(toAddress(token.getTokenId())));
        tokenAssociateAccountPersist(contractEntityId, entityIdFromEvmAddress(tokenAddress));
        nftTokenAllowancePersist(contractEntityId, owner, entityIdFromEvmAddress(tokenAddress));

        final var functionCall = contract.send_transferFromNFT(
                tokenAddress.toHexString(),
                toAddress(owner).toHexString(),
                toAddress(recipient).toHexString(),
                BigInteger.valueOf(serialNumber));

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferWithAlias() {
        final var recipient = accountPersistWithAlias(SPENDER_ALIAS, SPENDER_PUBLIC_KEY);
        final var treasury = accountPersistWithAlias(SENDER_ALIAS, SENDER_PUBLIC_KEY);
        final var token = fungibleTokenPersist(treasury);
        tokenAssociateAccountPersist(recipient, entityIdFromEvmAddress(toAddress(token.getTokenId())));

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAssociateAccountPersist(contractEntityId, entityIdFromEvmAddress(toAddress(token.getTokenId())));

        final var amount = 10L;
        final var functionCall = contract.send_transfer(
                toAddress(token.getTokenId()).toHexString(), SPENDER_ALIAS.toHexString(), BigInteger.valueOf(amount));

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromWithAlias() {
        final var treasury = accountPersist();
        final var owner = accountPersistWithAlias(SENDER_ALIAS, SENDER_PUBLIC_KEY);
        final var recipient = accountPersistWithAlias(SPENDER_ALIAS, SPENDER_PUBLIC_KEY);
        final var token = fungibleTokenPersist(treasury);
        final var tokenEntity = entityIdFromEvmAddress(toAddress(token.getTokenId()));
        tokenAssociateAccountPersist(owner, tokenEntity);
        tokenAssociateAccountPersist(recipient, tokenEntity);

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        tokenAssociateAccountPersist(contractEntityId, tokenEntity);

        final var amount = 10L;
        fungibleTokenAllowancePersist(contractEntityId, owner, tokenEntity, amount);

        final var functionCall = contract.send_transferFrom(
                toAddress(tokenEntity.getId()).toHexString(),
                SENDER_ALIAS.toHexString(),
                SPENDER_ALIAS.toHexString(),
                BigInteger.valueOf(amount));

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromNFTWithAlias() {
        final var treasury = accountPersist();
        final var owner = accountPersistWithAlias(SENDER_ALIAS, SENDER_PUBLIC_KEY);
        final var recipient = accountPersistWithAlias(SPENDER_ALIAS, SPENDER_PUBLIC_KEY);
        final var serialNumber = 1L;
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var token = nftPersist(treasury, owner);
        tokenAssociateAccountPersist(owner, entityIdFromEvmAddress(toAddress(token.getTokenId())));
        tokenAssociateAccountPersist(recipient, entityIdFromEvmAddress(toAddress(token.getTokenId())));
        tokenAssociateAccountPersist(contractEntityId, entityIdFromEvmAddress(toAddress(token.getTokenId())));
        nftTokenAllowancePersist(contractEntityId, owner, entityIdFromEvmAddress(toAddress(token.getTokenId())));

        final var functionCall = contract.send_transferFromNFT(
                toAddress(token.getTokenId()).toHexString(),
                SENDER_ALIAS.toHexString(),
                SPENDER_ALIAS.toHexString(),
                BigInteger.valueOf(serialNumber));

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void approveFungibleTokenRedirect() {
        final var spender = accountPersist();
        final var token = fungibleTokenPersist();
        final var amountGranted = 13L;
        final var tokenEntity = entityIdFromEvmAddress(toAddress(token.getTokenId()));
        final var tokenAddress = toAddress(token.getTokenId());
        tokenAssociateAccountPersist(spender, entityIdFromEvmAddress(toAddress(token.getTokenId())));

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        tokenAssociateAccountPersist(contractEntityId, tokenEntity);
        final var functionCall = contract.send_approveRedirect(
                tokenAddress.toHexString(), toAddress(spender).toHexString(), BigInteger.valueOf(amountGranted));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void approveNFTRedirect() {
        final var spender = accountPersist();
        final var treasury = accountPersist();
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var token = nftPersist(treasury, entityIdFromEvmAddress(toAddress(contractEntityId.getId())));
        final var tokenAddress = toAddress(token.getTokenId());
        tokenAssociateAccountPersist(contractEntityId, entityIdFromEvmAddress(toAddress(token.getTokenId())));

        final var functionCall = contract.send_approveRedirect(
                tokenAddress.toHexString(), toAddress(spender).toHexString(), BigInteger.ONE);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void deleteAllowanceNFTRedirect() {
        final var treasury = accountPersist();
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var token = nftPersist(treasury, entityIdFromEvmAddress(toAddress(contractEntityId.getId())));
        final var tokenAddress = toAddress(token.getTokenId());
        tokenAssociateAccountPersist(contractEntityId, entityIdFromEvmAddress(tokenAddress));

        final var functionCall =
                contract.send_approveRedirect(tokenAddress.toHexString(), Address.ZERO.toHexString(), BigInteger.ONE);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void approveFungibleTokenWithAliasRedirect() {
        final var spender = accountPersistWithAlias(SPENDER_ALIAS, SPENDER_PUBLIC_KEY);
        final var token = fungibleTokenPersist();
        final var amountGranted = 13L;
        final var tokenEntity = entityIdFromEvmAddress(toAddress(token.getTokenId()));
        final var tokenAddress = toAddress(token.getTokenId());
        tokenAssociateAccountPersist(spender, entityIdFromEvmAddress(toAddress(token.getTokenId())));

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAssociateAccountPersist(contractEntityId, tokenEntity);

        final var functionCall = contract.send_approveRedirect(
                tokenAddress.toHexString(), SPENDER_ALIAS.toHexString(), BigInteger.valueOf(amountGranted));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void approveNFTWithAliasRedirect() {
        final var treasury = accountPersist();
        accountPersistWithAlias(SPENDER_ALIAS, SPENDER_PUBLIC_KEY);
        final var serialNo = 1L;

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        final var token = nftPersist(treasury, contractEntityId);
        final var tokenEntity = entityIdFromEvmAddress(toAddress(token.getTokenId()));
        final var tokenAddress = toAddress(token.getTokenId());
        tokenAssociateAccountPersist(contractEntityId, tokenEntity);

        final var functionCall = contract.send_approveRedirect(
                tokenAddress.toHexString(), SPENDER_ALIAS.toHexString(), BigInteger.valueOf(serialNo));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferRedirect() {
        final var recipient = accountPersist();
        final var treasury = accountPersist();
        final var token = fungibleTokenPersist(treasury);
        final var amount = 10L;

        final var tokenEntity = entityIdFromEvmAddress(toAddress(token.getTokenId()));
        final var tokenAddress = toAddress(tokenEntity.getId());
        tokenAssociateAccountPersist(recipient, entityIdFromEvmAddress(toAddress(tokenEntity.getId())));

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        tokenAssociateAccountPersist(contractEntityId, tokenEntity);
        final var functionCall = contract.send_transferRedirect(
                tokenAddress.toHexString(), toAddress(recipient).toHexString(), BigInteger.valueOf(amount));

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromRedirect() {
        final var treasury = accountPersist();
        final var owner = accountPersist();
        final var recipient = accountPersist();

        final var token = fungibleTokenPersist(treasury);
        final var tokenEntity = entityIdFromEvmAddress(toAddress(token.getTokenId()));
        tokenAssociateAccountPersist(owner, tokenEntity);
        tokenAssociateAccountPersist(recipient, tokenEntity);

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAssociateAccountPersist(contractEntityId, entityIdFromEvmAddress(toAddress(tokenEntity.getId())));
        final var amount = 10L;
        fungibleTokenAllowancePersist(contractEntityId, owner, tokenEntity, amount);

        final var functionCall = contract.send_transferFromRedirect(
                toAddress(tokenEntity.getId()).toHexString(),
                toAddress(owner).toHexString(),
                toAddress(recipient).toHexString(),
                BigInteger.valueOf(amount));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromToHollowAccountRedirect() {
        final var treasury = accountPersist();
        final var owner = accountPersist();
        final var tokenEntity =
                domainBuilder.entity().customize(e -> e.type(TOKEN)).persist();

        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .treasuryAccountId(treasury)
                        .kycKey(new byte[0]))
                .persist();

        tokenAssociateAccountPersist(owner, entityIdFromEvmAddress(toAddress(tokenEntity.getId())));

        final var hollowAccount = domainBuilder
                .entity()
                .customize(e -> e.key(null).maxAutomaticTokenAssociations(10))
                .persist()
                .toEntityId();

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAssociateAccountPersist(contractEntityId, entityIdFromEvmAddress(toAddress(tokenEntity.getId())));
        final var amount = 10L;
        fungibleTokenAllowancePersist(
                contractEntityId, owner, entityIdFromEvmAddress(toAddress(tokenEntity.getId())), amount);

        final var functionCall = contract.send_transferFromRedirect(
                toAddress(tokenEntity.getId()).toHexString(),
                toAddress(owner).toHexString(),
                toAddress(hollowAccount.getId()).toHexString(),
                BigInteger.valueOf(amount));

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromNFTRedirect() {
        final var treasury = accountPersist();
        final var owner = accountPersist();
        final var recipient = accountPersist();
        final var serialNumber = 1L;

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        final var token = nftPersist(treasury, owner);
        final var tokenAddress = toAddress(token.getTokenId());

        tokenAssociateAccountPersist(owner, entityIdFromEvmAddress(toAddress(token.getTokenId())));
        tokenAssociateAccountPersist(recipient, entityIdFromEvmAddress(toAddress(token.getTokenId())));
        tokenAssociateAccountPersist(contractEntityId, entityIdFromEvmAddress(toAddress(token.getTokenId())));
        nftTokenAllowancePersist(contractEntityId, owner, entityIdFromEvmAddress(toAddress(token.getTokenId())));

        final var functionCall = contract.send_transferFromNFTRedirect(
                tokenAddress.toHexString(),
                toAddress(owner).toHexString(),
                toAddress(recipient).toHexString(),
                BigInteger.valueOf(serialNumber));

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferWithAliasRedirect() {
        final var recipient = accountPersistWithAlias(SPENDER_ALIAS, SPENDER_PUBLIC_KEY);
        final var treasury = accountPersistWithAlias(SENDER_ALIAS, SENDER_PUBLIC_KEY);
        final var token = fungibleTokenPersist(treasury);
        tokenAssociateAccountPersist(recipient, entityIdFromEvmAddress(toAddress(token.getTokenId())));

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAssociateAccountPersist(contractEntityId, entityIdFromEvmAddress(toAddress(token.getTokenId())));

        final var amount = 10L;
        final var functionCall = contract.send_transferRedirect(
                toAddress(token.getTokenId()).toHexString(), SPENDER_ALIAS.toHexString(), BigInteger.valueOf(amount));

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromWithAliasRedirect() {
        final var treasury = accountPersist();
        final var owner = accountPersistWithAlias(SENDER_ALIAS, SENDER_PUBLIC_KEY);
        final var recipient = accountPersistWithAlias(SPENDER_ALIAS, SPENDER_PUBLIC_KEY);
        final var token = fungibleTokenPersist(treasury);
        final var tokenEntity = entityIdFromEvmAddress(toAddress(token.getTokenId()));
        tokenAssociateAccountPersist(owner, tokenEntity);
        tokenAssociateAccountPersist(recipient, tokenEntity);

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAssociateAccountPersist(contractEntityId, tokenEntity);
        final var amount = 10L;
        fungibleTokenAllowancePersist(
                contractEntityId, owner, entityIdFromEvmAddress(toAddress(tokenEntity.getId())), amount);

        final var functionCall = contract.send_transferFromRedirect(
                toAddress(tokenEntity.getId()).toHexString(),
                SENDER_ALIAS.toHexString(),
                SPENDER_ALIAS.toHexString(),
                BigInteger.valueOf(amount));

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromNFTWithAliasRedirect() {
        final var treasury = accountPersist();
        final var owner = accountPersistWithAlias(SENDER_ALIAS, SENDER_PUBLIC_KEY);
        final var recipient = accountPersistWithAlias(SPENDER_ALIAS, SPENDER_PUBLIC_KEY);
        final var serialNumber = 1L;
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var token = nftPersist(treasury, owner);
        tokenAssociateAccountPersist(owner, entityIdFromEvmAddress(toAddress(token.getTokenId())));
        tokenAssociateAccountPersist(recipient, entityIdFromEvmAddress(toAddress(token.getTokenId())));
        tokenAssociateAccountPersist(contractEntityId, entityIdFromEvmAddress(toAddress(token.getTokenId())));
        nftTokenAllowancePersist(contractEntityId, owner, entityIdFromEvmAddress(toAddress(token.getTokenId())));

        final var functionCall = contract.send_transferFromNFTRedirect(
                toAddress(token.getTokenId()).toHexString(),
                SENDER_ALIAS.toHexString(),
                SPENDER_ALIAS.toHexString(),
                BigInteger.valueOf(serialNumber));

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    private EntityId accountPersistWithAlias(final Address alias, final ByteString publicKey) {
        return domainBuilder
                .entity()
                .customize(e -> e.evmAddress(alias.toArray()).alias(publicKey.toByteArray()))
                .persist()
                .toEntityId();
    }

    private EntityId accountPersist() {
        return domainBuilder
                .entity()
                .customize(e -> e.evmAddress(null).balance(12L))
                .persist()
                .toEntityId();
    }

    private Token fungibleTokenPersist(final EntityId treasuryEntityId) {
        final var tokenEntity =
                domainBuilder.entity().customize(e -> e.type(TOKEN)).persist();

        return domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .treasuryAccountId(treasuryEntityId)
                        .kycKey(domainBuilder.key()))
                .persist();
    }

    private Token fungibleTokenPersist() {
        final var tokenEntity =
                domainBuilder.entity().customize(e -> e.type(TOKEN)).persist();

        return domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(TokenTypeEnum.FUNGIBLE_COMMON))
                .persist();
    }

    private Token nftPersist(final EntityId treasuryEntityId, final EntityId ownerEntityId) {
        final var nftEntity =
                domainBuilder.entity().customize(e -> e.type(TOKEN)).persist();

        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(nftEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(treasuryEntityId)
                        .kycKey(domainBuilder.key()))
                .persist();

        domainBuilder
                .nft()
                .customize(n -> n.accountId(ownerEntityId)
                        .tokenId(nftEntity.getId())
                        .metadata("NFT_METADATA_URI".getBytes())
                        .serialNumber(1))
                .persist();
        return token;
    }

    protected void tokenAssociateAccountPersist(final EntityId account, final EntityId tokenEntityId) {
        domainBuilder
                .tokenAccount()
                .customize(e -> e.accountId(account.getId())
                        .tokenId(tokenEntityId.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED)
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
}
