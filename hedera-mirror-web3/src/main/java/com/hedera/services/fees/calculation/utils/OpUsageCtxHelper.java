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
package com.hedera.services.fees.calculation.utils;

import static com.hedera.services.hapi.fees.usage.token.TokenOpsUsageUtils.TOKEN_OPS_USAGE_UTILS;
import static com.hederahashgraph.api.proto.java.CustomFee.FeeCase.FIXED_FEE;
import static com.hederahashgraph.api.proto.java.CustomFee.FeeCase.FRACTIONAL_FEE;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;

import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenFeeScheduleUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Collections;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.hapi.fees.usage.crypto.ExtantCryptoContext;
import com.hedera.services.hapi.fees.usage.file.FileAppendMeta;
import com.hedera.services.hapi.fees.usage.token.TokenOpsUsage;
import com.hedera.services.hapi.fees.usage.token.meta.ExtantFeeScheduleContext;
import com.hedera.services.hapi.fees.usage.token.meta.TokenMintMeta;
import com.hedera.services.utils.accessors.TxnAccessor;

@Singleton
public class OpUsageCtxHelper {

    private static final ExtantFeeScheduleContext MISSING_FEE_SCHEDULE_UPDATE_CTX = new ExtantFeeScheduleContext(0, 0);

    private final StateView workingView;
    private final TokenOpsUsage tokenOpsUsage = new TokenOpsUsage();

    @Inject
    public OpUsageCtxHelper(
            final StateView workingView
    ) {
        this.workingView = workingView;
    }

    public FileAppendMeta metaForFileAppend(TransactionBody txn) {
        final var op = txn.getFileAppend();
        final var fid = op.getFileID();

        final var fileMeta = workingView.attrOf(fid);

        final var effCreationTime =
                txn.getTransactionID().getTransactionValidStart().getSeconds();
        final var effExpiration = fileMeta.map(HFileMeta::getExpiry).orElse(effCreationTime);
        final var effLifetime = effExpiration - effCreationTime;

        return new FileAppendMeta(op.getContents().size(), effLifetime);
    }

    public ExtantFeeScheduleContext ctxForFeeScheduleUpdate(TokenFeeScheduleUpdateTransactionBody op) {
        final var key = fromTokenId(op.getTokenId());
        final var token = tokens.get().get(key);
        if (token == null) {
            return MISSING_FEE_SCHEDULE_UPDATE_CTX;
        }
        return new ExtantFeeScheduleContext(token.expiry(), curFeeScheduleReprSize(token.customFeeSchedule()));
    }

    public ExtantCryptoContext ctxForCryptoUpdate(TransactionBody txn) {
        final var op = txn.getCryptoUpdateAccount();
        final var id = op.getAccountIDToUpdate();
        final var accountEntityNum =
                id.getAlias().isEmpty() ? fromAccountId(id) : aliasManager.lookupIdBy(id.getAlias());
        final var account = workingView.accounts().get(accountEntityNum);
        ExtantCryptoContext cryptoContext;
        if (account != null) {
            cryptoContext = ExtantCryptoContext.newBuilder()
                    .setCurrentKey(asKeyUnchecked(account.getAccountKey()))
                    .setCurrentMemo(account.getMemo())
                    .setCurrentExpiry(account.getExpiry())
                    .setCurrentlyHasProxy(account.getProxy() != null)
                    .setCurrentNumTokenRels(account.getNumAssociations())
                    .setCurrentMaxAutomaticAssociations(account.getMaxAutomaticAssociations())
                    .setCurrentCryptoAllowances(getCryptoAllowancesList(account))
                    .setCurrentTokenAllowances(getFungibleTokenAllowancesList(account))
                    .setCurrentApproveForAllNftAllowances(getNftApprovedForAll(account))
                    .build();
        } else {
            cryptoContext = ExtantCryptoContext.newBuilder()
                    .setCurrentExpiry(
                            txn.getTransactionID().getTransactionValidStart().getSeconds())
                    .setCurrentMemo(DEFAULT_MEMO)
                    .setCurrentKey(Key.getDefaultInstance())
                    .setCurrentlyHasProxy(false)
                    .setCurrentNumTokenRels(0)
                    .setCurrentMaxAutomaticAssociations(0)
                    .setCurrentCryptoAllowances(Collections.emptyMap())
                    .setCurrentTokenAllowances(Collections.emptyMap())
                    .setCurrentApproveForAllNftAllowances(Collections.emptySet())
                    .build();
        }
        return cryptoContext;
    }

    public ExtantCryptoContext ctxForCryptoAllowance(TxnAccessor accessor) {
        final var id = accessor.getPayer();
        final var accountEntityNum =
                id.getAlias().isEmpty() ? fromAccountId(id) : aliasManager.lookupIdBy(id.getAlias());
        final var account = workingView.accounts().get(accountEntityNum);
        ExtantCryptoContext cryptoContext;
        if (account != null) {
            cryptoContext = ExtantCryptoContext.newBuilder()
                    .setCurrentKey(asKeyUnchecked(account.getAccountKey()))
                    .setCurrentMemo(account.getMemo())
                    .setCurrentExpiry(account.getExpiry())
                    .setCurrentlyHasProxy(account.getProxy() != null)
                    .setCurrentNumTokenRels(account.getNumAssociations())
                    .setCurrentMaxAutomaticAssociations(account.getMaxAutomaticAssociations())
                    .setCurrentCryptoAllowances(getCryptoAllowancesList(account))
                    .setCurrentTokenAllowances(getFungibleTokenAllowancesList(account))
                    .setCurrentApproveForAllNftAllowances(getNftApprovedForAll(account))
                    .build();
        } else {
            cryptoContext = ExtantCryptoContext.newBuilder()
                    .setCurrentExpiry(accessor.getTxn()
                            .getTransactionID()
                            .getTransactionValidStart()
                            .getSeconds())
                    .setCurrentMemo(DEFAULT_MEMO)
                    .setCurrentKey(Key.getDefaultInstance())
                    .setCurrentlyHasProxy(false)
                    .setCurrentNumTokenRels(0)
                    .setCurrentMaxAutomaticAssociations(0)
                    .setCurrentCryptoAllowances(Collections.emptyMap())
                    .setCurrentTokenAllowances(Collections.emptyMap())
                    .setCurrentApproveForAllNftAllowances(Collections.emptySet())
                    .build();
        }
        return cryptoContext;
    }


    public TokenMintMeta metaForTokenMint(TxnAccessor accessor) {
        final var subType = accessor.getSubType();

        long lifeTime = 0L;
        if (subType == TOKEN_NON_FUNGIBLE_UNIQUE) {
            final var token = accessor.getTxn().getTokenMint().getToken();
            final var now = accessor.getTxnId().getTransactionValidStart().getSeconds();
            final var tokenIfPresent = workingView.tokenWith(token);
            lifeTime = tokenIfPresent.map(t -> Math.max(0L, t.expiry() - now)).orElse(0L);
        }
        return TOKEN_OPS_USAGE_UTILS.tokenMintUsageFrom(accessor.getTxn(), subType, lifeTime);
    }

    private int curFeeScheduleReprSize(List<FcCustomFee> feeSchedule) {
        int numFixedHbarFees = 0;
        int numFixedHtsFees = 0;
        int numFractionalFees = 0;
        int numRoyaltyNoFallbackFees = 0;
        int numRoyaltyHtsFallbackFees = 0;
        int numRoyaltyHbarFallbackFees = 0;
        for (var fee : feeSchedule) {
            if (fee.getFeeType() == FIXED_FEE) {
                if (fee.getFixedFeeSpec().getTokenDenomination() != null) {
                    numFixedHtsFees++;
                } else {
                    numFixedHbarFees++;
                }
            } else if (fee.getFeeType() == FRACTIONAL_FEE) {
                numFractionalFees++;
            } else {
                final var royaltyFee = fee.getRoyaltyFeeSpec();
                final var fallbackFee = royaltyFee.fallbackFee();
                if (fallbackFee != null) {
                    if (fallbackFee.getTokenDenomination() != null) {
                        numRoyaltyHtsFallbackFees++;
                    } else {
                        numRoyaltyHbarFallbackFees++;
                    }
                } else {
                    numRoyaltyNoFallbackFees++;
                }
            }
        }
        return tokenOpsUsage.bytesNeededToRepr(
                numFixedHbarFees,
                numFixedHtsFees,
                numFractionalFees,
                numRoyaltyNoFallbackFees,
                numRoyaltyHtsFallbackFees,
                numRoyaltyHbarFallbackFees);
    }
}
