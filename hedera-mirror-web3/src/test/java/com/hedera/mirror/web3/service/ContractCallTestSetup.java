/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;
import static com.hedera.mirror.common.util.DomainUtils.fromEvmAddress;
import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.NftId;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenId;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.common.domain.token.TokenPauseStatusEnum;
import com.hedera.mirror.common.domain.token.TokenSupplyTypeEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.common.domain.transaction.CustomFee;
import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.utils.FunctionEncodeDecoder;
import com.hederahashgraph.api.proto.java.CustomFee.FeeCase;
import java.nio.file.Path;
import java.util.Arrays;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

public class ContractCallTestSetup extends Web3IntegrationTest {
    protected static final Address CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1256, CONTRACT));
    protected static final Address SENDER_ADDRESS = toAddress(EntityId.of(0, 0, 742, ACCOUNT));
    protected static final Address SPENDER_ADDRESS = toAddress(EntityId.of(0, 0, 741, ACCOUNT));
    protected static final Address FUNGIBLE_TOKEN_ADDRESS = toAddress(EntityId.of(0, 0, 1046, TOKEN));
    protected static final Address NFT_ADDRESS = toAddress(EntityId.of(0, 0, 1047, TOKEN));

    protected static final Address MODIFICATION_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1257, CONTRACT));

    protected static final byte[] KEY_PROTO = new byte[] {
        58, 33, -52, -44, -10, 81, 99, 100, 6, -8, -94, -87, -112, 42, 42, 96, 75, -31, -5, 72, 13, -70, 101, -111, -1,
        77, -103, 47, -118, 107, -58, -85, -63, 55, -57
    };
    protected static final byte[] ECDSA_KEY = Arrays.copyOfRange(KEY_PROTO, 2, KEY_PROTO.length);
    protected static final Address ETH_ADDRESS = Address.fromHexString("0x23f5e49569a835d7bf9aefd30e4f60cdd570f225");
    protected static final Address EMPTY_ADDRESS = Address.wrap(Bytes.wrap(new byte[20]));
    protected static final Address ERC_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1258, CONTRACT));
    protected static final Address REVERTER_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1259, CONTRACT));
    protected static final Address ETH_CALL_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1260, CONTRACT));
    protected static final Address RECEIVER_ADDRESS = toAddress(EntityId.of(0, 0, 1045, CONTRACT));
    protected static final Address STATE_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1261, CONTRACT));

    @Autowired
    protected FunctionEncodeDecoder functionEncodeDecoder;

    @Autowired
    protected ContractCallService contractCallService;

    @Autowired
    protected MirrorNodeEvmProperties properties;
    // The contract source `PrecompileTestContract.sol` is in test resources
    @Value("classpath:contracts/PrecompileTestContract/PrecompileTestContract.bin")
    protected Path CONTRACT_BYTES_PATH;

    @Value("classpath:contracts/PrecompileTestContract/PrecompileTestContract.json")
    protected Path ABI_PATH;
    // The contract source `ModificationPrecompileTestContract.sol` is in test resources
    @Value("classpath:contracts/ModificationPrecompileTestContract/ModificationPrecompileTestContract.bin")
    protected Path MODIFICATION_CONTRACT_BYTES_PATH;

    @Value("classpath:contracts/ModificationPrecompileTestContract/ModificationPrecompileTestContract.json")
    protected Path MODIFICATION_CONTRACT_ABI_PATH;
    // The contract source `ERCTestContract.sol` is in test resources
    @Value("classpath:contracts/ERCTestContract/ERCTestContract.bin")
    protected Path ERC_CONTRACT_BYTES_PATH;

    @Value("classpath:contracts/ERCTestContract/ERCTestContract.json")
    protected Path ERC_ABI_PATH;

    // The contract sources `EthCall.sol` and `Reverter.sol` are in test/resources
    @Value("classpath:contracts/EthCall/EthCall.bin")
    protected Path ETH_CALL_CONTRACT_BYTES_PATH;

    @Value("classpath:contracts/Reverter/Reverter.bin")
    protected Path REVERTER_CONTRACT_BYTES_PATH;

    @Value("classpath:contracts/EthCall/State.bin")
    protected Path STATE_CONTRACT_BYTES_PATH;

    protected void persistEntities(boolean isRegularTransfer) {
        if (isRegularTransfer) {
            performRegularTransfer();
        }

        ethCallContractPersist();
        reverterContractPersist();
        stateContractPersist();
        precompileContractPersist();
        modificationContractPersist();
        ercContractPersist();
        final var senderEntityId = senderEntityPersist();
        final var spenderEntityId = spenderEntityPersist();
        final var tokenEntityId = fungibleTokenPersist(senderEntityId, KEY_PROTO);
        final var nftEntityId = nftPersist(senderEntityId, spenderEntityId, KEY_PROTO);
        final var ethAccount = ethAccountPersist();
        tokenAccountPersist(senderEntityId, ethAccount, tokenEntityId);
        nftCustomFeePersist(senderEntityId, nftEntityId);
        allowancesPersist(senderEntityId, spenderEntityId, tokenEntityId, nftEntityId);
    }

    private void nftCustomFeePersist(EntityId senderEntityId, EntityId nftEntityId) {
        domainBuilder
                .customFee()
                .customize(f -> f.collectorAccountId(senderEntityId)
                        .id(new CustomFee.Id(2L, nftEntityId))
                        .royaltyDenominator(0L)
                        .denominatingTokenId(nftEntityId))
                .persist();
    }

    private EntityId fungibleTokenPersist(final EntityId senderEntityId, final byte[] key) {
        final var tokenEntityId = fromEvmAddress(FUNGIBLE_TOKEN_ADDRESS.toArrayUnsafe());
        final var tokenEvmAddress = toEvmAddress(tokenEntityId);

        domainBuilder
                .entity()
                .customize(e -> e.id(tokenEntityId.getId())
                        .autoRenewAccountId(tokenEntityId.getId())
                        .num(tokenEntityId.getEntityNum())
                        .evmAddress(tokenEvmAddress)
                        .type(TOKEN)
                        .balance(1500L)
                        .key(key)
                        .memo("TestMemo"))
                .persist();

        domainBuilder
                .token()
                .customize(t -> t.tokenId(new TokenId(tokenEntityId))
                        .treasuryAccountId(EntityId.of(0, 0, senderEntityId.getId(), ACCOUNT))
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .kycKey(key)
                        .freezeDefault(true)
                        .feeScheduleKey(key)
                        .supplyType(TokenSupplyTypeEnum.INFINITE)
                        .maxSupply(2525L)
                        .totalSupply(12345L)
                        .decimals(12)
                        .wipeKey(key)
                        .freezeKey(key)
                        .pauseStatus(TokenPauseStatusEnum.PAUSED)
                        .pauseKey(key)
                        .supplyKey(key))
                .persist();

        return tokenEntityId;
    }

    private void tokenAccountPersist(
            final EntityId senderEntityId, final long ethAccount, final EntityId tokenEntityId) {
        domainBuilder
                .tokenAccount()
                .customize(e -> e.freezeStatus(TokenFreezeStatusEnum.FROZEN)
                        .accountId(senderEntityId.getId())
                        .tokenId(tokenEntityId.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .balance(12L))
                .persist();

        domainBuilder
                .tokenAccount()
                .customize(e -> e.freezeStatus(TokenFreezeStatusEnum.FROZEN)
                        .accountId(ethAccount)
                        .tokenId(tokenEntityId.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED))
                .persist();
    }

    @Nullable
    private EntityId spenderEntityPersist() {
        final var spenderEntityId = fromEvmAddress(SPENDER_ADDRESS.toArrayUnsafe());
        final var spenderEvmAddress = toEvmAddress(spenderEntityId);
        domainBuilder
                .entity()
                .customize(e -> e.id(spenderEntityId.getId())
                        .num(spenderEntityId.getEntityNum())
                        .evmAddress(spenderEvmAddress))
                .persist();
        return spenderEntityId;
    }

    private void performRegularTransfer() {
        final var receiverEntityId = fromEvmAddress(RECEIVER_ADDRESS.toArrayUnsafe());
        final var receiverEvmAddress = toEvmAddress(receiverEntityId);
        domainBuilder
                .entity()
                .customize(e -> e.id(receiverEntityId.getId())
                        .num(receiverEntityId.getEntityNum())
                        .evmAddress(receiverEvmAddress)
                        .type(CONTRACT))
                .persist();
    }

    private long ethAccountPersist() {
        final var ethAccount = 358L;

        domainBuilder
                .entity()
                .customize(e -> e.id(ethAccount)
                        .num(ethAccount)
                        .evmAddress(ETH_ADDRESS.toArrayUnsafe())
                        .balance(2000L))
                .persist();
        return ethAccount;
    }

    @Nullable
    private EntityId senderEntityPersist() {
        final var senderEntityId = fromEvmAddress(SENDER_ADDRESS.toArrayUnsafe());

        domainBuilder
                .entity()
                .customize(e -> e.id(senderEntityId.getId())
                        .num(senderEntityId.getEntityNum())
                        .evmAddress(null)
                        .alias(toEvmAddress(senderEntityId))
                        .balance(20000L))
                .persist();
        return senderEntityId;
    }

    @Nullable
    private EntityId nftPersist(final EntityId senderEntityId, final EntityId spenderEntityId, final byte[] key) {
        final var nftEntityId = fromEvmAddress(NFT_ADDRESS.toArrayUnsafe());
        final var nftEvmAddress = toEvmAddress(nftEntityId);

        domainBuilder
                .entity()
                .customize(e -> e.id(nftEntityId.getId())
                        .autoRenewAccountId(nftEntityId.getId())
                        .expirationTimestamp(null)
                        .num(nftEntityId.getEntityNum())
                        .evmAddress(nftEvmAddress)
                        .type(TOKEN)
                        .balance(1500L)
                        .key(key)
                        .memo("TestMemo"))
                .persist();

        domainBuilder
                .token()
                .customize(t -> t.tokenId(new TokenId(nftEntityId))
                        .treasuryAccountId(EntityId.of(0, 0, senderEntityId.getId(), ACCOUNT))
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .kycKey(key)
                        .freezeDefault(true)
                        .feeScheduleKey(key)
                        .maxSupply(1L)
                        .supplyType(TokenSupplyTypeEnum.FINITE)
                        .freezeKey(key)
                        .pauseKey(key)
                        .pauseStatus(TokenPauseStatusEnum.PAUSED)
                        .wipeKey(key)
                        .supplyKey(key)
                        .wipeKey(key))
                .persist();

        domainBuilder
                .nft()
                .customize(n -> n.id(new NftId(1L, nftEntityId))
                        .accountId(EntityId.of(0, 0, senderEntityId.getId(), ACCOUNT))
                        .createdTimestamp(1475067194949034022L)
                        .spender(spenderEntityId)
                        .metadata(new byte[] {1, 2, 3})
                        .modifiedTimestamp(1475067194949034022L))
                .persist();
        return nftEntityId;
    }

    private void allowancesPersist(
            final EntityId senderEntityId,
            final EntityId spenderEntityId,
            final EntityId tokenEntityId,
            final EntityId nftEntityId) {
        domainBuilder
                .tokenAllowance()
                .customize(a -> a.tokenId(tokenEntityId.getId())
                        .payerAccountId(senderEntityId)
                        .owner(senderEntityId.getEntityNum())
                        .spender(spenderEntityId.getEntityNum())
                        .amount(13))
                .persist();

        domainBuilder
                .nftAllowance()
                .customize(a -> a.tokenId(nftEntityId.getId())
                        .spender(spenderEntityId.getEntityNum())
                        .owner(senderEntityId.getEntityNum())
                        .approvedForAll(true)
                        .payerAccountId(senderEntityId))
                .persist();
    }

    private void ethCallContractPersist() {
        final var ethCallContractBytes = functionEncodeDecoder.getContractBytes(ETH_CALL_CONTRACT_BYTES_PATH);
        final var ethCallContractEntityId = fromEvmAddress(ETH_CALL_CONTRACT_ADDRESS.toArrayUnsafe());
        final var ethCallContractEvmAddress = toEvmAddress(ethCallContractEntityId);

        domainBuilder
                .entity()
                .customize(e -> e.id(ethCallContractEntityId.getId())
                        .num(ethCallContractEntityId.getEntityNum())
                        .evmAddress(ethCallContractEvmAddress)
                        .type(CONTRACT)
                        .balance(1500L))
                .persist();

        domainBuilder
                .contract()
                .customize(c -> c.id(ethCallContractEntityId.getId()).runtimeBytecode(ethCallContractBytes))
                .persist();

        domainBuilder
                .contractState()
                .customize(c -> c.contractId(ethCallContractEntityId.getId())
                        .slot(Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000000")
                                .toArrayUnsafe())
                        .value(Bytes.fromHexString("0x4746573740000000000000000000000000000000000000000000000000000000")
                                .toArrayUnsafe()))
                .persist();

        domainBuilder.recordFile().customize(f -> f.bytes(ethCallContractBytes)).persist();
    }

    private void reverterContractPersist() {
        final var reverterContractEntityId = fromEvmAddress(REVERTER_CONTRACT_ADDRESS.toArrayUnsafe());
        final var reverterContractEvmAddress = toEvmAddress(reverterContractEntityId);
        final var reverterContractBytes = functionEncodeDecoder.getContractBytes(REVERTER_CONTRACT_BYTES_PATH);

        domainBuilder
                .entity()
                .customize(e -> e.id(reverterContractEntityId.getId())
                        .num(reverterContractEntityId.getEntityNum())
                        .evmAddress(reverterContractEvmAddress)
                        .type(CONTRACT)
                        .balance(1500L))
                .persist();

        domainBuilder
                .contract()
                .customize(c -> c.id(reverterContractEntityId.getId()).runtimeBytecode(reverterContractBytes))
                .persist();
    }

    private void stateContractPersist() {
        final var stateContractId = fromEvmAddress(STATE_CONTRACT_ADDRESS.toArrayUnsafe());
        final var stateContractAddress = toEvmAddress(stateContractId);
        final var stateContractBytes = functionEncodeDecoder.getContractBytes(STATE_CONTRACT_BYTES_PATH);

        domainBuilder
                .entity()
                .customize(e -> e.id(stateContractId.getId())
                        .num(stateContractId.getEntityNum())
                        .evmAddress(stateContractAddress)
                        .type(CONTRACT)
                        .balance(1500L))
                .persist();

        domainBuilder
                .contract()
                .customize(c -> c.id(stateContractId.getId()).runtimeBytecode(stateContractBytes))
                .persist();
    }

    private void precompileContractPersist() {
        final var contractBytes = functionEncodeDecoder.getContractBytes(CONTRACT_BYTES_PATH);
        final var contractEntityId = fromEvmAddress(CONTRACT_ADDRESS.toArrayUnsafe());
        final var contractEvmAddress = toEvmAddress(contractEntityId);

        domainBuilder
                .entity()
                .customize(e -> e.id(contractEntityId.getId())
                        .num(contractEntityId.getEntityNum())
                        .evmAddress(contractEvmAddress)
                        .type(CONTRACT)
                        .balance(1500L))
                .persist();

        domainBuilder
                .contract()
                .customize(c -> c.id(contractEntityId.getId()).runtimeBytecode(contractBytes))
                .persist();

        domainBuilder
                .contractState()
                .customize(c -> c.contractId(contractEntityId.getId())
                        .slot(Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000000")
                                .toArrayUnsafe())
                        .value(Bytes.fromHexString("0x4746573740000000000000000000000000000000000000000000000000000000")
                                .toArrayUnsafe()))
                .persist();

        domainBuilder.recordFile().customize(f -> f.bytes(contractBytes)).persist();
    }

    private void modificationContractPersist() {
        final var modificationContractBytes = functionEncodeDecoder.getContractBytes(MODIFICATION_CONTRACT_BYTES_PATH);
        final var modificationContractEntityId = fromEvmAddress(MODIFICATION_CONTRACT_ADDRESS.toArrayUnsafe());
        final var modificationContractEvmAddress = toEvmAddress(modificationContractEntityId);

        domainBuilder
                .entity()
                .customize(e -> e.id(modificationContractEntityId.getId())
                        .num(modificationContractEntityId.getEntityNum())
                        .evmAddress(modificationContractEvmAddress)
                        .type(CONTRACT)
                        .balance(1500L))
                .persist();

        domainBuilder
                .contract()
                .customize(c -> c.id(modificationContractEntityId.getId()).runtimeBytecode(modificationContractBytes))
                .persist();
    }

    private void ercContractPersist() {
        final var ercContractBytes = functionEncodeDecoder.getContractBytes(ERC_CONTRACT_BYTES_PATH);
        final var ercContractEntityId = fromEvmAddress(ERC_CONTRACT_ADDRESS.toArrayUnsafe());
        final var ercContractEvmAddress = toEvmAddress(ercContractEntityId);

        domainBuilder
                .entity()
                .customize(e -> e.id(ercContractEntityId.getId())
                        .num(ercContractEntityId.getEntityNum())
                        .evmAddress(ercContractEvmAddress)
                        .type(CONTRACT)
                        .balance(1500L))
                .persist();

        domainBuilder
                .contract()
                .customize(c -> c.id(ercContractEntityId.getId()).runtimeBytecode(ercContractBytes))
                .persist();

        domainBuilder
                .contractState()
                .customize(c -> c.contractId(ercContractEntityId.getId())
                        .slot(Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000000")
                                .toArrayUnsafe())
                        .value(Bytes.fromHexString("0x4746573740000000000000000000000000000000000000000000000000000000")
                                .toArrayUnsafe()))
                .persist();

        domainBuilder.recordFile().customize(f -> f.bytes(ercContractBytes)).persist();
    }

    protected void customFeesPersist(final FeeCase feeCase) {
        var collectorAccountId = fromEvmAddress(SENDER_ADDRESS.toArrayUnsafe());
        var tokenEntityId = fromEvmAddress(FUNGIBLE_TOKEN_ADDRESS.toArrayUnsafe());
        var timeStamp = System.currentTimeMillis();
        switch (feeCase) {
            case ROYALTY_FEE -> domainBuilder
                    .customFee()
                    .customize(f -> f.collectorAccountId(collectorAccountId)
                            .id(new CustomFee.Id(timeStamp, tokenEntityId))
                            .denominatingTokenId(tokenEntityId))
                    .persist();
            case FRACTIONAL_FEE -> domainBuilder
                    .customFee()
                    .customize(f -> f.collectorAccountId(collectorAccountId)
                            .id(new CustomFee.Id(timeStamp, tokenEntityId))
                            .royaltyDenominator(0L)
                            .denominatingTokenId(tokenEntityId))
                    .persist();
            case FIXED_FEE -> domainBuilder
                    .customFee()
                    .customize(f -> f.collectorAccountId(collectorAccountId)
                            .id(new CustomFee.Id(timeStamp, tokenEntityId))
                            .royaltyDenominator(0L)
                            .amountDenominator(null)
                            .royaltyNumerator(0L)
                            .denominatingTokenId(tokenEntityId))
                    .persist();
            default -> domainBuilder
                    .customFee()
                    .customize(f -> f.collectorAccountId(null)
                            .id(new CustomFee.Id(timeStamp, tokenEntityId))
                            .royaltyDenominator(0L)
                            .royaltyNumerator(0L)
                            .denominatingTokenId(tokenEntityId))
                    .persist();
        }
    }
}
