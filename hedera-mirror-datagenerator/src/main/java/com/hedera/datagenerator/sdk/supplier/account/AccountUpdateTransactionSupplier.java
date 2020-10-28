package com.hedera.datagenerator.sdk.supplier.account;

import java.time.Duration;
import java.time.Instant;
import lombok.Builder;
import lombok.Value;
import lombok.extern.log4j.Log4j2;

import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.account.AccountUpdateTransaction;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PublicKey;

@Builder
@Value
@Log4j2
public class AccountUpdateTransactionSupplier implements TransactionSupplier<AccountUpdateTransaction> {

    //Required
    private final AccountId accountId;
    private final Ed25519PublicKey newPublicKey;

    //Optional
    private final Instant expirationTime;
    private final Duration autoRenewPeriod;
    private final AccountId proxyAccountId;
    @Builder.Default
    private final boolean receiverSignatureRequired = false;
    @Builder.Default
    private final long maxTransactionFee = 1_000_000_000;

    @Override
    public AccountUpdateTransaction get() {
        AccountUpdateTransaction transaction = new AccountUpdateTransaction()
                .setAccountId(accountId)
                .setKey(newPublicKey)
                .setReceiverSignatureRequired(receiverSignatureRequired)
                .setMaxTransactionFee(maxTransactionFee)
                .setTransactionMemo("Supplier update account_" + Instant.now());

        if (autoRenewPeriod != null) {
            transaction.setAutoRenewPeriod(autoRenewPeriod);
        }
        if (expirationTime != null) {
            transaction.setExpirationTime(expirationTime);
        }
        if (proxyAccountId != null) {
            transaction.setProxyAccountId(proxyAccountId);
        }
        return transaction;
    }
}
