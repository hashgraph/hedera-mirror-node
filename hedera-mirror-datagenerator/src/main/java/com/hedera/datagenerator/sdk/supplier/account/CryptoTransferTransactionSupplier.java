package com.hedera.datagenerator.sdk.supplier.account;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.account.CryptoTransferTransaction;

@Builder
@Value
public class CryptoTransferTransactionSupplier implements TransactionSupplier<CryptoTransferTransaction> {

    //Required
    private final AccountId senderId;
    private final AccountId recipientId;

    //Optional
    @Builder.Default
    private final long amount = 1;
    @Builder.Default
    private final long maxTransactionFee = 1_000_000;

    @Override
    public CryptoTransferTransaction get() {
        return new CryptoTransferTransaction()
                .addSender(senderId, amount)
                .addRecipient(recipientId, amount)
                .setTransactionMemo("transfer test")
                .setMaxTransactionFee(maxTransactionFee)
                .setTransactionMemo("Supplier crypto transfer_" + Instant.now());
    }
}
