package com.hedera.datagenerator.sdk.supplier.account;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    private final Ed25519PublicKey publicKey;

    //Optional
    private final AccountId proxyAccountId;
    @Builder.Default
    private final Instant expirationTime = Instant.now().plus(120, ChronoUnit.DAYS);
    @Builder.Default
    private final Duration autoRenewPeriod = Duration.ofSeconds(8000000);
    @Builder.Default
    private final boolean receiverSignatureRequired = false;
    @Builder.Default
    private final long maxTransactionFee = 1_000_000_000;

    @Override
    public AccountUpdateTransaction get() {
        AccountUpdateTransaction transaction = new AccountUpdateTransaction()
                .setAccountId(accountId)
                .setKey(publicKey)
                .setReceiverSignatureRequired(receiverSignatureRequired)
                .setExpirationTime(expirationTime)
                .setAutoRenewPeriod(autoRenewPeriod)
                .setMaxTransactionFee(maxTransactionFee)
                .setTransactionMemo("Supplier update account_" + Instant.now());

        if (proxyAccountId != null) {
            transaction.setProxyAccountId(proxyAccountId);
        }
        return transaction;
    }
}
