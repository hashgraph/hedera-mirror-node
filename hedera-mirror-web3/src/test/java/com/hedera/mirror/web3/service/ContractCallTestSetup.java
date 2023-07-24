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
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAssociateToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.common.domain.token.TokenPauseStatusEnum;
import com.hedera.mirror.common.domain.token.TokenSupplyTypeEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.common.domain.transaction.CustomFee;
import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessorFacadeImpl;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.service.model.CallServiceParameters.CallType;
import com.hedera.mirror.web3.utils.FunctionEncodeDecoder;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hedera.services.store.contracts.precompile.TokenCreateWrapper;
import com.hedera.services.store.contracts.precompile.TokenCreateWrapper.FixedFeeWrapper;
import com.hedera.services.store.contracts.precompile.TokenCreateWrapper.FractionalFeeWrapper;
import com.hedera.services.store.contracts.precompile.TokenCreateWrapper.RoyaltyFeeWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenExpiryWrapper;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.CustomFee.FeeCase;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.function.ToLongFunction;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

public class ContractCallTestSetup extends Web3IntegrationTest {

    protected static final long expiry = 1_234_567_890L;
    protected static final ExchangeRateSet exchangeRatesSet = ExchangeRateSet.newBuilder()
            .setCurrentRate(ExchangeRate.newBuilder()
                    .setCentEquiv(1)
                    .setHbarEquiv(12)
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(expiry))
                    .build())
            .setNextRate(ExchangeRate.newBuilder()
                    .setCentEquiv(2)
                    .setHbarEquiv(31)
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(2_234_567_890L))
                    .build())
            .build();
    protected static final EntityId FEE_SCHEDULE_ENTITY_ID = new EntityId(0L, 0L, 111L, EntityType.FILE);
    protected static final EntityId EXCHANGE_RATE_ENTITY_ID = new EntityId(0L, 0L, 112L, EntityType.FILE);
    protected static final Address CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1256, CONTRACT));
    protected static final Address SENDER_ADDRESS = toAddress(EntityId.of(0, 0, 742, ACCOUNT));
    protected static final Address OWNER_ADDRESS = toAddress(EntityId.of(0, 0, 750, ACCOUNT));
    protected static final Address SPENDER_ADDRESS = toAddress(EntityId.of(0, 0, 741, ACCOUNT));
    protected static final Address TREASURY_ADDRESS = toAddress(EntityId.of(0, 0, 743, ACCOUNT));
    protected static final Address FUNGIBLE_TOKEN_ADDRESS = toAddress(EntityId.of(0, 0, 1046, TOKEN));
    protected static final Address NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS = toAddress(EntityId.of(0, 0, 1048, TOKEN));
    protected static final Address FROZEN_FUNGIBLE_TOKEN_ADDRESS = toAddress(EntityId.of(0, 0, 1050, TOKEN));
    protected static final Address TREASURY_TOKEN_ADDRESS = toAddress(EntityId.of(0, 0, 1049, TOKEN));
    protected static final Address NFT_ADDRESS = toAddress(EntityId.of(0, 0, 1047, TOKEN));
    protected static final Address NFT_ADDRESS_WITH_DIFFERENT_OWNER_AND_TREASURY =
            toAddress(EntityId.of(0, 0, 1067, TOKEN));
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
    protected static final Address EVM_CODES_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1263, CONTRACT));
    protected static final Address RECEIVER_ADDRESS = toAddress(EntityId.of(0, 0, 1045, CONTRACT));
    protected static final Address STATE_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1261, CONTRACT));
    protected static final TokenCreateWrapper FUNGIBLE_TOKEN = getFungibleToken();
    protected static final TokenCreateWrapper NON_FUNGIBLE_TOKEN = getNonFungibleToken();
    protected static final FixedFeeWrapper FIXED_FEE_WRAPPER = getFixedFee();
    protected static final FractionalFeeWrapper FRACTIONAL_FEE_WRAPPER = getFractionalFee();
    protected static final RoyaltyFeeWrapper ROYALTY_FEE_WRAPPER = getRoyaltyFee();
    protected static final ToLongFunction<String> longValueOf =
            value -> Bytes.fromHexString(value).toLong();
    protected static CurrentAndNextFeeSchedule feeSchedules = CurrentAndNextFeeSchedule.newBuilder()
            .setCurrentFeeSchedule(FeeSchedule.newBuilder()
                    .setExpiryTime(TimestampSeconds.newBuilder().setSeconds(expiry))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(ContractCall)
                            .addFees(FeeData.newBuilder()
                                    .setServicedata(FeeComponents.newBuilder()
                                            .setGas(852000)
                                            .build())))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(TokenAccountWipe)
                            .addFees(FeeData.newBuilder()
                                    .setServicedata(FeeComponents.newBuilder()
                                            .setGas(852000)
                                            .build())))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(TokenMint)
                            .addFees(FeeData.newBuilder()
                                    .setServicedata(FeeComponents.newBuilder()
                                            .setGas(852000)
                                            .build())))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(TokenBurn)
                            .addFees(FeeData.newBuilder()
                                    .setServicedata(FeeComponents.newBuilder()
                                            .setGas(852000)
                                            .build())))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(TokenAssociateToAccount)
                            .addFees(FeeData.newBuilder()
                                    .setServicedata(FeeComponents.newBuilder()
                                            .setGas(852000)
                                            .build())
                                    .build()))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(TokenCreate)
                            .addFees(FeeData.newBuilder()
                                    .setServicedata(FeeComponents.newBuilder()
                                            .setGas(852000)
                                            .build())
                                    .build())))
            .setNextFeeSchedule(FeeSchedule.newBuilder()
                    .setExpiryTime(TimestampSeconds.newBuilder().setSeconds(2_234_567_890L))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(TokenMint)
                            .addFees(FeeData.newBuilder()
                                    .setServicedata(FeeComponents.newBuilder()
                                            .setGas(852000)
                                            .build())))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(TokenAccountWipe)
                            .addFees(FeeData.newBuilder()
                                    .setServicedata(FeeComponents.newBuilder()
                                            .setGas(852000)
                                            .build())))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(TokenBurn)
                            .addFees(FeeData.newBuilder()
                                    .setServicedata(FeeComponents.newBuilder()
                                            .setGas(852000)
                                            .build())))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(TokenAssociateToAccount)
                            .addFees(FeeData.newBuilder()
                                    .setServicedata(FeeComponents.newBuilder()
                                            .setGas(852000)
                                            .build())))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(ContractCall)
                            .addFees(FeeData.newBuilder()
                                    .setServicedata(FeeComponents.newBuilder()
                                            .setGas(852000)
                                            .build())))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(TokenCreate)
                            .addFees(FeeData.newBuilder()
                                    .setServicedata(FeeComponents.newBuilder()
                                            .setGas(852000)
                                            .build())
                                    .build())))
            .build();

    @Autowired
    protected MirrorEvmTxProcessorFacadeImpl processor;

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

    @Value("classpath:contracts/EvmCodes/EvmCodes.bin")
    protected Path EVM_CODES_BYTES_PATH;

    @Value("classpath:contracts/EvmCodes/EvmCodes.json")
    protected Path EVM_CODES_ABI_PATH;

    protected CallServiceParameters serviceParametersForExecution(
            final Bytes callData, final Address contractAddress, final CallType callType, final long value) {
        final var sender = new HederaEvmAccount(SENDER_ADDRESS);
        persistEntities();

        return CallServiceParameters.builder()
                .sender(sender)
                .value(value)
                .receiver(contractAddress)
                .callData(callData)
                .gas(15_000_000L)
                .isStatic(false)
                .callType(callType)
                .isEstimate(ETH_ESTIMATE_GAS == callType)
                .build();
    }

    protected long gasUsedAfterExecution(CallServiceParameters serviceParameters) {
        return processor
                .execute(
                        serviceParameters.getSender(),
                        serviceParameters.getReceiver(),
                        serviceParameters.getGas(),
                        serviceParameters.getValue(),
                        serviceParameters.getCallData(),
                        Instant.now(),
                        serviceParameters.isStatic())
                .getGasUsed();
    }

    protected void receiverPersist() {
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

    protected void persistEntities() {
        evmCodesContractPersist();
        ethCallContractPersist();
        reverterContractPersist();
        stateContractPersist();
        precompileContractPersist();
        final var modificationContarct = modificationContractPersist();
        final var ercContract = ercContractPersist();
        fileDataPersist();

        final var senderEntityId = senderEntityPersist();
        final var ownerEntityId = ownerEntityPersist();
        final var spenderEntityId = spenderEntityPersist();
        final var treasuryEntityId = treasureEntityPersist();
        final var tokenEntityId =
                fungibleTokenPersist(ownerEntityId, KEY_PROTO, FUNGIBLE_TOKEN_ADDRESS, 9999999999999L);
        final var notFrozenFungibleTokenEntityId =
                fungibleTokenPersist(spenderEntityId, KEY_PROTO, NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS, 0L);
        final var frozenFungibleTokenEntityId =
                fungibleTokenPersist(spenderEntityId, KEY_PROTO, FROZEN_FUNGIBLE_TOKEN_ADDRESS, 9999999999999L);
        final var tokenTreasuryEntityId = fungibleTokenPersist(treasuryEntityId, KEY_PROTO, TREASURY_TOKEN_ADDRESS, 0L);
        final var nftEntityId = nftPersist(NFT_ADDRESS, ownerEntityId, spenderEntityId, ownerEntityId, KEY_PROTO);
        final var nftEntityId2 = nftPersist(
                NFT_ADDRESS_WITH_DIFFERENT_OWNER_AND_TREASURY,
                senderEntityId,
                spenderEntityId,
                ownerEntityId,
                KEY_PROTO);
        final var ethAccount = ethAccountPersist();
        tokenAccountPersist(senderEntityId, tokenEntityId, TokenFreezeStatusEnum.FROZEN);
        tokenAccountPersist(ethAccount, tokenEntityId, TokenFreezeStatusEnum.FROZEN);
        tokenAccountPersist(spenderEntityId, notFrozenFungibleTokenEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(ethAccount, notFrozenFungibleTokenEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(spenderEntityId, frozenFungibleTokenEntityId, TokenFreezeStatusEnum.FROZEN);
        tokenAccountPersist(ethAccount, frozenFungibleTokenEntityId, TokenFreezeStatusEnum.FROZEN);
        tokenAccountPersist(spenderEntityId, tokenTreasuryEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(modificationContarct, tokenEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(ercContract, tokenEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(ethAccount, tokenTreasuryEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(ownerEntityId, nftEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(senderEntityId, nftEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(ownerEntityId, nftEntityId2, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(senderEntityId, nftEntityId2, TokenFreezeStatusEnum.UNFROZEN);

        nftCustomFeePersist(senderEntityId, nftEntityId);
        allowancesPersist(senderEntityId, spenderEntityId, tokenEntityId, nftEntityId);
        allowancesPersist(ownerEntityId, modificationContarct, tokenEntityId, nftEntityId);
        allowancesPersist(ownerEntityId, ercContract, tokenEntityId, nftEntityId);
        exchangeRatesPersist();
        feeSchedulesPersist();
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

    private void fileDataPersist() {
        final long nanos = 1_234_567_890L;
        final ExchangeRateSet exchangeRatesSet = ExchangeRateSet.newBuilder()
                .setCurrentRate(ExchangeRate.newBuilder()
                        .setCentEquiv(1)
                        .setHbarEquiv(12)
                        .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(nanos))
                        .build())
                .setNextRate(ExchangeRate.newBuilder()
                        .setCentEquiv(2)
                        .setHbarEquiv(31)
                        .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(2_234_567_890L))
                        .build())
                .build();
        var timeStamp = System.currentTimeMillis();
        var entityId = new EntityId(0L, 0L, 112L, EntityType.FILE);
        domainBuilder
                .fileData()
                .customize(f -> f.fileData(exchangeRatesSet.toByteArray())
                        .entityId(entityId)
                        .consensusTimestamp(timeStamp))
                .persist();
    }

    private EntityId fungibleTokenPersist(
            final EntityId treasuryId, final byte[] key, final Address tokenAddress, final long tokenExpiration) {
        final var tokenEntityId = fromEvmAddress(tokenAddress.toArrayUnsafe());
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
                        .expirationTimestamp(tokenExpiration)
                        .memo("TestMemo"))
                .persist();

        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntityId.getId())
                        .treasuryAccountId(EntityId.of(0, 0, treasuryId.getId(), ACCOUNT))
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .kycKey(key)
                        .freezeDefault(true)
                        .feeScheduleKey(key)
                        .supplyType(TokenSupplyTypeEnum.INFINITE)
                        .maxSupply(2525L)
                        .name("Hbars")
                        .totalSupply(12345L)
                        .decimals(12)
                        .wipeKey(key)
                        .freezeKey(key)
                        .pauseStatus(TokenPauseStatusEnum.PAUSED)
                        .pauseKey(key)
                        .supplyKey(key)
                        .symbol("HBAR"))
                .persist();

        return tokenEntityId;
    }

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
    private EntityId ownerEntityPersist() {
        final var ownerEntityId = fromEvmAddress(OWNER_ADDRESS.toArrayUnsafe());

        domainBuilder
                .entity()
                .customize(e -> e.id(ownerEntityId.getId())
                        .num(ownerEntityId.getEntityNum())
                        .evmAddress(null)
                        .alias(toEvmAddress(ownerEntityId))
                        .balance(20000L))
                .persist();
        return ownerEntityId;
    }

    @Nullable
    private EntityId treasureEntityPersist() {
        final var treasuryEntityId = fromEvmAddress(TREASURY_ADDRESS.toArrayUnsafe());

        domainBuilder
                .entity()
                .customize(e -> e.id(treasuryEntityId.getId())
                        .num(treasuryEntityId.getEntityNum())
                        .evmAddress(null)
                        .alias(toEvmAddress(treasuryEntityId)))
                .persist();
        return treasuryEntityId;
    }

    @Nullable
    private EntityId nftPersist(
            final Address nftAddress,
            final EntityId ownerEntityId,
            final EntityId spenderEntityId,
            final EntityId treasuryId,
            final byte[] key) {
        final var nftEntityId = fromEvmAddress(nftAddress.toArrayUnsafe());
        final var nftEvmAddress = toEvmAddress(nftEntityId);
        final var ownerEntity = EntityId.of(0, 0, ownerEntityId.getId(), ACCOUNT);

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
                .customize(t -> t.tokenId(nftEntityId.getId())
                        .treasuryAccountId(treasuryId)
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .kycKey(key)
                        .freezeDefault(true)
                        .feeScheduleKey(key)
                        .maxSupply(2000000000L)
                        .name("Hbars")
                        .supplyType(TokenSupplyTypeEnum.FINITE)
                        .freezeKey(key)
                        .pauseKey(key)
                        .pauseStatus(TokenPauseStatusEnum.PAUSED)
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
                        .metadata(new byte[] {1, 2, 3})
                        .accountId(ownerEntity)
                        .timestampRange(Range.atLeast(1475067194949034022L))
                        .tokenId(nftEntityId.getId()))
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

    private void evmCodesContractPersist() {
        final var evmCodesContractBytes = functionEncodeDecoder.getContractBytes(EVM_CODES_BYTES_PATH);
        final var evmCodesContractEntityId = fromEvmAddress(EVM_CODES_CONTRACT_ADDRESS.toArrayUnsafe());
        final var evmCodesContractEvmAddress = toEvmAddress(evmCodesContractEntityId);

        domainBuilder
                .entity()
                .customize(e -> e.id(evmCodesContractEntityId.getId())
                        .num(evmCodesContractEntityId.getEntityNum())
                        .evmAddress(evmCodesContractEvmAddress)
                        .type(CONTRACT)
                        .balance(1500L))
                .persist();

        domainBuilder
                .contract()
                .customize(c -> c.id(evmCodesContractEntityId.getId()).runtimeBytecode(evmCodesContractBytes))
                .persist();

        domainBuilder
                .recordFile()
                .customize(f -> f.bytes(evmCodesContractBytes))
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

    private EntityId modificationContractPersist() {
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
        return modificationContractEntityId;
    }

    private EntityId ercContractPersist() {
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
        return ercContractEntityId;
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

    protected void exchangeRatesPersist() {
        domainBuilder
                .fileData()
                .customize(f -> f.fileData(exchangeRatesSet.toByteArray())
                        .entityId(EXCHANGE_RATE_ENTITY_ID)
                        .consensusTimestamp(expiry))
                .persist();
    }

    protected void feeSchedulesPersist() {
        domainBuilder
                .fileData()
                .customize(f -> f.fileData(feeSchedules.toByteArray())
                        .entityId(FEE_SCHEDULE_ENTITY_ID)
                        .consensusTimestamp(expiry + 1))
                .persist();
    }

    private static TokenCreateWrapper getFungibleToken() {
        return new TokenCreateWrapper(
                true,
                "Test",
                "TST",
                EntityIdUtils.accountIdFromEvmAddress(SENDER_ADDRESS),
                "test",
                true,
                BigInteger.valueOf(10L),
                BigInteger.valueOf(10L),
                10_000_000L,
                false,
                List.of(),
                new TokenExpiryWrapper(
                        9_000_000_000L, EntityIdUtils.accountIdFromEvmAddress(SENDER_ADDRESS), 100_000L));
    }

    private static TokenCreateWrapper getNonFungibleToken() {
        return new TokenCreateWrapper(
                false,
                "TestNFT",
                "TFT",
                EntityIdUtils.accountIdFromEvmAddress(SENDER_ADDRESS),
                "test",
                true,
                BigInteger.valueOf(0L),
                BigInteger.valueOf(0L),
                0L,
                false,
                List.of(),
                new TokenExpiryWrapper(
                        9_000_000_000L, EntityIdUtils.accountIdFromEvmAddress(SENDER_ADDRESS), 100_000L));
    }

    private static FixedFeeWrapper getFixedFee() {
        return new FixedFeeWrapper(10L, EntityIdUtils.tokenIdFromEvmAddress(SENDER_ADDRESS), false, false, null);
    }

    private static FractionalFeeWrapper getFractionalFee() {
        return new FractionalFeeWrapper(10L, 10L, 1L, 100L, false, null);
    }

    private static RoyaltyFeeWrapper getRoyaltyFee() {
        return new RoyaltyFeeWrapper(0L, 0L, FIXED_FEE_WRAPPER, null);
    }
}
