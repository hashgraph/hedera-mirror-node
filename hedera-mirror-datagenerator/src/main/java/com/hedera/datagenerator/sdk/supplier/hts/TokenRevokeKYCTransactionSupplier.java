package com.hedera.datagenerator.sdk.supplier.hts;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.token.TokenId;
import com.hedera.hashgraph.sdk.token.TokenRevokeKycTransaction;

@Builder
@Value
public class TokenRevokeKYCTransactionSupplier implements TransactionSupplier<TokenRevokeKycTransaction> {
    //Required
    private final TokenId tokenId;
    private final AccountId accountId;

    //Optional
    @Builder.Default
    private final long maxTransactionFee = 1_000_000_000;

    @Override
    public TokenRevokeKycTransaction get() {
        return new TokenRevokeKycTransaction()
                .setAccountId(accountId)
                .setTokenId(tokenId)
                .setMaxTransactionFee(1_000_000_000)
                .setTransactionMemo("Revoke kyc for token_" + Instant.now());
    }
}
