/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.web3.evm.account.AccountAccessorImpl.EVM_ADDRESS_SIZE;
import static com.hedera.services.fees.calculation.utils.AccessorBasedUsages.THREE_MONTHS_IN_SECONDS;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.account;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.createFungibleTokenUpdateWrapperWithKeys;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.createNonFungibleTokenUpdateWrapperWithKeys;
import static com.hedera.services.store.contracts.precompile.SyntheticTxnFactory.AUTO_MEMO;
import static com.hedera.services.store.contracts.precompile.codec.Association.multiAssociation;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.EMPTY_KEY;
import static com.hedera.services.utils.EntityIdUtils.contractIdFromEvmAddress;
import static com.hedera.services.utils.MiscUtils.asKeyUnchecked;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GrantRevokeKycWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenFreezeUnfreezeWrapper;
import com.hedera.node.app.service.evm.utils.EthSigsUtils;
import com.hedera.services.jproto.JKey;
import com.hedera.services.store.contracts.precompile.codec.ApproveWrapper;
import com.hedera.services.store.contracts.precompile.codec.BurnWrapper;
import com.hedera.services.store.contracts.precompile.codec.Dissociation;
import com.hedera.services.store.contracts.precompile.codec.KeyValueWrapper;
import com.hedera.services.store.contracts.precompile.codec.MintWrapper;
import com.hedera.services.store.contracts.precompile.codec.PauseWrapper;
import com.hedera.services.store.contracts.precompile.codec.SetApprovalForAllWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenExpiryWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenKeyWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenUpdateExpiryInfoWrapper;
import com.hedera.services.store.contracts.precompile.codec.UnpauseWrapper;
import com.hedera.services.store.contracts.precompile.codec.WipeWrapper;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenID;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.util.Collections;
import java.util.List;
import org.apache.commons.codec.DecoderException;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyntheticTxnFactoryTest {
    private static final long serialNo = 100;
    private static final long secondAmount = 200;
    private static final long newExpiry = 1_234_567L;
    private final EntityNum contractNum = EntityNum.fromLong(666);
    private final EntityNum accountNum = EntityNum.fromLong(1234);
    private static final AccountID a = IdUtils.asAccount("0.0.2");
    private static final AccountID b = IdUtils.asAccount("0.0.3");
    private static final AccountID c = IdUtils.asAccount("0.0.4");
    private static final TokenID fungible = IdUtils.asToken("0.0.555");
    private static final TokenID nonFungible = IdUtils.asToken("0.0.666");
    private static final List<Long> targetSerialNos = List.of(1L, 2L, 3L);
    private static final List<ByteString> newMetadata =
            List.of(ByteString.copyFromUtf8("AAA"), ByteString.copyFromUtf8("BBB"), ByteString.copyFromUtf8("CCC"));
    private static final long valueInTinyBars = 123;
    public static final String HTS_PRECOMPILED_CONTRACT_ADDRESS = "0x167";
    public static final TokenID token = IdUtils.asToken("0.0.1");
    public static final AccountID payer = IdUtils.asAccount("0.0.12345");
    public static final AccountID sender = IdUtils.asAccount("0.0.2");

    public static final AccountID receiver = IdUtils.asAccount("0.0.3");
    public static final Id payerId = Id.fromGrpcAccount(payer);
    public static final Id senderId = Id.fromGrpcAccount(sender);

    private SyntheticTxnFactory subject = new SyntheticTxnFactory();

    @Test
    void createsExpectedCryptoCreateWithEDKeyAlias() {
        final var balance = 10L;
        final var bytes = new byte[33];
        bytes[0] = 0x02;
        final Key key =
                Key.newBuilder().setECDSASecp256K1(ByteString.copyFrom(bytes)).build();
        final var alias = key.toByteString();
        final var result = subject.createAccount(alias, key, balance, 0);
        final var txnBody = result.build();

        assertTrue(txnBody.hasCryptoCreateAccount());
        assertEquals(AUTO_MEMO, txnBody.getCryptoCreateAccount().getMemo());
        assertEquals(
                THREE_MONTHS_IN_SECONDS,
                txnBody.getCryptoCreateAccount().getAutoRenewPeriod().getSeconds());
        assertEquals(10L, txnBody.getCryptoCreateAccount().getInitialBalance());
        assertEquals(0L, txnBody.getCryptoCreateAccount().getMaxAutomaticTokenAssociations());
        assertEquals(
                key.toByteString(), txnBody.getCryptoCreateAccount().getKey().toByteString());
        assertEquals(alias, txnBody.getCryptoCreateAccount().getAlias());
    }

    @Test
    void createsExpectedCryptoCreateWithECKeyAlias() throws InvalidKeyException, DecoderException {
        final var balance = 10L;
        final var bytes = new byte[33];
        bytes[0] = 0x02;
        final Key key =
                Key.newBuilder().setECDSASecp256K1(ByteString.copyFrom(bytes)).build();
        final var alias = key.toByteString();
        final var evmAddress = ByteString.copyFrom(
                EthSigsUtils.recoverAddressFromPubKey(JKey.mapKey(key).getECDSASecp256k1Key()));
        final var result = subject.createAccount(alias, key, balance, 0);
        final var txnBody = result.build();

        assertTrue(txnBody.hasCryptoCreateAccount());
        assertEquals(AUTO_MEMO, txnBody.getCryptoCreateAccount().getMemo());
        assertEquals(
                THREE_MONTHS_IN_SECONDS,
                txnBody.getCryptoCreateAccount().getAutoRenewPeriod().getSeconds());
        assertEquals(10L, txnBody.getCryptoCreateAccount().getInitialBalance());
        assertEquals(0L, txnBody.getCryptoCreateAccount().getMaxAutomaticTokenAssociations());
        assertEquals(
                key.toByteString(), txnBody.getCryptoCreateAccount().getKey().toByteString());
        assertEquals(alias, txnBody.getCryptoCreateAccount().getAlias());
    }

    @Test
    void createsExpectedHollowAccountCreate() {
        final var balance = 10L;
        final var evmAddressAlias = ByteString.copyFrom(Hex.decode("a94f5374fce5edbc8e2a8697c15331677e6ebf0b"));
        final var result = subject.createHollowAccount(evmAddressAlias, balance);
        final var txnBody = result.build();

        assertTrue(txnBody.hasCryptoCreateAccount());
        assertEquals(asKeyUnchecked(EMPTY_KEY), txnBody.getCryptoCreateAccount().getKey());
        assertEquals(evmAddressAlias, txnBody.getCryptoCreateAccount().getAlias());
        assertEquals(
                EVM_ADDRESS_SIZE, txnBody.getCryptoCreateAccount().getAlias().size());
        assertEquals("lazy-created account", txnBody.getCryptoCreateAccount().getMemo());
        assertEquals(
                THREE_MONTHS_IN_SECONDS,
                txnBody.getCryptoCreateAccount().getAutoRenewPeriod().getSeconds());
        assertEquals(10L, txnBody.getCryptoCreateAccount().getInitialBalance());
        assertEquals(0L, txnBody.getCryptoCreateAccount().getMaxAutomaticTokenAssociations());
    }

    @Test
    void fungibleTokenChangeAddsAutoAssociations() {
        final var balance = 10L;
        final var bytes = new byte[33];
        bytes[0] = 0x02;
        final Key key =
                Key.newBuilder().setECDSASecp256K1(ByteString.copyFrom(bytes)).build();
        final var alias = key.toByteString();
        final var result = subject.createAccount(alias, key, balance, 1);
        final var txnBody = result.build();

        assertTrue(txnBody.hasCryptoCreateAccount());
        assertEquals(AUTO_MEMO, txnBody.getCryptoCreateAccount().getMemo());
        assertEquals(
                THREE_MONTHS_IN_SECONDS,
                txnBody.getCryptoCreateAccount().getAutoRenewPeriod().getSeconds());
        assertEquals(10L, txnBody.getCryptoCreateAccount().getInitialBalance());
        assertEquals(1, txnBody.getCryptoCreateAccount().getMaxAutomaticTokenAssociations());
        assertEquals(
                key.toByteString(), txnBody.getCryptoCreateAccount().getKey().toByteString());
        assertEquals(alias, txnBody.getCryptoCreateAccount().getAlias());
    }

    @Test
    void createsExpectedAssociations() {
        final var tokens = List.of(fungible, nonFungible);
        final var associations = multiAssociation(a, tokens);

        final var result = subject.createAssociate(associations);
        final var txnBody = result.build();

        assertEquals(a, txnBody.getTokenAssociate().getAccount());
        assertEquals(tokens, txnBody.getTokenAssociate().getTokensList());
    }

    @Test
    void createsExpectedDissociations() {
        final var tokens = List.of(fungible, nonFungible);
        final var associations = Dissociation.multiDissociation(a, tokens);

        final var result = subject.createDissociate(associations);
        final var txnBody = result.build();

        assertEquals(a, txnBody.getTokenDissociate().getAccount());
        assertEquals(tokens, txnBody.getTokenDissociate().getTokensList());
    }

    @Test
    void createsExpectedNftMint() {
        final var nftMints = MintWrapper.forNonFungible(nonFungible, newMetadata);

        final var result = subject.createMint(nftMints);
        final var txnBody = result.build();

        assertEquals(nonFungible, txnBody.getTokenMint().getToken());
        assertEquals(newMetadata, txnBody.getTokenMint().getMetadataList());
    }

    @Test
    void createsExpectedNftBurn() {
        final var nftBurns = BurnWrapper.forNonFungible(nonFungible, targetSerialNos);

        final var result = subject.createBurn(nftBurns);
        final var txnBody = result.build();

        assertEquals(nonFungible, txnBody.getTokenBurn().getToken());
        assertEquals(targetSerialNos, txnBody.getTokenBurn().getSerialNumbersList());
    }

    @Test
    void createsExpectedFungibleMint() {
        final var amount = 1234L;
        final var funMints = MintWrapper.forFungible(fungible, amount);

        final var result = subject.createMint(funMints);
        final var txnBody = result.build();

        assertEquals(fungible, txnBody.getTokenMint().getToken());
        assertEquals(amount, txnBody.getTokenMint().getAmount());
    }

    @Test
    void createsExpectedFungibleBurn() {
        final var amount = 1234L;
        final var funBurns = BurnWrapper.forFungible(fungible, amount);

        final var result = subject.createBurn(funBurns);
        final var txnBody = result.build();

        assertEquals(fungible, txnBody.getTokenBurn().getToken());
        assertEquals(amount, txnBody.getTokenBurn().getAmount());
    }

    @Test
    void createsExpectedGrantKycCall() {
        final var grantWrapper = new GrantRevokeKycWrapper<>(fungible, a);
        final var result = subject.createGrantKyc(grantWrapper);
        final var txnBody = result.build();

        assertEquals(fungible, txnBody.getTokenGrantKyc().getToken());
        assertEquals(a, txnBody.getTokenGrantKyc().getAccount());
    }

    @Test
    void createsExpectedFungibleWipe() {
        final var amount = 1234L;
        final var fungibleWipe = WipeWrapper.forFungible(fungible, a, amount);

        final var result = subject.createWipe(fungibleWipe);
        final var txnBody = result.build();

        assertEquals(fungible, txnBody.getTokenWipe().getToken());
        assertEquals(amount, txnBody.getTokenWipe().getAmount());
        assertEquals(a, txnBody.getTokenWipe().getAccount());
    }

    @Test
    void createsExpectedFungibleApproveAllowance() {
        final var amount = BigInteger.ONE;
        final var allowances = new ApproveWrapper(token, receiver, amount, BigInteger.ZERO, true, false);

        final var result = subject.createFungibleApproval(allowances, senderId);
        final var txnBody = result.build();

        assertEquals(
                amount.longValue(),
                txnBody.getCryptoApproveAllowance().getTokenAllowances(0).getAmount());
        assertEquals(
                token, txnBody.getCryptoApproveAllowance().getTokenAllowances(0).getTokenId());
        final var allowance = txnBody.getCryptoApproveAllowance().getTokenAllowances(0);
        assertEquals(senderId.asGrpcAccount(), allowance.getOwner());
        assertEquals(receiver, allowance.getSpender());
    }

    @Test
    void createsExpectedNonfungibleApproveAllowanceWithOwnerAsOperator() {
        final var allowances = new ApproveWrapper(token, receiver, BigInteger.ZERO, BigInteger.ONE, false, false);
        final var ownerId = new Id(0, 0, 666);

        final var result = subject.createNonfungibleApproval(allowances, ownerId, ownerId);
        final var txnBody = result.build();

        final var allowance = txnBody.getCryptoApproveAllowance().getNftAllowances(0);
        assertEquals(token, allowance.getTokenId());
        assertEquals(receiver, allowance.getSpender());
        assertEquals(ownerId.asGrpcAccount(), allowance.getOwner());
        assertEquals(AccountID.getDefaultInstance(), allowance.getDelegatingSpender());
        assertEquals(1L, allowance.getSerialNumbers(0));
    }

    @Test
    void createsExpectedNonfungibleApproveAllowanceWithNonOwnerOperator() {
        final var allowances = new ApproveWrapper(token, receiver, BigInteger.ZERO, BigInteger.ONE, false, false);
        final var ownerId = new Id(0, 0, 666);
        final var operatorId = new Id(0, 0, 777);

        final var result = subject.createNonfungibleApproval(allowances, ownerId, operatorId);
        final var txnBody = result.build();

        final var allowance = txnBody.getCryptoApproveAllowance().getNftAllowances(0);
        assertEquals(token, allowance.getTokenId());
        assertEquals(receiver, allowance.getSpender());
        assertEquals(ownerId.asGrpcAccount(), allowance.getOwner());
        assertEquals(operatorId.asGrpcAccount(), allowance.getDelegatingSpender());
        assertEquals(1L, allowance.getSerialNumbers(0));
    }

    @Test
    void createsExpectedNonfungibleApproveAllowanceWithoutOwner() {
        final var allowances = new ApproveWrapper(token, receiver, BigInteger.ZERO, BigInteger.ONE, false, false);
        final var operatorId = new Id(0, 0, 666);

        final var result = subject.createNonfungibleApproval(allowances, null, operatorId);
        final var txnBody = result.build();

        final var allowance = txnBody.getCryptoApproveAllowance().getNftAllowances(0);
        assertEquals(token, allowance.getTokenId());
        assertEquals(receiver, allowance.getSpender());
        assertEquals(AccountID.getDefaultInstance(), allowance.getOwner());
        assertEquals(1L, allowance.getSerialNumbers(0));
    }

    @Test
    void createsDeleteAllowance() {
        final var allowances = new ApproveWrapper(token, receiver, BigInteger.ZERO, BigInteger.ONE, false, false);

        final var result = subject.createDeleteAllowance(allowances, senderId);
        final var txnBody = result.build();

        assertEquals(
                token, txnBody.getCryptoDeleteAllowance().getNftAllowances(0).getTokenId());
        assertEquals(1L, txnBody.getCryptoDeleteAllowance().getNftAllowances(0).getSerialNumbers(0));
        assertEquals(
                HTSTestsUtil.sender,
                txnBody.getCryptoDeleteAllowance().getNftAllowances(0).getOwner());
    }

    @Test
    void createsExpectedNftWipe() {
        final var nftWipe = WipeWrapper.forNonFungible(nonFungible, a, targetSerialNos);

        final var result = subject.createWipe(nftWipe);
        final var txnBody = result.build();

        assertEquals(nonFungible, txnBody.getTokenWipe().getToken());
        assertEquals(a, txnBody.getTokenWipe().getAccount());
        assertEquals(targetSerialNos, txnBody.getTokenWipe().getSerialNumbersList());
    }

    @Test
    void createsAdjustAllowanceForAllNFT() {
        final var allowances = new SetApprovalForAllWrapper(nonFungible, receiver, true);

        final var result = subject.createApproveAllowanceForAllNFT(allowances, senderId);
        final var txnBody = result.build();

        assertEquals(
                receiver,
                txnBody.getCryptoApproveAllowance().getNftAllowances(0).getSpender());
        assertEquals(
                sender, txnBody.getCryptoApproveAllowance().getNftAllowances(0).getOwner());
        assertEquals(
                nonFungible,
                txnBody.getCryptoApproveAllowance().getNftAllowances(0).getTokenId());
        assertEquals(
                BoolValue.of(true),
                txnBody.getCryptoApproveAllowance().getNftAllowances(0).getApprovedForAll());
    }

    @Test
    void createsExpectedFreeze() {
        final var freezeWrapper = TokenFreezeUnfreezeWrapper.forFreeze(fungible, a);
        final var result = subject.createFreeze(freezeWrapper);
        final var txnBody = result.build();

        assertEquals(fungible, txnBody.getTokenFreeze().getToken());
        assertEquals(a, txnBody.getTokenFreeze().getAccount());
    }

    @Test
    void createsExpectedUnfreeze() {
        final var unfreezeWrapper = TokenFreezeUnfreezeWrapper.forUnfreeze(fungible, a);
        final var result = subject.createUnfreeze(unfreezeWrapper);
        final var txnBody = result.build();

        assertEquals(fungible, txnBody.getTokenUnfreeze().getToken());
        assertEquals(a, txnBody.getTokenUnfreeze().getAccount());
    }

    @Test
    void createsExpectedPause() {
        final var pauseWrapper = new PauseWrapper(fungible);
        final var result = subject.createPause(pauseWrapper);
        final var txnBody = result.build();

        assertEquals(fungible, txnBody.getTokenPause().getToken());
    }

    @Test
    void createsExpectedUnpause() {
        final var unpauseWrapper = new UnpauseWrapper(fungible);
        final var result = subject.createUnpause(unpauseWrapper);
        final var txnBody = result.build();

        assertEquals(fungible, txnBody.getTokenUnpause().getToken());
    }

    @Test
    void createsExpectedTransactionCall() {
        final var result = subject.createTransactionCall(1, Bytes.of(1));
        final var txnBody = result.build();

        assertTrue(result.hasContractCall());
        assertEquals(1, txnBody.getContractCall().getGas());
        assertEquals(
                contractIdFromEvmAddress(
                        Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS).toArray()),
                txnBody.getContractCall().getContractID());
        assertEquals(
                ByteString.copyFrom(Bytes.of(1).toArray()),
                txnBody.getContractCall().getFunctionParameters());
    }

    @Test
    void createsExpectedCryptoTransfer() {
        final var fungibleTransfer = new FungibleTokenTransfer(secondAmount, false, fungible, b, a);

        final var result = subject.createCryptoTransfer(
                List.of(new TokenTransferWrapper(Collections.emptyList(), List.of(fungibleTransfer))));
        final var txnBody = result.build();

        final var tokenTransfers = txnBody.getCryptoTransfer().getTokenTransfersList();
        final var expFungibleTransfer = tokenTransfers.get(0);
        assertEquals(fungible, expFungibleTransfer.getToken());
        assertEquals(
                List.of(fungibleTransfer.senderAdjustment(), fungibleTransfer.receiverAdjustment()),
                expFungibleTransfer.getTransfersList());
    }

    @Test
    void mergesRepeatedTokenIds() {
        final var fungibleTransfer = new FungibleTokenTransfer(secondAmount, false, fungible, b, a);
        final var nonFungibleTransfer = new NftExchange(1L, nonFungible, a, b);
        assertFalse(nonFungibleTransfer.isApproval());

        final var result = subject.createCryptoTransfer(List.of(
                new TokenTransferWrapper(Collections.emptyList(), List.of(fungibleTransfer)),
                new TokenTransferWrapper(Collections.emptyList(), List.of(fungibleTransfer)),
                new TokenTransferWrapper(List.of(nonFungibleTransfer), Collections.emptyList())));

        final var txnBody = result.build();

        final var finalTransfers = txnBody.getCryptoTransfer().getTokenTransfersList();
        assertEquals(2, finalTransfers.size());
        final var mergedFungible = finalTransfers.get(0);
        assertEquals(fungible, mergedFungible.getToken());
        assertEquals(
                List.of(aaWith(b, -2 * secondAmount), aaWith(a, +2 * secondAmount)), mergedFungible.getTransfersList());
    }

    @Test
    void createsExpectedCryptoTransferForNFTTransfer() {
        final var nftExchange = new NftExchange(serialNo, nonFungible, a, c);

        final var result = subject.createCryptoTransfer(
                Collections.singletonList(new TokenTransferWrapper(List.of(nftExchange), Collections.emptyList())));
        final var txnBody = result.build();

        final var tokenTransfers = txnBody.getCryptoTransfer().getTokenTransfersList();
        final var expNftTransfer = tokenTransfers.get(0);
        assertEquals(nonFungible, expNftTransfer.getToken());
        assertEquals(List.of(nftExchange.asGrpc()), expNftTransfer.getNftTransfersList());
        assertEquals(1, tokenTransfers.size());
    }

    @Test
    void createsExpectedCryptoTransferForFungibleTransfer() {
        final var fungibleTransfer = new FungibleTokenTransfer(secondAmount, false, fungible, b, a);

        final var result = subject.createCryptoTransfer(Collections.singletonList(
                new TokenTransferWrapper(Collections.emptyList(), List.of(fungibleTransfer))));
        final var txnBody = result.build();

        final var tokenTransfers = txnBody.getCryptoTransfer().getTokenTransfersList();
        final var expFungibleTransfer = tokenTransfers.get(0);
        assertEquals(fungible, expFungibleTransfer.getToken());
        assertEquals(
                List.of(fungibleTransfer.senderAdjustment(), fungibleTransfer.receiverAdjustment()),
                expFungibleTransfer.getTransfersList());
        assertEquals(1, tokenTransfers.size());
    }

    @Test
    void createsExpectedCryptoTransfersForMultipleTransferWrappers() {
        final var nftExchange = new NftExchange(serialNo, nonFungible, a, c);
        final var fungibleTransfer = new FungibleTokenTransfer(secondAmount, false, fungible, b, a);

        final var result = subject.createCryptoTransfer(List.of(
                new TokenTransferWrapper(Collections.emptyList(), List.of(fungibleTransfer)),
                new TokenTransferWrapper(List.of(nftExchange), Collections.emptyList())));
        final var txnBody = result.build();

        final var tokenTransfers = txnBody.getCryptoTransfer().getTokenTransfersList();

        final var expFungibleTransfer = tokenTransfers.get(0);
        assertEquals(fungible, expFungibleTransfer.getToken());
        assertEquals(
                List.of(fungibleTransfer.senderAdjustment(), fungibleTransfer.receiverAdjustment()),
                expFungibleTransfer.getTransfersList());

        final var expNftTransfer = tokenTransfers.get(1);
        assertEquals(nonFungible, expNftTransfer.getToken());
        assertEquals(List.of(nftExchange.asGrpc()), expNftTransfer.getNftTransfersList());
    }

    @Test
    void mergesFungibleTransfersAsExpected() {
        final var source = new TokenTransferWrapper(
                        Collections.emptyList(), List.of(new FungibleTokenTransfer(1, false, fungible, a, b)))
                .asGrpcBuilder();
        final var target = new TokenTransferWrapper(
                        Collections.emptyList(), List.of(new FungibleTokenTransfer(2, false, fungible, b, c)))
                .asGrpcBuilder();

        SyntheticTxnFactory.mergeTokenTransfers(target, source);

        assertEquals(fungible, target.getToken());
        final var transfers = target.getTransfersList();
        assertEquals(List.of(aaWith(b, -1), aaWith(c, +2), aaWith(a, -1)), transfers);
    }

    @Test
    void createsExpectedCryptoTransferForFungibleAndHbarTransfer() {
        final var fungibleTransfer = new FungibleTokenTransfer(secondAmount, false, fungible, b, a);

        final var hbarTransfer = new HbarTransfer(secondAmount, false, a, b);

        final var result = subject.createCryptoTransfer(Collections.singletonList(
                new TokenTransferWrapper(Collections.emptyList(), List.of(fungibleTransfer))));
        final var resultHBar = subject.createCryptoTransferForHbar(new TransferWrapper(List.of(hbarTransfer)));

        result.mergeFrom(resultHBar);

        final var txnBody = result.build();

        final var tokenTransfers = txnBody.getCryptoTransfer().getTokenTransfersList();
        final var expFungibleTransfer = tokenTransfers.get(0);
        assertEquals(fungible, expFungibleTransfer.getToken());
        assertEquals(
                List.of(fungibleTransfer.senderAdjustment(), fungibleTransfer.receiverAdjustment()),
                expFungibleTransfer.getTransfersList());
        assertEquals(1, tokenTransfers.size());

        final var hbarTransfers = txnBody.getCryptoTransfer().getTransfers();
        assertTrue(txnBody.getCryptoTransfer().hasTransfers());
        assertEquals(
                List.of(hbarTransfer.senderAdjustment(), hbarTransfer.receiverAdjustment()),
                hbarTransfers.getAccountAmountsList());
        assertEquals(2, hbarTransfers.getAccountAmountsList().size());
    }

    @Test
    void createsExpectedCryptoTransferForNFTAndHbarTransfer() {
        final var nftExchange = new NftExchange(serialNo, nonFungible, a, c);
        final var hbarTransfer = new HbarTransfer(secondAmount, false, a, b);

        final var result = subject.createCryptoTransfer(
                Collections.singletonList(new TokenTransferWrapper(List.of(nftExchange), Collections.emptyList())));

        final var resultHBar = subject.createCryptoTransferForHbar(new TransferWrapper(List.of(hbarTransfer)));

        result.mergeFrom(resultHBar);
        final var txnBody = result.build();

        final var tokenTransfers = txnBody.getCryptoTransfer().getTokenTransfersList();
        final var expNftTransfer = tokenTransfers.get(0);
        assertEquals(nonFungible, expNftTransfer.getToken());
        assertEquals(List.of(nftExchange.asGrpc()), expNftTransfer.getNftTransfersList());
        assertEquals(1, tokenTransfers.size());

        final var hbarTransfers = txnBody.getCryptoTransfer().getTransfers();
        assertTrue(txnBody.getCryptoTransfer().hasTransfers());
        assertEquals(
                List.of(hbarTransfer.senderAdjustment(), hbarTransfer.receiverAdjustment()),
                hbarTransfers.getAccountAmountsList());
        assertEquals(2, hbarTransfers.getAccountAmountsList().size());
    }

    @Test
    void createsExpectedCryptoTransferHbarOnlyTransfer() {
        final var hbarTransfer = new HbarTransfer(secondAmount, false, a, b);

        final var result = subject.createCryptoTransfer(Collections.emptyList());
        final var resultHBar = subject.createCryptoTransferForHbar(new TransferWrapper(List.of(hbarTransfer)));

        result.mergeFrom(resultHBar);

        final var txnBody = result.build();

        final var tokenTransfers = txnBody.getCryptoTransfer().getTokenTransfersList();
        assertEquals(0, tokenTransfers.size());

        final var hbarTransfers = txnBody.getCryptoTransfer().getTransfers();
        assertTrue(txnBody.getCryptoTransfer().hasTransfers());
        assertEquals(
                List.of(hbarTransfer.senderAdjustment(), hbarTransfer.receiverAdjustment()),
                hbarTransfers.getAccountAmountsList());
        assertEquals(2, hbarTransfers.getAccountAmountsList().size());
    }

    @Test
    void createsExpectedCryptoTransferTokensOnlyTransfer() {
        final var nftExchange = new NftExchange(serialNo, nonFungible, a, c);
        final var fungibleTransfer = new FungibleTokenTransfer(secondAmount, false, fungible, b, a);

        final var result = subject.createCryptoTransfer(List.of(
                new TokenTransferWrapper(Collections.emptyList(), List.of(fungibleTransfer)),
                new TokenTransferWrapper(List.of(nftExchange), Collections.emptyList())));
        final var resultHBar = subject.createCryptoTransferForHbar(new TransferWrapper(Collections.emptyList()));

        result.mergeFrom(resultHBar);
        final var txnBody = result.build();

        final var tokenTransfers = txnBody.getCryptoTransfer().getTokenTransfersList();

        final var expFungibleTransfer = tokenTransfers.get(0);
        assertEquals(fungible, expFungibleTransfer.getToken());
        assertEquals(
                List.of(fungibleTransfer.senderAdjustment(), fungibleTransfer.receiverAdjustment()),
                expFungibleTransfer.getTransfersList());

        final var expNftTransfer = tokenTransfers.get(1);
        assertEquals(nonFungible, expNftTransfer.getToken());
        assertEquals(List.of(nftExchange.asGrpc()), expNftTransfer.getNftTransfersList());

        final var hbarTransfers = txnBody.getCryptoTransfer().getTransfers();
        assertEquals(0, hbarTransfers.getAccountAmountsList().size());
        assertFalse(txnBody.getCryptoTransfer().hasTransfers());
    }

    @Test
    void acceptsEmptyWrappers() {
        final var result = subject.createCryptoTransfer(List.of());

        final var txnBody = result.build();
        assertEquals(0, txnBody.getCryptoTransfer().getTokenTransfersCount());
    }

    @Test
    void createsExpectedTokenUpdateCallForFungible() {
        // given
        final var adminKey = new KeyValueWrapper(
                false, null, new byte[] {}, new byte[] {}, contractIdFromEvmAddress(contractAddress));
        final var multiKey = new KeyValueWrapper(
                false, contractIdFromEvmAddress(contractAddress), new byte[] {}, new byte[] {}, null);
        final var tokenUpdateWrapper = createFungibleTokenUpdateWrapperWithKeys(
                List.of(new TokenKeyWrapper(112, multiKey), new TokenKeyWrapper(1, adminKey)));
        final var result = subject.createTokenUpdate(tokenUpdateWrapper);
        final var txnBody = result.build().getTokenUpdate();

        assertEquals(HTSTestsUtil.fungible, txnBody.getToken());

        assertEquals("fungible", txnBody.getName());
        assertEquals("G", txnBody.getSymbol());
        assertEquals(account, txnBody.getTreasury());
        assertEquals("G token memo", txnBody.getMemo().getValue());
        assertEquals(1, txnBody.getExpiry().getSeconds());
        assertEquals(2, txnBody.getAutoRenewPeriod().getSeconds());
        assertTrue(txnBody.hasAutoRenewAccount());
    }

    @Test
    void createsExpectedTokenUpdateCallForNonFungible() {
        // given
        final var ComplexKey = new KeyValueWrapper(
                false, null, new byte[] {}, new byte[] {}, contractIdFromEvmAddress(contractAddress));
        final var multiKey = new KeyValueWrapper(
                false, contractIdFromEvmAddress(contractAddress), new byte[] {}, new byte[] {}, null);
        final var wrapper = createNonFungibleTokenUpdateWrapperWithKeys(List.of(
                new TokenKeyWrapper(112, multiKey),
                new TokenKeyWrapper(2, ComplexKey),
                new TokenKeyWrapper(4, ComplexKey),
                new TokenKeyWrapper(8, ComplexKey)));

        // when
        final var result = subject.createTokenUpdate(wrapper);
        final var txnBody = result.build().getTokenUpdate();

        // then

        assertEquals(0, txnBody.getExpiry().getSeconds());
        assertEquals(0, txnBody.getAutoRenewPeriod().getSeconds());
        assertFalse(txnBody.hasAutoRenewAccount());
    }

    @Test
    void createsExpectedUpdateTokenExpiryInfoWithZeroExpiry() {
        final var updateExpiryInfo = new TokenUpdateExpiryInfoWrapper(token, new TokenExpiryWrapper(0L, payer, 555L));

        final var result = subject.createTokenUpdateExpiryInfo(updateExpiryInfo);
        final var txnBody = result.build();

        assertEquals(token, txnBody.getTokenUpdate().getToken());
        assertEquals(0L, txnBody.getTokenUpdate().getExpiry().getSeconds());
        assertEquals(payer, txnBody.getTokenUpdate().getAutoRenewAccount());
        assertEquals(555L, txnBody.getTokenUpdate().getAutoRenewPeriod().getSeconds());
    }

    @Test
    void createsExpectedUpdateTokenExpiryInfoWithZeroAutoRenewPeriod() {
        final var updateExpiryInfo = new TokenUpdateExpiryInfoWrapper(token, new TokenExpiryWrapper(442L, payer, 0L));

        final var result = subject.createTokenUpdateExpiryInfo(updateExpiryInfo);
        final var txnBody = result.build();

        assertEquals(token, txnBody.getTokenUpdate().getToken());
        assertEquals(442L, txnBody.getTokenUpdate().getExpiry().getSeconds());
        assertEquals(payer, txnBody.getTokenUpdate().getAutoRenewAccount());
        assertEquals(0L, txnBody.getTokenUpdate().getAutoRenewPeriod().getSeconds());
    }

    @Test
    void createsExpectedUpdateTokenExpiryInfoWithNoAutoRenewAccount() {
        final var updateExpiryInfo = new TokenUpdateExpiryInfoWrapper(token, new TokenExpiryWrapper(442L, null, 555L));

        final var result = subject.createTokenUpdateExpiryInfo(updateExpiryInfo);
        final var txnBody = result.build();

        assertEquals(token, txnBody.getTokenUpdate().getToken());
        assertEquals(442L, txnBody.getTokenUpdate().getExpiry().getSeconds());
        assertTrue(txnBody.getTokenUpdate().getAutoRenewAccount().toString().isEmpty());
        assertEquals(555L, txnBody.getTokenUpdate().getAutoRenewPeriod().getSeconds());
    }

    private static AccountAmount aaWith(final AccountID accountID, final long amount) {
        return AccountAmount.newBuilder()
                .setAccountID(accountID)
                .setAmount(amount)
                .build();
    }
}
