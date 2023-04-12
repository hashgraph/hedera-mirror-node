/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.services.utils;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.EthereumTransactionBody;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransferList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;

public class TxnUtils {
    public static com.swirlds.common.system.transaction.Transaction mockTransaction(final byte[] contents) {
        throw new AssertionError("Not implemented");
    }

    public static TransferList withAdjustments(AccountID a, long A, AccountID b, long B, AccountID c, long C) {
        return TransferList.newBuilder()
                .addAccountAmounts(
                        AccountAmount.newBuilder().setAccountID(a).setAmount(A).build())
                .addAccountAmounts(
                        AccountAmount.newBuilder().setAccountID(b).setAmount(B).build())
                .addAccountAmounts(
                        AccountAmount.newBuilder().setAccountID(c).setAmount(C).build())
                .build();
    }

    public static TransferList withAdjustments(
            AccountID a, long A, AccountID b, long B, AccountID c, long C, AccountID d, long D) {
        return TransferList.newBuilder()
                .addAccountAmounts(
                        AccountAmount.newBuilder().setAccountID(a).setAmount(A).build())
                .addAccountAmounts(
                        AccountAmount.newBuilder().setAccountID(b).setAmount(B).build())
                .addAccountAmounts(
                        AccountAmount.newBuilder().setAccountID(c).setAmount(C).build())
                .addAccountAmounts(
                        AccountAmount.newBuilder().setAccountID(d).setAmount(D).build())
                .build();
    }

    public static TransferList withAllowanceAdjustments(
            AccountID a,
            long A,
            boolean isAllowedA,
            AccountID b,
            long B,
            boolean isAllowedB,
            AccountID c,
            long C,
            boolean isAllowedC,
            AccountID d,
            long D,
            boolean isAllowedD) {
        return TransferList.newBuilder()
                .addAccountAmounts(AccountAmount.newBuilder()
                        .setAccountID(a)
                        .setAmount(A)
                        .setIsApproval(isAllowedA)
                        .build())
                .addAccountAmounts(AccountAmount.newBuilder()
                        .setAccountID(b)
                        .setAmount(B)
                        .setIsApproval(isAllowedB)
                        .build())
                .addAccountAmounts(AccountAmount.newBuilder()
                        .setAccountID(c)
                        .setAmount(C)
                        .setIsApproval(isAllowedC)
                        .build())
                .addAccountAmounts(AccountAmount.newBuilder()
                        .setAccountID(d)
                        .setAmount(D)
                        .setIsApproval(isAllowedD)
                        .build())
                .build();
    }

    public static TokenTransferList withNftAdjustments(
            TokenID a,
            AccountID aSenderId,
            AccountID aReceiverId,
            Long aSerialNumber,
            AccountID bSenderId,
            AccountID bReceiverId,
            Long bSerialNumber,
            AccountID cSenderId,
            AccountID cReceiverId,
            Long cSerialNumber) {
        return TokenTransferList.newBuilder()
                .setToken(a)
                .addNftTransfers(NftTransfer.newBuilder()
                        .setSenderAccountID(aSenderId)
                        .setReceiverAccountID(aReceiverId)
                        .setSerialNumber(aSerialNumber))
                .addNftTransfers(NftTransfer.newBuilder()
                        .setSenderAccountID(bSenderId)
                        .setReceiverAccountID(bReceiverId)
                        .setSerialNumber(bSerialNumber))
                .addNftTransfers(NftTransfer.newBuilder()
                        .setSenderAccountID(cSenderId)
                        .setReceiverAccountID(cReceiverId)
                        .setSerialNumber(cSerialNumber))
                .build();
    }

    public static List<TokenTransferList> withTokenAdjustments(
            TokenID a, AccountID aId, long A, TokenID b, AccountID bId, long B, TokenID c, AccountID cId, long C) {
        return List.of(
                TokenTransferList.newBuilder()
                        .setToken(a)
                        .addTransfers(AccountAmount.newBuilder()
                                .setAccountID(aId)
                                .setAmount(A)
                                .build())
                        .build(),
                TokenTransferList.newBuilder()
                        .setToken(b)
                        .addTransfers(AccountAmount.newBuilder()
                                .setAccountID(bId)
                                .setAmount(B)
                                .build())
                        .build(),
                TokenTransferList.newBuilder()
                        .setToken(c)
                        .addTransfers(AccountAmount.newBuilder()
                                .setAccountID(cId)
                                .setAmount(C)
                                .build())
                        .build());
    }

    public static List<TokenTransferList> withTokenAdjustments(
            TokenID a,
            AccountID aId,
            long A,
            TokenID b,
            AccountID bId,
            long B,
            TokenID c,
            AccountID cId,
            long C,
            TokenID d,
            AccountID dId,
            long D) {
        return List.of(
                TokenTransferList.newBuilder()
                        .setToken(a)
                        .addTransfers(AccountAmount.newBuilder()
                                .setAccountID(aId)
                                .setAmount(A)
                                .build())
                        .build(),
                TokenTransferList.newBuilder()
                        .setToken(b)
                        .addTransfers(AccountAmount.newBuilder()
                                .setAccountID(bId)
                                .setAmount(B)
                                .build())
                        .addTransfers(AccountAmount.newBuilder()
                                .setAccountID(cId)
                                .setAmount(C)
                                .build())
                        .addTransfers(AccountAmount.newBuilder()
                                .setAccountID(aId)
                                .setAmount(A)
                                .build())
                        .build(),
                TokenTransferList.newBuilder()
                        .setToken(c)
                        .addTransfers(AccountAmount.newBuilder()
                                .setAccountID(cId)
                                .setAmount(C)
                                .build())
                        .build(),
                TokenTransferList.newBuilder()
                        .setToken(d)
                        .addTransfers(AccountAmount.newBuilder()
                                .setAccountID(dId)
                                .setAmount(D)
                                .build())
                        .build());
    }

    public static List<TokenTransferList> withTokenAdjustments(TokenID a, TokenID b) {
        return List.of(
                TokenTransferList.newBuilder().setToken(a).build(),
                TokenTransferList.newBuilder().setToken(b).build());
    }

    public static byte[] randomUtf8Bytes(int n) {
        byte[] data = new byte[n];
        int i = 0;
        while (i < n) {
            byte[] rnd = UUID.randomUUID().toString().getBytes();
            System.arraycopy(rnd, 0, data, i, Math.min(rnd.length, n - 1 - i));
            i += rnd.length;
        }
        return data;
    }

    public static String random384BitBinaryText() {
        Random rand = new Random();
        // Use to collect result
        String result = "";
        for (int i = 0; i < 384; ++i) {
            // Collect the random number
            result = (Math.abs(rand.nextInt() % 2)) + result;
        }
        return result;
    }

    public static ByteString randomUtf8ByteString(int n) {
        return ByteString.copyFrom(randomUtf8Bytes(n));
    }

    public static Timestamp timestampFrom(long secs, int nanos) {
        return Timestamp.newBuilder().setSeconds(secs).setNanos(nanos).build();
    }

    public static void assertFailsWith(final Runnable something, final ResponseCodeEnum status) {
        final var ex = assertThrows(InvalidTransactionException.class, something::run);
        assertEquals(status, ex.getResponseCode());
    }

    public static void assertFailsRevertingWith(final Runnable something, final ResponseCodeEnum status) {
        final var ex = assertThrows(InvalidTransactionException.class, something::run);
        assertEquals(status, ex.getResponseCode());
        assertTrue(ex.isReverting());
    }

    public static TokenTransferList ttlOf(TokenID scope, AccountID src, AccountID dest, long amount) {
        return TokenTransferList.newBuilder()
                .setToken(scope)
                .addTransfers(aaOf(src, -amount))
                .addTransfers(aaOf(dest, +amount))
                .build();
    }

    public static TokenTransferList exchangeOf(TokenID scope, AccountID src, AccountID dest, long serial) {
        return TokenTransferList.newBuilder()
                .setToken(scope)
                .addNftTransfers(serialFromTo(serial, src, dest))
                .build();
    }

    public static TokenTransferList asymmetricTtlOf(TokenID scope, AccountID src, long amount) {
        return TokenTransferList.newBuilder()
                .setToken(scope)
                .addTransfers(aaOf(src, -amount))
                .build();
    }

    public static TokenTransferList returnExchangeOf(TokenID scope, AccountID src, AccountID dst, long serialNo) {
        return TokenTransferList.newBuilder()
                .setToken(scope)
                .addNftTransfers(serialFromTo(serialNo, src, dst))
                .build();
    }

    public static AccountAmount aaOf(AccountID id, long amount) {
        return AccountAmount.newBuilder().setAccountID(id).setAmount(amount).build();
    }

    public static NftTransfer serialFromTo(final long num, final AccountID sender, final AccountID receiver) {
        return NftTransfer.newBuilder()
                .setSerialNumber(num)
                .setSenderAccountID(sender)
                .setReceiverAccountID(receiver)
                .build();
    }

    public static TransactionBody ethereumTransactionOp() {
        final var op = EthereumTransactionBody.newBuilder()
                .setEthereumData(
                        ByteString.copyFrom(
                                com.swirlds.common.utility.CommonUtils.unhex(
                                        "f864012f83018000947e3a9eaf9bcc39e2ffa38eb30bf7a93feacbc18180827653820277a0f9fbff985d374be4a55f296915002eec11ac96f1ce2df183adf992baa9390b2fa00c1e867cc960d9c74ec2e6a662b7908ec4c8cc9f3091e886bcefbeb2290fb792")))
                .build();
        return TransactionBody.newBuilder()
                .setTransactionID(TransactionID.newBuilder()
                        .setTransactionValidStart(Timestamp.newBuilder().setSeconds(1_234_567L)))
                .setEthereumTransaction(op)
                .build();
    }

    public static TransactionBody fungibleMintOp() {
        final var op = TokenMintTransactionBody.newBuilder().setAmount(1234L).build();
        return TransactionBody.newBuilder()
                .setTransactionID(TransactionID.newBuilder()
                        .setTransactionValidStart(Timestamp.newBuilder().setSeconds(1_234_567L)))
                .setTokenMint(op)
                .build();
    }

    public static TransactionBody nonFungibleMintOp() {
        final var op = TokenMintTransactionBody.newBuilder()
                .addMetadata(ByteString.copyFromUtf8("FIRST"))
                .build();
        return TransactionBody.newBuilder()
                .setTransactionID(TransactionID.newBuilder()
                        .setTransactionValidStart(Timestamp.newBuilder().setSeconds(1_234_567L)))
                .setTokenMint(op)
                .build();
    }

    public static Transaction buildTransactionFrom(final TransactionBody transactionBody) {
        return buildTransactionFrom(signedTransactionFrom(transactionBody).toByteString());
    }

    public static Transaction buildTransactionFrom(final ByteString signedTransactionBytes) {
        return Transaction.newBuilder()
                .setSignedTransactionBytes(signedTransactionBytes)
                .build();
    }

    private static SignedTransaction signedTransactionFrom(final TransactionBody txnBody) {
        return signedTransactionFrom(txnBody, SignatureMap.getDefaultInstance());
    }

    public static SignedTransaction signedTransactionFrom(final TransactionBody txnBody, final SignatureMap sigMap) {
        return SignedTransaction.newBuilder()
                .setBodyBytes(txnBody.toByteString())
                .setSigMap(sigMap)
                .build();
    }
}
