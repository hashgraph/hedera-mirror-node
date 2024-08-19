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
import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.web3.web3j.generated.ERCTestContract;
import com.hedera.mirror.web3.web3j.generated.RedirectTestContract;
import java.math.BigInteger;
import org.apache.tuweni.bytes.Bytes;
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
        final var contractId = testWeb3jService.getEntityId();

        tokenAssociateContractPersist(tokenEntity, contractId);
        final var functionCall = contract.send_approve(
                tokenAddress.toHexString(), toAddress(spender).toHexString(), BigInteger.valueOf(amountGranted));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void approveNFT() {
        final var spender = accountPersist();
        final var treasuryEntityId = accountPersist();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractId = testWeb3jService.getEntityId();

        final var nftEntity =
                domainBuilder.entity().customize(e -> e.type(TOKEN)).persist();

        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(nftEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(treasuryEntityId))
                .persist();

        domainBuilder
                .nft()
                .customize(n -> n.accountId(treasuryEntityId)
                        .accountId(entityIdFromEvmAddress(toAddress(contractId)))
                        .tokenId(nftEntity.getId())
                        .serialNumber(1))
                .persist();

        final var tokenEntity = entityIdFromEvmAddress(toAddress(token.getTokenId()));
        final var tokenAddress = toAddress(token.getTokenId());

        tokenAssociateContractPersist(tokenEntity, contractId);
        final var functionCall = contract.send_approve(
                tokenAddress.toHexString(), toAddress(spender).toHexString(), BigInteger.ONE);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void deleteAllowanceNFT() {
        final var treasuryEntityId = accountPersist();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractId = testWeb3jService.getEntityId();

        final var nftEntity =
                domainBuilder.entity().customize(e -> e.type(TOKEN)).persist();

        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(nftEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(treasuryEntityId))
                .persist();

        domainBuilder
                .nft()
                .customize(n -> n.accountId(treasuryEntityId)
                        .accountId(entityIdFromEvmAddress(toAddress(contractId)))
                        .tokenId(nftEntity.getId())
                        .serialNumber(1))
                .persist();

        final var tokenEntity = entityIdFromEvmAddress(toAddress(token.getTokenId()));
        final var tokenAddress = toAddress(token.getTokenId());

        tokenAssociateContractPersist(tokenEntity, contractId);
        final var functionCall =
                contract.send_approve(tokenAddress.toHexString(), Address.ZERO.toHexString(), BigInteger.ONE);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void approveFungibleTokenWithAlias() {
        final var spenderPublicKeyString = "3a2102ff806fecbd31b4c377293cba8d2b78725965a4990e0ff1b1b29a1d2c61402310";
        final var spenderPublicKey = publicKeyFromString(spenderPublicKeyString);
        final var spenderAlias = generateAlias(spenderPublicKey);

        final var token = fungibleTokenPersist();
        final var amountGranted = 13L;
        final var tokenEntity = entityIdFromEvmAddress(toAddress(token.getTokenId()));
        final var tokenAddress = toAddress(token.getTokenId());

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractId = testWeb3jService.getEntityId();
        tokenAssociateContractPersist(tokenEntity, contractId);

        final var functionCall = contract.send_approve(
                tokenAddress.toHexString(), spenderAlias.toHexString(), BigInteger.valueOf(amountGranted));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void approveNFTWithAlias() {
        final var spenderPublicKeyString = "3a2102ff806fecbd31b4c377293cba8d2b78725965a4990e0ff1b1b29a1d2c61402310";
        final var spenderPublicKey = publicKeyFromString(spenderPublicKeyString);
        final var spenderAlias = generateAlias(spenderPublicKey);
        final var treasuryEntityId = accountPersist();
        spenderEntityPersistWithAlias(spenderAlias, spenderPublicKey);
        final var serialNo = 1L;
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractId = testWeb3jService.getEntityId();

        final var nftEntity =
                domainBuilder.entity().customize(e -> e.type(TOKEN)).persist();
        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(nftEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(treasuryEntityId))
                .persist();
        domainBuilder
                .nft()
                .customize(n -> n.accountId(treasuryEntityId)
                        .accountId(entityIdFromEvmAddress(toAddress(contractId)))
                        .tokenId(nftEntity.getId())
                        .serialNumber(serialNo))
                .persist();

        final var tokenEntity = entityIdFromEvmAddress(toAddress(token.getTokenId()));
        final var tokenAddress = toAddress(token.getTokenId());
        tokenAssociateContractPersist(tokenEntity, contractId);

        final var functionCall = contract.send_approve(
                tokenAddress.toHexString(), spenderAlias.toHexString(), BigInteger.valueOf(serialNo));
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
        final var contractId = testWeb3jService.getEntityId();

        tokenAssociateContractPersist(tokenEntity, contractId);
        final var amount = 10L;
        final var functionCall = contract.send_transfer(
                tokenAddress.toHexString(), toAddress(recipient).toHexString(), BigInteger.valueOf(amount));

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFrom() {
        final var treasury = accountPersist();
        final var spender = accountPersist();
        final var recipient = accountPersist();
        final var amount = 10L;

        final var tokenEntityId = fungibleTokenPersist(treasury);
        final var tokenEntity = entityIdFromEvmAddress(toAddress(tokenEntityId.getTokenId()));
        tokenAssociateAccountPersist(spender, tokenEntity);
        tokenAssociateAccountPersist(recipient, tokenEntity);

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractId = testWeb3jService.getEntityId();

        domainBuilder
                .tokenAccount()
                .customize(ta ->
                        ta.tokenId(tokenEntity.getId()).accountId(contractId).associated(true))
                .persist();

        domainBuilder
                .tokenAllowance()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .spender(contractId)
                        .amount(amount)
                        .owner(spender.getId()))
                .persist();

        final var functionCall = contract.send_transferFrom(
                toAddress(tokenEntity.getId()).toHexString(),
                toAddress(spender).toHexString(),
                toAddress(recipient).toHexString(),
                BigInteger.valueOf(amount));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromToHollowAccount() {
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

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractId = testWeb3jService.getEntityId();

        domainBuilder
                .tokenAccount()
                .customize(ta ->
                        ta.tokenId(tokenEntity.getId()).accountId(contractId).associated(true))
                .persist();

        final var amount = 10L;
        domainBuilder
                .tokenAllowance()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .spender(contractId)
                        .amount(amount)
                        .owner(owner.getId()))
                .persist();

        final var functionCall = contract.send_transferFrom(
                toAddress(tokenEntity.getId()).toHexString(),
                toAddress(owner).toHexString(),
                toAddress(hollowAccount.getId()).toHexString(),
                BigInteger.valueOf(amount));

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromNFT() {
        final var senderPublicKeyString = "3a2103af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d";
        final var senderPublicKey = publicKeyFromString(senderPublicKeyString);
        final var senderAlias = generateAlias(senderPublicKey);
        final var treasury = senderEntityPersistWithAlias(senderAlias, senderPublicKey);
        final var spender = accountPersist();
        final var recipient = accountPersist();
        final var serialNumber = 1L;

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractId = testWeb3jService.getEntityId();

        final var nftEntity = nftPersist(treasury, spender);
        final var tokenAddress = toAddress(nftEntity.getTokenId());

        tokenAssociateAccountPersist(spender, entityIdFromEvmAddress(toAddress(nftEntity.getTokenId())));
        tokenAssociateAccountPersist(recipient, entityIdFromEvmAddress(toAddress(nftEntity.getTokenId())));

        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(nftEntity.getTokenId())
                        .accountId(contractId)
                        .accountId(contractId)
                        .associated(true)
                        .kycStatus(TokenKycStatusEnum.GRANTED))
                .persist();

        domainBuilder
                .nftAllowance()
                .customize(ta -> ta.tokenId(nftEntity.getTokenId())
                        .spender(contractId)
                        .owner(spender.getId())
                        .tokenId(serialNumber)
                        .approvedForAll(true)
                        .payerAccountId(spender))
                .persist();

        final var functionCall = contract.send_transferFromNFT(
                tokenAddress.toHexString(),
                toAddress(spender).toHexString(),
                toAddress(recipient).toHexString(),
                BigInteger.valueOf(serialNumber));

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferWithAlias() {
        final var spenderPublicKeyString = "3a2102ff806fecbd31b4c377293cba8d2b78725965a4990e0ff1b1b29a1d2c61402310";
        final var spenderPublicKey = publicKeyFromString(spenderPublicKeyString);
        final var spenderAlias = generateAlias(spenderPublicKey);
        final var senderPublicKeyString = "3a2103af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d";
        final var senderPublicKey = publicKeyFromString(senderPublicKeyString);
        final var senderAlias = generateAlias(senderPublicKey);
        final var recipient = spenderEntityPersistWithAlias(spenderAlias, spenderPublicKey);
        final var treasury = senderEntityPersistWithAlias(senderAlias, senderPublicKey);
        final var tokenEntity = fungibleTokenPersist(treasury);

        tokenAssociateAccountPersist(recipient, entityIdFromEvmAddress(toAddress(tokenEntity.getTokenId())));

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractId = testWeb3jService.getEntityId();

        tokenAssociateContractPersist(entityIdFromEvmAddress(toAddress(tokenEntity.getTokenId())), contractId);
        final var amount = 10L;
        final var functionCall = contract.send_transfer(
                toAddress(tokenEntity.getTokenId()).toHexString(),
                spenderAlias.toHexString(),
                BigInteger.valueOf(amount));

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromWithAlias() {
        final var spenderPublicKeyString = "3a2102ff806fecbd31b4c377293cba8d2b78725965a4990e0ff1b1b29a1d2c61402310";
        final var spenderPublicKey = publicKeyFromString(spenderPublicKeyString);
        final var spenderAlias = generateAlias(spenderPublicKey);
        final var treasury = accountPersist();
        final var senderPublicKeyString = "3a2103af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d";
        final var senderPublicKey = publicKeyFromString(senderPublicKeyString);
        final var senderAlias = generateAlias(senderPublicKey);
        final var spender = senderEntityPersistWithAlias(senderAlias, senderPublicKey);
        final var recipient = spenderEntityPersistWithAlias(spenderAlias, spenderPublicKey);
        final var tokenEntityId = fungibleTokenPersist(treasury);
        final var tokenEntity = entityIdFromEvmAddress(toAddress(tokenEntityId.getTokenId()));
        tokenAssociateAccountPersist(spender, tokenEntity);
        tokenAssociateAccountPersist(recipient, tokenEntity);

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractId = testWeb3jService.getEntityId();

        domainBuilder
                .tokenAccount()
                .customize(ta ->
                        ta.tokenId(tokenEntity.getId()).accountId(contractId).associated(true))
                .persist();

        final var amount = 10L;
        domainBuilder
                .tokenAllowance()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .spender(contractId)
                        .amount(amount)
                        .owner(spender.getId()))
                .persist();

        final var functionCall = contract.send_transferFrom(
                toAddress(tokenEntity.getId()).toHexString(),
                senderAlias.toHexString(),
                spenderAlias.toHexString(),
                BigInteger.valueOf(amount));

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromNFTWithAlias() {
        final var spenderPublicKeyString = "3a2102ff806fecbd31b4c377293cba8d2b78725965a4990e0ff1b1b29a1d2c61402310";
        final var spenderPublicKey = publicKeyFromString(spenderPublicKeyString);
        final var spenderAlias = generateAlias(spenderPublicKey);
        final var senderPublicKeyString = "3a2103af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d";
        final var senderPublicKey = publicKeyFromString(senderPublicKeyString);
        final var senderAlias = generateAlias(senderPublicKey);
        final var treasury = accountPersist();
        final var spender = senderEntityPersistWithAlias(senderAlias, senderPublicKey);
        final var recipient = spenderEntityPersistWithAlias(spenderAlias, spenderPublicKey);
        final var serialNumber = 1L;

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractId = testWeb3jService.getEntityId();

        final var nftEntity = nftPersist(treasury, spender);

        tokenAssociateAccountPersist(spender, entityIdFromEvmAddress(toAddress(nftEntity.getTokenId())));
        tokenAssociateAccountPersist(recipient, entityIdFromEvmAddress(toAddress(nftEntity.getTokenId())));

        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(nftEntity.getTokenId())
                        .accountId(contractId)
                        .associated(true)
                        .kycStatus(TokenKycStatusEnum.GRANTED))
                .persist();

        domainBuilder
                .nftAllowance()
                .customize(ta -> ta.tokenId(nftEntity.getTokenId())
                        .spender(contractId)
                        .owner(spender.getId())
                        .tokenId(serialNumber)
                        .approvedForAll(true)
                        .payerAccountId(spender))
                .persist();

        final var functionCall = contract.send_transferFromNFT(
                toAddress(nftEntity.getTokenId()).toHexString(),
                senderAlias.toHexString(),
                spenderAlias.toHexString(),
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
        final var contractId = testWeb3jService.getEntityId();

        tokenAssociateContractPersist(tokenEntity, contractId);
        final var functionCall = contract.send_approveRedirect(
                tokenAddress.toHexString(), toAddress(spender).toHexString(), BigInteger.valueOf(amountGranted));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void approveNFTRedirect() {
        final var spender = accountPersist();
        final var treasuryEntityId = accountPersist();
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractId = testWeb3jService.getEntityId();

        final var nftEntity =
                domainBuilder.entity().customize(e -> e.type(TOKEN)).persist();

        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(nftEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(treasuryEntityId))
                .persist();

        domainBuilder
                .nft()
                .customize(n -> n.accountId(treasuryEntityId)
                        .accountId(entityIdFromEvmAddress(toAddress(contractId)))
                        .tokenId(nftEntity.getId())
                        .serialNumber(1))
                .persist();

        final var tokenEntity = entityIdFromEvmAddress(toAddress(token.getTokenId()));
        final var tokenAddress = toAddress(token.getTokenId());

        tokenAssociateContractPersist(tokenEntity, contractId);
        final var functionCall = contract.send_approveRedirect(
                tokenAddress.toHexString(), toAddress(spender).toHexString(), BigInteger.ONE);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void deleteAllowanceNFTRedirect() {
        final var treasuryEntityId = accountPersist();
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractId = testWeb3jService.getEntityId();

        final var nftEntity =
                domainBuilder.entity().customize(e -> e.type(TOKEN)).persist();

        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(nftEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(treasuryEntityId))
                .persist();

        domainBuilder
                .nft()
                .customize(n -> n.accountId(treasuryEntityId)
                        .accountId(entityIdFromEvmAddress(toAddress(contractId)))
                        .tokenId(nftEntity.getId())
                        .serialNumber(1))
                .persist();

        final var tokenEntity = entityIdFromEvmAddress(toAddress(token.getTokenId()));
        final var tokenAddress = toAddress(token.getTokenId());

        tokenAssociateContractPersist(tokenEntity, contractId);
        final var functionCall =
                contract.send_approveRedirect(tokenAddress.toHexString(), Address.ZERO.toHexString(), BigInteger.ONE);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void approveFungibleTokenWithAliasRedirect() {
        final var spenderPublicKeyString = "3a2102ff806fecbd31b4c377293cba8d2b78725965a4990e0ff1b1b29a1d2c61402310";
        final var spenderPublicKey = publicKeyFromString(spenderPublicKeyString);
        final var spenderAlias = generateAlias(spenderPublicKey);
        final var spender = spenderEntityPersistWithAlias(spenderAlias, spenderPublicKey);
        final var token = fungibleTokenPersist();
        final var amountGranted = 13L;
        final var tokenEntity = entityIdFromEvmAddress(toAddress(token.getTokenId()));
        final var tokenAddress = toAddress(token.getTokenId());

        tokenAssociateAccountPersist(spender, entityIdFromEvmAddress(toAddress(token.getTokenId())));

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractId = testWeb3jService.getEntityId();
        tokenAssociateContractPersist(tokenEntity, contractId);

        final var functionCall = contract.send_approveRedirect(
                tokenAddress.toHexString(), spenderAlias.toHexString(), BigInteger.valueOf(amountGranted));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void approveNFTWithAliasRedirect() {
        final var spenderPublicKeyString = "3a2102ff806fecbd31b4c377293cba8d2b78725965a4990e0ff1b1b29a1d2c61402310";
        final var spenderPublicKey = publicKeyFromString(spenderPublicKeyString);
        final var spenderAlias = generateAlias(spenderPublicKey);
        final var treasuryEntityId = accountPersist();
        final var serialNo = 1L;
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractId = testWeb3jService.getEntityId();

        final var nftEntity =
                domainBuilder.entity().customize(e -> e.type(TOKEN)).persist();
        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(nftEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(treasuryEntityId))
                .persist();
        domainBuilder
                .nft()
                .customize(n -> n.accountId(treasuryEntityId)
                        .accountId(entityIdFromEvmAddress(toAddress(contractId)))
                        .tokenId(nftEntity.getId())
                        .serialNumber(serialNo))
                .persist();

        final var tokenEntity = entityIdFromEvmAddress(toAddress(token.getTokenId()));
        final var tokenAddress = toAddress(token.getTokenId());
        tokenAssociateContractPersist(tokenEntity, contractId);

        final var functionCall = contract.send_approveRedirect(
                tokenAddress.toHexString(), spenderAlias.toHexString(), BigInteger.valueOf(serialNo));
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
        final var contractId = testWeb3jService.getEntityId();

        tokenAssociateContractPersist(tokenEntity, contractId);
        final var functionCall = contract.send_transferRedirect(
                tokenAddress.toHexString(), toAddress(recipient).toHexString(), BigInteger.valueOf(amount));

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromRedirect() {
        final var treasury = accountPersist();
        final var spender = accountPersist();
        final var recipient = accountPersist();
        final var amount = 10L;

        final var tokenEntityId = fungibleTokenPersist(treasury);
        final var tokenEntity = entityIdFromEvmAddress(toAddress(tokenEntityId.getTokenId()));
        tokenAssociateAccountPersist(spender, tokenEntity);
        tokenAssociateAccountPersist(recipient, tokenEntity);

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractId = testWeb3jService.getEntityId();

        domainBuilder
                .tokenAccount()
                .customize(ta ->
                        ta.tokenId(tokenEntity.getId()).accountId(contractId).associated(true))
                .persist();

        domainBuilder
                .tokenAllowance()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .spender(contractId)
                        .amount(amount)
                        .owner(spender.getId()))
                .persist();

        final var functionCall = contract.send_transferFromRedirect(
                toAddress(tokenEntity.getId()).toHexString(),
                toAddress(spender).toHexString(),
                toAddress(recipient).toHexString(),
                BigInteger.valueOf(amount));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromToHollowAccountRedirect() {
        final var treasury = accountPersist();
        final var owner = accountPersist();
        final var amount = 10L;
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
        final var contractId = testWeb3jService.getEntityId();

        domainBuilder
                .tokenAccount()
                .customize(ta ->
                        ta.tokenId(tokenEntity.getId()).accountId(contractId).associated(true))
                .persist();

        domainBuilder
                .tokenAllowance()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .spender(contractId)
                        .amount(amount)
                        .owner(owner.getId()))
                .persist();

        final var functionCall = contract.send_transferFromRedirect(
                toAddress(tokenEntity.getId()).toHexString(),
                toAddress(owner).toHexString(),
                toAddress(hollowAccount.getId()).toHexString(),
                BigInteger.valueOf(amount));

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromNFTRedirect() {
        final var senderPublicKeyString = "3a2103af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d";
        final var senderPublicKey = publicKeyFromString(senderPublicKeyString);
        final var senderAlias = generateAlias(senderPublicKey);
        final var treasury = senderEntityPersistWithAlias(senderAlias, senderPublicKey);
        final var spender = accountPersist();
        final var recipient = accountPersist();
        final var serialNumber = 1L;

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractId = testWeb3jService.getEntityId();

        final var nftEntity = nftPersist(treasury, spender);
        final var tokenAddress = toAddress(nftEntity.getTokenId());

        tokenAssociateAccountPersist(spender, entityIdFromEvmAddress(toAddress(nftEntity.getTokenId())));
        tokenAssociateAccountPersist(recipient, entityIdFromEvmAddress(toAddress(nftEntity.getTokenId())));

        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(nftEntity.getTokenId())
                        .accountId(contractId)
                        .accountId(contractId)
                        .associated(true)
                        .kycStatus(TokenKycStatusEnum.GRANTED))
                .persist();

        domainBuilder
                .nftAllowance()
                .customize(ta -> ta.tokenId(nftEntity.getTokenId())
                        .spender(contractId)
                        .owner(spender.getId())
                        .tokenId(serialNumber)
                        .approvedForAll(true)
                        .payerAccountId(spender))
                .persist();

        final var functionCall = contract.send_transferFromNFTRedirect(
                tokenAddress.toHexString(),
                toAddress(spender).toHexString(),
                toAddress(recipient).toHexString(),
                BigInteger.valueOf(serialNumber));

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferWithAliasRedirect() {
        final var spenderPublicKeyString = "3a2102ff806fecbd31b4c377293cba8d2b78725965a4990e0ff1b1b29a1d2c61402310";
        final var spenderPublicKey = publicKeyFromString(spenderPublicKeyString);
        final var spenderAlias = generateAlias(spenderPublicKey);
        final var senderPublicKeyString = "3a2103af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d";
        final var senderPublicKey = publicKeyFromString(senderPublicKeyString);
        final var senderAlias = generateAlias(senderPublicKey);
        final var recipient = spenderEntityPersistWithAlias(spenderAlias, spenderPublicKey);
        final var treasury = senderEntityPersistWithAlias(senderAlias, senderPublicKey);
        final var tokenEntity = fungibleTokenPersist(treasury);
        final var amount = 10L;

        tokenAssociateAccountPersist(recipient, entityIdFromEvmAddress(toAddress(tokenEntity.getTokenId())));

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractId = testWeb3jService.getEntityId();

        tokenAssociateContractPersist(entityIdFromEvmAddress(toAddress(tokenEntity.getTokenId())), contractId);
        final var functionCall = contract.send_transferRedirect(
                toAddress(tokenEntity.getTokenId()).toHexString(),
                spenderAlias.toHexString(),
                BigInteger.valueOf(amount));

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromWithAliasRedirect() {
        final var spenderPublicKeyString = "3a2102ff806fecbd31b4c377293cba8d2b78725965a4990e0ff1b1b29a1d2c61402310";
        final var spenderPublicKey = publicKeyFromString(spenderPublicKeyString);
        final var spenderAlias = generateAlias(spenderPublicKey);
        final var senderPublicKeyString = "3a2103af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d";
        final var senderPublicKey = publicKeyFromString(senderPublicKeyString);
        final var senderAlias = generateAlias(senderPublicKey);
        final var treasury = accountPersist();
        final var spender = senderEntityPersistWithAlias(senderAlias, senderPublicKey);
        final var recipient = spenderEntityPersistWithAlias(spenderAlias, spenderPublicKey);
        final var amount = 10L;
        final var tokenEntityId = fungibleTokenPersist(treasury);
        final var tokenEntity = entityIdFromEvmAddress(toAddress(tokenEntityId.getTokenId()));
        tokenAssociateAccountPersist(spender, tokenEntity);
        tokenAssociateAccountPersist(recipient, tokenEntity);

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractId = testWeb3jService.getEntityId();

        domainBuilder
                .tokenAccount()
                .customize(ta ->
                        ta.tokenId(tokenEntity.getId()).accountId(contractId).associated(true))
                .persist();

        domainBuilder
                .tokenAllowance()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .spender(contractId)
                        .amount(amount)
                        .owner(spender.getId()))
                .persist();

        final var functionCall = contract.send_transferFromRedirect(
                toAddress(tokenEntity.getId()).toHexString(),
                senderAlias.toHexString(),
                spenderAlias.toHexString(),
                BigInteger.valueOf(amount));

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromNFTWithAliasRedirect() {
        final var spenderPublicKeyString = "3a2102ff806fecbd31b4c377293cba8d2b78725965a4990e0ff1b1b29a1d2c61402310";
        final var spenderPublicKey = publicKeyFromString(spenderPublicKeyString);
        final var spenderAlias = generateAlias(spenderPublicKey);
        final var senderPublicKeyString = "3a2103af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d";
        final var senderPublicKey = publicKeyFromString(senderPublicKeyString);
        final var senderAlias = generateAlias(senderPublicKey);
        final var treasury = accountPersist();
        final var spender = senderEntityPersistWithAlias(senderAlias, senderPublicKey);
        final var recipient = spenderEntityPersistWithAlias(spenderAlias, spenderPublicKey);
        final var serialNumber = 1L;

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractId = testWeb3jService.getEntityId();

        final var nftEntity = nftPersist(treasury, spender);

        tokenAssociateAccountPersist(spender, entityIdFromEvmAddress(toAddress(nftEntity.getTokenId())));
        tokenAssociateAccountPersist(recipient, entityIdFromEvmAddress(toAddress(nftEntity.getTokenId())));

        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(nftEntity.getTokenId())
                        .accountId(contractId)
                        .associated(true)
                        .kycStatus(TokenKycStatusEnum.GRANTED))
                .persist();

        domainBuilder
                .nftAllowance()
                .customize(ta -> ta.tokenId(nftEntity.getTokenId())
                        .spender(contractId)
                        .owner(spender.getId())
                        .tokenId(serialNumber)
                        .approvedForAll(true)
                        .payerAccountId(spender))
                .persist();

        final var functionCall = contract.send_transferFromNFTRedirect(
                toAddress(nftEntity.getTokenId()).toHexString(),
                senderAlias.toHexString(),
                spenderAlias.toHexString(),
                BigInteger.valueOf(serialNumber));

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    private EntityId spenderEntityPersistWithAlias(final Address spenderAlias, final ByteString spenderPublicKey) {
        return accountPersistWithAlias(spenderAlias, spenderPublicKey);
    }

    private EntityId senderEntityPersistWithAlias(final Address senderAlias, final ByteString senderPublicKey) {
        return accountPersistWithAlias(senderAlias, senderPublicKey);
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
                .customize(n -> n.accountId(treasuryEntityId)
                        .spender(ownerEntityId)
                        .accountId(ownerEntityId)
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

    protected Address generateAlias(final ByteString publicKey) {
        return Address.wrap(
                Bytes.wrap(recoverAddressFromPubKey(publicKey.substring(2).toByteArray())));
    }

    protected ByteString publicKeyFromString(final String publicKeyString) {
        return ByteString.fromHex(publicKeyString);
    }

    protected void tokenAssociateContractPersist(EntityId tokenEntity, long contractId) {
        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(contractId)
                        .associated(true)
                        .kycStatus(TokenKycStatusEnum.GRANTED))
                .persist();
    }
}
