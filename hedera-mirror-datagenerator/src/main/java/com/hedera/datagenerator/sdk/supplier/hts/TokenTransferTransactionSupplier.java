package com.hedera.datagenerator.sdk.supplier.hts;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.token.TokenId;
import com.hedera.hashgraph.sdk.token.TokenTransferTransaction;

@Builder
@Value
public class TokenTransferTransactionSupplier implements TransactionSupplier<TokenTransferTransaction> {
    //Required
    private final TokenId tokenId;
    private final AccountId senderId;
    private final AccountId recipientId;

    //Optional
    @Builder.Default
    private final int amount = 1;
    @Builder.Default
    private final long maxTransactionFee = 1_000_000;

    @Override
    public TokenTransferTransaction get() {
        return new TokenTransferTransaction()
                .addSender(tokenId, senderId, amount)
                .addRecipient(tokenId, recipientId, amount)
                .setMaxTransactionFee(maxTransactionFee)
                .setTransactionMemo("Supplier Transfer token_" + Instant.now());
    }
}
