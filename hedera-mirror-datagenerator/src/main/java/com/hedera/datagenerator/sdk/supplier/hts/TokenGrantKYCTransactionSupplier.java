package com.hedera.datagenerator.sdk.supplier.hts;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.token.TokenGrantKycTransaction;
import com.hedera.hashgraph.sdk.token.TokenId;

@Builder
@Value
public class TokenGrantKYCTransactionSupplier implements TransactionSupplier<TokenGrantKycTransaction> {
    //Required
    private final TokenId tokenId;
    private final AccountId accountId;

    //Optional
    @Builder.Default
    private final long maxTransactionFee = 1_000_000_000;

    @Override
    public TokenGrantKycTransaction get() {
        return new TokenGrantKycTransaction()
                .setAccountId(accountId)
                .setTokenId(tokenId)
                .setMaxTransactionFee(maxTransactionFee)
                .setTransactionMemo("Supplier Grant kyc for token_" + Instant.now());
    }
}
