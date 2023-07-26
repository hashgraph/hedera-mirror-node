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

import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.EMPTY_KEY;
import static com.hedera.services.utils.MiscUtils.asKeyUnchecked;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.google.protobuf.StringValue;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GrantRevokeKycWrapper;
import com.hedera.services.store.contracts.precompile.codec.Association;
import com.hedera.services.store.contracts.precompile.codec.BurnWrapper;
import com.hedera.services.store.contracts.precompile.codec.Dissociation;
import com.hedera.services.store.contracts.precompile.codec.MintWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenKeyWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenUpdateExpiryInfoWrapper;
import com.hedera.services.store.contracts.precompile.codec.WipeWrapper;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenBurnTransactionBody;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenGrantKycTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;

public class SyntheticTxnFactory {

    public static final String AUTO_MEMO = "auto-created account";
    private static final String LAZY_MEMO = "lazy-created account";
    private static final long THREE_MONTHS_IN_SECONDS = 7776000L;

    public TransactionBody.Builder createMint(final MintWrapper mintWrapper) {
        final var builder = TokenMintTransactionBody.newBuilder();

        builder.setToken(mintWrapper.tokenType());
        if (mintWrapper.type() == NON_FUNGIBLE_UNIQUE) {
            builder.addAllMetadata(mintWrapper.metadata());
        } else {
            builder.setAmount(mintWrapper.amount());
        }

        return TransactionBody.newBuilder().setTokenMint(builder);
    }

    public TransactionBody.Builder createHollowAccount(final ByteString alias, final long balance) {
        final var baseBuilder = createAccountBase(balance);
        baseBuilder.setKey(asKeyUnchecked(EMPTY_KEY)).setAlias(alias).setMemo(LAZY_MEMO);
        return TransactionBody.newBuilder().setCryptoCreateAccount(baseBuilder.build());
    }

    public TransactionBody.Builder createAccount(
            final ByteString alias, final Key key, final long balance, final int maxAutoAssociations) {
        final var baseBuilder = createAccountBase(balance);
        baseBuilder.setKey(key).setAlias(alias).setMemo(AUTO_MEMO);

        if (maxAutoAssociations > 0) {
            baseBuilder.setMaxAutomaticTokenAssociations(maxAutoAssociations);
        }
        return TransactionBody.newBuilder().setCryptoCreateAccount(baseBuilder.build());
    }

    private CryptoCreateTransactionBody.Builder createAccountBase(final long balance) {
        return CryptoCreateTransactionBody.newBuilder()
                .setInitialBalance(balance)
                .setAutoRenewPeriod(Duration.newBuilder().setSeconds(THREE_MONTHS_IN_SECONDS));
    }

    public TransactionBody.Builder createAssociate(final Association association) {
        final var builder = TokenAssociateTransactionBody.newBuilder();

        builder.setAccount(association.accountId());
        builder.addAllTokens(association.tokenIds());

        return TransactionBody.newBuilder().setTokenAssociate(builder);
    }

    public TransactionBody.Builder createDissociate(final Dissociation dissociation) {
        final var builder = TokenDissociateTransactionBody.newBuilder();

        builder.setAccount(dissociation.accountId());
        builder.addAllTokens(dissociation.tokenIds());

        return TransactionBody.newBuilder().setTokenDissociate(builder);
    }

    public TransactionBody.Builder createBurn(final BurnWrapper burnWrapper) {
        final var builder = TokenBurnTransactionBody.newBuilder();

        builder.setToken(burnWrapper.tokenType());
        if (burnWrapper.type() == NON_FUNGIBLE_UNIQUE) {
            builder.addAllSerialNumbers(burnWrapper.serialNos());
        } else {
            builder.setAmount(burnWrapper.amount());
        }
        return TransactionBody.newBuilder().setTokenBurn(builder);
    }

    public TransactionBody.Builder createWipe(final WipeWrapper wipeWrapper) {
        final var builder = TokenWipeAccountTransactionBody.newBuilder();

        builder.setToken(wipeWrapper.token());
        builder.setAccount(wipeWrapper.account());
        if (wipeWrapper.type() == NON_FUNGIBLE_UNIQUE) {
            builder.addAllSerialNumbers(wipeWrapper.serialNumbers());
        } else {
            builder.setAmount(wipeWrapper.amount());
        }

        return TransactionBody.newBuilder().setTokenWipe(builder);
    }

    public TransactionBody.Builder createGrantKyc(
            final GrantRevokeKycWrapper<TokenID, AccountID> grantRevokeKycWrapper) {
        final var builder = TokenGrantKycTransactionBody.newBuilder();

        builder.setToken(grantRevokeKycWrapper.token());
        builder.setAccount(grantRevokeKycWrapper.account());

        return TransactionBody.newBuilder().setTokenGrantKyc(builder);
    }

    public TransactionBody.Builder createTokenUpdate(final TokenUpdateWrapper updateWrapper) {
        final var builder = TokenUpdateTransactionBody.newBuilder();
        builder.setToken(updateWrapper.tokenID());

        if (updateWrapper.name() != null) {
            builder.setName(updateWrapper.name());
        }
        if (updateWrapper.symbol() != null) {
            builder.setSymbol(updateWrapper.symbol());
        }
        if (updateWrapper.memo() != null) {
            builder.setMemo(StringValue.of(updateWrapper.memo()));
        }
        if (updateWrapper.treasury() != null) {
            builder.setTreasury(updateWrapper.treasury());
        }

        if (updateWrapper.expiry().second() != 0) {
            builder.setExpiry(Timestamp.newBuilder()
                    .setSeconds(updateWrapper.expiry().second())
                    .build());
        }
        if (updateWrapper.expiry().autoRenewAccount() != null) {
            builder.setAutoRenewAccount(updateWrapper.expiry().autoRenewAccount());
        }
        if (updateWrapper.expiry().autoRenewPeriod() != 0) {
            builder.setAutoRenewPeriod(
                    Duration.newBuilder().setSeconds(updateWrapper.expiry().autoRenewPeriod()));
        }

        return checkTokenKeysTypeAndBuild(updateWrapper.tokenKeys(), builder);
    }

    private TransactionBody.Builder checkTokenKeysTypeAndBuild(
            final List<TokenKeyWrapper> tokenKeys, final TokenUpdateTransactionBody.Builder builder) {
        tokenKeys.forEach(tokenKeyWrapper -> {
            final var key = tokenKeyWrapper.key().asGrpc();
            if (tokenKeyWrapper.isUsedForAdminKey()) {
                builder.setAdminKey(key);
            }
            if (tokenKeyWrapper.isUsedForKycKey()) {
                builder.setKycKey(key);
            }
            if (tokenKeyWrapper.isUsedForFreezeKey()) {
                builder.setFreezeKey(key);
            }
            if (tokenKeyWrapper.isUsedForWipeKey()) {
                builder.setWipeKey(key);
            }
            if (tokenKeyWrapper.isUsedForSupplyKey()) {
                builder.setSupplyKey(key);
            }
            if (tokenKeyWrapper.isUsedForFeeScheduleKey()) {
                builder.setFeeScheduleKey(key);
            }
            if (tokenKeyWrapper.isUsedForPauseKey()) {
                builder.setPauseKey(key);
            }
        });

        return TransactionBody.newBuilder().setTokenUpdate(builder);
    }

    public TransactionBody.Builder createTokenUpdateExpiryInfo(final TokenUpdateExpiryInfoWrapper expiryInfoWrapper) {
        final var builder = TokenUpdateTransactionBody.newBuilder();
        builder.setToken(expiryInfoWrapper.tokenID());

        if (expiryInfoWrapper.expiry().second() != 0) {
            builder.setExpiry(Timestamp.newBuilder()
                    .setSeconds(expiryInfoWrapper.expiry().second())
                    .build());
        }
        if (expiryInfoWrapper.expiry().autoRenewAccount() != null) {
            builder.setAutoRenewAccount(expiryInfoWrapper.expiry().autoRenewAccount());
        }
        if (expiryInfoWrapper.expiry().autoRenewPeriod() != 0) {
            builder.setAutoRenewPeriod(
                    Duration.newBuilder().setSeconds(expiryInfoWrapper.expiry().autoRenewPeriod()));
        }

        return TransactionBody.newBuilder().setTokenUpdate(builder);
    }
}
