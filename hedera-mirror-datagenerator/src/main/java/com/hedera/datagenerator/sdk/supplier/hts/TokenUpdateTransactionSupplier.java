package com.hedera.datagenerator.sdk.supplier.hts;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.Builder;
import lombok.Value;

import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PublicKey;
import com.hedera.hashgraph.sdk.token.TokenId;
import com.hedera.hashgraph.sdk.token.TokenUpdateTransaction;

@Builder
@Value
public class TokenUpdateTransactionSupplier implements TransactionSupplier<TokenUpdateTransaction> {
    //Required
    private final AccountId treasuryAccountId;
    private final TokenId tokenId;

    //Optional
    private final Ed25519PublicKey adminKey;
    @Builder.Default
    private final String symbol = "HMNT";
    @Builder.Default
    private final long maxTransactionFee = 1_000_000_000;
    @Builder.Default
    private final Instant expirationTime = Instant.now().plus(120, ChronoUnit.DAYS);
    @Builder.Default
    private final Duration autoRenewPeriod = Duration.ofSeconds(8000000);

    @Override
    public TokenUpdateTransaction get() {
        TokenUpdateTransaction tokenUpdateTransaction = new TokenUpdateTransaction()
                .setTokenId(tokenId)
                .setSybmol(symbol)
                .setName(symbol + "_name")
                .setExpirationTime(expirationTime)
                .setAutoRenewPeriod(autoRenewPeriod)
                .setMaxTransactionFee(maxTransactionFee)
                .setTransactionMemo("Supplier Update token_");

        if (adminKey != null) {
            tokenUpdateTransaction
                    .setAdminKey(adminKey)
                    .setSupplyKey(adminKey)
                    .setWipeKey(adminKey)
                    .setFreezeKey(adminKey)
                    .setKycKey(adminKey);
        }
        if (treasuryAccountId != null) {
            tokenUpdateTransaction
                    .setTreasury(treasuryAccountId)
                    .setAutoRenewAccount(treasuryAccountId);
        }
        return tokenUpdateTransaction;
    }
}
