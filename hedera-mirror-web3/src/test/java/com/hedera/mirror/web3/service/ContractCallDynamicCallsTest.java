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
import static com.hedera.mirror.common.util.DomainUtils.fromEvmAddress;
import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.EthereumTransaction;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.common.domain.token.TokenPauseStatusEnum;
import com.hedera.mirror.common.domain.token.TokenSupplyTypeEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessor;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.utils.FunctionEncodeDecoder;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.services.hapi.utils.ByteStringUtils;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.function.ToLongFunction;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.data.Percentage;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.datatypes.Address;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

class ContractCallDynamicCallsTest extends Web3IntegrationTest {

    // Contract addresses
    private static final Address DYNAMIC_ETH_CALLS_CONTRACT_ALIAS =
            Address.fromHexString("0x742d35Cc6634C0532925a3b844Bc454e4438f44e");
    // Account addresses
    private static final Address AUTO_RENEW_ACCOUNT_ADDRESS = toAddress(EntityId.of(0, 0, 740));
    private static final ByteString SPENDER_PUBLIC_KEY =
            ByteString.fromHex("3a2102ff806fecbd31b4c377293cba8d2b78725965a4990e0ff1b1b29a1d2c61402310");
    private static final Address SPENDER_ALIAS = Address.wrap(
            Bytes.wrap(recoverAddressFromPubKey(SPENDER_PUBLIC_KEY.substring(2).toByteArray())));
    private static final Address SENDER_ADDRESS = toAddress(EntityId.of(0, 0, 1043));
    private static final ByteString SENDER_PUBLIC_KEY =
            ByteString.copyFrom(Hex.decode("3a2103af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d"));
    private static final Address SENDER_ALIAS = Address.wrap(
            Bytes.wrap(recoverAddressFromPubKey(SENDER_PUBLIC_KEY.substring(2).toByteArray())));
    private static final Address TREASURY_ADDRESS = toAddress(EntityId.of(0, 0, 743));
    private static final ByteString NOT_ASSOCIATED_SPENDER_PUBLIC_KEY =
            ByteString.fromHex("3a21033a514176466fa815ed481ffad09110a2d344f6c9b78c1d14afc351c3a51be33d");
    private static final Address NOT_ASSOCIATED_SPENDER_ALIAS = Address.wrap(Bytes.wrap(recoverAddressFromPubKey(
            NOT_ASSOCIATED_SPENDER_PUBLIC_KEY.substring(2).toByteArray())));
    private static final Address OWNER_ADDRESS = toAddress(EntityId.of(0, 0, 1044));

    // Token addresses
    private static final Address RECEIVER_ADDRESS = toAddress(EntityId.of(0, 0, 1045));
    private static final Address FUNGIBLE_TOKEN_ADDRESS = toAddress(EntityId.of(0, 0, 1046));
    private static final Address NFT_ADDRESS = toAddress(EntityId.of(0, 0, 1047));
    private static final Address NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS = toAddress(EntityId.of(0, 0, 1048));
    private static final Address TREASURY_TOKEN_ADDRESS = toAddress(EntityId.of(0, 0, 1049));
    private static final Address NFT_TRANSFER_ADDRESS = toAddress(EntityId.of(0, 0, 1051));
    private static final Address NFT_ADDRESS_WITH_DIFFERENT_OWNER_AND_TREASURY = toAddress(EntityId.of(0, 0, 1067));
    private static final Address NFT_TRANSFER_ADDRESS_WITHOUT_KYC_KEY = toAddress(EntityId.of(0, 0, 1071));

    private static final byte[] KEY_PROTO = new byte[] {
        58, 33, -52, -44, -10, 81, 99, 100, 6, -8, -94, -87, -112, 42, 42, 96, 75, -31, -5, 72, 13, -70, 101, -111, -1,
        77, -103, 47, -118, 107, -58, -85, -63, 55, -57
    };

    // Fee schedules
    private static final ToLongFunction<String> longValueOf =
            value -> Bytes.fromHexString(value).toLong();

    @Autowired
    private MirrorEvmTxProcessor processor;

    @Autowired
    private FunctionEncodeDecoder functionEncodeDecoder;

    @Autowired
    private ContractCallService contractCallService;

    @Autowired
    private MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @Value("classpath:contracts/DynamicEthCalls/DynamicEthCalls.bin")
    private Path DYNAMIC_ETH_CALLS_BYTES_PATH;

    @Value("classpath:contracts/DynamicEthCalls/DynamicEthCalls.json")
    private Path DYNAMIC_ETH_CALLS_ABI_PATH;

    @ParameterizedTest
    @EnumSource(DynamicCallsContractFunctions.class)
    void dynamicCallsTestWithAliasSenderForEthCall(DynamicCallsContractFunctions contractFunctions) {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                contractFunctions.name, DYNAMIC_ETH_CALLS_ABI_PATH, contractFunctions.functionParameters);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, DYNAMIC_ETH_CALLS_CONTRACT_ALIAS, ETH_CALL, 0L, BlockType.LATEST, SENDER_ADDRESS);
        if (contractFunctions.expectedErrorMessage != null) {
            assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                    .isInstanceOf(MirrorEvmTransactionException.class)
                    .satisfies(ex -> {
                        MirrorEvmTransactionException exception = (MirrorEvmTransactionException) ex;
                        assertEquals(exception.getDetail(), contractFunctions.expectedErrorMessage);
                    });
        } else {
            contractCallService.processCall(serviceParameters);
        }
    }

    @ParameterizedTest
    @EnumSource(DynamicCallsContractFunctions.class)
    void dynamicCallsTestWithAliasSenderForEstimateGas(DynamicCallsContractFunctions contractFunctions) {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                contractFunctions.name, DYNAMIC_ETH_CALLS_ABI_PATH, contractFunctions.functionParameters);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, DYNAMIC_ETH_CALLS_CONTRACT_ALIAS, ETH_ESTIMATE_GAS, 0L, BlockType.LATEST, SENDER_ADDRESS);
        if (contractFunctions.expectedErrorMessage != null) {
            assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                    .isInstanceOf(MirrorEvmTransactionException.class)
                    .satisfies(ex -> {
                        MirrorEvmTransactionException exception = (MirrorEvmTransactionException) ex;
                        assertEquals(exception.getDetail(), contractFunctions.expectedErrorMessage);
                    });
        } else {
            final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);
            assertThat(longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)))
                    .as("result must be within 5-20% bigger than the gas used from the first call")
                    .isGreaterThanOrEqualTo((long) (expectedGasUsed * 1.05)) // expectedGasUsed value increased by 5%
                    .isCloseTo(expectedGasUsed, Percentage.withPercentage(20)); // Maximum percentage
        }
    }

    private long gasUsedAfterExecution(final CallServiceParameters serviceParameters) {
        return ContractCallContext.run(ctx -> {
            ctx.initializeStackFrames(store.getStackedStateFrames());
            long result = processor
                    .execute(serviceParameters, serviceParameters.getGas())
                    .getGasUsed();

            assertThat(store.getStackedStateFrames().height()).isEqualTo(1);
            return result;
        });
    }

    @Test
    void testGetAddressThisWithEvmAliasRecipient() {
        String ethCallContractWithout0x =
                DYNAMIC_ETH_CALLS_CONTRACT_ALIAS.toString().substring(2);
        String successfulResponse = "0x000000000000000000000000" + ethCallContractWithout0x;
        final var functionHash = functionEncodeDecoder.functionHashFor("getAddressThis", DYNAMIC_ETH_CALLS_ABI_PATH);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, DYNAMIC_ETH_CALLS_CONTRACT_ALIAS, ETH_CALL, 0L, BlockType.LATEST, SENDER_ADDRESS);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
    }

    @Test
    void testGetAddressThisWithLongZeroRecipientThatHasEvmAlias() {
        String ethCallContractWithout0x =
                DYNAMIC_ETH_CALLS_CONTRACT_ALIAS.toString().substring(2);
        String successfulResponse = "0x000000000000000000000000" + ethCallContractWithout0x;
        final var functionHash = functionEncodeDecoder.functionHashFor("getAddressThis", DYNAMIC_ETH_CALLS_ABI_PATH);
        var dynamicEthCallsContractAddress = toAddress(EntityId.of(0, 0, 1255));
        final var serviceParameters = serviceParametersForExecution(
                functionHash, dynamicEthCallsContractAddress, ETH_CALL, 0L, BlockType.LATEST, SENDER_ADDRESS);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
    }

    protected void persistEntities() {
        final var nestedContractId = dynamicEthCallContractPresist();
        final var senderEntityId = senderEntityPersist();
        final var ownerEntityId = ownerEntityPersist();
        final var spenderEntityId = spenderEntityPersist();
        notAssociatedSpenderEntityPersist();
        final var treasuryEntityId = treasureEntityPersist();

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

        final var tokenTreasuryEntityId = fungibleTokenPersist(
                treasuryEntityId,
                new byte[0],
                TREASURY_TOKEN_ADDRESS,
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
        final var nftEntityId2 = nftPersist(
                NFT_ADDRESS_WITH_DIFFERENT_OWNER_AND_TREASURY,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                senderEntityId,
                spenderEntityId,
                ownerEntityId,
                KEY_PROTO,
                TokenPauseStatusEnum.UNPAUSED,
                false);
        final var nftEntityId3 = nftPersist(
                NFT_TRANSFER_ADDRESS,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                ownerEntityId,
                spenderEntityId,
                ownerEntityId,
                KEY_PROTO,
                TokenPauseStatusEnum.UNPAUSED,
                false);

        final var nftEntityId8 = nftPersistWithoutKycKey(
                NFT_TRANSFER_ADDRESS_WITHOUT_KYC_KEY,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                nestedContractId,
                spenderEntityId,
                nestedContractId,
                KEY_PROTO,
                TokenPauseStatusEnum.UNPAUSED,
                false);

        tokenAccountPersist(senderEntityId, tokenEntityId, TokenFreezeStatusEnum.FROZEN);
        tokenAccountPersist(spenderEntityId, notFrozenFungibleTokenEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(senderEntityId, notFrozenFungibleTokenEntityId, TokenFreezeStatusEnum.UNFROZEN);

        tokenAccountPersist(treasuryEntityId, notFrozenFungibleTokenEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(nestedContractId, tokenEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(nestedContractId, nftEntityId3, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(nestedContractId, tokenTreasuryEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(nestedContractId, nftEntityId8, TokenFreezeStatusEnum.UNFROZEN);

        tokenAccountPersist(ownerEntityId, nftEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(senderEntityId, nftEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(spenderEntityId, nftEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(ownerEntityId, nftEntityId3, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(spenderEntityId, nftEntityId3, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(senderEntityId, nftEntityId2, TokenFreezeStatusEnum.UNFROZEN);

        allowancesPersist(ownerEntityId, nestedContractId, tokenEntityId, nftEntityId);
        exchangeRatesPersist();
        feeSchedulesPersist();
    }

    @Nullable
    private EntityId ownerEntityPersist() {
        final var ownerEntityId = fromEvmAddress(OWNER_ADDRESS.toArrayUnsafe());

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

    @Nullable
    private EntityId nftPersist(
            final Address nftAddress,
            final Address autoRenewAddress,
            final EntityId ownerEntityId,
            final EntityId spenderEntityId,
            final EntityId treasuryId,
            final byte[] key,
            final TokenPauseStatusEnum pauseStatus,
            final boolean freezeDefault) {
        final var nftEntityId = fromEvmAddress(nftAddress.toArrayUnsafe());
        final var autoRenewEntityId = fromEvmAddress(autoRenewAddress.toArrayUnsafe());
        final var nftEvmAddress = toEvmAddress(nftEntityId);
        final var ownerEntity = EntityId.of(0, 0, ownerEntityId.getId());

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

    @Nullable
    private EntityId nftPersistWithoutKycKey(
            final Address nftAddress,
            final Address autoRenewAddress,
            final EntityId ownerEntityId,
            final EntityId spenderEntityId,
            final EntityId treasuryId,
            final byte[] key,
            final TokenPauseStatusEnum pauseStatus,
            final boolean freezeDefault) {
        final var nftEntityId = fromEvmAddress(nftAddress.toArrayUnsafe());
        final var autoRenewEntityId = fromEvmAddress(autoRenewAddress.toArrayUnsafe());
        final var nftEvmAddress = toEvmAddress(nftEntityId);
        final var ownerEntity = EntityId.of(0, 0, ownerEntityId.getId());

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
                        .memo("TestMemo"))
                .persist();

        domainBuilder
                .token()
                .customize(t -> t.tokenId(nftEntityId.getId())
                        .treasuryAccountId(treasuryId)
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .kycKey(null)
                        .freezeDefault(freezeDefault)
                        .feeScheduleKey(key)
                        .maxSupply(2000000000L)
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

    private EntityId senderEntityPersist() {
        final var senderEntityId = fromEvmAddress(SENDER_ADDRESS.toArrayUnsafe());

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

    @Nullable
    private EntityId spenderEntityPersist() {
        final Address spenderAddress = toAddress(EntityId.of(0, 0, 1041));
        final var spenderEntityId = fromEvmAddress(spenderAddress.toArrayUnsafe());
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

    // Entity persist
    @Nullable
    private EntityId notAssociatedSpenderEntityPersist() {
        final Address notAssociatedSpenderAddress = toAddress(EntityId.of(0, 0, 1066));
        final var spenderEntityId = fromEvmAddress(notAssociatedSpenderAddress.toArrayUnsafe());
        domainBuilder
                .entity()
                .customize(e -> e.id(spenderEntityId.getId())
                        .num(spenderEntityId.getNum())
                        .evmAddress(NOT_ASSOCIATED_SPENDER_ALIAS.toArray())
                        .alias(NOT_ASSOCIATED_SPENDER_PUBLIC_KEY.toByteArray())
                        .deleted(false))
                .persist();
        return spenderEntityId;
    }

    @Nullable
    private EntityId treasureEntityPersist() {
        final var treasuryEntityId = fromEvmAddress(TREASURY_ADDRESS.toArrayUnsafe());

        domainBuilder
                .entity()
                .customize(e -> e.id(treasuryEntityId.getId())
                        .num(treasuryEntityId.getNum())
                        .evmAddress(null)
                        .alias(toEvmAddress(treasuryEntityId)))
                .persist();
        return treasuryEntityId;
    }

    // Token persist
    private EntityId fungibleTokenPersist(
            final EntityId treasuryId,
            final byte[] key,
            final Address tokenAddress,
            final Address autoRenewAddress,
            final long tokenExpiration,
            final TokenPauseStatusEnum pauseStatus,
            final boolean freezeDefault) {
        final var tokenEntityId = fromEvmAddress(tokenAddress.toArrayUnsafe());
        final var autoRenewEntityId = fromEvmAddress(autoRenewAddress.toArrayUnsafe());
        final var tokenEvmAddress = toEvmAddress(tokenEntityId);

        domainBuilder
                .entity()
                .customize(e -> e.id(tokenEntityId.getId())
                        .autoRenewAccountId(autoRenewEntityId.getId())
                        .num(tokenEntityId.getNum())
                        .evmAddress(tokenEvmAddress)
                        .type(TOKEN)
                        .balance(1500L)
                        .key(key)
                        .expirationTimestamp(tokenExpiration)
                        .memo("TestMemo"))
                .persist();

        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntityId.getId())
                        .treasuryAccountId(EntityId.of(0, 0, treasuryId.getId()))
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

    // Account persist
    private void tokenAccountPersist(
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

    // Allowances persist
    private void allowancesPersist(
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

    private EntityId dynamicEthCallContractPresist() {
        var dynamicEthCallsContractAddress = toAddress(EntityId.of(0, 0, 1255));

        final var contractBytes = functionEncodeDecoder.getContractBytes(DYNAMIC_ETH_CALLS_BYTES_PATH);
        final var contractEntityId = fromEvmAddress(dynamicEthCallsContractAddress.toArrayUnsafe());

        domainBuilder
                .entity()
                .customize(e -> e.id(contractEntityId.getId())
                        .num(contractEntityId.getNum())
                        .evmAddress(DYNAMIC_ETH_CALLS_CONTRACT_ALIAS.toArray())
                        .alias(ByteStringUtils.wrapUnsafely(SENDER_ALIAS.toArrayUnsafe())
                                .toByteArray())
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
        return contractEntityId;
    }

    private void exchangeRatesPersist() {
        // Exchange rates from local node.
        ExchangeRateSet exchangeRatesSet = ExchangeRateSet.newBuilder()
                .setCurrentRate(ExchangeRate.newBuilder()
                        .setCentEquiv(12)
                        .setHbarEquiv(1)
                        .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(4_102_444_800L))
                        .build())
                .build();

        var exchangeRateEntityId = EntityId.of(0L, 0L, 112L);
        domainBuilder
                .fileData()
                .customize(f -> f.fileData(exchangeRatesSet.toByteArray())
                        .entityId(exchangeRateEntityId)
                        .consensusTimestamp(EXPIRY))
                .persist();
    }

    private void feeSchedulesPersist() {
        var feeScheduleEntityId = EntityId.of(0L, 0L, 111L);
        var feeSchedules = CurrentAndNextFeeSchedule.newBuilder()
                .setCurrentFeeSchedule(FeeSchedule.newBuilder()
                        .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                                .setHederaFunctionality(ContractCall)
                                .addFees(FeeData.newBuilder()
                                        .setServicedata(FeeComponents.newBuilder()
                                                .setGas(852000)
                                                .build())))
                        .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                                .setHederaFunctionality(EthereumTransaction)
                                .addFees(FeeData.newBuilder()
                                        .setServicedata(FeeComponents.newBuilder()
                                                .setGas(852000)
                                                .build())))
                        .addTransactionFeeSchedule(
                                TransactionFeeSchedule.newBuilder().setHederaFunctionality(TokenMint))
                        .addTransactionFeeSchedule(
                                TransactionFeeSchedule.newBuilder().setHederaFunctionality(CryptoTransfer))
                        .addTransactionFeeSchedule(
                                TransactionFeeSchedule.newBuilder().setHederaFunctionality(TokenAccountWipe))
                        .addTransactionFeeSchedule(
                                TransactionFeeSchedule.newBuilder().setHederaFunctionality(TokenBurn))
                        .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                                .setHederaFunctionality(TokenCreate)
                                .build()))
                .build();
        domainBuilder
                .fileData()
                .customize(f -> f.fileData(feeSchedules.toByteArray())
                        .entityId(feeScheduleEntityId)
                        .consensusTimestamp(EXPIRY + 1))
                .persist();
    }

    @RequiredArgsConstructor
    enum DynamicCallsContractFunctions {
        MINT_FUNGIBLE_TOKEN(
                "mintTokenGetTotalSupplyAndBalanceOfTreasury",
                new Object[] {NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS, 100L, new byte[0][0], TREASURY_ADDRESS},
                null),
        MINT_NFT(
                "mintTokenGetTotalSupplyAndBalanceOfTreasury",
                new Object[] {
                    NFT_ADDRESS,
                    0L,
                    new byte[][] {ByteString.copyFromUtf8("firstMeta").toByteArray()},
                    OWNER_ADDRESS
                },
                null),
        BURN_FUNGIBLE_TOKEN(
                "burnTokenGetTotalSupplyAndBalanceOfTreasury",
                new Object[] {NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS, 12L, new long[0], TREASURY_ADDRESS},
                null),
        BURN_NFT(
                "burnTokenGetTotalSupplyAndBalanceOfTreasury",
                new Object[] {NFT_ADDRESS, 0L, new long[] {1L}, OWNER_ADDRESS},
                null),
        WIPE_FUNGIBLE_TOKEN(
                "wipeTokenGetTotalSupplyAndBalanceOfTreasury",
                new Object[] {NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS, 10L, new long[0], SENDER_ALIAS},
                null),
        WIPE_NFT(
                "wipeTokenGetTotalSupplyAndBalanceOfTreasury",
                new Object[] {NFT_ADDRESS_WITH_DIFFERENT_OWNER_AND_TREASURY, 0L, new long[] {1L}, SENDER_ALIAS},
                null),
        PAUSE_UNPAUSE_FUNGIBLE_TOKEN(
                "pauseTokenGetPauseStatusUnpauseGetPauseStatus", new Object[] {FUNGIBLE_TOKEN_ADDRESS}, null),
        FREEZE_UNFREEZE_FUNGIBLE_TOKEN(
                "freezeTokenGetPauseStatusUnpauseGetPauseStatus",
                new Object[] {NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS, SPENDER_ALIAS},
                null),
        PAUSE_UNPAUSE_NFT("pauseTokenGetPauseStatusUnpauseGetPauseStatus", new Object[] {NFT_ADDRESS}, null),
        FREEZE_UNFREEZE_NFT(
                "freezeTokenGetPauseStatusUnpauseGetPauseStatus", new Object[] {NFT_ADDRESS, SPENDER_ALIAS}, null),
        ASSOCIATE_TRANSFER_NFT(
                "associateTokenTransfer",
                new Object[] {
                    NFT_TRANSFER_ADDRESS_WITHOUT_KYC_KEY,
                    DYNAMIC_ETH_CALLS_CONTRACT_ALIAS,
                    NOT_ASSOCIATED_SPENDER_ALIAS,
                    BigInteger.ZERO,
                    BigInteger.ONE
                },
                null),
        ASSOCIATE_TRANSFER_FUNGIBLE_TOKEN(
                "associateTokenTransfer",
                new Object[] {
                    TREASURY_TOKEN_ADDRESS,
                    DYNAMIC_ETH_CALLS_CONTRACT_ALIAS,
                    NOT_ASSOCIATED_SPENDER_ALIAS,
                    BigInteger.ONE,
                    BigInteger.ZERO
                },
                null),
        ASSOCIATE_DISSOCIATE_TRANSFER_FUNGIBLE_TOKEN_FAIL(
                "associateTokenDissociateFailTransfer",
                new Object[] {
                    TREASURY_TOKEN_ADDRESS,
                    NOT_ASSOCIATED_SPENDER_ALIAS,
                    DYNAMIC_ETH_CALLS_CONTRACT_ALIAS,
                    BigInteger.ONE,
                    BigInteger.ZERO
                },
                "IERC20: failed to transfer"),
        ASSOCIATE_DISSOCIATE_TRANSFER_NFT_FAIL(
                "associateTokenDissociateFailTransfer",
                new Object[] {NFT_TRANSFER_ADDRESS, SENDER_ALIAS, RECEIVER_ADDRESS, BigInteger.ZERO, BigInteger.ONE},
                "IERC721: failed to transfer"),
        ASSOCIATE_TRANSFER_NFT_EXCEPTION(
                "associateTokenTransfer",
                new Object[] {
                    toAddress(EntityId.of(0, 0, 1)), // Not persisted address
                    DYNAMIC_ETH_CALLS_CONTRACT_ALIAS,
                    NOT_ASSOCIATED_SPENDER_ALIAS,
                    BigInteger.ZERO,
                    BigInteger.ONE
                },
                "Failed to associate tokens"),
        APPROVE_FUNGIBLE_TOKEN_GET_ALLOWANCE(
                "approveTokenGetAllowance",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS, OWNER_ADDRESS, BigInteger.ONE, BigInteger.ZERO},
                null),
        APPROVE_NFT_GET_ALLOWANCE(
                "approveTokenGetAllowance",
                new Object[] {NFT_ADDRESS, SPENDER_ALIAS, BigInteger.ZERO, BigInteger.ONE},
                null),
        APPROVE_FUNGIBLE_TOKEN_TRANSFER_FROM_GET_ALLOWANCE(
                "approveTokenTransferFromGetAllowanceGetBalance",
                new Object[] {TREASURY_TOKEN_ADDRESS, SPENDER_ALIAS, BigInteger.ONE, BigInteger.ZERO},
                null),
        APPROVE_FUNGIBLE_TOKEN_TRANSFER_FROM_GET_ALLOWANCE_2(
                "approveTokenTransferFromGetAllowanceGetBalance",
                new Object[] {TREASURY_TOKEN_ADDRESS, SENDER_ALIAS, BigInteger.ONE, BigInteger.ZERO},
                null),
        APPROVE_NFT_TOKEN_TRANSFER_FROM_GET_ALLOWANCE(
                "approveTokenTransferFromGetAllowanceGetBalance",
                new Object[] {NFT_TRANSFER_ADDRESS_WITHOUT_KYC_KEY, SPENDER_ALIAS, BigInteger.ZERO, BigInteger.ONE},
                null),
        APPROVE_NFT_TOKEN_TRANSFER_FROM_GET_ALLOWANCE_2(
                "approveTokenTransferFromGetAllowanceGetBalance",
                new Object[] {NFT_TRANSFER_ADDRESS_WITHOUT_KYC_KEY, SENDER_ALIAS, BigInteger.ZERO, BigInteger.ONE},
                null),
        APPROVE_FUNGIBLE_TOKEN_TRANSFER_GET_ALLOWANCE(
                "approveTokenTransferGetAllowanceGetBalance",
                new Object[] {TREASURY_TOKEN_ADDRESS, SPENDER_ALIAS, BigInteger.ONE, BigInteger.ZERO},
                null),
        APPROVE_NFT_TRANSFER_GET_ALLOWANCE(
                "approveTokenTransferGetAllowanceGetBalance",
                new Object[] {NFT_TRANSFER_ADDRESS, SPENDER_ALIAS, BigInteger.ZERO, BigInteger.ONE},
                null),
        APPROVE_CRYPTO_TRANSFER_FUNGIBLE_GET_ALLOWANCE(
                "approveTokenCryptoTransferGetAllowanceGetBalance",
                new Object[] {
                    new Object[] {},
                    new Object[] {TREASURY_TOKEN_ADDRESS, DYNAMIC_ETH_CALLS_CONTRACT_ALIAS, SPENDER_ALIAS, 1L, false}
                },
                null),
        APPROVE_CRYPTO_TRANSFER_NFT_GET_ALLOWANCE(
                "approveTokenCryptoTransferGetAllowanceGetBalance",
                new Object[] {
                    new Object[] {},
                    new Object[] {NFT_TRANSFER_ADDRESS, DYNAMIC_ETH_CALLS_CONTRACT_ALIAS, SPENDER_ALIAS, 1L, true}
                },
                null),
        APPROVE_FOR_ALL_TRANSFER_FROM_NFT_GET_ALLOWANCE(
                "approveForAllTokenTransferFromGetAllowance",
                new Object[] {NFT_TRANSFER_ADDRESS, SPENDER_ALIAS, 1L},
                null),
        APPROVE_FOR_ALL_TRANSFER_NFT_GET_ALLOWANCE(
                "approveForAllTokenTransferGetAllowance", new Object[] {NFT_TRANSFER_ADDRESS, SPENDER_ALIAS, 1L}, null),
        APPROVE_FOR_ALL_CRYPTO_TRANSFER_NFT_GET_ALLOWANCE(
                "approveForAllCryptoTransferGetAllowance",
                new Object[] {
                    new Object[] {},
                    new Object[] {NFT_TRANSFER_ADDRESS, DYNAMIC_ETH_CALLS_CONTRACT_ALIAS, SPENDER_ALIAS, 1L, true}
                },
                null),
        TRANSFER_NFT_GET_ALLOWANCE_OWNER_OF(
                "transferFromNFTGetAllowance", new Object[] {NFT_TRANSFER_ADDRESS, 1L}, null),
        TRANSFER_FUNGIBLE_TOKEN_GET_BALANCE(
                "transferFromGetAllowanceGetBalance",
                new Object[] {TREASURY_TOKEN_ADDRESS, SPENDER_ALIAS, BigInteger.ONE, BigInteger.ZERO},
                null),
        TRANSFER_NFT_GET_OWNER(
                "transferFromGetAllowanceGetBalance",
                new Object[] {NFT_TRANSFER_ADDRESS, SPENDER_ALIAS, BigInteger.ZERO, BigInteger.ONE},
                null),
        CRYPTO_TRANSFER_FUNFIBLE_TOKEN_GET_OWNER(
                "cryptoTransferFromGetAllowanceGetBalance",
                new Object[] {
                    new Object[] {},
                    new Object[] {TREASURY_TOKEN_ADDRESS, DYNAMIC_ETH_CALLS_CONTRACT_ALIAS, SPENDER_ALIAS, 1L, false}
                },
                null),
        CRYPTO_TRANSFER_NFT_GET_OWNER(
                "cryptoTransferFromGetAllowanceGetBalance",
                new Object[] {
                    new Object[] {},
                    new Object[] {NFT_TRANSFER_ADDRESS, DYNAMIC_ETH_CALLS_CONTRACT_ALIAS, SPENDER_ALIAS, 1L, true}
                },
                null),
        GRANT_KYC_REVOKE_KYC_FUNGIBLE("grantKycRevokeKyc", new Object[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ALIAS}, null),
        GRANT_KYC_REVOKE_KYC_NFT("grantKycRevokeKyc", new Object[] {NFT_ADDRESS, SENDER_ALIAS}, null),
        ADDRESS_THIS("getAddressThis", null, null);
        private final String name;
        private final Object[] functionParameters;
        private final String expectedErrorMessage;
    }
}
