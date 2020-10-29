package com.hedera.datagenerator.sdk.supplier.hts;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.token.TokenFreezeTransaction;
import com.hedera.hashgraph.sdk.token.TokenId;

@Builder
@Value
public class TokenFreezeTransactionSupplier implements TransactionSupplier<TokenFreezeTransaction> {
    //Required
    private final TokenId tokenId;
    private final AccountId accountId;

    //Optional
    @Builder.Default
    private final long maxTransactionFee = 1_000_000_000;

    @Override
    public TokenFreezeTransaction get() {
        return new TokenFreezeTransaction()
                .setAccountId(accountId)
                .setMaxTransactionFee(1_000_000_000)
                .setTokenId(tokenId)
                .setTransactionMemo("Supplier Freeze token account_" + Instant.now());
    }
}
