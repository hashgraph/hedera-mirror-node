package com.hedera.datagenerator.sdk.supplier.hts;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.token.TokenAssociateTransaction;
import com.hedera.hashgraph.sdk.token.TokenId;

@Builder
@Value
public class TokenAssociateTransactionSupplier implements TransactionSupplier<TokenAssociateTransaction> {
    //Required
    private final TokenId tokenId;
    private final AccountId accountId;

    //Optional
    @Builder.Default
    private final long maxTransactionFee = 1_000_000_000;

    @Override
    public TokenAssociateTransaction get() {
        return new TokenAssociateTransaction()
                .setAccountId(accountId)
                .addTokenId(tokenId)
                .setMaxTransactionFee(maxTransactionFee)
                .setTransactionMemo("Associate token_" + Instant.now());
    }
}
