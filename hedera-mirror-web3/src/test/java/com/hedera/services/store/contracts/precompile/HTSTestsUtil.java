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

package com.hedera.services.store.contracts.precompile;

import static com.google.protobuf.UnsafeByteOperations.unsafeWrap;
import static com.hedera.services.utils.IdUtils.asAccount;
import static com.hedera.services.utils.IdUtils.asContract;
import static com.hedera.services.utils.IdUtils.asToken;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT_VALUE;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GetTokenDefaultFreezeStatusWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GetTokenDefaultKycStatusWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GetTokenExpiryInfoWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GrantRevokeKycWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.OwnerOfAndTokenURIWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenFreezeUnfreezeWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenGetCustomFeesWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenInfoWrapper;
import com.hedera.services.store.contracts.precompile.codec.*;
import com.hedera.services.store.contracts.precompile.codec.Association;
import com.hedera.services.store.contracts.precompile.codec.BurnWrapper;
import com.hedera.services.store.contracts.precompile.codec.Dissociation;
import com.hedera.services.store.contracts.precompile.codec.MintWrapper;
import com.hedera.services.store.contracts.precompile.codec.PauseWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenExpiryWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenKeyWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenUpdateExpiryInfoWrapper;
import com.hedera.services.store.contracts.precompile.codec.WipeWrapper;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;

public class HTSTestsUtil {

    public static final long AMOUNT = 1_234_567L;
    public static final long DEFAULT_GAS_PRICE = 10_000L;
    public static final long TEST_CONSENSUS_TIME = 1_640_000_000L; // Monday, December 20, 2021 11:33:20 AM UTC
    public static final TokenID token = asToken("0.0.1");
    public static final TokenID token2 = asToken("0.0.1176");
    public static final AccountID payer = asAccount("0.0.12345");
    public static final AccountID sender = asAccount("0.0.2");
    public static final AccountID receiver = asAccount("0.0.3");
    public static final AccountID receiverAliased =
            AccountID.newBuilder().setAlias(unsafeWrap(new byte[20])).build();
    public static final AccountID receiverAliased2 = AccountID.newBuilder()
            .setAlias(unsafeWrap(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20}))
            .build();
    public static final AccountID feeCollector = asAccount("0.0.4");
    public static final AccountID account = asAccount("0.0.3");
    public static final AccountID contract = asAccount("0.0.7");
    public static final AccountID secondSender = asAccount("0.0.1154");
    public static final AccountID accountMerkleId = asAccount("0.0.999");
    public static final ContractID precompiledContract = asContract(asAccount("0.0.359"));
    public static final TokenID nonFungible = asToken("0.0.777");
    public static final TokenID tokenMerkleId = asToken("0.0.777");
    public static final Id accountId = Id.fromGrpcAccount(account);
    public static final Association multiAssociateOp = Association.singleAssociation(accountMerkleId, tokenMerkleId);
    public static final Dissociation multiDissociateOp =
            Dissociation.singleDissociation(accountMerkleId, tokenMerkleId);
    public static final Address recipientAddr = Address.ALTBN128_ADD;
    public static final Address tokenAddress = Address.ECREC;
    public static final Address contractAddr = Address.ALTBN128_MUL;
    public static final Address senderAddress = Address.ALTBN128_PAIRING;
    public static final Address create1ContractAddress =
            Address.wrap(Bytes.fromHexString("0x4388985fc3efb7978b71b7fc59114aa64a42e285"));
    public static final Address parentContractAddress = Address.BLAKE2B_F_COMPRESSION;
    public static final Address parentRecipientAddress = Address.BLS12_G1ADD;
    public static final Timestamp timestamp =
            Timestamp.newBuilder().setSeconds(TEST_CONSENSUS_TIME).build();
    public static final Bytes successResult = UInt256.valueOf(ResponseCodeEnum.SUCCESS_VALUE);
    public static final Bytes failResult = UInt256.valueOf(ResponseCodeEnum.FAIL_INVALID_VALUE);
    public static final Bytes invalidAutoRenewAccountResult = UInt256.valueOf(INVALID_AUTORENEW_ACCOUNT_VALUE);
    public static final Bytes invalidTokenIdResult = UInt256.valueOf(ResponseCodeEnum.INVALID_TOKEN_ID_VALUE);
    public static final Bytes invalidSerialNumberResult =
            UInt256.valueOf(ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER_VALUE);
    public static final Bytes invalidSigResult = UInt256.valueOf(ResponseCodeEnum.INVALID_SIGNATURE_VALUE);
    public static final Bytes invalidFullPrefix =
            UInt256.valueOf(ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE_VALUE);
    public static final Bytes missingNftResult =
            UInt256.valueOf(ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER_VALUE);
    public static final Long serialNumber = 1L;
    public static final Association associateOp = Association.singleAssociation(accountMerkleId, tokenMerkleId);
    public static final Dissociation dissociateOp = Dissociation.singleDissociation(accountMerkleId, tokenMerkleId);
    public static final TokenID fungible = IdUtils.asToken("0.0.888");
    public static final Id nonFungibleId = Id.fromGrpcToken(nonFungible);
    public static final Address nonFungibleTokenAddr = nonFungibleId.asEvmAddress();
    public static final Id fungibleId = Id.fromGrpcToken(fungible);
    public static final Address fungibleTokenAddr = fungibleId.asEvmAddress();
    public static final OwnerOfAndTokenURIWrapper ownerOfAndTokenUriWrapper =
            new OwnerOfAndTokenURIWrapper(serialNumber);
    public static final GetTokenDefaultFreezeStatusWrapper<TokenID> defaultFreezeStatusWrapper =
            new GetTokenDefaultFreezeStatusWrapper<>(fungible);
    public static final GetTokenDefaultKycStatusWrapper<TokenID> defaultKycStatusWrapper =
            new GetTokenDefaultKycStatusWrapper<>(fungible);
    public static final GrantRevokeKycWrapper<TokenID, AccountID> grantRevokeKycWrapper =
            new GrantRevokeKycWrapper<>(fungible, account);

    public static final TokenFreezeUnfreezeWrapper<TokenID, AccountID> tokenFreezeUnFreezeWrapper =
            new TokenFreezeUnfreezeWrapper<>(fungible, account);

    public static final Address recipientAddress = Address.ALTBN128_ADD;

    public static final Address contractAddress = Address.ALTBN128_MUL;

    public static final List<Long> targetSerialNos = List.of(1L, 2L, 3L);
    public static final BurnWrapper nonFungibleBurn = BurnWrapper.forNonFungible(nonFungible, targetSerialNos);
    public static final Bytes burnSuccessResultWith49Supply = Bytes.fromHexString(
            "0x00000000000000000000000000000000000000000000000000000000000000160000000000000000000000000000000000000000000000000000000000000031");
    public static final Bytes burnSuccessResultWithLongMaxValueSupply = Bytes.fromHexString(
            "0x00000000000000000000000000000000000000000000000000000000000000b70000000000000000000000000000000000000000000000000000000000000000");

    public static final List<ByteString> newMetadata =
            List.of(ByteString.copyFromUtf8("AAA"), ByteString.copyFromUtf8("BBB"), ByteString.copyFromUtf8("CCC"));
    public static final MintWrapper nftMint = MintWrapper.forNonFungible(nonFungible, newMetadata);
    public static final UnpauseWrapper fungibleUnpause = new UnpauseWrapper(fungible);
    public static final WipeWrapper fungibleWipe = WipeWrapper.forFungible(fungible, account, AMOUNT);
    public static final WipeWrapper nonFungibleWipe = WipeWrapper.forNonFungible(nonFungible, account, targetSerialNos);
    public static final Bytes fungibleSuccessResultWith10Supply = Bytes.fromHexString(
            "0x0000000000000000000000000000000000000000000000000000000000000016000000000000000000000000000000000000000000000000000000000000000a00000000000000000000000000000000000000000000000000000000000000600000000000000000000000000000000000000000000000000000000000000000");
    public static final Bytes fungibleSuccessResultWithLongMaxValueSupply = Bytes.fromHexString(
            "0x00000000000000000000000000000000000000000000000000000000000000160000000000000000000000000000000000000000000000007fffffffffffffff00000000000000000000000000000000000000000000000000000000000000600000000000000000000000000000000000000000000000000000000000000000");
    public static final PauseWrapper fungiblePause = new PauseWrapper(fungible);
    public static final Bytes failInvalidResult = UInt256.valueOf(ResponseCodeEnum.FAIL_INVALID_VALUE);
    public static final Instant pendingChildConsTime = Instant.ofEpochSecond(1_234_567L, 890);
    public static final Address senderAddr = Address.ALTBN128_PAIRING;
    public static final String NOT_SUPPORTED_FUNGIBLE_OPERATION_REASON = "Invalid operation for ERC-20 token!";
    public static final String NOT_SUPPORTED_NON_FUNGIBLE_OPERATION_REASON = "Invalid operation for ERC-721 token!";
    public static final MintWrapper fungibleMint = MintWrapper.forFungible(fungible, AMOUNT);
    public static final WipeWrapper fungibleWipeMaxAmount = WipeWrapper.forFungible(fungible, account, Long.MAX_VALUE);
    public static final TokenGetCustomFeesWrapper<TokenID> customFeesWrapper = new TokenGetCustomFeesWrapper<>(token);
    public static final GetTokenExpiryInfoWrapper<TokenID> getTokenExpiryInfoWrapper =
            new GetTokenExpiryInfoWrapper<>(token);
    public static final TokenUpdateExpiryInfoWrapper tokenUpdateExpiryInfoWrapper =
            new TokenUpdateExpiryInfoWrapper(token, new TokenExpiryWrapper(442L, payer, 555L));

    public static final TokenUpdateExpiryInfoWrapper tokenUpdateExpiryInfoWrapperWithInvalidTokenID =
            new TokenUpdateExpiryInfoWrapper(null, new TokenExpiryWrapper(442L, payer, 555L));

    public static final Bytes ercTransferSuccessResult =
            Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000001");

    public static final Bytes BALANCE_OF =
            Bytes.fromHexString("0x70a082310000000000000000000000000000000000000000000" + "0000000000000000003ee");

    public static final Bytes TOKEN_TRANSFER =
            Bytes.fromHexString("0xa9059cbb0000000000000000000000000000000000000000000000000000000000000"
                    + "3f00000000000000000000000000000000000000000000000000000000000000002");

    public static final Bytes OWNER_OF =
            Bytes.fromHexString("0x6352211e0000000000000000000000000000000000000000000000000000000000000001");

    public static final Bytes SAFE_TRANSFER_FROM_WITH_DATA =
            Bytes.fromHexString("0xb88d4fde0000000000000000000000000000000000000000000000000000000000000"
                    + "3e900000000000000000000000000000000000000000000000000000000000003ea000000000000000000000000000000000000000000000000000000000000000"
                    + "10000000000000000000000000000000000000000000000000000000000000080000000000000000000000000000000000000000000000000000000000000000"
                    + "95465737420646174610000000000000000000000000000000000000000000000");

    public static final Bytes SAFE_TRANSFER_FROM = Bytes.fromHexString(
            "0x42842e0e0000000000000000000000000000000000000000000000000000000000000"
                    + "41200000000000000000000000000000000000000000000000000000000000004130000000000000000000000000000000000000000000000000000000000000001");

    public static final Bytes TRANSFER_FROM = Bytes.fromHexString(
            "0x23b872dd0000000000000000000000000000000000000000000000000000000000000"
                    + "40c000000000000000000000000000000000000000000000000000000000000040d0000000000000000000000000000000000000000000000000000000000000001");

    public static final Bytes TOKEN_URI =
            Bytes.fromHexString("0xc87b56dd0000000000000000000000000000000000000000000000000000000000000001");

    public static final TokenTransferWrapper nftTransferList =
            new TokenTransferWrapper(List.of(new NftExchange(1, token, sender, receiver)), new ArrayList<>() {});
    public static final CryptoTransferWrapper CRYPTO_TRANSFER_NFT_WRAPPER = new CryptoTransferWrapper(
            new TransferWrapper(Collections.emptyList()), Collections.singletonList(nftTransferList));
    public static final FungibleTokenTransfer transfer =
            new FungibleTokenTransfer(AMOUNT, false, token, sender, receiver);
    public static final TokenTransferWrapper TOKEN_TRANSFER_WRAPPER =
            new TokenTransferWrapper(new ArrayList<>() {}, List.of(transfer));
    public static final CryptoTransferWrapper CRYPTO_TRANSFER_TOKEN_WRAPPER = new CryptoTransferWrapper(
            new TransferWrapper(Collections.emptyList()), Collections.singletonList(TOKEN_TRANSFER_WRAPPER));
    public static final TokenTransferWrapper tokensTransferList =
            new TokenTransferWrapper(new ArrayList<>() {}, List.of(transfer, transfer));

    public static final TokenTransferWrapper tokensTransferList2 =
            new TokenTransferWrapper(new ArrayList<>() {}, List.of(transfer));
    public static final CryptoTransferWrapper CRYPTO_TRANSFER_FUNGIBLE_WRAPPER = new CryptoTransferWrapper(
            new TransferWrapper(Collections.emptyList()), Collections.singletonList(tokensTransferList));
    public static final CryptoTransferWrapper CRYPTO_TRANSFER_FUNGIBLE_WRAPPER2 = new CryptoTransferWrapper(
            new TransferWrapper(Collections.emptyList()), Collections.singletonList(tokensTransferList2));
    public static final FungibleTokenTransfer transferToAlias1SenderOnly =
            new FungibleTokenTransfer(-AMOUNT, false, token, sender, null);
    public static final FungibleTokenTransfer transferToAlias1ReceiverOnly =
            new FungibleTokenTransfer(AMOUNT, false, token, null, receiverAliased);
    public static final FungibleTokenTransfer transferToAlias2SenderOnly =
            new FungibleTokenTransfer(-AMOUNT, false, token, sender, null);
    public static final FungibleTokenTransfer transferToAlias2ReceiverOnly =
            new FungibleTokenTransfer(AMOUNT, false, token, null, receiverAliased2);
    public static final TokenTransferWrapper tokensTransferListAliasedX2 = new TokenTransferWrapper(
            new ArrayList<>() {},
            List.of(
                    transferToAlias1SenderOnly,
                    transferToAlias1ReceiverOnly,
                    transferToAlias2SenderOnly,
                    transferToAlias2ReceiverOnly));
    public static final CryptoTransferWrapper CRYPTO_TRANSFER_FUNGIBLE_WRAPPER_2_ALIASES = new CryptoTransferWrapper(
            new TransferWrapper(Collections.emptyList()), Collections.singletonList(tokensTransferListAliasedX2));
    public static final FungibleTokenTransfer transferSenderOnly =
            new FungibleTokenTransfer(AMOUNT, false, token, sender, null);
    public static final TokenTransferWrapper tokensTransferListSenderOnly =
            new TokenTransferWrapper(new ArrayList<>() {}, List.of(transferSenderOnly, transferSenderOnly));
    public static final CryptoTransferWrapper CRYPTO_TRANSFER_SENDER_WRAPPER = new CryptoTransferWrapper(
            new TransferWrapper(Collections.emptyList()), Collections.singletonList(tokensTransferListSenderOnly));
    public static final FungibleTokenTransfer transferReceiverOnly =
            new FungibleTokenTransfer(AMOUNT, false, token, null, receiver);
    public static final TokenTransferWrapper tokensTransferListReceiverOnly =
            new TokenTransferWrapper(new ArrayList<>() {}, List.of(transferReceiverOnly, transferReceiverOnly));
    public static final CryptoTransferWrapper CRYPTO_TRANSFER_RECEIVER_WRAPPER = new CryptoTransferWrapper(
            new TransferWrapper(Collections.emptyList()), Collections.singletonList(tokensTransferListReceiverOnly));
    public static final TokenTransferWrapper nftsTransferList = new TokenTransferWrapper(
            List.of(new NftExchange(1, token, sender, receiver), new NftExchange(2, token, sender, receiver)),
            new ArrayList<>() {});
    public static final CryptoTransferWrapper CRYPTO_TRANSFER_NFTS_WRAPPER = new CryptoTransferWrapper(
            new TransferWrapper(Collections.emptyList()), Collections.singletonList(nftsTransferList));
    public static final TokenTransferWrapper nftsTransferListAliasReceiver = new TokenTransferWrapper(
            List.of(
                    new NftExchange(1, token, sender, receiverAliased),
                    new NftExchange(2, token, sender, receiverAliased)),
            new ArrayList<>() {});
    public static final CryptoTransferWrapper CRYPTO_TRANSFER_NFTS_WRAPPER_ALIAS_RECEIVER = new CryptoTransferWrapper(
            new TransferWrapper(Collections.emptyList()), Collections.singletonList(nftsTransferListAliasReceiver));
    public static final CryptoTransferWrapper CRYPTO_TRANSFER_EMPTY_WRAPPER =
            new CryptoTransferWrapper(new TransferWrapper(Collections.emptyList()), Collections.emptyList());
    public static final List<HbarTransfer> hbarTransfers =
            List.of(new HbarTransfer(AMOUNT, false, null, receiver), new HbarTransfer(AMOUNT, false, sender, null));
    public static final CryptoTransferWrapper CRYPTO_TRANSFER_HBAR_ONLY_WRAPPER =
            new CryptoTransferWrapper(new TransferWrapper(hbarTransfers), Collections.emptyList());
    public static final CryptoTransferWrapper CRYPTO_TRANSFER_HBAR_FUNGIBLE_WRAPPER = new CryptoTransferWrapper(
            new TransferWrapper(hbarTransfers), Collections.singletonList(tokensTransferList));
    public static final CryptoTransferWrapper CRYPTO_TRANSFER_HBAR_FUNGIBLE_WRAPPER2 = new CryptoTransferWrapper(
            new TransferWrapper(hbarTransfers), Collections.singletonList(tokensTransferList2));
    public static final CryptoTransferWrapper CRYPTO_TRANSFER_HBAR_NFT_WRAPPER =
            new CryptoTransferWrapper(new TransferWrapper(hbarTransfers), Collections.singletonList(nftsTransferList));
    public static final CryptoTransferWrapper CRYPTO_TRANSFER_HBAR_FUNGIBLE_NFT_WRAPPER = new CryptoTransferWrapper(
            new TransferWrapper(hbarTransfers), List.of(tokensTransferList, nftsTransferList));
    public static final List<HbarTransfer> hbarTransfersAliased = List.of(
            new HbarTransfer(AMOUNT, false, null, receiverAliased), new HbarTransfer(AMOUNT, false, sender, null));
    public static final CryptoTransferWrapper CRYPTO_TRANSFER_HBAR_ONLY_WRAPPER_ALIASED =
            new CryptoTransferWrapper(new TransferWrapper(hbarTransfersAliased), Collections.emptyList());
    public static final List<HbarTransfer> twoHbarTransfers = List.of(
            new HbarTransfer(AMOUNT, false, null, receiver),
            new HbarTransfer(-AMOUNT, false, sender, null),
            new HbarTransfer(AMOUNT, false, null, sender),
            new HbarTransfer(-AMOUNT, false, receiver, null));
    public static final CryptoTransferWrapper CRYPTO_TRANSFER_TWO_HBAR_ONLY_WRAPPER =
            new CryptoTransferWrapper(new TransferWrapper(twoHbarTransfers), Collections.emptyList());
    public static final TokenCreateWrapper.FixedFeeWrapper fixedFee =
            new TokenCreateWrapper.FixedFeeWrapper(5, token, false, false, receiver);
    public static final TokenCreateWrapper.RoyaltyFeeWrapper royaltyFee =
            new TokenCreateWrapper.RoyaltyFeeWrapper(4, 5, fixedFee, receiver);
    public static final TokenCreateWrapper.FractionalFeeWrapper fractionalFee =
            new TokenCreateWrapper.FractionalFeeWrapper(4, 5, 10, 20, true, receiver);

    public static TokenCreateWrapper createTokenCreateWrapperWithKeys(final List<TokenKeyWrapper> keys) {
        return new TokenCreateWrapper(
                true,
                "token",
                "symbol",
                account,
                "memo",
                false,
                BigInteger.valueOf(Long.MAX_VALUE),
                BigInteger.valueOf(Integer.MAX_VALUE),
                5054L,
                false,
                keys,
                new TokenExpiryWrapper(442L, payer, 555L));
    }

    public static TokenCreateWrapper createNonFungibleTokenCreateWrapperWithKeys(final List<TokenKeyWrapper> keys) {
        return new TokenCreateWrapper(
                false,
                "nft",
                "NFT",
                account,
                "nftMemo",
                true,
                BigInteger.ZERO,
                BigInteger.ZERO,
                5054L,
                true,
                keys,
                new TokenExpiryWrapper(0L, null, 0L));
    }

    public static TokenInfoWrapper<TokenID> createTokenInfoWrapperForToken(final TokenID tokenId) {
        return TokenInfoWrapper.forToken(tokenId);
    }

    public static TokenInfoWrapper<TokenID> createTokenInfoWrapperForNonFungibleToken(
            final TokenID tokenId, final long serialNumber) {
        return TokenInfoWrapper.forNonFungibleToken(tokenId, serialNumber);
    }

    public static TokenUpdateWrapper createFungibleTokenUpdateWrapperWithKeys(final List<TokenKeyWrapper> keys) {
        return new TokenUpdateWrapper(
                fungible, "fungible", "G", account, "G token memo", keys, new TokenExpiryWrapper(1L, account, 2L));
    }

    public static TokenUpdateWrapper createNonFungibleTokenUpdateWrapperWithKeys(final List<TokenKeyWrapper> keys) {
        return new TokenUpdateWrapper(nonFungible, null, null, null, null, keys, new TokenExpiryWrapper(0, null, 0));
    }

    private static byte[] expandByteArrayTo32Length(final byte[] bytesToExpand) {
        final byte[] expandedArray = new byte[32];

        System.arraycopy(
                bytesToExpand, 0, expandedArray, expandedArray.length - bytesToExpand.length, bytesToExpand.length);
        return expandedArray;
    }
}
