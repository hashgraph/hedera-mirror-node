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

package com.hedera.services.fees.calculation.token.txns;

import static com.hedera.services.hapi.fees.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.services.fees.calculation.TxnResourceUsageEstimator;
import com.hedera.services.fees.usage.token.TokenUpdateUsage;
import com.hedera.services.hapi.fees.usage.EstimatorFactory;
import com.hedera.services.hapi.fees.usage.SigUsage;
import com.hedera.services.hapi.fees.usage.TxnUsageEstimator;
import com.hedera.services.hapi.utils.fees.SigValueObj;
import com.hedera.services.jproto.JKey;
import com.hedera.services.store.models.Token;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.commons.codec.DecoderException;

/**
 * Copied ResourceUsage type from hedera-services. Differences with the original:
 * 1. Uses the store interface to get the token in usageGiven
 * 2. Moved GetTokenInfoResourceUsage::ifPresent to a local method and modified it to convert JKey to Key
 * and to accept Token instead of TokenInfo
 */
public class TokenUpdateResourceUsage extends AbstractTokenResourceUsage implements TxnResourceUsageEstimator {
    private static final BiFunction<TransactionBody, TxnUsageEstimator, TokenUpdateUsage> factory =
            TokenUpdateUsage::newEstimate;

    public TokenUpdateResourceUsage(final EstimatorFactory estimatorFactory) {
        super(estimatorFactory);
    }

    @Override
    public boolean applicableTo(final TransactionBody txn) {
        return txn.hasTokenUpdate();
    }

    @Override
    public FeeData usageGiven(final TransactionBody txn, final SigValueObj svo, Store store) throws Exception {
        final var op = txn.getTokenUpdate();
        final var sigUsage = new SigUsage(svo.getTotalSigCount(), svo.getSignatureSize(), svo.getPayerAcctSigCount());
        final var token = store.getToken(asTypedEvmAddress(op.getToken()), OnMissing.DONT_THROW);
        if (!token.equals(Token.getEmptyToken())) {
            final var estimate = factory.apply(txn, estimatorFactory.get(sigUsage, txn, ESTIMATOR_UTILS))
                    .givenCurrentExpiry(token.getExpiry())
                    .givenCurrentAdminKey(ifPresent(token, Token::hasAdminKey, Token::getAdminKey))
                    .givenCurrentFreezeKey(ifPresent(token, Token::hasFreezeKey, Token::getFreezeKey))
                    .givenCurrentWipeKey(ifPresent(token, Token::hasWipeKey, Token::getWipeKey))
                    .givenCurrentSupplyKey(ifPresent(token, Token::hasSupplyKey, Token::getSupplyKey))
                    .givenCurrentKycKey(ifPresent(token, Token::hasKycKey, Token::getKycKey))
                    .givenCurrentFeeScheduleKey(ifPresent(token, Token::hasFeeScheduleKey, Token::getFeeScheduleKey))
                    .givenCurrentPauseKey(ifPresent(token, Token::hasPauseKey, Token::getPauseKey))
                    .givenCurrentMemo(token.getMemo())
                    .givenCurrentName(token.getName())
                    .givenCurrentSymbol(token.getSymbol());
            if (token.hasAutoRenewAccount()) {
                estimate.givenCurrentlyUsingAutoRenewAccount();
            }
            return estimate.get();
        } else {
            return FeeData.getDefaultInstance();
        }
    }

    private Optional<Key> ifPresent(
            final Token info, final Predicate<Token> check, final Function<Token, JKey> getter) {
        if (check.test(info)) {
            try {
                final var key = JKey.mapJKey(getter.apply(info));
                return Optional.of(key);
            } catch (DecoderException e) {
                // empty
            }
        }
        return Optional.empty();
    }
}
