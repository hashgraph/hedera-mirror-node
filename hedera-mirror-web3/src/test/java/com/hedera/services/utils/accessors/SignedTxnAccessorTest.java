/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.utils.accessors;

import static com.hedera.services.fees.usage.token.TokenOpsUsageUtilsTest.A_COMPLEX_KEY;
import static com.hedera.services.fees.usage.token.TokenOpsUsageUtilsTest.A_KEY_LIST;
import static com.hedera.services.fees.usage.token.TokenOpsUsageUtilsTest.A_THRESHOLD_KEY;
import static com.hedera.services.fees.usage.token.TokenOpsUsageUtilsTest.B_COMPLEX_KEY;
import static com.hedera.services.fees.usage.token.TokenOpsUsageUtilsTest.C_COMPLEX_KEY;
import static com.hedera.services.utils.IdUtils.asAccount;
import static com.hedera.services.utils.IdUtils.asToken;
import static com.hedera.services.utils.TxnUtils.buildTransactionFrom;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.Int32Value;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.StringValue;
import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.services.utils.IdUtils;
import com.hedera.services.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenBurnTransactionBody;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TokenPauseTransactionBody;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TokenUnpauseTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionOrBuilder;
import com.hederahashgraph.api.proto.java.TransferList;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class SignedTxnAccessorTest {
    private static final String memo = "Eternal sunshine of the spotless mind";
    private static final String zeroByteMemo = "Eternal s\u0000nshine of the spotless mind";
    private static final byte[] memoUtf8Bytes = memo.getBytes();
    private static final byte[] zeroByteMemoUtf8Bytes = zeroByteMemo.getBytes();

    private static final AccountID spender1 = asAccount("0.0.1000");
    private static final AccountID owner = asAccount("0.0.1001");
    private static final TokenID token1 = asToken("0.0.2000");
    private static final TokenID token2 = asToken("0.0.3000");
    private static final CryptoAllowance cryptoAllowance1 =
            CryptoAllowance.newBuilder().setSpender(spender1).setAmount(10L).build();
    private static final TokenAllowance tokenAllowance1 = TokenAllowance.newBuilder()
            .setSpender(spender1)
            .setAmount(10L)
            .setTokenId(token1)
            .build();
    private static final NftAllowance nftAllowance1 = NftAllowance.newBuilder()
            .setSpender(spender1)
            .setTokenId(token2)
            .setApprovedForAll(BoolValue.of(false))
            .addAllSerialNumbers(List.of(1L, 10L))
            .build();

    private static final NftRemoveAllowance nftRemoveAllowance = NftRemoveAllowance.newBuilder()
            .setOwner(owner)
            .setTokenId(token2)
            .addAllSerialNumbers(List.of(1L, 10L))
            .build();

    private static final SignatureMap expectedMap = SignatureMap.newBuilder()
            .addSigPair(SignaturePair.newBuilder()
                    .setPubKeyPrefix(ByteString.copyFromUtf8("f"))
                    .setEd25519(ByteString.copyFromUtf8("irst")))
            .addSigPair(SignaturePair.newBuilder()
                    .setPubKeyPrefix(ByteString.copyFromUtf8("s"))
                    .setEd25519(ByteString.copyFromUtf8("econd")))
            .build();

    @Test
    void uncheckedPropagatesIaeOnNonsense() {
        final var nonsenseTxn = buildTransactionFrom(ByteString.copyFromUtf8("NONSENSE"));

        assertThrows(IllegalArgumentException.class, () -> SignedTxnAccessor.uncheckedFrom(nonsenseTxn));
    }

    @Test
    void parsesLegacyCorrectly() throws Exception {
        final Key aPrimitiveKey = Key.newBuilder()
                .setEd25519(ByteString.copyFromUtf8("01234567890123456789012345678901"))
                .build();
        final ByteString aNewAlias = aPrimitiveKey.toByteString();
        final MirrorEvmContractAliases aliasManager = mock(MirrorEvmContractAliases.class);
        given(aliasManager.resolveForEvm(any())).willReturn(Address.ZERO);

        final long offeredFee = 100_000_000L;
        var xferNoAliases = RequestBuilderUtils.getCryptoTransferRequest(
                1234l,
                0l,
                0l,
                3l,
                0l,
                0l,
                offeredFee,
                Timestamp.getDefaultInstance(),
                Duration.getDefaultInstance(),
                false,
                zeroByteMemo,
                5678l,
                -70000l,
                5679l,
                70000l);
        xferNoAliases = xferNoAliases.toBuilder().setSigMap(expectedMap).build();
        var xferWithAutoCreation = RequestBuilderUtils.getHbarCryptoTransferRequestToAlias(
                1234l,
                0l,
                0l,
                3l,
                0l,
                0l,
                offeredFee,
                Timestamp.getDefaultInstance(),
                Duration.getDefaultInstance(),
                false,
                zeroByteMemo,
                5678l,
                -70000l,
                aNewAlias,
                70000l);
        xferWithAutoCreation =
                xferWithAutoCreation.toBuilder().setSigMap(expectedMap).build();
        var xferWithAliasesNoAutoCreation = RequestBuilderUtils.getTokenTransferRequestToAlias(
                1234l,
                0l,
                0l,
                3l,
                0l,
                0l,
                offeredFee,
                Timestamp.getDefaultInstance(),
                Duration.getDefaultInstance(),
                false,
                zeroByteMemo,
                5678l,
                5555l,
                -70000l,
                ByteString.copyFromUtf8("aaaa"),
                70000l);
        xferWithAliasesNoAutoCreation =
                xferWithAliasesNoAutoCreation.toBuilder().setSigMap(expectedMap).build();
        final var body = TransactionBody.parseFrom(extractTransactionBodyByteString(xferNoAliases));

        final var signedTransaction = TxnUtils.signedTransactionFrom(body, expectedMap);
        final var newTransaction = buildTransactionFrom(signedTransaction.toByteString());
        var accessor = SignedTxnAccessor.uncheckedFrom(newTransaction);
        final var txnUsageMeta = accessor.baseUsageMeta();

        assertEquals(newTransaction, accessor.getSignedTxnWrapper());
        assertEquals(body, accessor.getTxn());
        assertArrayEquals(body.toByteArray(), accessor.getTxnBytes());
        assertEquals(body.getTransactionID(), accessor.getTxnId());
        assertEquals(1234l, accessor.getPayer().getAccountNum());
        assertEquals(HederaFunctionality.CryptoTransfer, accessor.getFunction());
        assertArrayEquals(noThrowSha384HashOf(signedTransaction.toByteArray()), accessor.getHash());
        assertEquals(expectedMap, accessor.getSigMap());
        assertArrayEquals(zeroByteMemoUtf8Bytes, accessor.getMemoUtf8Bytes());
        assertEquals(zeroByteMemo, accessor.getMemo());
        assertEquals(memoUtf8Bytes.length, txnUsageMeta.memoUtf8Bytes());
    }

    @Test
    void detectsCommonTokenBurnSubtypeFromGrpcSyntax() {
        final var op = TokenBurnTransactionBody.newBuilder().setAmount(1_234).build();
        final var txn = buildTransactionFrom(
                TransactionBody.newBuilder().setTokenBurn(op).build());

        final var subject = SignedTxnAccessor.uncheckedFrom(txn);

        assertEquals(TOKEN_FUNGIBLE_COMMON, subject.getSubType());
    }

    @Test
    void detectsUniqueTokenBurnSubtypeFromGrpcSyntax() {
        final var op = TokenBurnTransactionBody.newBuilder()
                .addAllSerialNumbers(List.of(1L, 2L, 3L))
                .build();
        final var txn = buildTransactionFrom(
                TransactionBody.newBuilder().setTokenBurn(op).build());

        final var subject = SignedTxnAccessor.uncheckedFrom(txn);

        assertEquals(TOKEN_NON_FUNGIBLE_UNIQUE, subject.getSubType());
    }

    @Test
    void detectsCommonTokenMintSubtypeFromGrpcSyntax() {
        final var op = TokenMintTransactionBody.newBuilder().setAmount(1_234).build();
        final var txn = buildTransactionFrom(
                TransactionBody.newBuilder().setTokenMint(op).build());

        final var subject = SignedTxnAccessor.uncheckedFrom(txn);

        assertEquals(TOKEN_FUNGIBLE_COMMON, subject.getSubType());
    }

    @Test
    void detectsUniqueTokenMintSubtypeFromGrpcSyntax() {
        final var op = TokenMintTransactionBody.newBuilder()
                .addAllMetadata(List.of(ByteString.copyFromUtf8("STANDARD")))
                .build();
        final var txn = buildTransactionFrom(
                TransactionBody.newBuilder().setTokenMint(op).build());

        final var subject = SignedTxnAccessor.uncheckedFrom(txn);

        assertEquals(TOKEN_NON_FUNGIBLE_UNIQUE, subject.getSubType());
    }

    @Test
    void fetchesSubTypeAsExpected() throws InvalidProtocolBufferException {
        final var nftTransfers = TokenTransferList.newBuilder()
                .setToken(anId)
                .addNftTransfers(NftTransfer.newBuilder()
                        .setSenderAccountID(a)
                        .setReceiverAccountID(b)
                        .setSerialNumber(1))
                .build();
        final var fungibleTokenXfers = TokenTransferList.newBuilder()
                .setToken(anotherId)
                .addAllTransfers(List.of(adjustFrom(a, -50), adjustFrom(b, 25), adjustFrom(c, 25)))
                .build();

        var txn = buildTokenTransferTxn(nftTransfers);
        var subject = SignedTxnAccessor.from(txn.toByteArray());
        assertEquals(
                SubType.TOKEN_NON_FUNGIBLE_UNIQUE, subject.availXferUsageMeta().getSubType());
        assertEquals(subject.availXferUsageMeta().getSubType(), subject.getSubType());

        // set customFee
        var xferUsageMeta = subject.availXferUsageMeta();
        xferUsageMeta.setCustomFeeHbarTransfers(1);
        assertEquals(SubType.TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES, subject.getSubType());
        xferUsageMeta.setCustomFeeHbarTransfers(0);

        txn = buildTokenTransferTxn(fungibleTokenXfers);
        subject = SignedTxnAccessor.from(txn.toByteArray(), txn);
        assertEquals(TOKEN_FUNGIBLE_COMMON, subject.availXferUsageMeta().getSubType());
        assertEquals(subject.availXferUsageMeta().getSubType(), subject.getSubType());

        // set customFee
        xferUsageMeta = subject.availXferUsageMeta();
        xferUsageMeta.setCustomFeeTokenTransfers(1);
        assertEquals(SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES, subject.getSubType());
        xferUsageMeta.setCustomFeeTokenTransfers(0);

        txn = buildDefaultCryptoCreateTxn();
        subject = SignedTxnAccessor.from(txn.toByteArray(), txn);
        assertEquals(SubType.DEFAULT, subject.getSubType());
    }

    @Test
    void understandsFullXferUsageIncTokens() {
        final var txn = buildTransactionFrom(tokenXfers());
        final var subject = SignedTxnAccessor.uncheckedFrom(txn);

        final var xferMeta = subject.availXferUsageMeta();

        assertEquals(1, xferMeta.getTokenMultiplier());
        assertEquals(3, xferMeta.getNumTokensInvolved());
        assertEquals(7, xferMeta.getNumFungibleTokenTransfers());
    }

    @Test
    void rejectsRequestForMetaIfNotAvail() {
        final var txn = buildDefaultCryptoCreateTxn();

        final var subject = SignedTxnAccessor.uncheckedFrom(txn);

        assertEquals(SubType.DEFAULT, subject.getSubType());
        assertThrows(IllegalStateException.class, subject::availXferUsageMeta);
    }

    @Test
    void parseNewTransactionCorrectly() throws Exception {
        final var transaction = RequestBuilderUtils.getCryptoTransferRequest(
                1234l,
                0l,
                0l,
                3l,
                0l,
                0l,
                100_000_000l,
                Timestamp.getDefaultInstance(),
                Duration.getDefaultInstance(),
                false,
                memo,
                5678l,
                -70000l,
                5679l,
                70000l);
        final var body = extractTransactionBody(transaction);
        final var signedTransaction = TxnUtils.signedTransactionFrom(body, expectedMap);
        final var newTransaction = buildTransactionFrom(signedTransaction.toByteString());
        final var accessor = SignedTxnAccessor.uncheckedFrom(newTransaction);

        assertEquals(newTransaction, accessor.getSignedTxnWrapper());
        assertEquals(body, accessor.getTxn());
        assertArrayEquals(body.toByteArray(), accessor.getTxnBytes());
        assertEquals(body.getTransactionID(), accessor.getTxnId());
        assertEquals(1234l, accessor.getPayer().getAccountNum());
        assertEquals(HederaFunctionality.CryptoTransfer, accessor.getFunction());
        assertArrayEquals(noThrowSha384HashOf(signedTransaction.toByteArray()), accessor.getHash());
        assertEquals(expectedMap, accessor.getSigMap());
        assertArrayEquals(memoUtf8Bytes, accessor.getMemoUtf8Bytes());
        assertEquals(memo, accessor.getMemo());
    }

    @Test
    void registersNoneOnMalformedCreation() throws InvalidProtocolBufferException {
        final var xferWithTopLevelBodyBytes = RequestBuilderUtils.getCryptoTransferRequest(
                1234l,
                0l,
                0l,
                3l,
                0l,
                0l,
                100_000_000l,
                Timestamp.getDefaultInstance(),
                Duration.getDefaultInstance(),
                false,
                "test memo",
                5678l,
                -70000l,
                5679l,
                70000l);
        final var signedTxn = SignedTransaction.parseFrom(xferWithTopLevelBodyBytes.getSignedTransactionBytes());
        final var body = TransactionBody.parseFrom(signedTxn.getBodyBytes());

        final var confusedTxn = Transaction.parseFrom(body.toByteArray());

        final var confusedAccessor = SignedTxnAccessor.uncheckedFrom(confusedTxn);

        assertEquals(HederaFunctionality.NONE, confusedAccessor.getFunction());
    }

    @Test
    void setTokenCreateUsageMetaWorks() {
        final var txn = signedTokenCreateTxn();
        final var accessor = SignedTxnAccessor.uncheckedFrom(txn);
        final var spanMapAccessor = accessor.getSpanMapAccessor();

        final var expandedMeta = spanMapAccessor.getTokenCreateMeta(accessor);

        assertEquals(0, expandedMeta.getNftsTransfers());
        assertEquals(1, expandedMeta.getFungibleNumTransfers());
        assertEquals(1, expandedMeta.getNumTokens());
        assertEquals(1070, expandedMeta.getBaseSize());
        assertEquals(TOKEN_FUNGIBLE_COMMON, accessor.getSubType());
    }

    @Test
    void setTokenPauseUsageMetaWorks() {
        final var op = TokenPauseTransactionBody.newBuilder()
                .setToken(TokenID.newBuilder().setTokenNum(123).build());
        final var txnBody = TransactionBody.newBuilder()
                .setTransactionID(TransactionID.newBuilder()
                        .setTransactionValidStart(Timestamp.newBuilder().setSeconds(now)))
                .setTokenPause(op)
                .build();
        final var txn = buildTransactionFrom(txnBody);
        final var accessor = SignedTxnAccessor.uncheckedFrom(txn);
        final var spanMapAccessor = accessor.getSpanMapAccessor();

        final var expandedMeta = spanMapAccessor.getTokenPauseMeta(accessor);

        assertEquals(24, expandedMeta.getBpt());
    }

    @Test
    void setTokenUnpauseUsageMetaWorks() {
        final var op = TokenUnpauseTransactionBody.newBuilder()
                .setToken(TokenID.newBuilder().setTokenNum(123).build());
        final var txnBody = TransactionBody.newBuilder()
                .setTransactionID(TransactionID.newBuilder()
                        .setTransactionValidStart(Timestamp.newBuilder().setSeconds(now)))
                .setTokenUnpause(op)
                .build();
        final var txn = buildTransactionFrom(txnBody);
        final var accessor = SignedTxnAccessor.uncheckedFrom(txn);
        final var spanMapAccessor = accessor.getSpanMapAccessor();

        final var expandedMeta = spanMapAccessor.getTokenUnpauseMeta(accessor);

        assertEquals(24, expandedMeta.getBpt());
    }

    @Test
    void setCryptoCreateUsageMetaWorks() {
        final var txn = signedCryptoCreateTxn();
        final var accessor = SignedTxnAccessor.uncheckedFrom(txn);
        final var spanMapAccessor = accessor.getSpanMapAccessor();

        final var expandedMeta = spanMapAccessor.getCryptoCreateMeta(accessor);

        assertEquals(137, expandedMeta.getBaseSize());
        assertEquals(autoRenewPeriod, expandedMeta.getLifeTime());
        assertEquals(10, expandedMeta.getMaxAutomaticAssociations());
    }

    @Test
    void setCryptoUpdateUsageMetaWorks() {
        final var txn = signedCryptoUpdateTxn();
        final var accessor = SignedTxnAccessor.uncheckedFrom(txn);
        final var spanMapAccessor = accessor.getSpanMapAccessor();

        final var expandedMeta = spanMapAccessor.getCryptoUpdateMeta(accessor);

        assertEquals(100, expandedMeta.getKeyBytesUsed());
        assertEquals(197, expandedMeta.getMsgBytesUsed());
        assertEquals(now, expandedMeta.getEffectiveNow());
        assertEquals(now + autoRenewPeriod, expandedMeta.getExpiry());
        assertEquals(memo.getBytes().length, expandedMeta.getMemoSize());
        assertEquals(25, expandedMeta.getMaxAutomaticAssociations());
        assertTrue(expandedMeta.hasProxy());
    }

    @Test
    void setCryptoApproveUsageMetaWorks() {
        final var txn = signedCryptoApproveTxn();
        final var accessor = SignedTxnAccessor.uncheckedFrom(txn);
        final var spanMapAccessor = accessor.getSpanMapAccessor();

        final var expandedMeta = spanMapAccessor.getCryptoApproveMeta(accessor);

        assertEquals(128, expandedMeta.getMsgBytesUsed());
        assertEquals(now, expandedMeta.getEffectiveNow());
    }

    @Test
    void setCryptoDeleteAllowanceUsageMetaWorks() {
        final var txn = signedCryptoDeleteAllowanceTxn();
        final var accessor = SignedTxnAccessor.uncheckedFrom(txn);
        final var spanMapAccessor = accessor.getSpanMapAccessor();

        final var expandedMeta = spanMapAccessor.getCryptoDeleteAllowanceMeta(accessor);

        assertEquals(64, expandedMeta.getMsgBytesUsed());
        assertEquals(now, expandedMeta.getEffectiveNow());
    }

    private Transaction signedCryptoCreateTxn() {
        return buildTransactionFrom(cryptoCreateOp());
    }

    private Transaction signedCryptoUpdateTxn() {
        return buildTransactionFrom(cryptoUpdateOp());
    }

    private Transaction signedCryptoApproveTxn() {
        return buildTransactionFrom(cryptoApproveOp());
    }

    private Transaction signedCryptoDeleteAllowanceTxn() {
        return buildTransactionFrom(cryptoDeleteAllowanceOp());
    }

    private TransactionBody cryptoCreateOp() {
        final var op = CryptoCreateTransactionBody.newBuilder()
                .setMemo(memo)
                .setAutoRenewPeriod(Duration.newBuilder().setSeconds(autoRenewPeriod))
                .setKey(adminKey)
                .setMaxAutomaticTokenAssociations(10);
        return TransactionBody.newBuilder()
                .setTransactionID(TransactionID.newBuilder()
                        .setTransactionValidStart(Timestamp.newBuilder().setSeconds(now)))
                .setCryptoCreateAccount(op)
                .build();
    }

    private TransactionBody cryptoUpdateOp() {
        final var op = CryptoUpdateTransactionBody.newBuilder()
                .setExpirationTime(Timestamp.newBuilder().setSeconds(now + autoRenewPeriod))
                .setProxyAccountID(autoRenewAccount)
                .setMemo(StringValue.newBuilder().setValue(memo))
                .setMaxAutomaticTokenAssociations(Int32Value.of(25))
                .setKey(adminKey);
        return TransactionBody.newBuilder()
                .setTransactionID(TransactionID.newBuilder()
                        .setTransactionValidStart(Timestamp.newBuilder().setSeconds(now)))
                .setCryptoUpdateAccount(op)
                .build();
    }

    private TransactionBody cryptoApproveOp() {
        final var op = CryptoApproveAllowanceTransactionBody.newBuilder()
                .addAllCryptoAllowances(List.of(cryptoAllowance1))
                .addAllTokenAllowances(List.of(tokenAllowance1))
                .addAllNftAllowances(List.of(nftAllowance1))
                .build();
        return TransactionBody.newBuilder()
                .setTransactionID(TransactionID.newBuilder()
                        .setTransactionValidStart(Timestamp.newBuilder().setSeconds(now)))
                .setCryptoApproveAllowance(op)
                .build();
    }

    private TransactionBody cryptoDeleteAllowanceOp() {
        final var op = CryptoDeleteAllowanceTransactionBody.newBuilder()
                .addAllNftAllowances(List.of(nftRemoveAllowance))
                .build();
        return TransactionBody.newBuilder()
                .setTransactionID(TransactionID.newBuilder()
                        .setTransactionValidStart(Timestamp.newBuilder().setSeconds(now)))
                .setCryptoDeleteAllowance(op)
                .build();
    }

    private Transaction buildTokenTransferTxn(final TokenTransferList tokenTransferList) {
        final var op = CryptoTransferTransactionBody.newBuilder()
                .addTokenTransfers(tokenTransferList)
                .build();
        final var txnBody = TransactionBody.newBuilder()
                .setMemo(memo)
                .setTransactionID(TransactionID.newBuilder()
                        .setTransactionValidStart(Timestamp.newBuilder().setSeconds(now)))
                .setCryptoTransfer(op)
                .build();

        return buildTransactionFrom(txnBody);
    }

    private Transaction buildDefaultCryptoCreateTxn() {
        final var txnBody = TransactionBody.newBuilder()
                .setCryptoCreateAccount(CryptoCreateTransactionBody.getDefaultInstance())
                .build();

        return buildTransactionFrom(txnBody);
    }

    private TransactionBody tokenXfers() {
        final var hbarAdjusts = TransferList.newBuilder()
                .addAccountAmounts(adjustFrom(a, -100))
                .addAccountAmounts(adjustFrom(b, 50))
                .addAccountAmounts(adjustFrom(c, 50))
                .build();
        final var op = CryptoTransferTransactionBody.newBuilder()
                .setTransfers(hbarAdjusts)
                .addTokenTransfers(TokenTransferList.newBuilder()
                        .setToken(anotherId)
                        .addAllTransfers(List.of(adjustFrom(a, -50), adjustFrom(b, 25), adjustFrom(c, 25))))
                .addTokenTransfers(TokenTransferList.newBuilder()
                        .setToken(anId)
                        .addAllTransfers(List.of(adjustFrom(b, -100), adjustFrom(c, 100))))
                .addTokenTransfers(TokenTransferList.newBuilder()
                        .setToken(yetAnotherId)
                        .addAllTransfers(List.of(adjustFrom(a, -15), adjustFrom(b, 15))))
                .build();

        return TransactionBody.newBuilder()
                .setMemo(memo)
                .setTransactionID(TransactionID.newBuilder()
                        .setTransactionValidStart(Timestamp.newBuilder().setSeconds(now)))
                .setCryptoTransfer(op)
                .build();
    }

    private AccountAmount adjustFrom(final AccountID account, final long amount) {
        return AccountAmount.newBuilder()
                .setAmount(amount)
                .setAccountID(account)
                .build();
    }

    private Transaction signedTokenCreateTxn() {
        return buildTransactionFrom(givenAutoRenewBasedOp());
    }

    private TransactionBody givenAutoRenewBasedOp() {
        final var op = TokenCreateTransactionBody.newBuilder()
                .setAutoRenewAccount(autoRenewAccount)
                .setMemo(memo)
                .setAutoRenewPeriod(Duration.newBuilder().setSeconds(autoRenewPeriod))
                .setSymbol(symbol)
                .setName(name)
                .setKycKey(kycKey)
                .setAdminKey(adminKey)
                .setFreezeKey(freezeKey)
                .setSupplyKey(supplyKey)
                .setWipeKey(wipeKey)
                .setInitialSupply(1);
        final var txn = TransactionBody.newBuilder()
                .setTransactionID(TransactionID.newBuilder()
                        .setTransactionValidStart(Timestamp.newBuilder().setSeconds(now)))
                .setTokenCreation(op)
                .build();
        return txn;
    }

    private static final Key kycKey = A_COMPLEX_KEY;
    private static final Key adminKey = A_THRESHOLD_KEY;
    private static final Key freezeKey = A_KEY_LIST;
    private static final Key supplyKey = B_COMPLEX_KEY;
    private static final Key wipeKey = C_COMPLEX_KEY;
    private static final long autoRenewPeriod = 1_234_567L;
    private static final String symbol = "ABCDEFGH";
    private static final String name = "WhyWhyWHy";
    private static final AccountID autoRenewAccount = IdUtils.asAccount("0.0.75231");

    private static final long now = 1_234_567L;
    private static final AccountID a = asAccount("1.2.3");
    private static final AccountID b = asAccount("2.3.4");
    private static final AccountID c = asAccount("3.4.5");
    private static final TokenID anId = IdUtils.asToken("0.0.75231");
    private static final TokenID anotherId = IdUtils.asToken("0.0.75232");
    private static final TokenID yetAnotherId = IdUtils.asToken("0.0.75233");

    static ByteString extractTransactionBodyByteString(final TransactionOrBuilder transaction)
            throws InvalidProtocolBufferException {
        final var signedTransactionBytes = transaction.getSignedTransactionBytes();
        if (!signedTransactionBytes.isEmpty()) {
            return SignedTransaction.parseFrom(signedTransactionBytes).getBodyBytes();
        }
        return transaction.getBodyBytes();
    }

    static byte[] noThrowSha384HashOf(final byte[] byteArray) {
        try {
            return MessageDigest.getInstance("SHA-384").digest(byteArray);
        } catch (final NoSuchAlgorithmException fatal) {
            throw new IllegalStateException(fatal);
        }
    }

    static TransactionBody extractTransactionBody(final TransactionOrBuilder transaction)
            throws InvalidProtocolBufferException {
        return TransactionBody.parseFrom(extractTransactionBodyByteString(transaction));
    }
}
