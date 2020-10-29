package com.hedera.datagenerator.sdk.supplier.hts;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.hashgraph.sdk.token.TokenBurnTransaction;
import com.hedera.hashgraph.sdk.token.TokenId;

@Builder
@Value
public class TokenBurnTransactionSupplier implements TransactionSupplier<TokenBurnTransaction> {
    //Required
    private final TokenId tokenId;

    //Optional
    @Builder.Default
    private final long amount = 1;
    @Builder.Default
    private final long maxTransactionFee = 1_000_000_000;

    @Override
    public TokenBurnTransaction get() {
        return new TokenBurnTransaction()
                .setTokenId(tokenId)
                .setAmount(amount)
                .setMaxTransactionFee(maxTransactionFee)
                .setTransactionMemo("Supplier Burn token_" + Instant.now());
    }
}
