package com.hedera.datagenerator.sdk.supplier.account;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;
import lombok.extern.log4j.Log4j2;

import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.hashgraph.sdk.account.AccountCreateTransaction;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PrivateKey;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PublicKey;

@Builder
@Value
@Log4j2
public class AccountCreateTransactionSupplier implements TransactionSupplier<AccountCreateTransaction> {

    //Optional
    private final Ed25519PublicKey publicKey;
    @Builder.Default
    private final long initialBalance = 10_000_000;
    @Builder.Default
    private final long maxTransactionFee = 1_000_000_000;

    @Override
    public AccountCreateTransaction get() {
        return new AccountCreateTransaction()
                .setKey(publicKey != null ? publicKey : generateKeys())
                .setInitialBalance(initialBalance)
                .setMaxTransactionFee(maxTransactionFee)
                .setTransactionMemo("Supplier create account_" + Instant.now());
    }

    private Ed25519PublicKey generateKeys() {
        Ed25519PrivateKey privateKey = Ed25519PrivateKey.generate();
        Ed25519PublicKey publicKey = privateKey.publicKey;

        log.debug("Private key = {}", privateKey);
        log.debug("Public key = {}", publicKey);
        return publicKey;
    }
}
