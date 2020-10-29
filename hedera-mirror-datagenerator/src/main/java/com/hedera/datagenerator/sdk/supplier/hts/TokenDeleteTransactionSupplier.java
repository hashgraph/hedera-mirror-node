package com.hedera.datagenerator.sdk.supplier.hts;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.hashgraph.sdk.token.TokenDeleteTransaction;
import com.hedera.hashgraph.sdk.token.TokenId;

@Builder
@Value
public class TokenDeleteTransactionSupplier implements TransactionSupplier<TokenDeleteTransaction> {
    private final TokenId tokenId;

    @Builder.Default
    private final long maxTransactionFee = 1_000_000_000;

    @Override
    public TokenDeleteTransaction get() {
        return new TokenDeleteTransaction()
                .setTokenId(tokenId)
                .setMaxTransactionFee(maxTransactionFee)
                .setTransactionMemo("Supplier Delete token_" + Instant.now());
    }
}
