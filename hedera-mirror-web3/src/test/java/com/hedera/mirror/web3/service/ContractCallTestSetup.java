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

import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;
import static com.hedera.mirror.common.util.DomainUtils.fromEvmAddress;
import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;
import static com.hedera.mirror.web3.common.ContractCallContext.init;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.services.utils.EntityIdUtils.contractIdFromEvmAddress;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.EthereumTransaction;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAssociateToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
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
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessor;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.service.model.CallServiceParameters.CallType;
import com.hedera.mirror.web3.utils.FunctionEncodeDecoder;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hedera.services.hapi.utils.ByteStringUtils;
import com.hedera.services.store.contracts.precompile.TokenCreateWrapper;
import com.hedera.services.store.contracts.precompile.TokenCreateWrapper.FixedFeeWrapper;
import com.hedera.services.store.contracts.precompile.TokenCreateWrapper.FractionalFeeWrapper;
import com.hedera.services.store.contracts.precompile.TokenCreateWrapper.RoyaltyFeeWrapper;
import com.hedera.services.store.contracts.precompile.codec.KeyValueWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenExpiryWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenKeyWrapper;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.CustomFee.FeeCase;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.function.ToLongFunction;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.datatypes.Address;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

public class ContractCallTestSetup extends Web3IntegrationTest {

    protected static final long expiry = 1_234_567_890L;
    protected static final BigInteger SUCCESS_RESULT = BigInteger.valueOf(ResponseCodeEnum.SUCCESS_VALUE);

    // Exchange rates from local node.
    protected static final ExchangeRateSet exchangeRatesSet = ExchangeRateSet.newBuilder()
            .setCurrentRate(ExchangeRate.newBuilder()
                    .setCentEquiv(12)
                    .setHbarEquiv(1)
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(4_102_444_800L))
                    .build())
            .setNextRate(ExchangeRate.newBuilder()
                    .setCentEquiv(15)
                    .setHbarEquiv(1)
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(4_102_444_800L))
                    .build())
            .build();

    // System addresses
    protected static final EntityId FEE_SCHEDULE_ENTITY_ID = EntityId.of(0L, 0L, 111L);
    protected static final EntityId EXCHANGE_RATE_ENTITY_ID = EntityId.of(0L, 0L, 112L);

    // Contract addresses
    protected static final Address ETH_ADDRESS = Address.fromHexString("0x23f5e49569a835d7bf9aefd30e4f60cdd570f225");
    protected static final Address DYNAMIC_ETH_CALLS_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1255));
    protected static final Address DYNAMIC_ETH_CALLS_CONTRACT_ALIAS =
            Address.fromHexString("0x742d35Cc6634C0532925a3b844Bc454e4438f44e");
    protected static final Address PRECOMPILE_TEST_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1256));
    protected static final Address MODIFICATION_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1257));
    protected static final Address ERC_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1258));
    protected static final Address REVERTER_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1259));
    protected static final Address ETH_CALL_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1260));
    protected static final Address STATE_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1261));
    protected static final Address NESTED_ETH_CALLS_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1262));
    protected static final Address EVM_CODES_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1263));
    protected static final Address EXCHANGE_RATE_PRECOMPILE_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1264));
    protected static final Address REDIRECT_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1265));
    protected static final Address PRNG_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1266));

    // Account addresses
    protected static final Address AUTO_RENEW_ACCOUNT_ADDRESS = toAddress(EntityId.of(0, 0, 740));
    protected static final Address SPENDER_ADDRESS = toAddress(EntityId.of(0, 0, 741));
    protected static final ByteString SPENDER_PUBLIC_KEY =
            ByteString.fromHex("3a2102ff806fecbd31b4c377293cba8d2b78725965a4990e0ff1b1b29a1d2c61402310");
    protected static final Address SPENDER_ALIAS = Address.wrap(
            Bytes.wrap(recoverAddressFromPubKey(SPENDER_PUBLIC_KEY.substring(2).toByteArray())));
    protected static final Address SENDER_ADDRESS = toAddress(EntityId.of(0, 0, 742));
    protected static final ByteString SENDER_PUBLIC_KEY =
            ByteString.copyFrom(Hex.decode("3a2103af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d"));
    protected static final Address SENDER_ALIAS = Address.wrap(
            Bytes.wrap(recoverAddressFromPubKey(SENDER_PUBLIC_KEY.substring(2).toByteArray())));
    protected static final Address TREASURY_ADDRESS = toAddress(EntityId.of(0, 0, 743));
    protected static final Address NOT_ASSOCIATED_SPENDER_ADDRESS = toAddress(EntityId.of(0, 0, 744));
    protected static final ByteString NOT_ASSOCIATED_SPENDER_PUBLIC_KEY =
            ByteString.fromHex("3a21033a514176466fa815ed481ffad09110a2d344f6c9b78c1d14afc351c3a51be33d");
    protected static final Address NOT_ASSOCIATED_SPENDER_ALIAS = Address.wrap(Bytes.wrap(recoverAddressFromPubKey(
            NOT_ASSOCIATED_SPENDER_PUBLIC_KEY.substring(2).toByteArray())));
    protected static final Address OWNER_ADDRESS = toAddress(EntityId.of(0, 0, 750));

    // Token addresses
    protected static final Address FUNGIBLE_TOKEN_ADDRESS_WITH_EXPIRY = toAddress(EntityId.of(0, 0, 1042));
    protected static final Address RECEIVER_ADDRESS = toAddress(EntityId.of(0, 0, 1045));
    protected static final Address FUNGIBLE_TOKEN_ADDRESS = toAddress(EntityId.of(0, 0, 1046));
    protected static final Address NFT_ADDRESS = toAddress(EntityId.of(0, 0, 1047));
    protected static final Address NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS = toAddress(EntityId.of(0, 0, 1048));
    protected static final Address TREASURY_TOKEN_ADDRESS = toAddress(EntityId.of(0, 0, 1049));

    protected static final Address FROZEN_FUNGIBLE_TOKEN_ADDRESS = toAddress(EntityId.of(0, 0, 1050));
    protected static final Address NFT_TRANSFER_ADDRESS = toAddress(EntityId.of(0, 0, 1051));
    protected static final Address UNPAUSED_FUNGIBLE_TOKEN_ADDRESS = toAddress(EntityId.of(0, 0, 1052));
    protected static final Address NFT_ADDRESS_GET_KEY_WITH_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1053));
    protected static final Address FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ED25519_KEY = toAddress(EntityId.of(0, 0, 1054));
    protected static final Address FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ECDSA_KEY = toAddress(EntityId.of(0, 0, 1055));
    protected static final Address FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_DELEGATABLE_CONTRACT_ID =
            toAddress(EntityId.of(0, 0, 1056));
    protected static final Address NFT_ADDRESS_GET_KEY_WITH_ED25519_KEY = toAddress(EntityId.of(0, 0, 1057));
    protected static final Address NFT_ADDRESS_GET_KEY_WITH_ECDSA_KEY = toAddress(EntityId.of(0, 0, 1058));
    protected static final Address NFT_ADDRESS_GET_KEY_WITH_DELEGATABLE_CONTRACT_ID =
            toAddress(EntityId.of(0, 0, 1059));
    protected static final Address FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_CONTRACT_ADDRESS =
            toAddress(EntityId.of(0, 0, 1060));
    protected static final Address FUNGIBLE_TOKEN_ADDRESS_NOT_ASSOCIATED = toAddress(EntityId.of(0, 0, 1061));
    protected static final Address NFT_ADDRESS_WITH_DIFFERENT_OWNER_AND_TREASURY = toAddress(EntityId.of(0, 0, 1067));
    protected static final Address NFT_TRANSFER_ADDRESS_WITHOUT_KYC_KEY = toAddress(EntityId.of(0, 0, 1071));

    protected static final byte[] KEY_PROTO = new byte[] {
        58, 33, -52, -44, -10, 81, 99, 100, 6, -8, -94, -87, -112, 42, 42, 96, 75, -31, -5, 72, 13, -70, 101, -111, -1,
        77, -103, 47, -118, 107, -58, -85, -63, 55, -57
    };
    protected static final byte[] ECDSA_KEY = Arrays.copyOfRange(KEY_PROTO, 2, KEY_PROTO.length);
    protected static Key keyWithECDSASecp256K1 =
            Key.newBuilder().setECDSASecp256K1(ByteString.copyFrom(ECDSA_KEY)).build();
    // bit field representing the key type. Keys of all types that have corresponding bits set to 1
    // will be created for the token.
    // 0th bit: adminKey
    // 1st bit: kycKey
    // 2nd bit: freezeKey
    // 3rd bit: wipeKey
    // 4th bit: supplyKey
    // 5th bit: feeScheduleKey
    // 6th bit: pauseKey
    // 7th bit: ignored
    protected static final int АLL_CASES_KEY_TYPE = 0b1111111;
    protected static final byte[] NEW_ECDSA_KEY = new byte[] {
        2, 64, 59, -126, 81, -22, 0, 35, 67, -70, 110, 96, 109, 2, -8, 111, -112, -100, -87, -85, 66, 36, 37, -97, 19,
        68, -87, -110, -13, -115, 74, 86, 90
    };
    protected static final byte[] ED25519_KEY = Arrays.copyOfRange(KEY_PROTO, 2, KEY_PROTO.length);
    protected static Key keyWithEd25519 =
            Key.newBuilder().setEd25519(ByteString.copyFrom(ED25519_KEY)).build();
    protected static final byte[] NEW_ED25519_KEY = new byte[] {
        -128, -61, -12, 63, 3, -45, 108, 34, 61, -2, -83, -48, -118, 20, 84, 85, 85, 67, -125, 46, 49, 26, 17, -116, 27,
        25, 38, -95, 50, 77, 40, -38
    };

    // Token Wrappers
    protected static final TokenCreateWrapper FUNGIBLE_TOKEN = getFungibleToken();
    protected static final TokenCreateWrapper FUNGIBLE_TOKEN2 = getFungibleToken2();
    protected static final TokenCreateWrapper FUNGIBLE_TOKEN_WITH_KEYS = getFungibleTokenWithKeys();
    protected static final TokenCreateWrapper FUNGIBLE_TOKEN_EXPIRY_IN_UINT32_RANGE =
            getFungibleTokenExpiryInUint32Range();
    protected static final TokenCreateWrapper FUNGIBLE_HBAR_TOKEN_AND_KEYS = getFungibleHbarsTokenWrapper();
    protected static final TokenCreateWrapper FUNGIBLE_TOKEN_INHERIT_KEYS = getFungibleTokenInheritKeys();
    protected static final TokenCreateWrapper NON_FUNGIBLE_TOKEN = getNonFungibleToken();
    protected static final TokenCreateWrapper NON_FUNGIBLE_TOKEN_WITH_KEYS = getNonFungibleTokenWithKeys();
    protected static final TokenCreateWrapper NON_FUNGIBLE_TOKEN_EXPIRY_IN_UINT32_RANGE =
            getNonFungibleTokenExpiryInUint32Range();
    protected static final TokenCreateWrapper NFT_HBAR_TOKEN_AND_KEYS = getNftHbarTokenAndKeysHbarsTokenWrapper();
    protected static final TokenCreateWrapper NON_FUNGIBLE_TOKEN_INHERIT_KEYS = getNonFungibleTokenInheritKeys();

    // Custom Fee wrappers
    protected static final FixedFeeWrapper FIXED_FEE_WRAPPER = getFixedFee();
    protected static final RoyaltyFeeWrapper ROYALTY_FEE_WRAPPER = getRoyaltyFee();
    protected static final FractionalFeeWrapper FRACTIONAL_FEE_WRAPPER = getFractionalFee();
    protected static final TokenExpiryWrapper TOKEN_EXPIRY_WRAPPER = getTokenExpiry();

    // Fee schedules
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
                            .setHederaFunctionality(CryptoTransfer)
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
                                    .setSubType(SubType.TOKEN_NON_FUNGIBLE_UNIQUE)
                                    .setServicedata(FeeComponents.newBuilder()
                                            .setMax(1000000000000000L)
                                            .setMin(0)
                                            .build())
                                    .setNodedata(FeeComponents.newBuilder()
                                            .setBpt(40000000000L)
                                            .setMax(1000000000000000L)
                                            .setMin(0)
                                            .build())
                                    .setNetworkdata(FeeComponents.newBuilder()
                                            .setMax(1000000000000000L)
                                            .setBpt(160000000000L)
                                            .setMin(0)
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
                                            .setConstant(7874923918408L)
                                            .setGas(2331415)
                                            .setBpt(349712319)
                                            .setVpt(874280797002L)
                                            .setBpr(349712319)
                                            .setSbpr(8742808)
                                            .setRbh(233142)
                                            .setSbh(17486)
                                            .setMin(0)
                                            .setMax(1000000000000000L)
                                            .build())
                                    .setNetworkdata(FeeComponents.newBuilder()
                                            .setConstant(7874923918408L)
                                            .setGas(2331415)
                                            .setBpt(349712319)
                                            .setVpt(874280797002L)
                                            .setRbh(233142)
                                            .setSbh(17486)
                                            .setBpr(349712319)
                                            .setSbpr(8742808)
                                            .setMin(0)
                                            .setMax(1000000000000000L)
                                            .build())
                                    .setNodedata(FeeComponents.newBuilder()
                                            .setConstant(393746195920L)
                                            .setGas(116571)
                                            .setRbh(11657)
                                            .setSbh(874)
                                            .setBpt(17485616)
                                            .setSbpr(437140)
                                            .setVpt(43714039850L)
                                            .setBpr(17485616)
                                            .setMin(0)
                                            .setMax(1000000000000000L)
                                            .build())
                                    .build())))
            .setNextFeeSchedule(FeeSchedule.newBuilder()
                    .setExpiryTime(TimestampSeconds.newBuilder().setSeconds(2_234_567_890L))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(TokenMint)
                            .addFees(FeeData.newBuilder()
                                    .setSubType(SubType.TOKEN_NON_FUNGIBLE_UNIQUE)
                                    .setServicedata(FeeComponents.newBuilder()
                                            .setMax(1000000000000000L)
                                            .setMin(0)
                                            .build())
                                    .setNodedata(FeeComponents.newBuilder()
                                            .setBpt(40000000000L)
                                            .setMax(1000000000000000L)
                                            .setMin(0)
                                            .build())
                                    .setNetworkdata(FeeComponents.newBuilder()
                                            .setMax(1000000000000000L)
                                            .setMin(0)
                                            .setBpt(160000000000L)
                                            .build())))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(CryptoTransfer)
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
                                            .setConstant(7874923918408L)
                                            .setGas(2331415)
                                            .setBpt(349712319)
                                            .setVpt(874280797002L)
                                            .setBpr(349712319)
                                            .setSbpr(8742808)
                                            .setRbh(233142)
                                            .setSbh(17486)
                                            .setMin(0)
                                            .setMax(1000000000000000L)
                                            .build())
                                    .setNetworkdata(FeeComponents.newBuilder()
                                            .setConstant(7874923918408L)
                                            .setGas(2331415)
                                            .setBpt(349712319)
                                            .setVpt(874280797002L)
                                            .setRbh(233142)
                                            .setSbh(17486)
                                            .setBpr(349712319)
                                            .setSbpr(8742808)
                                            .setMin(0)
                                            .setMax(1000000000000000L)
                                            .build())
                                    .setNodedata(FeeComponents.newBuilder()
                                            .setConstant(393746195920L)
                                            .setGas(116571)
                                            .setRbh(11657)
                                            .setSbh(874)
                                            .setBpt(17485616)
                                            .setSbpr(437140)
                                            .setVpt(43714039850L)
                                            .setBpr(17485616)
                                            .setMin(0)
                                            .setMax(1000000000000000L)
                                            .build())
                                    .build()))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(EthereumTransaction)
                            .addFees(FeeData.newBuilder()
                                    .setServicedata(FeeComponents.newBuilder()
                                            .setGas(852000)
                                            .build()))))
            .build();

    protected static Key keyWithContractId = Key.newBuilder()
            .setContractID(contractIdFromEvmAddress(PRECOMPILE_TEST_CONTRACT_ADDRESS.toArrayUnsafe()))
            .build();
    protected static Key keyWithDelegatableContractId = Key.newBuilder()
            .setDelegatableContractId(contractIdFromEvmAddress(PRECOMPILE_TEST_CONTRACT_ADDRESS.toArrayUnsafe()))
            .build();

    protected static RecordFile recordFileForBlockHash;

    @Autowired
    protected MirrorEvmTxProcessor processor;

    @Autowired
    protected FunctionEncodeDecoder functionEncodeDecoder;

    @Autowired
    protected ContractCallService contractCallService;

    @Autowired
    protected MirrorNodeEvmProperties properties;
    // The contract source `PrecompileTestContract.sol` is in test resources
    @Value("classpath:contracts/PrecompileTestContract/PrecompileTestContract.bin")
    protected Path CONTRACT_BYTES_PATH;

    @Value("classpath:contracts/DynamicEthCalls/DynamicEthCalls.bin")
    protected Path DYNAMIC_ETH_CALLS_BYTES_PATH;

    @Value("classpath:contracts/DynamicEthCalls/DynamicEthCalls.json")
    protected Path DYNAMIC_ETH_CALLS_ABI_PATH;

    @Value("classpath:contracts/PrecompileTestContract/PrecompileTestContract.json")
    protected Path PRECOMPILE_TEST_CONTRACT_ABI_PATH;

    @Value("classpath:contracts/RedirectTestContract/RedirectTestContract.json")
    protected Path REDIRECT_CONTRACT_ABI_PATH;

    @Value("classpath:contracts/RedirectTestContract/RedirectTestContract.bin")
    protected Path REDIRECT_CONTRACT_BYTES_PATH;

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

    // The contract source `ExchangeRatePrecompile.sol` is in test resources
    @Value("classpath:contracts/ExchangeRatePrecompile/ExchangeRatePrecompile.bin")
    protected Path EXCHANGE_RATE_PRECOMPILE_CONTRACT_BYTES_PATH;

    @Value("classpath:contracts/ExchangeRatePrecompile/ExchangeRatePrecompile.json")
    protected Path EXCHANGE_RATE_PRECOMPILE_ABI_PATH;

    // The contract source `PrngSystemContract.sol` is in test resources
    @Value("classpath:contracts/PrngSystemContract/PrngSystemContract.bin")
    protected Path PRNG_PRECOMPILE_CONTRACT_BYTES_PATH;

    @Value("classpath:contracts/PrngSystemContract/PrngSystemContract.json")
    protected Path PRNG_PRECOMPILE_ABI_PATH;

    // The contract sources `EthCall.sol` and `Reverter.sol` are in test/resources
    @Value("classpath:contracts/EthCall/EthCall.bin")
    protected Path ETH_CALL_CONTRACT_BYTES_PATH;

    @Value("classpath:contracts/EthCall/EthCallInit.bin")
    protected Path ETH_CALL_INIT_CONTRACT_BYTES_PATH;

    @Value("classpath:contracts/Reverter/Reverter.bin")
    protected Path REVERTER_CONTRACT_BYTES_PATH;

    @Value("classpath:contracts/EthCall/State.bin")
    protected Path STATE_CONTRACT_BYTES_PATH;

    @Value("classpath:contracts/EvmCodes/EvmCodes.bin")
    protected Path EVM_CODES_BYTES_PATH;

    @Value("classpath:contracts/EvmCodes/EvmCodes.json")
    protected Path EVM_CODES_ABI_PATH;

    @Value("classpath:contracts/NestedCallsTestContract/NestedCallsTestContract.bin")
    protected Path NESTED_CALLS_CONTRACT_BYTES_PATH;

    @Value("classpath:contracts/NestedCallsTestContract/NestedCallsTestContract.json")
    protected Path NESTED_CALLS_ABI_PATH;

    /**
     * Checks if the *actual* gas usage is within 5-20% greater than the *expected* gas used from the initial call.
     *
     * @param actualGas   The actual gas used.
     * @param expectedGas The expected gas used from the initial call.
     * @return {@code true} if the actual gas usage is within the expected range, otherwise {@code false}.
     */
    protected static boolean isWithinExpectedGasRange(final long actualGas, final long expectedGas) {
        return actualGas >= (expectedGas * 1.05) && actualGas <= (expectedGas * 1.20);
    }

    private static TokenCreateWrapper getFungibleTokenWithKeys() {
        return new TokenCreateWrapper(
                true,
                "Test",
                "TST",
                EntityIdUtils.accountIdFromEvmAddress(OWNER_ADDRESS),
                "test",
                true,
                BigInteger.valueOf(10L),
                BigInteger.valueOf(10L),
                10_000_000L,
                true,
                List.of(new TokenKeyWrapper(
                        0b1111111,
                        new KeyValueWrapper(
                                false,
                                contractIdFromEvmAddress(NESTED_ETH_CALLS_CONTRACT_ADDRESS.toArrayUnsafe()),
                                new byte[] {},
                                new byte[] {},
                                null))),
                new TokenExpiryWrapper(9_000_000_000L, EntityIdUtils.accountIdFromEvmAddress(OWNER_ADDRESS), 10_000L));
    }

    private static TokenCreateWrapper getFungibleTokenExpiryInUint32Range() {
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
                new TokenExpiryWrapper(4_000_000_000L, EntityIdUtils.accountIdFromEvmAddress(SENDER_ADDRESS), 10_000L));
    }

    private static TokenCreateWrapper getNonFungibleTokenWithKeys() {
        return new TokenCreateWrapper(
                false,
                "TestNFT",
                "TFT",
                EntityIdUtils.accountIdFromEvmAddress(OWNER_ADDRESS),
                "test",
                true,
                BigInteger.valueOf(0L),
                BigInteger.valueOf(0L),
                0L,
                true,
                List.of(new TokenKeyWrapper(
                        0b1111111,
                        new KeyValueWrapper(
                                false,
                                contractIdFromEvmAddress(NESTED_ETH_CALLS_CONTRACT_ADDRESS.toArrayUnsafe()),
                                new byte[] {},
                                new byte[] {},
                                null))),
                new TokenExpiryWrapper(9_000_000_000L, EntityIdUtils.accountIdFromEvmAddress(OWNER_ADDRESS), 10_000L));
    }

    private static TokenCreateWrapper getNonFungibleTokenExpiryInUint32Range() {
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
                new TokenExpiryWrapper(4_000_000_000L, EntityIdUtils.accountIdFromEvmAddress(SENDER_ADDRESS), 10_000L));
    }

    private static TokenCreateWrapper getFungibleToken() {
        return new TokenCreateWrapper(
                true,
                "Test",
                "TST",
                EntityIdUtils.accountIdFromEvmAddress(OWNER_ADDRESS),
                "test",
                true,
                BigInteger.valueOf(10L),
                BigInteger.valueOf(10L),
                10_000_000L,
                false,
                List.of(),
                new TokenExpiryWrapper(9_000_000_000L, EntityIdUtils.accountIdFromEvmAddress(OWNER_ADDRESS), 10_000L));
    }

    private static TokenCreateWrapper getFungibleToken2() {
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
                new TokenExpiryWrapper(9_000_000_000L, EntityIdUtils.accountIdFromEvmAddress(SENDER_ADDRESS), 10_000L));
    }

    private static TokenCreateWrapper getNonFungibleToken() {
        return new TokenCreateWrapper(
                false,
                "TestNFT",
                "TFT",
                EntityIdUtils.accountIdFromEvmAddress(OWNER_ADDRESS),
                "test",
                true,
                BigInteger.valueOf(0L),
                BigInteger.valueOf(0L),
                0L,
                false,
                List.of(),
                new TokenExpiryWrapper(9_000_000_000L, EntityIdUtils.accountIdFromEvmAddress(OWNER_ADDRESS), 10_000L));
    }

    private static TokenCreateWrapper getFungibleHbarsTokenWrapper() {
        final var keyValue =
                new KeyValueWrapper(false, null, new byte[0], Arrays.copyOfRange(KEY_PROTO, 2, KEY_PROTO.length), null);
        return new TokenCreateWrapper(
                true,
                "Hbars",
                "HBAR",
                EntityIdUtils.accountIdFromEvmAddress(OWNER_ADDRESS),
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
                        9_999L, EntityIdUtils.accountIdFromEvmAddress(AUTO_RENEW_ACCOUNT_ADDRESS), 1800L));
    }

    private static TokenCreateWrapper getFungibleTokenInheritKeys() {
        return new TokenCreateWrapper(
                true,
                "Test",
                "TST",
                EntityIdUtils.accountIdFromEvmAddress(OWNER_ADDRESS),
                "test",
                true,
                BigInteger.valueOf(10L),
                BigInteger.valueOf(10L),
                10_000_000L,
                true,
                List.of(new TokenKeyWrapper(
                        0b1111111, new KeyValueWrapper(true, null, new byte[] {}, new byte[] {}, null))),
                new TokenExpiryWrapper(9_000_000_000L, EntityIdUtils.accountIdFromEvmAddress(OWNER_ADDRESS), 10_000L));
    }

    private static TokenCreateWrapper getNftHbarTokenAndKeysHbarsTokenWrapper() {
        final var keyValue =
                new KeyValueWrapper(false, null, new byte[0], Arrays.copyOfRange(KEY_PROTO, 2, KEY_PROTO.length), null);
        return new TokenCreateWrapper(
                false,
                "Hbars",
                "HBAR",
                EntityIdUtils.accountIdFromEvmAddress(OWNER_ADDRESS),
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
                        9999L, EntityIdUtils.accountIdFromEvmAddress(AUTO_RENEW_ACCOUNT_ADDRESS), 1800L));
    }

    private static TokenCreateWrapper getNonFungibleTokenInheritKeys() {
        return new TokenCreateWrapper(
                false,
                "TestNFT",
                "TFT",
                EntityIdUtils.accountIdFromEvmAddress(OWNER_ADDRESS),
                "test",
                true,
                BigInteger.valueOf(0L),
                BigInteger.valueOf(0L),
                0L,
                true,
                List.of(new TokenKeyWrapper(
                        0b1111111, new KeyValueWrapper(true, null, new byte[] {}, new byte[] {}, null))),
                new TokenExpiryWrapper(9_000_000_000L, EntityIdUtils.accountIdFromEvmAddress(OWNER_ADDRESS), 10_000L));
    }

    // Get Custom Fee Wrappers
    private static FixedFeeWrapper getFixedFee() {
        return new FixedFeeWrapper(10L, EntityIdUtils.tokenIdFromEvmAddress(SENDER_ADDRESS), false, false, null);
    }

    private static FractionalFeeWrapper getFractionalFee() {
        return new FractionalFeeWrapper(10L, 10L, 1L, 100L, false, null);
    }

    private static RoyaltyFeeWrapper getRoyaltyFee() {
        return new RoyaltyFeeWrapper(0L, 0L, FIXED_FEE_WRAPPER, null);
    }

    private static TokenExpiryWrapper getTokenExpiry() {
        return new TokenExpiryWrapper(9_000_000_000L, EntityIdUtils.accountIdFromEvmAddress(SENDER_ADDRESS), 10_000L);
    }

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

    protected CallServiceParameters serviceParametersForTopLevelContractCreate(
            final Path contractInitCodePath, final CallType callType, final Address senderAddress) {
        final var sender = new HederaEvmAccount(senderAddress);
        persistEntities();

        final var callData = Bytes.wrap(functionEncodeDecoder.getContractBytes(contractInitCodePath));
        return CallServiceParameters.builder()
                .sender(sender)
                .callData(callData)
                .receiver(Address.ZERO)
                .gas(15_000_000L)
                .isStatic(false)
                .callType(callType)
                .isEstimate(ETH_ESTIMATE_GAS == callType)
                .build();
    }

    @SuppressWarnings("try")
    protected long gasUsedAfterExecution(final CallServiceParameters serviceParameters) {
        long result;
        try (ContractCallContext ctx = init(store.getStackedStateFrames())) {
            result = processor
                    .execute(
                            serviceParameters.getSender(),
                            serviceParameters.getReceiver(),
                            serviceParameters.getGas(),
                            serviceParameters.getValue(),
                            serviceParameters.getCallData(),
                            Instant.now(),
                            serviceParameters.isStatic(),
                            true)
                    .getGasUsed();

            assertThat(store.getStackedStateFrames().height()).isEqualTo(1);
        }

        return result;
    }

    protected void persistEntities() {
        evmCodesContractPersist();
        ethCallContractPersist();
        reverterContractPersist();
        stateContractPersist();
        precompileContractPersist();
        systemExchangeRateContractPersist();
        pseudoRandomNumberGeneratorContractPersist();
        final var modificationContract = modificationContractPersist();
        final var ercContract = ercContractPersist();
        final var nestedContractId = dynamicEthCallContractPresist();
        nestedEthCallsContractPersist();
        final var redirectContract = redirectContractPersist();
        fileDataPersist();

        receiverPersist();
        final var senderEntityId = senderEntityPersist();
        final var ownerEntityId = ownerEntityPersist();
        final var spenderEntityId = spenderEntityPersist();
        notAssociatedSpenderEntityPersist();
        final var treasuryEntityId = treasureEntityPersist();
        autoRenewAccountPersist();

        fungibleTokenPersist(
                ownerEntityId,
                KEY_PROTO,
                FUNGIBLE_TOKEN_ADDRESS_WITH_EXPIRY,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                1000000000000L,
                TokenPauseStatusEnum.PAUSED,
                false);
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
        final var tokenEntityIdNotAssociated = fungibleTokenPersist(
                ownerEntityId,
                KEY_PROTO,
                FUNGIBLE_TOKEN_ADDRESS_NOT_ASSOCIATED,
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
        final var tokenTreasuryEntityId = fungibleTokenPersist(
                treasuryEntityId,
                new byte[0],
                TREASURY_TOKEN_ADDRESS,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                9999999999999L,
                TokenPauseStatusEnum.UNPAUSED,
                false);
        final var tokenGetKeyContractAddressEntityId = fungibleTokenPersist(
                senderEntityId,
                keyWithContractId.toByteArray(),
                FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_CONTRACT_ADDRESS,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                9999999999999L,
                TokenPauseStatusEnum.PAUSED,
                false);
        final var tokenGetKeyEcdsaEntityId = fungibleTokenPersist(
                senderEntityId,
                keyWithECDSASecp256K1.toByteArray(),
                FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ECDSA_KEY,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                9999999999999L,
                TokenPauseStatusEnum.PAUSED,
                false);
        final var tokenGetKeyEd25519EntityId = fungibleTokenPersist(
                senderEntityId,
                keyWithEd25519.toByteArray(),
                FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ED25519_KEY,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                9999999999999L,
                TokenPauseStatusEnum.PAUSED,
                false);
        final var tokenGetKeyDelegatableContractIdEntityId = fungibleTokenPersist(
                senderEntityId,
                keyWithDelegatableContractId.toByteArray(),
                FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_DELEGATABLE_CONTRACT_ID,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                9999999999999L,
                TokenPauseStatusEnum.PAUSED,
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
        final var nftEntityId4 = nftPersist(
                NFT_ADDRESS_GET_KEY_WITH_CONTRACT_ADDRESS,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                ownerEntityId,
                spenderEntityId,
                ownerEntityId,
                keyWithContractId.toByteArray(),
                TokenPauseStatusEnum.PAUSED,
                true);
        final var nftEntityId5 = nftPersist(
                NFT_ADDRESS_GET_KEY_WITH_ED25519_KEY,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                ownerEntityId,
                spenderEntityId,
                ownerEntityId,
                keyWithEd25519.toByteArray(),
                TokenPauseStatusEnum.PAUSED,
                true);
        final var nftEntityId6 = nftPersist(
                NFT_ADDRESS_GET_KEY_WITH_ECDSA_KEY,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                ownerEntityId,
                spenderEntityId,
                ownerEntityId,
                keyWithECDSASecp256K1.toByteArray(),
                TokenPauseStatusEnum.PAUSED,
                true);
        final var nftEntityId7 = nftPersist(
                NFT_ADDRESS_GET_KEY_WITH_DELEGATABLE_CONTRACT_ID,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                ownerEntityId,
                spenderEntityId,
                ownerEntityId,
                keyWithDelegatableContractId.toByteArray(),
                TokenPauseStatusEnum.PAUSED,
                true);
        final var nftEntityId8 = nftPersistWithoutKycKey(
                NFT_TRANSFER_ADDRESS_WITHOUT_KYC_KEY,
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
        tokenAccountPersist(senderEntityId, tokenTreasuryEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(spenderEntityId, notFrozenFungibleTokenEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(spenderEntityId, tokenTreasuryEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(ethAccount, notFrozenFungibleTokenEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(senderEntityId, notFrozenFungibleTokenEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(spenderEntityId, frozenFungibleTokenEntityId, TokenFreezeStatusEnum.FROZEN);
        tokenAccountPersist(ethAccount, frozenFungibleTokenEntityId, TokenFreezeStatusEnum.FROZEN);
        tokenAccountPersist(modificationContract, tokenEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(modificationContract, nftEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(ercContract, tokenEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(ercContract, nftEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(redirectContract, tokenEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(redirectContract, nftEntityId, TokenFreezeStatusEnum.UNFROZEN);

        tokenAccountPersist(treasuryEntityId, notFrozenFungibleTokenEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(nestedContractId, nftEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(nestedContractId, tokenEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(nestedContractId, nftEntityId3, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(nestedContractId, tokenTreasuryEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(ethAccount, tokenTreasuryEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(senderEntityId, tokenGetKeyContractAddressEntityId, TokenFreezeStatusEnum.UNFROZEN);

        tokenAccountPersist(ownerEntityId, nftEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(senderEntityId, nftEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(spenderEntityId, nftEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(ownerEntityId, nftEntityId3, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(spenderEntityId, nftEntityId3, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(ownerEntityId, nftEntityId2, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(senderEntityId, nftEntityId2, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(ownerEntityId, nftEntityId8, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(spenderEntityId, nftEntityId8, TokenFreezeStatusEnum.UNFROZEN);
        ercContractTokenPersist(ERC_CONTRACT_ADDRESS, tokenTreasuryEntityId, TokenFreezeStatusEnum.UNFROZEN);
        ercContractTokenPersist(REDIRECT_CONTRACT_ADDRESS, tokenTreasuryEntityId, TokenFreezeStatusEnum.UNFROZEN);
        nftCustomFeePersist(senderEntityId, nftEntityId);

        allowancesPersist(senderEntityId, spenderEntityId, tokenEntityId, nftEntityId);
        allowancesPersist(ownerEntityId, modificationContract, tokenEntityId, nftEntityId);
        allowancesPersist(ownerEntityId, nestedContractId, tokenEntityId, nftEntityId);
        allowancesPersist(ownerEntityId, ercContract, tokenEntityId, nftEntityId);
        allowancesPersist(ownerEntityId, redirectContract, tokenEntityId, nftEntityId);
        allowancesPersist(senderEntityId, spenderEntityId, tokenTreasuryEntityId, nftEntityId3);
        contractAllowancesPersist(senderEntityId, MODIFICATION_CONTRACT_ADDRESS, tokenTreasuryEntityId, nftEntityId3);
        contractAllowancesPersist(senderEntityId, ERC_CONTRACT_ADDRESS, tokenTreasuryEntityId, nftEntityId3);
        contractAllowancesPersist(senderEntityId, REDIRECT_CONTRACT_ADDRESS, tokenTreasuryEntityId, nftEntityId3);
        exchangeRatesPersist();
        feeSchedulesPersist();
    }

    // Custom fees and rates persist
    protected void customFeePersist(final FeeCase feeCase) {
        final var collectorAccountId = fromEvmAddress(SENDER_ADDRESS.toArrayUnsafe());
        final var tokenEntityId = fromEvmAddress(FUNGIBLE_TOKEN_ADDRESS.toArrayUnsafe());
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
                                .tokenId(tokenEntityId.getId()))
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
                                .tokenId(tokenEntityId.getId()))
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
                                .tokenId(tokenEntityId.getId()))
                        .persist();
            }
            default -> domainBuilder
                    .customFee()
                    .customize(f -> f.tokenId(tokenEntityId.getId()))
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
        final var timeStamp = System.currentTimeMillis();
        final var entityId = EntityId.of(0L, 0L, 112L);
        domainBuilder
                .fileData()
                .customize(f -> f.fileData(exchangeRatesSet.toByteArray())
                        .entityId(entityId)
                        .consensusTimestamp(timeStamp))
                .persist();
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

    private void ercContractTokenPersist(
            final Address contractAddress, final EntityId tokenEntityId, final TokenFreezeStatusEnum freezeStatusEnum) {
        final var contractEntityId = fromEvmAddress(contractAddress.toArrayUnsafe());
        domainBuilder
                .tokenAccount()
                .customize(e -> e.freezeStatus(freezeStatusEnum)
                        .accountId(contractEntityId.getNum())
                        .tokenId(tokenEntityId.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .balance(10L))
                .persist();
    }

    // Entity persist
    @Nullable
    private EntityId notAssociatedSpenderEntityPersist() {
        final var spenderEntityId = fromEvmAddress(NOT_ASSOCIATED_SPENDER_ADDRESS.toArrayUnsafe());
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
    private EntityId spenderEntityPersist() {
        final var spenderEntityId = fromEvmAddress(SPENDER_ADDRESS.toArrayUnsafe());
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

    @Nullable
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
    private EntityId autoRenewAccountPersist() {
        final var autoRenewEntityId = fromEvmAddress(AUTO_RENEW_ACCOUNT_ADDRESS.toArrayUnsafe());

        domainBuilder
                .entity()
                .customize(e -> e.id(autoRenewEntityId.getId())
                        .num(autoRenewEntityId.getNum())
                        .evmAddress(null)
                        .alias(toEvmAddress(autoRenewEntityId)))
                .persist();
        return autoRenewEntityId;
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

    private void receiverPersist() {
        final var receiverEntityId = fromEvmAddress(RECEIVER_ADDRESS.toArrayUnsafe());
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

    private void contractAllowancesPersist(
            final EntityId senderEntityId,
            final Address contractAddress,
            final EntityId tokenEntityId,
            final EntityId nftEntityId) {
        final var contractId = fromEvmAddress(contractAddress.toArrayUnsafe());
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

    // Contracts persist
    private void evmCodesContractPersist() {
        final var evmCodesContractBytes = functionEncodeDecoder.getContractBytes(EVM_CODES_BYTES_PATH);
        final var evmCodesContractEntityId = fromEvmAddress(EVM_CODES_CONTRACT_ADDRESS.toArrayUnsafe());
        final var evmCodesContractEvmAddress = toEvmAddress(evmCodesContractEntityId);

        domainBuilder
                .entity()
                .customize(e -> e.id(evmCodesContractEntityId.getId())
                        .num(evmCodesContractEntityId.getNum())
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
                        .num(ethCallContractEntityId.getNum())
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
                        .num(reverterContractEntityId.getNum())
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
                        .num(stateContractId.getNum())
                        .evmAddress(stateContractAddress)
                        .type(CONTRACT)
                        .balance(1500L))
                .persist();

        domainBuilder
                .contract()
                .customize(c -> c.id(stateContractId.getId()).runtimeBytecode(stateContractBytes))
                .persist();
    }

    private EntityId dynamicEthCallContractPresist() {
        final var contractBytes = functionEncodeDecoder.getContractBytes(DYNAMIC_ETH_CALLS_BYTES_PATH);
        final var contractEntityId = fromEvmAddress(DYNAMIC_ETH_CALLS_CONTRACT_ADDRESS.toArrayUnsafe());

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

    private void precompileContractPersist() {
        final var contractBytes = functionEncodeDecoder.getContractBytes(CONTRACT_BYTES_PATH);
        final var contractEntityId = fromEvmAddress(PRECOMPILE_TEST_CONTRACT_ADDRESS.toArrayUnsafe());
        final var contractEvmAddress = toEvmAddress(contractEntityId);

        domainBuilder
                .entity()
                .customize(e -> e.id(contractEntityId.getId())
                        .num(contractEntityId.getNum())
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
                        .num(modificationContractEntityId.getNum())
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
                        .num(ercContractEntityId.getNum())
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

    private EntityId redirectContractPersist() {
        final var redirectContractBytes = functionEncodeDecoder.getContractBytes(REDIRECT_CONTRACT_BYTES_PATH);
        final var redirectContractEntityId = fromEvmAddress(REDIRECT_CONTRACT_ADDRESS.toArrayUnsafe());
        final var redirectContractEvmAddress = toEvmAddress(redirectContractEntityId);

        domainBuilder
                .entity()
                .customize(e -> e.id(redirectContractEntityId.getId())
                        .num(redirectContractEntityId.getNum())
                        .evmAddress(redirectContractEvmAddress)
                        .type(CONTRACT)
                        .balance(1500L))
                .persist();

        domainBuilder
                .contract()
                .customize(c -> c.id(redirectContractEntityId.getId()).runtimeBytecode(redirectContractBytes))
                .persist();

        domainBuilder
                .contractState()
                .customize(c -> c.contractId(redirectContractEntityId.getId())
                        .slot(Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000000")
                                .toArrayUnsafe())
                        .value(Bytes.fromHexString("0x4746573740000000000000000000000000000000000000000000000000000000")
                                .toArrayUnsafe()))
                .persist();

        domainBuilder
                .recordFile()
                .customize(f -> f.bytes(redirectContractBytes))
                .persist();
        return redirectContractEntityId;
    }

    private EntityId pseudoRandomNumberGeneratorContractPersist() {
        final var randomNumberContractBytes =
                functionEncodeDecoder.getContractBytes(PRNG_PRECOMPILE_CONTRACT_BYTES_PATH);
        final var randomNumberContractEntityId = fromEvmAddress(PRNG_CONTRACT_ADDRESS.toArrayUnsafe());
        final var randomNumberContractEvmAddress = toEvmAddress(randomNumberContractEntityId);

        domainBuilder
                .entity()
                .customize(e -> e.id(randomNumberContractEntityId.getId())
                        .num(randomNumberContractEntityId.getNum())
                        .evmAddress(randomNumberContractEvmAddress)
                        .type(CONTRACT)
                        .balance(1500L))
                .persist();

        domainBuilder
                .contract()
                .customize(c -> c.id(randomNumberContractEntityId.getId()).runtimeBytecode(randomNumberContractBytes))
                .persist();

        domainBuilder
                .contractState()
                .customize(c -> c.contractId(randomNumberContractEntityId.getId())
                        .slot(Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000000")
                                .toArrayUnsafe())
                        .value(Bytes.fromHexString("0x4746573740000000000000000000000000000000000000000000000000000000")
                                .toArrayUnsafe()))
                .persist();

        domainBuilder
                .recordFile()
                .customize(f -> f.bytes(randomNumberContractBytes))
                .persist();
        return randomNumberContractEntityId;
    }

    private void nestedEthCallsContractPersist() {
        final var contractBytes = functionEncodeDecoder.getContractBytes(NESTED_CALLS_CONTRACT_BYTES_PATH);
        final var contractEntityId = fromEvmAddress(NESTED_ETH_CALLS_CONTRACT_ADDRESS.toArrayUnsafe());
        final var contractEvmAddress = toEvmAddress(contractEntityId);

        domainBuilder
                .entity()
                .customize(e -> e.id(contractEntityId.getId())
                        .num(contractEntityId.getNum())
                        .evmAddress(contractEvmAddress)
                        .type(CONTRACT)
                        .key(Key.newBuilder()
                                .setEd25519(ByteString.copyFrom(Arrays.copyOfRange(KEY_PROTO, 3, KEY_PROTO.length)))
                                .build()
                                .toByteArray())
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

        recordFileForBlockHash = domainBuilder
                .recordFile()
                .customize(f -> f.bytes(contractBytes))
                .persist();
    }

    private EntityId systemExchangeRateContractPersist() {
        final var exchangeRateContractBytes =
                functionEncodeDecoder.getContractBytes(EXCHANGE_RATE_PRECOMPILE_CONTRACT_BYTES_PATH);
        final var exchangeRateContractEntityId =
                fromEvmAddress(EXCHANGE_RATE_PRECOMPILE_CONTRACT_ADDRESS.toArrayUnsafe());
        final var exchangeRteContractEvmAddress = toEvmAddress(exchangeRateContractEntityId);

        domainBuilder
                .entity()
                .customize(e -> e.id(exchangeRateContractEntityId.getId())
                        .num(exchangeRateContractEntityId.getNum())
                        .evmAddress(exchangeRteContractEvmAddress)
                        .type(CONTRACT)
                        .balance(1500L))
                .persist();

        domainBuilder
                .contract()
                .customize(c -> c.id(exchangeRateContractEntityId.getId()).runtimeBytecode(exchangeRateContractBytes))
                .persist();

        domainBuilder
                .contractState()
                .customize(c -> c.contractId(exchangeRateContractEntityId.getId())
                        .slot(Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000000")
                                .toArrayUnsafe())
                        .value(Bytes.fromHexString("0x4746573740000000000000000000000000000000000000000000000000000000")
                                .toArrayUnsafe()))
                .persist();

        domainBuilder
                .recordFile()
                .customize(f -> f.bytes(exchangeRateContractBytes))
                .persist();
        return exchangeRateContractEntityId;
    }
}
