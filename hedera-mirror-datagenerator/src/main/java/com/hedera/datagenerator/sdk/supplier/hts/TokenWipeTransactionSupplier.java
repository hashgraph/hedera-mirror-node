package com.hedera.datagenerator.sdk.supplier.hts;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.token.TokenId;
import com.hedera.hashgraph.sdk.token.TokenWipeTransaction;

@Builder
@Value
public class TokenWipeTransactionSupplier implements TransactionSupplier<TokenWipeTransaction> {
    private final TokenId tokenId;
    private final AccountId operatorId;

    //Optional
    @Builder.Default
    private final long maxTransactionFee = 1_000_000_000;
    @Builder.Default
    private final long amount = 1;

    @Override
    public TokenWipeTransaction get() {
        return new TokenWipeTransaction()
                .setAccountId(operatorId)
                .setTokenId(tokenId)
                .setAmount(amount)
                .setMaxTransactionFee(maxTransactionFee)
                .setTransactionMemo("Supplier Wipe token_" + Instant.now());
    }
}
