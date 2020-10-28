package com.hedera.datagenerator.sdk.supplier.account;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;
import lombok.extern.log4j.Log4j2;

import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.hashgraph.sdk.account.AccountDeleteTransaction;
import com.hedera.hashgraph.sdk.account.AccountId;

@Builder
@Value
@Log4j2
public class AccountDeleteTransactionSupplier implements TransactionSupplier<AccountDeleteTransaction> {

    //Required
    private final AccountId accountId;

    //Optional
    @Builder.Default
    private final AccountId transferAccountId = AccountId.fromString("0.0.2");
    @Builder.Default
    private final long maxTransactionFee = 1_000_000_000;

    @Override
    public AccountDeleteTransaction get() {
        return new AccountDeleteTransaction()
                .setDeleteAccountId(accountId)
                .setTransferAccountId(transferAccountId)
                .setMaxTransactionFee(maxTransactionFee)
                .setTransactionMemo("Supplier delete account_" + Instant.now());
    }
}
