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

import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
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

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.services.store.contracts.precompile.TokenCreateWrapper;
import com.hedera.services.store.contracts.precompile.TokenCreateWrapper.FixedFeeWrapper;
import com.hedera.services.store.contracts.precompile.TokenCreateWrapper.FractionalFeeWrapper;
import com.hedera.services.store.contracts.precompile.TokenCreateWrapper.RoyaltyFeeWrapper;
import com.hedera.services.store.contracts.precompile.codec.KeyValueWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenExpiryWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenKeyWrapper;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
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
import java.util.Arrays;
import java.util.List;
import java.util.function.ToLongFunction;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.datatypes.Address;

public class ContractCallTestConstants {

    public static final long expiry = 1_234_567_890L;
    public static final BigInteger SUCCESS_RESULT = BigInteger.valueOf(ResponseCodeEnum.SUCCESS_VALUE);

    // Exchange rates from local node.
    public static final ExchangeRateSet exchangeRatesSet = ExchangeRateSet.newBuilder()
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
    public static final EntityId FEE_SCHEDULE_ENTITY_ID = EntityId.of(0L, 0L, 111L);
    public static final EntityId EXCHANGE_RATE_ENTITY_ID = EntityId.of(0L, 0L, 112L);

    // Contract addresses
    public static final Address ETH_ADDRESS = Address.fromHexString("0x23f5e49569a835d7bf9aefd30e4f60cdd570f225");
    public static final Address DYNAMIC_ETH_CALLS_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1255));
    public static final Address DYNAMIC_ETH_CALLS_CONTRACT_ALIAS =
            Address.fromHexString("0x742d35Cc6634C0532925a3b844Bc454e4438f44e");
    public static final Address PRECOMPILE_TEST_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1256));
    public static final Address MODIFICATION_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1257));
    public static final Address ERC_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1258));
    public static final Address REVERTER_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1259));
    public static final Address ETH_CALL_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1260));
    public static final Address STATE_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1261));
    public static final Address NESTED_ETH_CALLS_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1262));
    public static final Address EVM_CODES_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1263));
    public static final Address EXCHANGE_RATE_PRECOMPILE_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1264));
    public static final Address REDIRECT_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1265));
    public static final Address PRNG_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1266));

    // Account addresses
    public static final Address AUTO_RENEW_ACCOUNT_ADDRESS = toAddress(EntityId.of(0, 0, 740));
    public static final Address SPENDER_ADDRESS = toAddress(EntityId.of(0, 0, 741));
    public static final ByteString SPENDER_PUBLIC_KEY =
            ByteString.fromHex("3a2102ff806fecbd31b4c377293cba8d2b78725965a4990e0ff1b1b29a1d2c61402310");
    public static final Address SPENDER_ALIAS = Address.wrap(
            Bytes.wrap(recoverAddressFromPubKey(SPENDER_PUBLIC_KEY.substring(2).toByteArray())));
    public static final Address SENDER_ADDRESS = toAddress(EntityId.of(0, 0, 742));
    public static final ByteString SENDER_PUBLIC_KEY =
            ByteString.copyFrom(Hex.decode("3a2103af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d"));
    public static final Address SENDER_ALIAS = Address.wrap(
            Bytes.wrap(recoverAddressFromPubKey(SENDER_PUBLIC_KEY.substring(2).toByteArray())));
    public static final Address TREASURY_ADDRESS = toAddress(EntityId.of(0, 0, 743));
    public static final Address NOT_ASSOCIATED_SPENDER_ADDRESS = toAddress(EntityId.of(0, 0, 744));
    public static final ByteString NOT_ASSOCIATED_SPENDER_PUBLIC_KEY =
            ByteString.fromHex("3a21033a514176466fa815ed481ffad09110a2d344f6c9b78c1d14afc351c3a51be33d");
    public static final Address NOT_ASSOCIATED_SPENDER_ALIAS = Address.wrap(Bytes.wrap(recoverAddressFromPubKey(
            NOT_ASSOCIATED_SPENDER_PUBLIC_KEY.substring(2).toByteArray())));
    public static final Address OWNER_ADDRESS = toAddress(EntityId.of(0, 0, 750));

    // Token addresses
    public static final Address FUNGIBLE_TOKEN_ADDRESS_WITH_EXPIRY = toAddress(EntityId.of(0, 0, 1042));
    public static final Address RECEIVER_ADDRESS = toAddress(EntityId.of(0, 0, 1045));
    public static final Address FUNGIBLE_TOKEN_ADDRESS = toAddress(EntityId.of(0, 0, 1046));
    public static final Address NFT_ADDRESS = toAddress(EntityId.of(0, 0, 1047));
    public static final Address NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS = toAddress(EntityId.of(0, 0, 1048));
    public static final Address TREASURY_TOKEN_ADDRESS = toAddress(EntityId.of(0, 0, 1049));
    public static final Address TRANSFRER_FROM_TOKEN_ADDRESS = toAddress(EntityId.of(0, 0, 1111));
    public static final Address FROZEN_FUNGIBLE_TOKEN_ADDRESS = toAddress(EntityId.of(0, 0, 1050));
    public static final Address NFT_TRANSFER_ADDRESS = toAddress(EntityId.of(0, 0, 1051));
    public static final Address UNPAUSED_FUNGIBLE_TOKEN_ADDRESS = toAddress(EntityId.of(0, 0, 1052));
    public static final Address NFT_ADDRESS_GET_KEY_WITH_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1053));
    public static final Address FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ED25519_KEY = toAddress(EntityId.of(0, 0, 1054));
    public static final Address FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ECDSA_KEY = toAddress(EntityId.of(0, 0, 1055));
    public static final Address FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_DELEGATABLE_CONTRACT_ID =
            toAddress(EntityId.of(0, 0, 1056));
    public static final Address NFT_ADDRESS_GET_KEY_WITH_ED25519_KEY = toAddress(EntityId.of(0, 0, 1057));
    public static final Address NFT_ADDRESS_GET_KEY_WITH_ECDSA_KEY = toAddress(EntityId.of(0, 0, 1058));
    public static final Address NFT_ADDRESS_GET_KEY_WITH_DELEGATABLE_CONTRACT_ID = toAddress(EntityId.of(0, 0, 1059));
    public static final Address FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_CONTRACT_ADDRESS =
            toAddress(EntityId.of(0, 0, 1060));
    public static final Address FUNGIBLE_TOKEN_ADDRESS_NOT_ASSOCIATED = toAddress(EntityId.of(0, 0, 1061));
    public static final Address NFT_ADDRESS_WITH_DIFFERENT_OWNER_AND_TREASURY = toAddress(EntityId.of(0, 0, 1067));
    public static final Address NFT_TRANSFER_ADDRESS_WITHOUT_KYC_KEY = toAddress(EntityId.of(0, 0, 1071));

    public static final byte[] KEY_PROTO = new byte[] {
        58, 33, -52, -44, -10, 81, 99, 100, 6, -8, -94, -87, -112, 42, 42, 96, 75, -31, -5, 72, 13, -70, 101, -111, -1,
        77, -103, 47, -118, 107, -58, -85, -63, 55, -57
    };
    public static final byte[] ECDSA_KEY = Arrays.copyOfRange(KEY_PROTO, 2, KEY_PROTO.length);
    public static Key keyWithECDSASecp256K1 =
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
    public static final int АLL_CASES_KEY_TYPE = 0b1111111;
    public static final byte[] NEW_ECDSA_KEY = new byte[] {
        2, 64, 59, -126, 81, -22, 0, 35, 67, -70, 110, 96, 109, 2, -8, 111, -112, -100, -87, -85, 66, 36, 37, -97, 19,
        68, -87, -110, -13, -115, 74, 86, 90
    };
    public static final byte[] ED25519_KEY = Arrays.copyOfRange(KEY_PROTO, 2, KEY_PROTO.length);
    public static Key keyWithEd25519 =
            Key.newBuilder().setEd25519(ByteString.copyFrom(ED25519_KEY)).build();
    public static final byte[] NEW_ED25519_KEY = new byte[] {
        -128, -61, -12, 63, 3, -45, 108, 34, 61, -2, -83, -48, -118, 20, 84, 85, 85, 67, -125, 46, 49, 26, 17, -116, 27,
        25, 38, -95, 50, 77, 40, -38
    };

    // Token Wrappers
    public static final TokenCreateWrapper FUNGIBLE_TOKEN = getFungibleToken();
    public static final TokenCreateWrapper FUNGIBLE_TOKEN2 = getFungibleToken2();
    public static final TokenCreateWrapper FUNGIBLE_TOKEN_WITH_KEYS = getFungibleTokenWithKeys();
    public static final TokenCreateWrapper FUNGIBLE_TOKEN_EXPIRY_IN_UINT32_RANGE =
            getFungibleTokenExpiryInUint32Range();
    public static final TokenCreateWrapper FUNGIBLE_HBAR_TOKEN_AND_KEYS = getFungibleHbarsTokenWrapper();
    public static final TokenCreateWrapper FUNGIBLE_TOKEN_INHERIT_KEYS = getFungibleTokenInheritKeys();
    public static final TokenCreateWrapper NON_FUNGIBLE_TOKEN = getNonFungibleToken();
    public static final TokenCreateWrapper NON_FUNGIBLE_TOKEN_WITH_KEYS = getNonFungibleTokenWithKeys();
    public static final TokenCreateWrapper NON_FUNGIBLE_TOKEN_EXPIRY_IN_UINT32_RANGE =
            getNonFungibleTokenExpiryInUint32Range();
    public static final TokenCreateWrapper NFT_HBAR_TOKEN_AND_KEYS = getNftHbarTokenAndKeysHbarsTokenWrapper();
    public static final TokenCreateWrapper NON_FUNGIBLE_TOKEN_INHERIT_KEYS = getNonFungibleTokenInheritKeys();

    // Custom Fee wrappers
    public static final FixedFeeWrapper FIXED_FEE_WRAPPER = getFixedFee();
    public static final RoyaltyFeeWrapper ROYALTY_FEE_WRAPPER = getRoyaltyFee();
    public static final FractionalFeeWrapper FRACTIONAL_FEE_WRAPPER = getFractionalFee();
    public static final TokenExpiryWrapper TOKEN_EXPIRY_WRAPPER = getTokenExpiry();

    // Fee schedules
    public static final ToLongFunction<String> longValueOf =
            value -> Bytes.fromHexString(value).toLong();
    public static CurrentAndNextFeeSchedule feeSchedules = CurrentAndNextFeeSchedule.newBuilder()
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

    public static Key keyWithContractId = Key.newBuilder()
            .setContractID(contractIdFromEvmAddress(PRECOMPILE_TEST_CONTRACT_ADDRESS.toArrayUnsafe()))
            .build();
    public static Key keyWithDelegatableContractId = Key.newBuilder()
            .setDelegatableContractId(contractIdFromEvmAddress(PRECOMPILE_TEST_CONTRACT_ADDRESS.toArrayUnsafe()))
            .build();

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
}
