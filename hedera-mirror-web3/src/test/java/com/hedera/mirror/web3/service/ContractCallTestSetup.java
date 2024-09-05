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

import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;
import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.entityIdFromEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.services.utils.EntityIdUtils.contractIdFromEvmAddress;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.balance.TokenBalance;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.FallbackFee;
import com.hedera.mirror.common.domain.token.FixedFee;
import com.hedera.mirror.common.domain.token.FractionalFee;
import com.hedera.mirror.common.domain.token.RoyaltyFee;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.common.domain.token.TokenPauseStatusEnum;
import com.hedera.mirror.common.domain.token.TokenSupplyTypeEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.repository.RecordFileRepository;
import com.hedera.mirror.web3.service.model.CallServiceParameters.CallType;
import com.hedera.mirror.web3.service.model.ContractExecutionParameters;
import com.hedera.mirror.web3.utils.FunctionEncodeDecoder;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hedera.services.store.contracts.precompile.TokenCreateWrapper;
import com.hedera.services.store.contracts.precompile.codec.KeyValueWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenExpiryWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenKeyWrapper;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.CustomFee.FeeCase;
import com.hederahashgraph.api.proto.java.Key;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.datatypes.Address;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

public class ContractCallTestSetup extends Web3IntegrationTest {

    // The block numbers lower than EVM v0.34 are considered part of EVM v0.30 which includes all precompiles
    public static final long EVM_V_34_BLOCK = 50L;
    protected static final long expiry = 1_234_567_890L;
    protected static final long EVM_V_38_BLOCK = 100L;
    protected static final long EVM_V_46_BLOCK = 150L;

    // Contract addresses
    protected static final Address ETH_ADDRESS = Address.fromHexString("0x23f5e49569a835d7bf9aefd30e4f60cdd570f225");
    protected static final Address PRECOMPILE_TEST_CONTRACT_ADDRESS = toAddress(1256);
    protected static final Address ERC_CONTRACT_ADDRESS = toAddress(1258);

    // Account addresses
    protected static final Address AUTO_RENEW_ACCOUNT_ADDRESS = toAddress(740);
    protected static final Address AUTO_RENEW_ACCOUNT_ADDRESS_HISTORICAL = toAddress(1078);
    protected static final EntityId NETWORK_TREASURY_ACCOUNT_ID = EntityId.of(2);
    protected static final Address SPENDER_ADDRESS = toAddress(1041);
    protected static final Address SPENDER_ADDRESS_HISTORICAL = toAddress(1016);
    protected static final ByteString SPENDER_PUBLIC_KEY =
            ByteString.fromHex("3a2102ff806fecbd31b4c377293cba8d2b78725965a4990e0ff1b1b29a1d2c61402310");
    protected static final ByteString SPENDER_PUBLIC_KEY_HISTORICAL =
            ByteString.fromHex("3a210398e17bcbd2926c4d8a31e32616b4754ac0a2fc71d7fb768e657db46202625f34");
    protected static final Address SPENDER_ALIAS = Address.wrap(
            Bytes.wrap(recoverAddressFromPubKey(SPENDER_PUBLIC_KEY.substring(2).toByteArray())));
    protected static final Address SPENDER_ALIAS_HISTORICAL = Address.wrap(Bytes.wrap(
            recoverAddressFromPubKey(SPENDER_PUBLIC_KEY_HISTORICAL.substring(2).toByteArray())));
    protected static final Address SENDER_ADDRESS = toAddress(1043);
    protected static final Address SENDER_ADDRESS_HISTORICAL = toAddress(1014);
    protected static final ByteString SENDER_PUBLIC_KEY =
            ByteString.copyFrom(Hex.decode("3a2103af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d"));
    protected static final ByteString SENDER_PUBLIC_KEY_HISTORICAL =
            ByteString.copyFrom(Hex.decode("3a2102930a39a381a68d90afc8e8c82935bd93f89800e88ec29a18e8cc13d51947c6c8"));
    protected static final Address SENDER_ALIAS = Address.wrap(
            Bytes.wrap(recoverAddressFromPubKey(SENDER_PUBLIC_KEY.substring(2).toByteArray())));
    protected static final Address SENDER_ALIAS_HISTORICAL = Address.wrap(Bytes.wrap(
            recoverAddressFromPubKey(SENDER_PUBLIC_KEY_HISTORICAL.substring(2).toByteArray())));
    protected static final Address TREASURY_ADDRESS = toAddress(743);
    protected static final ByteString NOT_ASSOCIATED_SPENDER_PUBLIC_KEY =
            ByteString.fromHex("3a21033a514176466fa815ed481ffad09110a2d344f6c9b78c1d14afc351c3a51be33d");
    protected static final Address NOT_ASSOCIATED_SPENDER_ALIAS = Address.wrap(Bytes.wrap(recoverAddressFromPubKey(
            NOT_ASSOCIATED_SPENDER_PUBLIC_KEY.substring(2).toByteArray())));
    protected static final Address OWNER_ADDRESS = toAddress(1044);
    protected static final Address OWNER_ADDRESS_HISTORICAL = toAddress(1065);

    // Token addresses
    protected static final Address FUNGIBLE_TOKEN_ADDRESS_WITH_EXPIRY_HISTORICAL = toAddress(1077);
    protected static final Address RECEIVER_ADDRESS = toAddress(1045);
    protected static final Address FUNGIBLE_TOKEN_ADDRESS = toAddress(1046);
    protected static final Address FUNGIBLE_TOKEN_ADDRESS_HISTORICAL = toAddress(1062);
    protected static final Address NFT_ADDRESS = toAddress(1047);
    protected static final Address NFT_ADDRESS_HISTORICAL = toAddress(1063);
    protected static final Address NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS = toAddress(1048);
    protected static final Address TREASURY_TOKEN_ADDRESS = toAddress(1049);
    protected static final Address TREASURY_TOKEN_ADDRESS_WITH_ALL_KEYS = toAddress(1110);
    protected static final Address TRANSFRER_FROM_TOKEN_ADDRESS = toAddress(1111);
    protected static final Address FROZEN_FUNGIBLE_TOKEN_ADDRESS = toAddress(1050);
    protected static final Address NFT_TRANSFER_ADDRESS = toAddress(1051);
    protected static final Address UNPAUSED_FUNGIBLE_TOKEN_ADDRESS = toAddress(1052);
    protected static final Address NFT_ADDRESS_GET_KEY_WITH_CONTRACT_ADDRESS_HISTORICAL = toAddress(1073);
    protected static final Address FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ED25519_KEY_HISTORICAL = toAddress(1069);
    protected static final Address FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ECDSA_KEY_HISTORICAL = toAddress(1070);
    protected static final Address FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_DELEGATABLE_CONTRACT_ID_HISTORICAL =
            toAddress(1072);
    protected static final Address NFT_ADDRESS_GET_KEY_WITH_ED25519_KEY_HISTORICAL = toAddress(1074);
    protected static final Address NFT_ADDRESS_GET_KEY_WITH_ECDSA_KEY_HISTORICAL = toAddress(1075);
    protected static final Address NFT_ADDRESS_GET_KEY_WITH_DELEGATABLE_CONTRACT_ID_HISTORICAL = toAddress(1076);
    protected static final Address FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_CONTRACT_ADDRESS_HISTORICAL = toAddress(1068);

    protected static final byte[] KEY_PROTO = new byte[] {
        58, 33, -52, -44, -10, 81, 99, 100, 6, -8, -94, -87, -112, 42, 42, 96, 75, -31, -5, 72, 13, -70, 101, -111, -1,
        77, -103, 47, -118, 107, -58, -85, -63, 55, -57
    };
    protected static final byte[] ECDSA_KEY = Arrays.copyOfRange(KEY_PROTO, 2, KEY_PROTO.length);
    protected static Key keyWithECDSASecp256K1 =
            Key.newBuilder().setECDSASecp256K1(ByteString.copyFrom(ECDSA_KEY)).build();
    protected static final byte[] ED25519_KEY = Arrays.copyOfRange(KEY_PROTO, 2, KEY_PROTO.length);
    protected static Key keyWithEd25519 =
            Key.newBuilder().setEd25519(ByteString.copyFrom(ED25519_KEY)).build();

    // Token Wrappers
    protected static final TokenCreateWrapper FUNGIBLE_HBAR_TOKEN_AND_KEYS_HISTORICAL =
            getFungibleHbarsTokenWrapper(OWNER_ADDRESS_HISTORICAL, AUTO_RENEW_ACCOUNT_ADDRESS_HISTORICAL);
    protected static final TokenCreateWrapper NFT_HBAR_TOKEN_AND_KEYS_HISTORICAL =
            getNftHbarTokenAndKeysHbarsTokenWrapper(OWNER_ADDRESS_HISTORICAL, AUTO_RENEW_ACCOUNT_ADDRESS_HISTORICAL);

    protected static Key keyWithContractId = Key.newBuilder()
            .setContractID(contractIdFromEvmAddress(PRECOMPILE_TEST_CONTRACT_ADDRESS.toArrayUnsafe()))
            .build();
    protected static Key keyWithDelegatableContractId = Key.newBuilder()
            .setDelegatableContractId(contractIdFromEvmAddress(PRECOMPILE_TEST_CONTRACT_ADDRESS.toArrayUnsafe()))
            .build();

    protected static RecordFile genesisRecordFileForBlockHash;
    protected static RecordFile recordFileBeforeEvm34;
    protected static RecordFile recordFileAfterEvm34;
    protected static RecordFile recordFileEvm38;
    protected static RecordFile recordFileEvm46;
    protected static RecordFile recordFileEvm46Latest;

    @Autowired
    protected FunctionEncodeDecoder functionEncodeDecoder;

    @Autowired
    protected ContractExecutionService contractCallService;

    @Autowired
    protected RecordFileRepository recordFileRepository;

    // The contract source `PrecompileTestContract.sol` is in test resources
    @Value("classpath:contracts/PrecompileTestContract/PrecompileTestContract.bin")
    protected Path CONTRACT_BYTES_PATH;

    @Value("classpath:contracts/PrecompileTestContract/PrecompileTestContract.json")
    protected Path PRECOMPILE_TEST_CONTRACT_ABI_PATH;

    // The contract source `ERCTestContract.sol` is in test resources
    @Value("classpath:contracts/ERCTestContract/ERCTestContract.bin")
    protected Path ERC_CONTRACT_BYTES_PATH;

    @Value("classpath:contracts/ERCTestContract/ERCTestContract.json")
    protected Path ERC_ABI_PATH;

    private static TokenCreateWrapper getFungibleHbarsTokenWrapper(
            final Address ownerAddress, final Address autoRenewAccountAddress) {
        final var keyValue =
                new KeyValueWrapper(false, null, new byte[0], Arrays.copyOfRange(KEY_PROTO, 2, KEY_PROTO.length), null);
        return new TokenCreateWrapper(
                true,
                "Hbars",
                "HBAR",
                EntityIdUtils.accountIdFromEvmAddress(ownerAddress),
                "TestMemo",
                false,
                BigInteger.valueOf(10_000_000L),
                BigInteger.valueOf(12L),
                2525L,
                true,
                List.of(
                        new TokenKeyWrapper(0b0000001, keyValue),
                        new TokenKeyWrapper(0b0000010, keyValue),
                        new TokenKeyWrapper(0b0000100, keyValue),
                        new TokenKeyWrapper(0b0001000, keyValue),
                        new TokenKeyWrapper(0b0010000, keyValue),
                        new TokenKeyWrapper(0b0100000, keyValue),
                        new TokenKeyWrapper(0b1000000, keyValue)),
                new TokenExpiryWrapper(
                        9_999L, EntityIdUtils.accountIdFromEvmAddress(autoRenewAccountAddress), 8_000_000L));
    }

    private static TokenCreateWrapper getNftHbarTokenAndKeysHbarsTokenWrapper(
            final Address ownerAddress, final Address autoRenewAccountAddress) {
        final var keyValue =
                new KeyValueWrapper(false, null, new byte[0], Arrays.copyOfRange(KEY_PROTO, 2, KEY_PROTO.length), null);
        return new TokenCreateWrapper(
                false,
                "Hbars",
                "HBAR",
                EntityIdUtils.accountIdFromEvmAddress(ownerAddress),
                "TestMemo",
                true,
                BigInteger.valueOf(0L),
                BigInteger.valueOf(0L),
                2_000_000_000L,
                true,
                List.of(
                        new TokenKeyWrapper(0b0000001, keyValue),
                        new TokenKeyWrapper(0b0000010, keyValue),
                        new TokenKeyWrapper(0b0000100, keyValue),
                        new TokenKeyWrapper(0b0001000, keyValue),
                        new TokenKeyWrapper(0b0010000, keyValue),
                        new TokenKeyWrapper(0b0100000, keyValue),
                        new TokenKeyWrapper(0b1000000, keyValue)),
                new TokenExpiryWrapper(
                        9999L, EntityIdUtils.accountIdFromEvmAddress(autoRenewAccountAddress), 8_000_000L));
    }

    protected ContractExecutionParameters serviceParametersForExecution(
            final Bytes callData,
            final Address contractAddress,
            final CallType callType,
            final long value,
            final BlockType block) {
        return serviceParametersForExecution(callData, contractAddress, callType, value, block, 15_000_000L);
    }

    protected ContractExecutionParameters serviceParametersForExecution(
            final Bytes callData,
            final Address contractAddress,
            final CallType callType,
            final long value,
            final BlockType block,
            final long gasLimit) {
        HederaEvmAccount sender;
        if (block != BlockType.LATEST) {
            sender = new HederaEvmAccount(SENDER_ADDRESS_HISTORICAL);
        } else {
            sender = new HederaEvmAccount(SENDER_ADDRESS);
        }
        // in the end, this persist will be removed because every test
        // will be responsible to persist its own needed data
        persistEntities();

        return ContractExecutionParameters.builder()
                .sender(sender)
                .value(value)
                .receiver(contractAddress)
                .callData(callData)
                .gas(gasLimit)
                .isStatic(false)
                .callType(callType)
                .isEstimate(ETH_ESTIMATE_GAS == callType)
                .block(block)
                .build();
    }

    protected void persistEntities() {
        genesisBlockPersist();
        historicalBlocksPersist();
        historicalDataPersist();
        precompileContractPersist();
        final var ercContract = ercContractPersist();

        receiverPersist();
        final var senderEntityId = senderEntityPersist();
        final var ownerEntityId = ownerEntityPersist();
        final var spenderEntityId = spenderEntityPersist();
        final var treasuryEntityId = treasureEntityPersist();
        autoRenewAccountPersist();

        fungibleTokenPersist(
                senderEntityId,
                KEY_PROTO,
                UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                9999999999999L,
                TokenPauseStatusEnum.UNPAUSED,
                false);
        final var tokenEntityId = fungibleTokenPersist(
                ownerEntityId,
                KEY_PROTO,
                FUNGIBLE_TOKEN_ADDRESS,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                9999999999999L,
                TokenPauseStatusEnum.PAUSED,
                true);
        final var notFrozenFungibleTokenEntityId = fungibleTokenPersist(
                treasuryEntityId,
                KEY_PROTO,
                NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                0L,
                TokenPauseStatusEnum.PAUSED,
                false);
        final var frozenFungibleTokenEntityId = fungibleTokenPersist(
                spenderEntityId,
                KEY_PROTO,
                FROZEN_FUNGIBLE_TOKEN_ADDRESS,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                9999999999999L,
                TokenPauseStatusEnum.PAUSED,
                true);
        final var transferFromTokenTreasuryEntityId = fungibleTokenPersist(
                treasuryEntityId,
                new byte[0],
                TRANSFRER_FROM_TOKEN_ADDRESS,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                9999999999999L,
                TokenPauseStatusEnum.UNPAUSED,
                false);
        final var tokenTreasuryEntityId = fungibleTokenPersist(
                treasuryEntityId,
                new byte[0],
                TREASURY_TOKEN_ADDRESS,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                9999999999999L,
                TokenPauseStatusEnum.UNPAUSED,
                false);
        final var tokenTreasuryWithAllKeysEntityId = fungibleTokenPersist(
                treasuryEntityId,
                KEY_PROTO,
                TREASURY_TOKEN_ADDRESS_WITH_ALL_KEYS,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                9999999999999L,
                TokenPauseStatusEnum.UNPAUSED,
                false);

        final var nftEntityId = nftPersist(
                NFT_ADDRESS,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                ownerEntityId,
                spenderEntityId,
                ownerEntityId,
                KEY_PROTO,
                TokenPauseStatusEnum.PAUSED,
                true);
        final var nftEntityId3 = nftPersist(
                NFT_TRANSFER_ADDRESS,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                ownerEntityId,
                spenderEntityId,
                ownerEntityId,
                KEY_PROTO,
                TokenPauseStatusEnum.UNPAUSED,
                false);
        final var ethAccount = ethAccountPersist(358L, ETH_ADDRESS);

        tokenAccountPersist(senderEntityId, tokenEntityId, TokenFreezeStatusEnum.FROZEN);
        tokenAccountPersist(ethAccount, tokenEntityId, TokenFreezeStatusEnum.FROZEN);
        tokenAccountPersist(senderEntityId, transferFromTokenTreasuryEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(senderEntityId, tokenTreasuryEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(senderEntityId, tokenTreasuryWithAllKeysEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(spenderEntityId, notFrozenFungibleTokenEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(spenderEntityId, tokenTreasuryEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(ethAccount, notFrozenFungibleTokenEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(senderEntityId, notFrozenFungibleTokenEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(spenderEntityId, frozenFungibleTokenEntityId, TokenFreezeStatusEnum.FROZEN);
        tokenAccountPersist(ethAccount, frozenFungibleTokenEntityId, TokenFreezeStatusEnum.FROZEN);
        tokenAccountPersist(ercContract, tokenEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(ercContract, nftEntityId, TokenFreezeStatusEnum.UNFROZEN);

        tokenAccountPersist(treasuryEntityId, notFrozenFungibleTokenEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(ethAccount, transferFromTokenTreasuryEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(ethAccount, tokenTreasuryEntityId, TokenFreezeStatusEnum.UNFROZEN);

        tokenAccountPersist(ownerEntityId, nftEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(senderEntityId, nftEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(spenderEntityId, nftEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(ownerEntityId, nftEntityId3, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(spenderEntityId, nftEntityId3, TokenFreezeStatusEnum.UNFROZEN);
        ercContractTokenPersist(ERC_CONTRACT_ADDRESS, tokenTreasuryEntityId, TokenFreezeStatusEnum.UNFROZEN);
        nftCustomFeePersist(senderEntityId, nftEntityId);

        allowancesPersist(senderEntityId, spenderEntityId, tokenEntityId, nftEntityId);
        allowancesPersist(ownerEntityId, ercContract, tokenEntityId, nftEntityId);
        allowancesPersist(senderEntityId, spenderEntityId, tokenTreasuryEntityId, nftEntityId3);
        contractAllowancesPersist(senderEntityId, ERC_CONTRACT_ADDRESS, tokenTreasuryEntityId, nftEntityId3);
    }

    protected void genesisBlockPersist() {
        genesisRecordFileForBlockHash =
                domainBuilder.recordFile().customize(f -> f.index(0L)).persist();
    }

    protected void historicalBlocksPersist() {
        recordFileBeforeEvm34 = domainBuilder
                .recordFile()
                .customize(f -> f.index(EVM_V_34_BLOCK - 1))
                .persist();
        recordFileAfterEvm34 = domainBuilder
                .recordFile()
                .customize(f -> f.index(EVM_V_34_BLOCK))
                .persist();
        recordFileEvm38 = domainBuilder
                .recordFile()
                .customize(f -> f.index(EVM_V_38_BLOCK))
                .persist();
        recordFileEvm46 = domainBuilder
                .recordFile()
                .customize(f -> f.index(EVM_V_46_BLOCK))
                .persist();
        recordFileEvm46Latest = domainBuilder.recordFile().persist();
    }

    protected void historicalDataPersist() {
        // Accounts
        final var ownerEntityId = ownerEntityPersistHistorical();
        final var senderEntityId = senderEntityPersistHistorical();
        final var spenderEntityId = spenderEntityPersistHistorical();
        autoRenewAccountPersistHistorical();

        // Fungible token
        final var tokenEntityId = entityIdFromEvmAddress(FUNGIBLE_TOKEN_ADDRESS_HISTORICAL);

        balancePersistHistorical(
                FUNGIBLE_TOKEN_ADDRESS_HISTORICAL,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));

        fungibleTokenPersistHistorical(
                ownerEntityId,
                KEY_PROTO,
                FUNGIBLE_TOKEN_ADDRESS_HISTORICAL,
                AUTO_RENEW_ACCOUNT_ADDRESS_HISTORICAL,
                9999999999999L,
                TokenPauseStatusEnum.PAUSED,
                true,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));

        // NFT
        final var nftEntityId = entityIdFromEvmAddress(NFT_ADDRESS_HISTORICAL);
        nftPersistHistorical(
                NFT_ADDRESS_HISTORICAL,
                AUTO_RENEW_ACCOUNT_ADDRESS_HISTORICAL,
                ownerEntityId,
                spenderEntityId,
                ownerEntityId,
                KEY_PROTO,
                TokenPauseStatusEnum.PAUSED,
                true,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));

        // Token relationships
        tokenAccountPersistHistorical(ownerEntityId, tokenEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersistHistorical(senderEntityId, tokenEntityId, TokenFreezeStatusEnum.FROZEN);
        tokenAccountPersistHistorical(ownerEntityId, nftEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersistHistorical(senderEntityId, nftEntityId, TokenFreezeStatusEnum.FROZEN);

        // Contracts
        final var contractEntityId = entityIdFromEvmAddress(ERC_CONTRACT_ADDRESS);
        final var precompileTestContractId = entityIdFromEvmAddress(PRECOMPILE_TEST_CONTRACT_ADDRESS);

        // Token allowances
        tokenAllowancePersistHistorical(tokenEntityId, senderEntityId, senderEntityId, spenderEntityId, 13L);
        tokenAllowancePersistHistorical(tokenEntityId, senderEntityId, senderEntityId, contractEntityId, 20L);
        tokenAllowancePersistHistorical(tokenEntityId, senderEntityId, senderEntityId, precompileTestContractId, 20L);

        nftAllowancePersistHistorical(nftEntityId, senderEntityId, senderEntityId, spenderEntityId);
        nftAllowancePersistHistorical(nftEntityId, senderEntityId, senderEntityId, contractEntityId);
        nftAllowancePersistHistorical(nftEntityId, senderEntityId, senderEntityId, precompileTestContractId);

        fungibleTokenPersistHistorical(
                senderEntityId,
                keyWithContractId.toByteArray(),
                FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_CONTRACT_ADDRESS_HISTORICAL,
                AUTO_RENEW_ACCOUNT_ADDRESS_HISTORICAL,
                9999999999999L,
                TokenPauseStatusEnum.PAUSED,
                false,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));

        fungibleTokenPersistHistorical(
                senderEntityId,
                keyWithEd25519.toByteArray(),
                FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ED25519_KEY_HISTORICAL,
                AUTO_RENEW_ACCOUNT_ADDRESS_HISTORICAL,
                9999999999999L,
                TokenPauseStatusEnum.PAUSED,
                false,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));

        fungibleTokenPersistHistorical(
                senderEntityId,
                keyWithECDSASecp256K1.toByteArray(),
                FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ECDSA_KEY_HISTORICAL,
                AUTO_RENEW_ACCOUNT_ADDRESS_HISTORICAL,
                9999999999999L,
                TokenPauseStatusEnum.PAUSED,
                false,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));

        fungibleTokenPersistHistorical(
                senderEntityId,
                keyWithDelegatableContractId.toByteArray(),
                FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_DELEGATABLE_CONTRACT_ID_HISTORICAL,
                AUTO_RENEW_ACCOUNT_ADDRESS_HISTORICAL,
                9999999999999L,
                TokenPauseStatusEnum.PAUSED,
                false,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));

        nftPersistHistorical(
                NFT_ADDRESS_GET_KEY_WITH_CONTRACT_ADDRESS_HISTORICAL,
                AUTO_RENEW_ACCOUNT_ADDRESS_HISTORICAL,
                ownerEntityId,
                spenderEntityId,
                ownerEntityId,
                keyWithContractId.toByteArray(),
                TokenPauseStatusEnum.PAUSED,
                true,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));

        nftPersistHistorical(
                NFT_ADDRESS_GET_KEY_WITH_ED25519_KEY_HISTORICAL,
                AUTO_RENEW_ACCOUNT_ADDRESS_HISTORICAL,
                ownerEntityId,
                spenderEntityId,
                ownerEntityId,
                keyWithEd25519.toByteArray(),
                TokenPauseStatusEnum.PAUSED,
                true,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));

        nftPersistHistorical(
                NFT_ADDRESS_GET_KEY_WITH_ECDSA_KEY_HISTORICAL,
                AUTO_RENEW_ACCOUNT_ADDRESS_HISTORICAL,
                ownerEntityId,
                spenderEntityId,
                ownerEntityId,
                keyWithECDSASecp256K1.toByteArray(),
                TokenPauseStatusEnum.PAUSED,
                true,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));

        nftPersistHistorical(
                NFT_ADDRESS_GET_KEY_WITH_DELEGATABLE_CONTRACT_ID_HISTORICAL,
                AUTO_RENEW_ACCOUNT_ADDRESS_HISTORICAL,
                ownerEntityId,
                spenderEntityId,
                ownerEntityId,
                keyWithDelegatableContractId.toByteArray(),
                TokenPauseStatusEnum.PAUSED,
                true,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));

        fungibleTokenPersistHistorical(
                ownerEntityId,
                KEY_PROTO,
                FUNGIBLE_TOKEN_ADDRESS_WITH_EXPIRY_HISTORICAL,
                AUTO_RENEW_ACCOUNT_ADDRESS_HISTORICAL,
                1000000000000L,
                TokenPauseStatusEnum.PAUSED,
                false,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));

        domainBuilder
                .customFeeHistory()
                .customize(f -> f.tokenId(nftEntityId.getId())
                        .fractionalFees(List.of(FractionalFee.builder()
                                .collectorAccountId(senderEntityId)
                                .build()))
                        .royaltyFees(List.of())
                        .fixedFees(List.of())
                        .timestampRange(Range.closedOpen(
                                recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd())))
                .persist();
    }

    protected void customFeePersistHistorical(final FeeCase feeCase, final Range<Long> historicalBlock) {
        final var collectorAccountId = entityIdFromEvmAddress(SENDER_ADDRESS_HISTORICAL);
        final var tokenEntityId = entityIdFromEvmAddress(FUNGIBLE_TOKEN_ADDRESS_HISTORICAL);
        switch (feeCase) {
            case ROYALTY_FEE -> {
                final var royaltyFee = RoyaltyFee.builder()
                        .collectorAccountId(collectorAccountId)
                        .denominator(10L)
                        .fallbackFee(FallbackFee.builder()
                                .amount(100L)
                                .denominatingTokenId(tokenEntityId)
                                .build())
                        .numerator(20L)
                        .build();
                domainBuilder
                        .customFee()
                        .customize(f -> f.royaltyFees(List.of(royaltyFee))
                                .fixedFees(List.of())
                                .fractionalFees(List.of())
                                .tokenId(tokenEntityId.getId())
                                .timestampRange(historicalBlock))
                        .persist();
            }
            case FRACTIONAL_FEE -> {
                final var fractionalFee = FractionalFee.builder()
                        .collectorAccountId(collectorAccountId)
                        .denominator(10L)
                        .minimumAmount(1L)
                        .maximumAmount(1000L)
                        .netOfTransfers(true)
                        .numerator(100L)
                        .build();
                domainBuilder
                        .customFee()
                        .customize(f -> f.fractionalFees(List.of(fractionalFee))
                                .fixedFees(List.of())
                                .royaltyFees(List.of())
                                .tokenId(tokenEntityId.getId())
                                .timestampRange(historicalBlock))
                        .persist();
            }
            case FIXED_FEE -> {
                final var fixedFee = FixedFee.builder()
                        .amount(100L)
                        .collectorAccountId(collectorAccountId)
                        .denominatingTokenId(tokenEntityId)
                        .build();
                domainBuilder
                        .customFee()
                        .customize(f -> f.fixedFees(List.of(fixedFee))
                                .fractionalFees(List.of())
                                .royaltyFees(List.of())
                                .tokenId(tokenEntityId.getId())
                                .timestampRange(historicalBlock))
                        .persist();
            }
            default -> domainBuilder
                    .customFee()
                    .customize(f -> f.tokenId(tokenEntityId.getId()).timestampRange(historicalBlock))
                    .persist();
        }
    }

    private void nftCustomFeePersist(final EntityId senderEntityId, final EntityId nftEntityId) {
        domainBuilder
                .customFee()
                .customize(f -> f.tokenId(nftEntityId.getId())
                        .fractionalFees(List.of(FractionalFee.builder()
                                .collectorAccountId(senderEntityId)
                                .build()))
                        .royaltyFees(List.of())
                        .fixedFees(List.of()))
                .persist();
    }

    // Account persist
    protected void tokenAccountPersist(
            final EntityId senderEntityId, final EntityId tokenEntityId, final TokenFreezeStatusEnum freezeStatus) {
        domainBuilder
                .tokenAccount()
                .customize(e -> e.freezeStatus(freezeStatus)
                        .accountId(senderEntityId.getId())
                        .tokenId(tokenEntityId.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .associated(true)
                        .balance(12L))
                .persist();
    }

    private void tokenAccountPersist(
            final long ethAccount, final EntityId tokenEntityId, final TokenFreezeStatusEnum freezeStatus) {
        domainBuilder
                .tokenAccount()
                .customize(e -> e.freezeStatus(freezeStatus)
                        .accountId(ethAccount)
                        .tokenId(tokenEntityId.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .balance(10L))
                .persist();
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
                        .balance(12L)
                        .timestampRange(Range.closedOpen(
                                recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd())))
                .persist();
    }

    private void ercContractTokenPersist(
            final Address contractAddress, final EntityId tokenEntityId, final TokenFreezeStatusEnum freezeStatusEnum) {
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        domainBuilder
                .tokenAccount()
                .customize(e -> e.freezeStatus(freezeStatusEnum)
                        .accountId(contractEntityId.getNum())
                        .tokenId(tokenEntityId.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .balance(10L))
                .persist();
    }

    protected EntityId spenderEntityPersist() {
        final var spenderEntityId = entityIdFromEvmAddress(SPENDER_ADDRESS);
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

    private EntityId spenderEntityPersistHistorical() {
        final var spenderEntityId = entityIdFromEvmAddress(SPENDER_ADDRESS_HISTORICAL);

        domainBuilder
                .entity()
                .customize(e -> e.id(spenderEntityId.getId())
                        .num(spenderEntityId.getNum())
                        .evmAddress(SPENDER_ALIAS_HISTORICAL.toArray())
                        .alias(SPENDER_PUBLIC_KEY_HISTORICAL.toByteArray())
                        .deleted(false)
                        .createdTimestamp(recordFileAfterEvm34.getConsensusStart())
                        .timestampRange(Range.closedOpen(
                                recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd())))
                .persist();
        return spenderEntityId;
    }

    private long ethAccountPersist(final long ethAccount, final Address evmAddress) {

        domainBuilder
                .entity()
                .customize(e -> e.id(ethAccount)
                        .num(ethAccount)
                        .evmAddress(evmAddress.toArrayUnsafe())
                        .balance(2000L)
                        .deleted(false))
                .persist();
        return ethAccount;
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

    private EntityId senderEntityPersistHistorical() {
        final var senderEntityId = entityIdFromEvmAddress(SENDER_ADDRESS_HISTORICAL);

        domainBuilder
                .entity()
                .customize(e -> e.id(senderEntityId.getId())
                        .num(senderEntityId.getNum())
                        .evmAddress(SENDER_ALIAS_HISTORICAL.toArray())
                        .deleted(false)
                        .alias(SENDER_PUBLIC_KEY_HISTORICAL.toByteArray())
                        .balance(10000 * 100_000_000L)
                        .createdTimestamp(recordFileAfterEvm34.getConsensusStart())
                        .timestampRange(Range.closedOpen(
                                recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd())))
                .persist();

        return senderEntityId;
    }

    protected EntityId ownerEntityPersist() {
        final var ownerEntityId = entityIdFromEvmAddress(OWNER_ADDRESS);

        domainBuilder
                .entity()
                .customize(e -> e.id(ownerEntityId.getId())
                        .num(ownerEntityId.getNum())
                        .evmAddress(null)
                        .alias(toEvmAddress(ownerEntityId))
                        .balance(20000L))
                .persist();
        return ownerEntityId;
    }

    private EntityId ownerEntityPersistHistorical() {
        final var ownerEntityId = entityIdFromEvmAddress(OWNER_ADDRESS_HISTORICAL);

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

    private EntityId autoRenewAccountPersist() {
        final var autoRenewEntityId = entityIdFromEvmAddress(AUTO_RENEW_ACCOUNT_ADDRESS);

        domainBuilder
                .entity()
                .customize(e -> e.id(autoRenewEntityId.getId())
                        .num(autoRenewEntityId.getNum())
                        .evmAddress(null)
                        .alias(toEvmAddress(autoRenewEntityId)))
                .persist();
        return autoRenewEntityId;
    }

    private EntityId autoRenewAccountPersistHistorical() {
        final var autoRenewEntityId = entityIdFromEvmAddress(AUTO_RENEW_ACCOUNT_ADDRESS_HISTORICAL);

        domainBuilder
                .entity()
                .customize(e -> e.id(autoRenewEntityId.getId())
                        .num(autoRenewEntityId.getNum())
                        .evmAddress(null)
                        .alias(toEvmAddress(autoRenewEntityId))
                        .timestampRange(Range.closedOpen(
                                recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd())))
                .persist();

        return autoRenewEntityId;
    }

    protected EntityId treasureEntityPersist() {
        final var treasuryEntityId = entityIdFromEvmAddress(TREASURY_ADDRESS);

        domainBuilder
                .entity()
                .customize(e -> e.id(treasuryEntityId.getId())
                        .num(treasuryEntityId.getNum())
                        .evmAddress(null)
                        .alias(toEvmAddress(treasuryEntityId)))
                .persist();
        return treasuryEntityId;
    }

    protected void receiverPersist() {
        final var receiverEntityId = entityIdFromEvmAddress(RECEIVER_ADDRESS);
        final var receiverEvmAddress = toEvmAddress(receiverEntityId);
        domainBuilder
                .entity()
                .customize(e -> e.id(receiverEntityId.getId())
                        .num(receiverEntityId.getNum())
                        .evmAddress(receiverEvmAddress)
                        .deleted(false)
                        .type(CONTRACT))
                .persist();
    }

    // Token persist
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

    private EntityId balancePersistHistorical(final Address tokenAddress, final Range<Long> historicalBlock) {
        final var tokenEntityId = entityIdFromEvmAddress(tokenAddress);
        final var accountId = entityIdFromEvmAddress(SENDER_ADDRESS_HISTORICAL);
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
                        .balance(12L))
                .persist();
        domainBuilder
                .tokenBalance()
                // Expected total supply is 12345
                .customize(tb -> tb.balance(12345L - 12L)
                        .id(new TokenBalance.Id(lowerTimestamp, domainBuilder.entityId(), tokenEntityId)))
                .persist();

        return tokenEntityId;
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

    protected EntityId nftPersist(
            final Address nftAddress,
            final Address autoRenewAddress,
            final EntityId ownerEntityId,
            final EntityId spenderEntityId,
            final EntityId treasuryId,
            final byte[] key,
            final TokenPauseStatusEnum pauseStatus,
            final boolean freezeDefault) {
        final var nftEntityId = entityIdFromEvmAddress(nftAddress);
        final var autoRenewEntityId = entityIdFromEvmAddress(autoRenewAddress);
        final var nftEvmAddress = toEvmAddress(nftEntityId);
        final var ownerEntity = EntityId.of(ownerEntityId.getId());

        domainBuilder
                .entity()
                .customize(e -> e.id(nftEntityId.getId())
                        .autoRenewAccountId(autoRenewEntityId.getId())
                        .expirationTimestamp(null)
                        .num(nftEntityId.getNum())
                        .evmAddress(nftEvmAddress)
                        .type(TOKEN)
                        .balance(1500L)
                        .key(key)
                        .expirationTimestamp(9999999999999L)
                        .memo("TestMemo"))
                .persist();

        domainBuilder
                .token()
                .customize(t -> t.tokenId(nftEntityId.getId())
                        .treasuryAccountId(treasuryId)
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .kycKey(key)
                        .freezeDefault(freezeDefault)
                        .feeScheduleKey(key)
                        .totalSupply(1_000_000_000L)
                        .maxSupply(2_000_000_000L)
                        .name("Hbars")
                        .supplyType(TokenSupplyTypeEnum.FINITE)
                        .freezeKey(key)
                        .pauseKey(key)
                        .pauseStatus(pauseStatus)
                        .wipeKey(key)
                        .supplyKey(key)
                        .symbol("HBAR")
                        .wipeKey(key))
                .persist();

        domainBuilder
                .nft()
                .customize(n -> n.accountId(spenderEntityId)
                        .createdTimestamp(1475067194949034022L)
                        .serialNumber(1)
                        .spender(spenderEntityId)
                        .metadata("NFT_METADATA_URI".getBytes())
                        .accountId(ownerEntity)
                        .timestampRange(Range.atLeast(1475067194949034022L))
                        .tokenId(nftEntityId.getId()))
                .persist();
        return nftEntityId;
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
                        .spender(spenderEntityId)
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
                        .createdTimestamp(1475067194949034022L)
                        .serialNumber(3L)
                        .spender(spenderEntityId)
                        .metadata("NFT_METADATA_URI".getBytes())
                        .accountId(ownerEntity)
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

    // Allowances persist
    protected void allowancesPersist(
            final EntityId senderEntityId,
            final EntityId spenderEntityId,
            final EntityId tokenEntityId,
            final EntityId nftEntityId) {
        domainBuilder
                .tokenAllowance()
                .customize(a -> a.tokenId(tokenEntityId.getId())
                        .payerAccountId(senderEntityId)
                        .owner(senderEntityId.getNum())
                        .spender(spenderEntityId.getNum())
                        .amount(13))
                .persist();

        domainBuilder
                .nftAllowance()
                .customize(a -> a.tokenId(nftEntityId.getId())
                        .spender(spenderEntityId.getNum())
                        .owner(senderEntityId.getNum())
                        .approvedForAll(true)
                        .payerAccountId(senderEntityId))
                .persist();
    }

    private void tokenAllowancePersistHistorical(
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

    protected void contractAllowancesPersist(
            final EntityId senderEntityId,
            final Address contractAddress,
            final EntityId tokenEntityId,
            final EntityId nftEntityId) {
        final var contractId = entityIdFromEvmAddress(contractAddress);
        domainBuilder
                .tokenAllowance()
                .customize(a -> a.tokenId(tokenEntityId.getId())
                        .payerAccountId(senderEntityId)
                        .owner(senderEntityId.getNum())
                        .spender(contractId.getNum())
                        .amount(20))
                .persist();

        domainBuilder
                .nftAllowance()
                .customize(a -> a.tokenId(nftEntityId.getId())
                        .owner(senderEntityId.getNum())
                        .spender(contractId.getNum())
                        .approvedForAll(true)
                        .payerAccountId(senderEntityId))
                .persist();
    }

    protected void precompileContractPersist() {
        final var contractBytes = functionEncodeDecoder.getContractBytes(CONTRACT_BYTES_PATH);
        final var contractEntityId = entityIdFromEvmAddress(PRECOMPILE_TEST_CONTRACT_ADDRESS);
        final var contractEvmAddress = toEvmAddress(contractEntityId);

        domainBuilder
                .entity()
                .customize(e -> e.id(contractEntityId.getId())
                        .num(contractEntityId.getNum())
                        .evmAddress(contractEvmAddress)
                        .type(CONTRACT)
                        .balance(1500L)
                        .timestampRange(Range.closedOpen(
                                recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd())))
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

    private EntityId ercContractPersist() {
        final var ercContractBytes = functionEncodeDecoder.getContractBytes(ERC_CONTRACT_BYTES_PATH);
        final var ercContractEntityId = entityIdFromEvmAddress(ERC_CONTRACT_ADDRESS);
        final var ercContractEvmAddress = toEvmAddress(ercContractEntityId);

        domainBuilder
                .entity()
                .customize(e -> e.id(ercContractEntityId.getId())
                        .num(ercContractEntityId.getNum())
                        .evmAddress(ercContractEvmAddress)
                        .type(CONTRACT)
                        .balance(1500L)
                        .timestampRange(Range.closedOpen(
                                recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd())))
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
        return ercContractEntityId;
    }
}
