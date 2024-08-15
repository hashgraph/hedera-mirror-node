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
import static com.hedera.mirror.web3.service.ContractCallTestSetup.HOLLOW_ACCOUNT_ALIAS;
import static com.hedera.mirror.web3.service.ContractCallTestSetup.SENDER_ADDRESS;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.SENDER_ALIAS;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.SENDER_PUBLIC_KEY;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.SPENDER_ALIAS;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.SPENDER_PUBLIC_KEY;
import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.common.domain.token.TokenPauseStatusEnum;
import com.hedera.mirror.common.domain.token.TokenSupplyTypeEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.web3.web3j.TestWeb3jService;
import com.hedera.mirror.web3.web3j.generated.ERCTestContract;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityIdUtils;
import java.math.BigInteger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
class ContractCallServiceERCTokenModificationFunctionsTest extends AbstractContractCallServiceTest {
private ContractCallServiceERCTokenModificationFunctionsTest(TestWeb3jService testWeb3jService) {
        super(testWeb3jService);
    }

    @Test
    void approveFungibleToken() throws Exception {
        final var spender = accountPersist();
        final var token = fungibleTokenPersist();
        final var amountGranted = 13L;
        final var tokenEntity = entityIdFromEvmAddress(toAddress(token.getTokenId()));
        final var tokenAddress = toAddress(token.getTokenId());
        tokenAssociateAccountPersist(spender,entityIdFromEvmAddress(toAddress(token.getTokenId())));

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractId = testWeb3jService.getEntityId();

        tokenAssociateContractPersist(tokenEntity, contractId);
        final var functionCall = contract.send_approve(
                tokenAddress.toHexString(),
                toAddress(spender).toHexString(),
                BigInteger.valueOf(amountGranted));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void approveNFT() throws Exception {
        final var spender = accountPersist();
        final var treasuryEntityId = accountPersist();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractId = testWeb3jService.getEntityId();

        //contract owner
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

        final var serialNo = 1L;
        final var tokenEntity = entityIdFromEvmAddress(toAddress(token.getTokenId()));
        final var tokenAddress = toAddress(token.getTokenId());

        //associate contract
        tokenAssociateContractPersist(tokenEntity, contractId);
        final var functionCall = contract.send_approve(
                tokenAddress.toHexString(),
                toAddress(spender).toHexString(),
                BigInteger.valueOf(serialNo));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void approveFungibleTokenWithAlias() throws Exception {
        final var spender = spenderEntityPersistWithAlias();
        final var token = fungibleTokenPersist();
        final var amountGranted = 13L;
        final var tokenEntity = entityIdFromEvmAddress(toAddress(token.getTokenId()));
        final var tokenAddress = toAddress(token.getTokenId());
        tokenAssociateAccountPersist(spender,entityIdFromEvmAddress(toAddress(token.getTokenId())));

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractId = testWeb3jService.getEntityId();

        tokenAssociateContractPersist(tokenEntity, contractId);
        final var functionCall = contract.send_approve(
                tokenAddress.toHexString(),
                SPENDER_ALIAS.toHexString(),
                BigInteger.valueOf(amountGranted));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void approveNFTwithAlias() throws Exception {
        final var spender = spenderEntityPersistWithAlias();
        final var treasuryEntityId = accountPersist();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractId = testWeb3jService.getEntityId();

        //contract owner
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

        final var serialNo = 1L;
        final var tokenEntity = entityIdFromEvmAddress(toAddress(token.getTokenId()));
        final var tokenAddress = toAddress(token.getTokenId());

        //associate contract
        tokenAssociateContractPersist(tokenEntity, contractId);
        final var functionCall = contract.send_approve(
                tokenAddress.toHexString(),
                SPENDER_ALIAS.toHexString(),
                BigInteger.valueOf(serialNo));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transfer() throws Exception {
        final var recipent = accountPersist();
        final var treasury = accountPersist();
        final var tokenEntityId = persistTokenEntity();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntityId.getId())
                        .treasuryAccountId(treasury)
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .supplyType(TokenSupplyTypeEnum.INFINITE)
                        .maxSupply(2525L)
                        .initialSupply(10_000_000L)
                        .name("Hbars")
                        .totalSupply(12345L)
                        .decimals(12)
                        .wipeKey(new byte[0])
                        .freezeKey(new byte[0])
                        .pauseStatus(TokenPauseStatusEnum.UNPAUSED)
                        .pauseKey(new byte[0])
                        .supplyKey(new byte[0])
                        .symbol("HBAR"))
                .persist();
        final var amount = 10L;

        final var tokenEntity = entityIdFromEvmAddress(toAddress(tokenEntityId.getId()));
        final var tokenAddress = toAddress(tokenEntity.getId());
        tokenAssociateAccountPersist(recipent,entityIdFromEvmAddress(toAddress(tokenEntity.getId())));

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractId = testWeb3jService.getEntityId();

        tokenAssociateContractPersist(tokenEntity, contractId);
        final var functionCall = contract.send_transfer(
                tokenAddress.toHexString(),
                toAddress(recipent).toHexString(),
                BigInteger.valueOf(amount));

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

//    @Test
//    void transferFrom() throws Exception {
//        final var treasury = accountPersist();
//        final var spender = accountPersist(); //from
//        final var recipient = accountPersist(); //to
//        final var amount = 10L;
//        final var tokenEntityId = persistTokenEntity();
//        domainBuilder
//                .token()
//                .customize(t -> t.tokenId(tokenEntityId.getId())
//                        .treasuryAccountId(treasury)
//                        .type(TokenTypeEnum.FUNGIBLE_COMMON))
//                .persist();
//        //associate spender
//        domainBuilder
//                .tokenAccount()
//                .customize(ta -> ta.tokenId(tokenEntityId.getId())
//                        .accountId(spender.getId())
//                        .kycStatus(TokenKycStatusEnum.GRANTED)
//                        .associated(true))
//                .persist();
//        //associate recipient
//        domainBuilder
//                .tokenAccount()
//                .customize(ta -> ta.tokenId(tokenEntityId.getId())
//                        .accountId(recipient.getId())
//                        .kycStatus(TokenKycStatusEnum.GRANTED)
//                        .associated(true))
//                .persist();
//
//        final var tokenEntity = entityIdFromEvmAddress(toAddress(tokenEntityId.getId()));
//        final var tokenAddress = toAddress(tokenEntity.getId());
//
//        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
//        final var contractId = testWeb3jService.getEntityId();
//
//        //associate contract
//        domainBuilder
//                .tokenAccount()
//                .customize(ta ->
//                        ta.tokenId(tokenEntity.getId()).accountId(contractId).associated(true))
//                .persist();
//
//        //spender gives allowance to the contract
//        domainBuilder
//                .tokenAllowance()
//                .customize(ta -> ta.tokenId(tokenEntity.getId())
//                        .spender(contractId)
//                        .amount(10L)
//                        .owner(spender.getId()))
//                .persist();
//
//        final var functionCall = contract.send_transferFrom(
//                tokenAddress.toHexString(),
//                toAddress(spender).toHexString(),
//                toAddress(recipient).toHexString(),
//                BigInteger.valueOf(amount));
//
//        verifyEthCallAndEstimateGas(functionCall, contract);
//    }

    @Test
    void transferFrom2() throws Exception {
        final var treasury = accountPersist();
        final var spender = accountPersist(); //from
        final var recipient = accountPersist(); //to
        final var amount = 10L;
        final var tokenEntityId = fungibleTokenPersist(treasury);
        final var tokenEntity = entityIdFromEvmAddress(toAddress(tokenEntityId.getTokenId()));
        tokenAssociateAccountPersist(spender, tokenEntity);
        tokenAssociateAccountPersist(recipient, tokenEntity);
        final var tokenAddress = toAddress(tokenEntity.getId());

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
                tokenAddress.toHexString(),
                toAddress(spender).toHexString(),
                toAddress(recipient).toHexString(),
                BigInteger.valueOf(amount));

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromToHollowAccount() throws Exception {
    final var treasury = accountPersist();
        final var owner = accountPersist(); //from
        final var amount = 10L;
        final var payer = accountPersistEntity();
        final var tokenEntityId = fungibleTokenPersist(treasury);
        final var tokenAddress = toAddress(tokenEntityId.getTokenId());
//        final var tokenEntity =
//                domainBuilder.entity().customize(e -> e.type(TOKEN)).persist();
//        domainBuilder
//                .token()
//                .customize(t -> t.tokenId(tokenEntity.getId())
//                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
//                        .kycStatus(TokenKycStatusEnum.GRANTED)
//                        .treasuryAccountId(treasury))
//                .persist();
        //final var tokenEntity = entityIdFromEvmAddress(toAddress(tokenEntityId.getTokenId()));
        tokenAssociateAccountPersist(owner, entityIdFromEvmAddress(toAddress(tokenEntityId.getTokenId())));
//
//        final var hollow = domainBuilder
//                .entity()
//                .customize(e -> e.evmAddress(HOLLOW_ACCOUNT_ALIAS.toArray()).key(null).maxAutomaticTokenAssociations(100))
//                .persist()
//                .toEntityId();

      //  final var hollowAddress = toAddress(hollow.getId());
//        domainBuilder
//                .tokenAccount()
//                .customize(e -> e.accountId(hollow.getId())
//                        .tokenId(tokenEntity.getId())
//                        .kycStatus(TokenKycStatusEnum.GRANTED)
//                        .associated(true))
//                .persist();

        //tokenAssociateAccountPersist(entityIdFromEvmAddress(HOLLOW_ACCOUNT_ALIAS), tokenEntity);


        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractId = testWeb3jService.getEntityId();

        domainBuilder
                .tokenAccount()
                .customize(ta ->
                        ta.tokenId(tokenEntityId.getTokenId()).accountId(contractId).associated(true))
                .persist();

        domainBuilder
                .tokenAllowance()
                .customize(ta -> ta.tokenId(tokenEntityId.getTokenId())
                        .spender(contractId)
                        .amount(amount)
                        .owner(owner.getId()))
                .persist();

        testWeb3jService.setSender(getAddressFromEntity(payer));
        final var functionCall = contract.send_transferFrom(
                tokenAddress.toHexString(),
                toAddress(owner).toHexString(),
                HOLLOW_ACCOUNT_ALIAS.toHexString(),
                BigInteger.valueOf(amount));

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromNFT() throws Exception {
        final var treasury = senderEntityPersistWithAlias();
        final var spender = accountPersist(); //from
        final var recipient = accountPersist(); //to
        final var serialNumber = 1L;

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractId = testWeb3jService.getEntityId();
        final var contractEntityId = entityIdFromEvmAddress(toAddress(contractId));

        final var nftEntity = nftPersist(treasury, spender);
        final var tokenAddress = toAddress(nftEntity.getTokenId());

        tokenAssociateAccountPersist(spender, entityIdFromEvmAddress(toAddress(nftEntity.getTokenId())));
        tokenAssociateAccountPersist(recipient, entityIdFromEvmAddress(toAddress(nftEntity.getTokenId())));

        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(nftEntity
                        .getTokenId())
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
    void transferWithAlias() throws Exception {
        final var recipient = spenderEntityPersistWithAlias();
        final var treasury = senderEntityPersistWithAlias();
         final var tokenEntity = fungibleTokenPersist(treasury);
        final var amount = 10L;

       // final var tokenEntity = entityIdFromEvmAddress(toAddress(tokenEntityId.getId()));
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        tokenAssociateAccountPersist(recipient,entityIdFromEvmAddress(toAddress(tokenEntity.getTokenId())));

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractId = testWeb3jService.getEntityId();

        tokenAssociateContractPersist(entityIdFromEvmAddress(toAddress(tokenEntity.getTokenId())), contractId);
        final var functionCall = contract.send_transfer(
                tokenAddress.toHexString(),
                SPENDER_ALIAS.toHexString(),
                BigInteger.valueOf(amount));

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromWithAlias() throws Exception {
        final var treasury = accountPersist();
        final var spender = senderEntityPersistWithAlias(); //from
        final var recipient = spenderEntityPersistWithAlias(); //to
        final var amount = 10L;
        final var tokenEntityId = fungibleTokenPersist(treasury);
        final var tokenEntity = entityIdFromEvmAddress(toAddress(tokenEntityId.getTokenId()));
        tokenAssociateAccountPersist(spender, tokenEntity);
        tokenAssociateAccountPersist(recipient, tokenEntity);
        final var tokenAddress = toAddress(tokenEntity.getId());

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
                tokenAddress.toHexString(),
                SENDER_ALIAS.toHexString(),
                SPENDER_ALIAS.toHexString(),
                BigInteger.valueOf(amount));

        verifyEthCallAndEstimateGas(functionCall, contract);
    }
@Test
    void transferFromNFTWithAlias() throws Exception {
        final var treasury = accountPersist();
        final var spender = senderEntityPersistWithAlias(); //from
        final var recipient = spenderEntityPersistWithAlias(); //to
        final var serialNumber = 1L;

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractId = testWeb3jService.getEntityId();
        final var contractEntityId = entityIdFromEvmAddress(toAddress(contractId));

        final var nftEntity = nftPersist(treasury, spender);
        final var tokenAddress = toAddress(nftEntity.getTokenId());

        tokenAssociateAccountPersist(spender, entityIdFromEvmAddress(toAddress(nftEntity.getTokenId())));
        tokenAssociateAccountPersist(recipient, entityIdFromEvmAddress(toAddress(nftEntity.getTokenId())));

        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(nftEntity
                                .getTokenId())
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
                SENDER_ALIAS.toHexString(),
                SPENDER_ALIAS.toHexString(),
                BigInteger.valueOf(serialNumber));

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void delegateTransfer() throws Exception {
        final var recipent = accountPersist();
        final var treasury = accountPersist();
        final var tokenEntityId = persistTokenEntity();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntityId.getId())
                        .treasuryAccountId(treasury)
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .supplyType(TokenSupplyTypeEnum.INFINITE)
                        .maxSupply(2525L)
                        .initialSupply(10_000_000L)
                        .name("Hbars")
                        .totalSupply(12345L)
                        .decimals(12)
                        .wipeKey(new byte[0])
                        .freezeKey(new byte[0])
                        .pauseStatus(TokenPauseStatusEnum.UNPAUSED)
                        .pauseKey(new byte[0])
                        .supplyKey(new byte[0])
                        .symbol("HBAR"))
                .persist();
        final var amount = 10L;

        final var tokenEntity = entityIdFromEvmAddress(toAddress(tokenEntityId.getId()));
        final var tokenAddress = toAddress(tokenEntity.getId());
        tokenAssociateAccountPersist(recipent,entityIdFromEvmAddress(toAddress(tokenEntity.getId())));

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractId = testWeb3jService.getEntityId();

        tokenAssociateContractPersist(tokenEntity, contractId);

        final var functionCall = contract.send_delegateTransfer(
                tokenAddress.toHexString(),
                toAddress(recipent).toHexString(),
                BigInteger.valueOf(amount));
        //final var resultHex = functionCall.toString();
       //assertThat(functionCall.send())
        verifyEthCallAndEstimateGas(functionCall, contract);
    }


    private EntityId spenderEntityPersistWithAlias() {
        return accountPersistWithAlias(SPENDER_ALIAS, SPENDER_PUBLIC_KEY);
    }

    private EntityId senderEntityPersistWithAlias() {
        return accountPersistWithAlias(SENDER_ALIAS, SENDER_PUBLIC_KEY);
    }

    private EntityId accountPersistWithAlias(final Address alias, final ByteString publicKey) {
        return domainBuilder
                .entity()
                .customize(e -> e.evmAddress(alias.toArray()).alias(publicKey.toByteArray()))
                .persist()
                .toEntityId();
    }

    private String getAddressFromEntity(Entity entity) {
        return EntityIdUtils.asHexedEvmAddress(new Id(entity.getShard(), entity.getRealm(), entity.getId()));
    }

    private EntityId accountPersist() {
        return domainBuilder
                .entity()
                .customize(e -> e.evmAddress(null).balance(12L))
                .persist()
                .toEntityId();
    }

    private Entity accountPersistEntity() {
        return domainBuilder
                .entity()
                .customize(e -> e.evmAddress(null).balance(12L))
                .persist();
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
                        .kycKey(new byte[0]))
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

    private Token nftPersist() {
        final var tokenEntity = persistTokenEntity();
        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE))
                .persist();
        domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId()).serialNumber(1L))
                .persist();
        return token;
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
                        .metadata("NFT_METADATA_URI".getBytes())
                        .serialNumber(1))
                .persist();
        return token;
    }

    protected EntityId spenderEntityPersist() {
        final var spenderEntityId = accountPersist();
        domainBuilder
                .entity()
                .customize(e -> e.id(spenderEntityId.getId())
                        .num(spenderEntityId.getNum())
                        .evmAddress(SPENDER_ALIAS.toArray())
                        .alias(SPENDER_PUBLIC_KEY.toByteArray())
                        .deleted(false))
                .persist();
        return spenderEntityId;
    }

    protected void fungibleTokenAllowancesPersist(
            final EntityId senderEntityId,
            final EntityId spenderEntityId,
            final EntityId tokenEntityId) {
        domainBuilder
                .tokenAllowance()
                .customize(a -> a.tokenId(tokenEntityId.getId())
                        .payerAccountId(senderEntityId)
                        .owner(senderEntityId.getNum())
                        .spender(spenderEntityId.getNum())
                        .amount(13))
                .persist();
    }

    protected void tokenAssociateAccountPersist(
            final EntityId account, final EntityId tokenEntityId) {
        domainBuilder
                .tokenAccount()
                .customize(e -> e.accountId(account.getId())
                        .tokenId(tokenEntityId.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .associated(true))
                .persist();

    }

    protected void tokenAssociateContractPersist(EntityId tokenEntity, long contractId){
        domainBuilder
                .tokenAccount()
                .customize(ta ->
                        ta.tokenId(tokenEntity.getId())
                                .accountId(contractId)
                                .associated(true)
                                .kycStatus(TokenKycStatusEnum.GRANTED))
                .persist();
    }


    private Entity persistTokenEntity() {
        return domainBuilder.entity().customize(e -> e.type(EntityType.TOKEN)).persist();
    }

    protected EntityId senderEntityPersist() {
        final var senderEntityId = entityIdFromEvmAddress(SENDER_ADDRESS);

        domainBuilder
                .entity()
                .customize(e -> e.id(senderEntityId.getId())
                        .num(senderEntityId.getNum())
                        .evmAddress(SENDER_ALIAS.toArray())
                        .deleted(false)
                        .alias(SENDER_PUBLIC_KEY.toByteArray())
                        .balance(10000 * 100_000_000L))
                .persist();
        return senderEntityId;
    }

    protected EntityId fungibleTokenPersist(
            final EntityId treasuryId,
            final byte[] key,
            final Address tokenAddress,
            final Address autoRenewAddress,
            final long tokenExpiration,
            final TokenPauseStatusEnum pauseStatus,
            final boolean freezeDefault) {
        final var tokenEntityId = entityIdFromEvmAddress(tokenAddress);
        final var autoRenewEntityId = entityIdFromEvmAddress(autoRenewAddress);

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
                        .memo("TestMemo"))
                .persist();

        domainBuilder
                .token()
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
                        .symbol("HBAR"))
                .persist();

        return tokenEntityId;
    }

    protected String getAliasFromEntity(Entity entity) {
        return Address.fromHexString(Bytes.wrap(entity.getEvmAddress()).toHexString())
                .toHexString();
    }

    private String getAddressFromEntityId(EntityId entity) {
        return EntityIdUtils.asHexedEvmAddress(new Id(entity.getShard(), entity.getRealm(), entity.getNum()));
    }
}
