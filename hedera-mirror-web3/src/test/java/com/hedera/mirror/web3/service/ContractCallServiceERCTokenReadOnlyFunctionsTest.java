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
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.SENDER_ALIAS;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.SENDER_PUBLIC_KEY;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.SPENDER_ALIAS;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.SPENDER_PUBLIC_KEY;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.web3.web3j.TestWeb3jService;
import com.hedera.mirror.web3.web3j.generated.ERCTestContract;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityIdUtils;
import java.math.BigInteger;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;

class ContractCallServiceERCTokenReadOnlyFunctionsTest extends AbstractContractCallServiceTest {

    public ContractCallServiceERCTokenReadOnlyFunctionsTest(TestWeb3jService testWeb3jService) {
        super(testWeb3jService);
    }

    @Test
    void ethCallGetApprovedEmptySpenderStatic() throws Exception {
      final var treasuryEntityId = accountPersist();
        final var tokenEntity = persistTokenEntity();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(treasuryEntityId))
                .persist();
        domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId()).serialNumber(1L).accountId(treasuryEntityId))
                .persist();

        final var tokenAddress = getAddressFromEntity(tokenEntity);
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_getApproved(tokenAddress, BigInteger.valueOf(1)).send();
        final var functionCall = contract.send_getApproved(tokenAddress, BigInteger.valueOf(1));
        assertThat(result).isEqualTo((Address.ZERO).toHexString());
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetApprovedEmptySpenderNonStatic() throws Exception {
        final var treasuryEntityId = accountPersist();
        final var tokenEntity = persistTokenEntity();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(treasuryEntityId))
                .persist();
        domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId()).serialNumber(1L).accountId(treasuryEntityId))
                .persist();

        final var tokenAddress = getAddressFromEntity(tokenEntity);
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_getApprovedNonStatic(tokenAddress, BigInteger.valueOf(1)).send();
        final var functionCall = contract.send_getApprovedNonStatic(tokenAddress, BigInteger.valueOf(1));
        assertThat(result).isEqualTo((Address.ZERO).toHexString());
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallIsApporvedForAllStatic() throws Exception {
        final var owner = accountPersist();
        final var spender = accountPersist();
        final var tokenEntity = nftPersist(owner);
        domainBuilder
                .nftAllowance()
                .customize(a -> a.tokenId(tokenEntity.getTokenId())
                        .spender(spender.getId())
                        .owner(owner.getId())
                        .payerAccountId(owner)
                        .approvedForAll(true))
                .persist();
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_isApprovedForAll(tokenAddress.toHexString(), toAddress(owner).toHexString(), toAddress(spender).toHexString()).send();
        final var functionCall = contract.send_isApprovedForAll(tokenAddress.toHexString(), toAddress(owner).toHexString(), toAddress(spender).toHexString());
        assertThat(result).isTrue();
       verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallIsApporvedForAllNonStatic() throws Exception {
        final var owner = accountPersist();
        final var spender = accountPersist();
        final var tokenEntity = nftPersist(owner);
        domainBuilder
                .nftAllowance()
                .customize(a -> a.tokenId(tokenEntity.getTokenId())
                        .spender(spender.getId())
                        .owner(owner.getId())
                        .payerAccountId(owner)
                        .approvedForAll(true))
                .persist();
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_isApprovedForAllNonStatic(tokenAddress.toHexString(), toAddress(owner).toHexString(), toAddress(spender).toHexString()).send();
        final var functionCall = contract.send_isApprovedForAllNonStatic(tokenAddress.toHexString(), toAddress(owner).toHexString(), toAddress(spender).toHexString());
        assertThat(result).isTrue();
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallIsApporvedForAllWithAliasStatic() throws Exception {
        final var spender = spenderEntityPersistWithAlias();
        final var owner = senderEntityPersistWithAlias();
        final var tokenEntity = nftPersist(owner);
        domainBuilder
                .nftAllowance()
                .customize(a -> a.tokenId(tokenEntity.getTokenId())
                        .spender(spender.getId())
                        .owner(owner.getId())
                        .payerAccountId(owner)
                        .approvedForAll(true))
                .persist();
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_isApprovedForAll(tokenAddress.toHexString(), SENDER_ALIAS.toHexString(), SPENDER_ALIAS.toHexString()).send();
        final var functionCall = contract.send_isApprovedForAll(tokenAddress.toHexString(), toAddress(owner).toHexString(), toAddress(spender).toHexString());
        assertThat(result).isTrue();
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallIsApporvedForAllWithAliasNonStatic() throws Exception {
        final var spender = spenderEntityPersistWithAlias();
        final var owner = senderEntityPersistWithAlias();
        final var tokenEntity = nftPersist(owner);
        domainBuilder
                .nftAllowance()
                .customize(a -> a.tokenId(tokenEntity.getTokenId())
                        .spender(spender.getId())
                        .owner(owner.getId())
                        .payerAccountId(owner)
                        .approvedForAll(true))
                .persist();
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_isApprovedForAllNonStatic(tokenAddress.toHexString(), SENDER_ALIAS.toHexString(), SPENDER_ALIAS.toHexString()).send();
        final var functionCall = contract.send_isApprovedForAllNonStatic(tokenAddress.toHexString(), toAddress(owner).toHexString(), toAddress(spender).toHexString());
        assertThat(result).isTrue();
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallAllowanceStatic() throws Exception {
        final var owner = accountPersist();
        final var spender = accountPersist();
        final var tokenEntity = fungibleTokenPersist();
        final var amountGranted = 50L;
        domainBuilder
                .tokenAllowance()
                .customize(a -> a.tokenId(tokenEntity.getTokenId())
                        .owner(owner.getNum())
                        .spender(spender.getNum())
                        .amount(amountGranted)
                        .amountGranted(amountGranted))
                .persist();
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_allowance(tokenAddress.toHexString(), toAddress(owner).toHexString(), toAddress(spender).toHexString()).send();
        final var functionCall = contract.send_allowance(tokenAddress.toHexString(), toAddress(owner).toHexString(), toAddress(spender).toHexString());
        assertThat(result).isEqualTo(BigInteger.valueOf(amountGranted));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallAllowanceNonStatic() throws Exception {
        final var owner = accountPersist();
        final var spender = accountPersist();
        final var tokenEntity = fungibleTokenPersist();
        final var amountGranted = 50L;
        domainBuilder
                .tokenAllowance()
                .customize(a -> a.tokenId(tokenEntity.getTokenId())
                        .owner(owner.getNum())
                        .spender(spender.getNum())
                        .amount(amountGranted)
                        .amountGranted(amountGranted))
                .persist();
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_allowanceNonStatic(tokenAddress.toHexString(), toAddress(owner).toHexString(), toAddress(spender).toHexString()).send();
        final var functionCall = contract.send_allowanceNonStatic(tokenAddress.toHexString(), toAddress(owner).toHexString(), toAddress(spender).toHexString());
        assertThat(result).isEqualTo(BigInteger.valueOf(amountGranted));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallAllowanceWithAliasStatic() throws Exception {
        final var spender = spenderEntityPersistWithAlias();
        final var owner = senderEntityPersistWithAlias();
        final var tokenEntity = fungibleTokenPersist();
        final var amountGranted = 50L;
        domainBuilder
                .tokenAllowance()
                .customize(a -> a.tokenId(tokenEntity.getTokenId())
                        .owner(owner.getNum())
                        .spender(spender.getNum())
                        .amount(amountGranted)
                        .amountGranted(amountGranted))
                .persist();
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_allowance(tokenAddress.toHexString(), SENDER_ALIAS.toHexString(), SPENDER_ALIAS.toHexString()).send();
        final var functionCall = contract.send_allowance(tokenAddress.toHexString(), SENDER_ALIAS.toHexString(), SPENDER_ALIAS.toHexString());
        assertThat(result).isEqualTo(BigInteger.valueOf(amountGranted));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallAllowanceWithAliasNonStatic() throws Exception {
        final var spender = spenderEntityPersistWithAlias();
        final var owner = senderEntityPersistWithAlias();
        final var tokenEntity = fungibleTokenPersist();
        final var amountGranted = 50L;
        domainBuilder
                .tokenAllowance()
                .customize(a -> a.tokenId(tokenEntity.getTokenId())
                        .owner(owner.getNum())
                        .spender(spender.getNum())
                        .amount(amountGranted)
                        .amountGranted(amountGranted))
                .persist();
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_allowanceNonStatic(tokenAddress.toHexString(), SENDER_ALIAS.toHexString(), SPENDER_ALIAS.toHexString()).send();
        final var functionCall = contract.send_allowanceNonStatic(tokenAddress.toHexString(), SENDER_ALIAS.toHexString(), SPENDER_ALIAS.toHexString());
        assertThat(result).isEqualTo(BigInteger.valueOf(amountGranted));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetApprovedStatic() throws Exception {
        final var owner = accountPersist();
        final var spender = accountPersist();
        final var tokenEntity = nftPersist(owner, owner, spender);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_getApproved(tokenAddress.toHexString(), BigInteger.valueOf(1)).send();
        final var functionCall = contract.send_getApproved(tokenAddress.toHexString(), BigInteger.valueOf(1));
        assertThat(result).isEqualTo(toAddress(spender).toHexString());
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetApprovedNonStatic() throws Exception {
        final var owner = accountPersist();
        final var spender = accountPersist();
        final var tokenEntity = nftPersist(owner, owner, spender);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_getApprovedNonStatic(tokenAddress.toHexString(), BigInteger.valueOf(1)).send();
        final var functionCall = contract.send_getApprovedNonStatic(tokenAddress.toHexString(), BigInteger.valueOf(1));
        assertThat(result).isEqualTo(toAddress(spender).toHexString());
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetDecimalsStatic() throws Exception {
        final var decimals = 12;
        final var tokenEntity = persistTokenEntity();
        domainBuilder
                .token()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .decimals(decimals))
                .persist();
        final var tokenAddress = toAddress(tokenEntity.getId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_decimals(tokenAddress.toHexString()).send();
        final var functionCall = contract.send_decimals(tokenAddress.toHexString());
        assertThat(result).isEqualTo(BigInteger.valueOf(decimals));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetDecimalsNonStatic() throws Exception {
        final var decimals = 12;
        final var tokenEntity = persistTokenEntity();
        domainBuilder
                .token()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .decimals(decimals))
                .persist();
        final var tokenAddress = toAddress(tokenEntity.getId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_decimalsNonStatic(tokenAddress.toHexString()).send();
        final var functionCall = contract.send_decimalsNonStatic(tokenAddress.toHexString());
        assertThat(result).isEqualTo(BigInteger.valueOf(decimals));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetTotalSupplyStatic() throws Exception {
        final var totalSupply = 12345L;
        final var tokenEntity = persistTokenEntity();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .totalSupply(totalSupply))
                .persist();

        final var tokenAddress = getAddressFromEntity(tokenEntity);
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_totalSupply(tokenAddress).send();
        final var functionCall = contract.send_totalSupply(tokenAddress);
        assertThat(result).isEqualTo(BigInteger.valueOf(totalSupply));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetTotalSupplyNonStatic() throws Exception {
        final var totalSupply = 12345L;
        final var tokenEntity = persistTokenEntity();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .totalSupply(totalSupply))
                .persist();

        final var tokenAddress = getAddressFromEntity(tokenEntity);
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_totalSupplyNonStatic(tokenAddress).send();
        final var functionCall = contract.send_totalSupplyNonStatic(tokenAddress);
        assertThat(result).isEqualTo(BigInteger.valueOf(totalSupply));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallSymbolStatic() throws Exception {
        final var symbol = "HBAR";
        final var tokenEntity = persistTokenEntity();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .symbol(symbol))
                .persist();
        final var tokenAddress = getAddressFromEntity(tokenEntity);
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_symbol(tokenAddress).send();
        final var functionCall = contract.send_symbol(tokenAddress);
        assertThat(result).isEqualTo(symbol);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallSymbolNonStatic() throws Exception {
        final var symbol = "HBAR";
        final var tokenEntity = persistTokenEntity();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .symbol(symbol))
                .persist();
        final var tokenAddress = getAddressFromEntity(tokenEntity);
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_symbolNonStatic(tokenAddress).send();
        final var functionCall = contract.send_symbolNonStatic(tokenAddress);
        assertThat(result).isEqualTo(symbol);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallBalanceOfStatic() throws Exception {
        final var owner = accountPersist();
        final var tokenEntity = fungibleTokenPersist(owner);
        final var balance = 12L;
       domainBuilder
                .tokenAccount()
                .customize(e -> e.accountId(owner.getId())
                        .tokenId(tokenEntity.getTokenId())
                        .balance(balance))
                .persist();
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_balanceOf(tokenAddress.toHexString(), toAddress(owner).toHexString()).send();
        final var functionCall = contract.send_balanceOf(tokenAddress.toHexString(), toAddress(owner).toHexString());
        assertThat(result).isEqualTo(BigInteger.valueOf(balance));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallBalanceOfNonStatic() throws Exception {
        final var owner = accountPersist();
        final var tokenEntity = fungibleTokenPersist(owner);
        final var balance = 12L;
        domainBuilder
                .tokenAccount()
                .customize(e -> e.accountId(owner.getId())
                        .tokenId(tokenEntity.getTokenId())
                        .balance(balance))
                .persist();
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_balanceOfNonStatic(tokenAddress.toHexString(), toAddress(owner).toHexString()).send();
        final var functionCall = contract.send_balanceOfNonStatic(tokenAddress.toHexString(), toAddress(owner).toHexString());
        assertThat(result).isEqualTo(BigInteger.valueOf(balance));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallBalanceOfWithAliasStatic() throws Exception {
        final var owner = senderEntityPersistWithAlias();
        final var tokenEntity = fungibleTokenPersist(owner);
        final var balance = 12L;
        domainBuilder
                .tokenAccount()
                .customize(e -> e.accountId(owner.getId())
                        .tokenId(tokenEntity.getTokenId())
                        .balance(balance))
                .persist();
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_balanceOf(tokenAddress.toHexString(), SENDER_ALIAS.toHexString()).send();
        final var functionCall = contract.send_balanceOf(tokenAddress.toHexString(), SENDER_ALIAS.toHexString());
        assertThat(result).isEqualTo(BigInteger.valueOf(balance));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallBalanceOfWithAliasNonStatic() throws Exception {
        final var owner = senderEntityPersistWithAlias();
        final var tokenEntity = fungibleTokenPersist(owner);
        final var balance = 12L;
        domainBuilder
                .tokenAccount()
                .customize(e -> e.accountId(owner.getId())
                        .tokenId(tokenEntity.getTokenId())
                        .balance(balance))
                .persist();
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_balanceOfNonStatic(tokenAddress.toHexString(), SENDER_ALIAS.toHexString()).send();
        final var functionCall = contract.send_balanceOfNonStatic(tokenAddress.toHexString(), SENDER_ALIAS.toHexString());
        assertThat(result).isEqualTo(BigInteger.valueOf(balance));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallNameStatic() throws Exception {
        final var tokenName = "Hbars";
        final var tokenEntity = persistTokenEntity();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .name(tokenName))
                .persist();
        final var tokenAddress = toAddress(tokenEntity.getId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_name(tokenAddress.toHexString()).send();
        final var functionCall = contract.send_name(tokenAddress.toHexString());
        assertThat(result).isEqualTo(tokenName);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallNameNonStatic() throws Exception {
        final var tokenName = "Hbars";
        final var tokenEntity = persistTokenEntity();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .name(tokenName))
                .persist();
        final var tokenAddress = toAddress(tokenEntity.getId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_nameNonStatic(tokenAddress.toHexString()).send();
        final var functionCall = contract.send_nameNonStatic(tokenAddress.toHexString());
        assertThat(result).isEqualTo(tokenName);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetOwnerOfStatic() throws Exception {
        final var owner = accountPersist();
        final var tokenEntity = nftPersist(owner);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_getOwnerOf(tokenAddress.toHexString(), BigInteger.valueOf(1)).send();
        final var functionCall = contract.send_getOwnerOf(tokenAddress.toHexString(), BigInteger.valueOf(1));
        assertThat(result).isEqualTo(toAddress(owner).toHexString());
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetOwnerOfNonStatic() throws Exception {
        final var owner = accountPersist();
        final var tokenEntity = nftPersist(owner);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_getOwnerOfNonStatic(tokenAddress.toHexString(), BigInteger.valueOf(1)).send();
        final var functionCall = contract.send_getOwnerOfNonStatic(tokenAddress.toHexString(), BigInteger.valueOf(1));
        assertThat(result).isEqualTo(toAddress(owner).toHexString());
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetOwnerOfStaticEmptyOwner() throws Exception {
        final var tokenEntity = persistTokenEntity();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE))
                .persist();
        domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId()).serialNumber(1L))
                .persist();
        final var tokenAddress = getAddressFromEntity(tokenEntity);
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_getOwnerOf(tokenAddress, BigInteger.valueOf(1)).send();
        final var functionCall = contract.send_getOwnerOf(tokenAddress, BigInteger.valueOf(1));
        assertThat(result).isEqualTo(Address.ZERO.toHexString());
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetOwnerOfStaticEmptyOwnerNonStatic() throws Exception {
        final var tokenEntity = persistTokenEntity();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE))
                .persist();
        domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId()).serialNumber(1L))
                .persist();
        final var tokenAddress = getAddressFromEntity(tokenEntity);
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_getOwnerOfNonStatic(tokenAddress, BigInteger.valueOf(1)).send();
        final var functionCall = contract.send_getOwnerOfNonStatic(tokenAddress, BigInteger.valueOf(1));
        assertThat(result).isEqualTo(Address.ZERO.toHexString());
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallTokenURIStatic() throws Exception {
        final var ownerEntity = accountPersist();
        final byte[] kycKey = domainBuilder.key();
        final var metadata = "NFT_METADATA_URI";
        final var tokenEntity = persistTokenEntity();

        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(ownerEntity)
                        .kycKey(kycKey))
                .persist();
        domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId())
                        .metadata(metadata.getBytes())
                        .serialNumber(1))
                .persist();

        final var tokenAddress = toAddress(tokenEntity.getId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_tokenURI(tokenAddress.toHexString(), BigInteger.valueOf(1)).send();
        final var functionCall = contract.send_tokenURI(tokenAddress.toHexString(), BigInteger.valueOf(1));
        assertThat(result).isEqualTo(metadata);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallTokenURINonStatic() throws Exception {
        final var ownerEntity = accountPersist();
        final byte[] kycKey = domainBuilder.key();
        final var metadata = "NFT_METADATA_URI";
        final var tokenEntity = persistTokenEntity();

        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(ownerEntity)
                        .kycKey(kycKey))
                .persist();
        domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId())
                        .metadata(metadata.getBytes())
                        .serialNumber(1))
                .persist();

        final var tokenAddress = toAddress(tokenEntity.getId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_tokenURINonStatic(tokenAddress.toHexString(), BigInteger.valueOf(1)).send();
        final var functionCall = contract.send_tokenURINonStatic(tokenAddress.toHexString(), BigInteger.valueOf(1));
        assertThat(result).isEqualTo(metadata);
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
                .customize(e -> e.evmAddress(null)
                        .balance(12L))
                .persist()
                .toEntityId()
                ;
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


    private Token fungibleTokenPersist() {
        final var tokenEntity =
                domainBuilder.entity().customize(e -> e.type(TOKEN)).persist();

        return domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON))
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
                        .metadata("NFT_METADATA_URI".getBytes())
                        .serialNumber(1))
                .persist();
        return token;
    }

    private Entity persistTokenEntity() {
        return domainBuilder.entity().customize(e -> e.type(EntityType.TOKEN)).persist();
    }
}
