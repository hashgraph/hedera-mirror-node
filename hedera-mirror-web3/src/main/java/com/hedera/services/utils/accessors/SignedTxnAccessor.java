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

package com.hedera.services.utils.accessors;

import static com.hedera.services.utils.MiscUtils.FUNCTION_EXTRACTOR;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.hapi.fees.usage.SigUsage;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SignedTxnAccessor implements TxnAccessor {
    private HederaFunctionality function;
    private final int sigMapSize;
    private final int numSigPairs;
    private final byte[] hash;
    private final byte[] txnBytes;
    private final String memo;
    private final Transaction signedTxnWrapper;
    private final SignatureMap sigMap;
    private final TransactionID txnId;
    private final TransactionBody txn;
    private AccountID payer;
    private static final Logger log = LogManager.getLogger(SignedTxnAccessor.class);

    public static SignedTxnAccessor uncheckedFrom(final Transaction validSignedTxn) {
        try {
            return SignedTxnAccessor.from(validSignedTxn.toByteArray());
        } catch (final Exception illegal) {
            log.warn("Unexpected use of factory with invalid gRPC transaction", illegal);
            throw new IllegalArgumentException("Argument 'validSignedTxn' must be a valid signed txn");
        }
    }

    public static SignedTxnAccessor from(final byte[] signedTxnWrapperBytes) throws InvalidProtocolBufferException {
        return new SignedTxnAccessor(signedTxnWrapperBytes, null);
    }

    public static SignedTxnAccessor from(final byte[] signedTxnWrapperBytes, final Transaction signedTxnWrapper)
            throws InvalidProtocolBufferException {
        return new SignedTxnAccessor(signedTxnWrapperBytes, signedTxnWrapper);
    }

    @SuppressWarnings("deprecation")
    protected SignedTxnAccessor(final byte[] signedTxnWrapperBytes, @Nullable final Transaction transaction)
            throws InvalidProtocolBufferException {

        final Transaction txnWrapper;
        if (transaction != null) {
            txnWrapper = transaction;
        } else {
            txnWrapper = Transaction.parseFrom(signedTxnWrapperBytes);
        }
        this.signedTxnWrapper = txnWrapper;

        final var signedTxnBytes = signedTxnWrapper.getSignedTransactionBytes();
        if (signedTxnBytes.isEmpty()) {
            txnBytes = unwrapUnsafelyIfPossible(signedTxnWrapper.getBodyBytes());
            sigMap = signedTxnWrapper.getSigMap();
            hash = noThrowSha384HashOf(signedTxnWrapperBytes);
        } else {
            final var signedTxn = SignedTransaction.parseFrom(signedTxnBytes);
            txnBytes = unwrapUnsafelyIfPossible(signedTxn.getBodyBytes());
            sigMap = signedTxn.getSigMap();
            hash = noThrowSha384HashOf(unwrapUnsafelyIfPossible(signedTxnBytes));
        }

        txn = TransactionBody.parseFrom(txnBytes);
        // Note that the SignatureMap was parsed with either the top-level
        // Transaction or the SignedTransaction, so we've already checked
        // it for unknown fields either way; only still need to check the body
        memo = txn.getMemo();
        txnId = txn.getTransactionID();
        sigMapSize = sigMap.getSerializedSize();
        numSigPairs = sigMap.getSigPairCount();
        payer = getTxnId().getAccountID();

        getFunction();
    }

    public static byte[] unwrapUnsafelyIfPossible(@NonNull final ByteString byteString) {
        return byteString.toByteArray();
    }

    public static byte[] noThrowSha384HashOf(final byte[] byteArray) {
        try {
            return MessageDigest.getInstance("SHA-384").digest(byteArray);
        } catch (final NoSuchAlgorithmException fatal) {
            throw new IllegalStateException(fatal);
        }
    }

    @Override
    public HederaFunctionality getFunction() {
        if (function == null) {
            function = FUNCTION_EXTRACTOR.apply(getTxn());
        }
        return function;
    }

    @Override
    public Transaction getSignedTxnWrapper() {
        return signedTxnWrapper;
    }

    @Override
    public TransactionBody getTxn() {
        return txn;
    }

    @Override
    public TransactionID getTxnId() {
        return txnId;
    }

    @Override
    public AccountID getPayer() {
        return payer;
    }

    @Override
    public String getMemo() {
        return memo;
    }

    @Override
    public byte[] getHash() {
        return hash;
    }

    @Override
    public SubType getSubType() {
        return SubType.DEFAULT;
    }

    @Override
    public SigUsage usageGiven(final int numPayerKeys) {
        return new SigUsage(numSigPairs, sigMapSize, numPayerKeys);
    }
}
