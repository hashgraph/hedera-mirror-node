package com.hedera.datagenerator.sdk.supplier.hts;

import java.time.Duration;
import java.time.Instant;
import lombok.Builder;
import lombok.Value;

import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PublicKey;
import com.hedera.hashgraph.sdk.token.TokenCreateTransaction;

@Builder
@Value
public class TokenCreateTransactionSupplier implements TransactionSupplier<TokenCreateTransaction> {

    //Required
    private final AccountId treasuryAccount;

    //Optional
    private final Ed25519PublicKey adminKey;
    @Builder.Default
    private final String symbol = "HMNT";
    @Builder.Default
    private final int initialSupply = 1000000000;
    @Builder.Default
    private boolean freezeDefault = false;
    @Builder.Default
    private int decimals = 10;
    @Builder.Default
    private final Duration autoRenewPeriod = Duration.ofSeconds(8000000);
    @Builder.Default
    private final long maxTransactionFee = 1_000_000_000;

    @Override
    public TokenCreateTransaction get() {
        TokenCreateTransaction tokenCreateTransaction = new TokenCreateTransaction()
                .setSymbol(symbol)
                .setName(symbol + "_name")
                .setDecimals(decimals)
                .setInitialSupply(initialSupply)
                .setAutoRenewAccount(treasuryAccount)
                .setAutoRenewPeriod(autoRenewPeriod)
                .setTreasury(treasuryAccount)
                .setMaxTransactionFee(maxTransactionFee)
                .setTransactionMemo("Supplier Create token_" + Instant.now());

        if (adminKey != null) {
            tokenCreateTransaction
                    .setAdminKey(adminKey)
                    .setSupplyKey(adminKey)
                    .setWipeKey(adminKey)
                    .setFreezeKey(adminKey)
                    .setKycKey(adminKey);
        }

        return tokenCreateTransaction;
    }
}
