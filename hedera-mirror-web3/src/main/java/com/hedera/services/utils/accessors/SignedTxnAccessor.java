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

package com.hedera.services.utils.accessors;

import static com.hedera.services.fees.usage.token.TokenOpsUsageUtils.TOKEN_OPS_USAGE_UTILS;
import static com.hedera.services.utils.ByteStringUtils.unwrapUnsafelyIfPossible;
import static com.hedera.services.utils.CommonUtils.noThrowSha384HashOf;
import static com.hedera.services.utils.MiscUtils.FUNCTION_EXTRACTOR;
import static com.hedera.services.utils.MiscUtils.hasUnknownFields;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.*;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.mirror.web3.evm.store.CachingStateFrame;
import com.hedera.services.hapi.fees.usage.BaseTransactionMeta;
import com.hedera.services.hapi.fees.usage.SigUsage;
import com.hedera.services.hapi.fees.usage.crypto.CryptoApproveAllowanceMeta;
import com.hedera.services.hapi.fees.usage.crypto.CryptoCreateMeta;
import com.hedera.services.hapi.fees.usage.crypto.CryptoDeleteAllowanceMeta;
import com.hedera.services.hapi.fees.usage.crypto.CryptoUpdateMeta;
import com.hedera.services.txns.span.ExpandHandleSpanMapAccessor;
import com.hederahashgraph.api.proto.java.*;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.codec.binary.StringUtils;
import org.bouncycastle.util.Arrays;

/** Encapsulates access to several commonly referenced parts of a gRPC {@link Transaction}. */
public class SignedTxnAccessor implements TxnAccessor {
    private static final ExpandHandleSpanMapAccessor SPAN_MAP_ACCESSOR = new ExpandHandleSpanMapAccessor();

    private Map<String, Object> spanMap = new HashMap<>();

    private final int sigMapSize;
    private final int numSigPairs;
    private final byte[] hash;
    private final byte[] txnBytes;
    private final byte[] utf8MemoBytes;
    private final byte[] signedTxnWrapperBytes;
    private final String memo;
    private final boolean memoHasZeroByte;
    private final Transaction signedTxnWrapper;
    private final SignatureMap sigMap;
    private final TransactionID txnId;
    private final TransactionBody txn;
    // private SubmitMessageMeta submitMessageMeta;
    // private CryptoTransferMeta xferUsageMeta;
    private BaseTransactionMeta txnUsageMeta;
    private HederaFunctionality function;
    private boolean throttleExempt;
    private boolean congestionExempt;
    private boolean usesUnknownFields = false;

    private CachingStateFrame<?> state;

    private AccountID payer;

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
        this.signedTxnWrapperBytes = signedTxnWrapperBytes;

        final Transaction txnWrapper;
        if (transaction != null) {
            txnWrapper = transaction;
        } else {
            txnWrapper = Transaction.parseFrom(signedTxnWrapperBytes);
        }
        this.signedTxnWrapper = txnWrapper;
        usesUnknownFields |= hasUnknownFields(signedTxnWrapper);

        final var signedTxnBytes = signedTxnWrapper.getSignedTransactionBytes();
        if (signedTxnBytes.isEmpty()) {
            txnBytes = unwrapUnsafelyIfPossible(signedTxnWrapper.getBodyBytes());
            sigMap = signedTxnWrapper.getSigMap();
            hash = noThrowSha384HashOf(signedTxnWrapperBytes);
        } else {
            final var signedTxn = SignedTransaction.parseFrom(signedTxnBytes);
            usesUnknownFields |= hasUnknownFields(signedTxn);
            txnBytes = unwrapUnsafelyIfPossible(signedTxn.getBodyBytes());
            sigMap = signedTxn.getSigMap();
            hash = noThrowSha384HashOf(unwrapUnsafelyIfPossible(signedTxnBytes));
        }

        txn = TransactionBody.parseFrom(txnBytes);
        // Note that the SignatureMap was parsed with either the top-level
        // Transaction or the SignedTransaction, so we've already checked
        // it for unknown fields either way; only still need to check the body
        usesUnknownFields |= hasUnknownFields(txn);
        memo = txn.getMemo();
        txnId = txn.getTransactionID();
        sigMapSize = sigMap.getSerializedSize();
        numSigPairs = sigMap.getSigPairCount();
        utf8MemoBytes = StringUtils.getBytesUtf8(memo);
        memoHasZeroByte = Arrays.contains(utf8MemoBytes, (byte) 0);
        payer = getTxnId().getAccountID();

        getFunction();
        setBaseUsageMeta();
        setOpUsageMeta();
    }

    @Override
    public HederaFunctionality getFunction() {
        if (function == null) {
            function = FUNCTION_EXTRACTOR.apply(getTxn());
        }
        return function;
    }

    @Override
    public long getOfferedFee() {
        return txn.getTransactionFee();
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
    public String getMemo() {
        return memo;
    }

    @Override
    public byte[] getHash() {
        return hash;
    }

    public void markThrottleExempt() {
        this.throttleExempt = true;
    }

    public void markCongestionExempt() {
        this.congestionExempt = true;
    }

    @Override
    public BaseTransactionMeta baseUsageMeta() {
        return txnUsageMeta;
    }

    @Override
    public Map<String, Object> getSpanMap() {
        return spanMap;
    }

    @Override
    public SigUsage usageGiven(final int numPayerKeys) {
        return new SigUsage(numSigPairs, sigMapSize, numPayerKeys);
    }

    private void setBaseUsageMeta() {
        if (function == CryptoTransfer) {
            txnUsageMeta = new BaseTransactionMeta(
                    utf8MemoBytes.length, txn.getCryptoTransfer().getTransfers().getAccountAmountsCount());
        } else {
            txnUsageMeta = new BaseTransactionMeta(utf8MemoBytes.length, 0);
        }
    }

    /* This section should be deleted after custom accessors are complete */
    private void setOpUsageMeta() {
        if (function == CryptoTransfer) {
            setXferUsageMeta();
        } else if (function == ConsensusSubmitMessage) {
            // setSubmitUsageMeta();
        } else if (function == TokenCreate) {
            setTokenCreateUsageMeta();
        } else if (function == TokenBurn) {
            setTokenBurnUsageMeta();
        } else if (function == TokenFreezeAccount) {
            setTokenFreezeUsageMeta();
        } else if (function == TokenUnfreezeAccount) {
            setTokenUnfreezeUsageMeta();
        } else if (function == TokenPause) {
            setTokenPauseUsageMeta();
        } else if (function == TokenUnpause) {
            setTokenUnpauseUsageMeta();
        } else if (function == CryptoCreate) {
            setCryptoCreateUsageMeta();
        } else if (function == CryptoUpdate) {
            setCryptoUpdateUsageMeta();
        } else if (function == CryptoApproveAllowance) {
            setCryptoApproveUsageMeta();
        } else if (function == CryptoDeleteAllowance) {
            setCryptoDeleteAllowanceUsageMeta();
        } else if (function == EthereumTransaction) {
            // setEthTxDataMeta();
        }
    }

    private void setXferUsageMeta() {
        var totalTokensInvolved = 0;
        var totalTokenTransfers = 0;
        var numNftOwnershipChanges = 0;
        final var op = txn.getCryptoTransfer();
        for (final var tokenTransfers : op.getTokenTransfersList()) {
            totalTokensInvolved++;
            totalTokenTransfers += tokenTransfers.getTransfersCount();
            numNftOwnershipChanges += tokenTransfers.getNftTransfersCount();
        }
        // xferUsageMeta = new CryptoTransferMeta(1, totalTokensInvolved, totalTokenTransfers, numNftOwnershipChanges);
    }

    //    private void setSubmitUsageMeta() {
    //        submitMessageMeta = new SubmitMessageMeta(
    //                txn.getConsensusSubmitMessage().getMessage().size());
    //    }

    private void setTokenCreateUsageMeta() {
        final var tokenCreateMeta = TOKEN_OPS_USAGE_UTILS.tokenCreateUsageFrom(txn);
        SPAN_MAP_ACCESSOR.setTokenCreateMeta(this, tokenCreateMeta);
    }

    private void setTokenBurnUsageMeta() {
        final var tokenBurnMeta = TOKEN_OPS_USAGE_UTILS.tokenBurnUsageFrom(txn);
        SPAN_MAP_ACCESSOR.setTokenBurnMeta(this, tokenBurnMeta);
    }

    private void setTokenFreezeUsageMeta() {
        final var tokenFreezeMeta = TOKEN_OPS_USAGE_UTILS.tokenFreezeUsageFrom();
        SPAN_MAP_ACCESSOR.setTokenFreezeMeta(this, tokenFreezeMeta);
    }

    private void setTokenUnfreezeUsageMeta() {
        final var tokenUnfreezeMeta = TOKEN_OPS_USAGE_UTILS.tokenUnfreezeUsageFrom();
        SPAN_MAP_ACCESSOR.setTokenUnfreezeMeta(this, tokenUnfreezeMeta);
    }

    private void setTokenPauseUsageMeta() {
        final var tokenPauseMeta = TOKEN_OPS_USAGE_UTILS.tokenPauseUsageFrom();
        SPAN_MAP_ACCESSOR.setTokenPauseMeta(this, tokenPauseMeta);
    }

    private void setTokenUnpauseUsageMeta() {
        final var tokenUnpauseMeta = TOKEN_OPS_USAGE_UTILS.tokenUnpauseUsageFrom();
        SPAN_MAP_ACCESSOR.setTokenUnpauseMeta(this, tokenUnpauseMeta);
    }

    private void setCryptoCreateUsageMeta() {
        final var cryptoCreateMeta = new CryptoCreateMeta(txn.getCryptoCreateAccount());
        SPAN_MAP_ACCESSOR.setCryptoCreateMeta(this, cryptoCreateMeta);
    }

    private void setCryptoUpdateUsageMeta() {
        final var cryptoUpdateMeta = new CryptoUpdateMeta(
                txn.getCryptoUpdateAccount(),
                txn.getTransactionID().getTransactionValidStart().getSeconds());
        SPAN_MAP_ACCESSOR.setCryptoUpdate(this, cryptoUpdateMeta);
    }

    private void setCryptoApproveUsageMeta() {
        final var cryptoApproveMeta = new CryptoApproveAllowanceMeta(
                txn.getCryptoApproveAllowance(),
                txn.getTransactionID().getTransactionValidStart().getSeconds());
        SPAN_MAP_ACCESSOR.setCryptoApproveMeta(this, cryptoApproveMeta);
    }

    private void setCryptoDeleteAllowanceUsageMeta() {
        final var cryptoDeleteAllowanceMeta = new CryptoDeleteAllowanceMeta(
                txn.getCryptoDeleteAllowance(),
                txn.getTransactionID().getTransactionValidStart().getSeconds());
        SPAN_MAP_ACCESSOR.setCryptoDeleteAllowanceMeta(this, cryptoDeleteAllowanceMeta);
    }

    @Override
    public SubType getSubType() {
        if (function == CryptoTransfer) {
            // return xferUsageMeta.getSubType();
        } else if (function == TokenCreate) {
            return SPAN_MAP_ACCESSOR.getTokenCreateMeta(this).getSubType();
        } else if (function == TokenMint) {
            final var op = getTxn().getTokenMint();
            return op.getMetadataCount() > 0 ? TOKEN_NON_FUNGIBLE_UNIQUE : TOKEN_FUNGIBLE_COMMON;
        } else if (function == TokenBurn) {
            return SPAN_MAP_ACCESSOR.getTokenBurnMeta(this).getSubType();
        } else if (function == TokenAccountWipe) {
            return SPAN_MAP_ACCESSOR.getTokenWipeMeta(this).getSubType();
        }
        return SubType.DEFAULT;
    }

    @Override
    public CachingStateFrame<?> getState() {
        return state;
    }
}
