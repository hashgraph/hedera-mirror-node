package com.hedera.mirror.monitor.publish.transaction.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.hedera.hashgraph.sdk.AccountCreateTransaction;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;

class AccountCreateTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        AccountCreateTransactionSupplier accountCreateTransactionSupplier = new AccountCreateTransactionSupplier();
        AccountCreateTransaction actual = accountCreateTransactionSupplier.get();
        AccountCreateTransaction expected = new AccountCreateTransaction()
                .setAccountMemo(actual.getAccountMemo())
                .setInitialBalance(Hbar.fromTinybars(10_000_000))
                .setKey(actual.getKey())
                .setMaxTransactionFee(MAX_TRANSACTION_FEE_HBAR)
                .setReceiverSignatureRequired(false)
                .setTransactionMemo(actual.getTransactionMemo());
        assertAll(
                () -> assertThat(actual.getAccountMemo()).contains("Mirror node created test account"),
                () -> assertThat(actual.getKey()).isNotNull(),
                () -> assertThat(actual.getTransactionMemo()).contains("Mirror node created test account"),
                () -> assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
        );
    }

    @Test
    void createWithCustomData() {
        PublicKey key = PrivateKey.generate().getPublicKey();

        AccountCreateTransactionSupplier accountCreateTransactionSupplier = new AccountCreateTransactionSupplier();
        accountCreateTransactionSupplier.setInitialBalance(1);
        accountCreateTransactionSupplier.setMaxTransactionFee(1);
        accountCreateTransactionSupplier.setReceiverSignatureRequired(true);
        accountCreateTransactionSupplier.setPublicKey(key.toString());
        AccountCreateTransaction actual = accountCreateTransactionSupplier.get();

        AccountCreateTransaction expected = new AccountCreateTransaction()
                .setAccountMemo(actual.getAccountMemo())
                .setInitialBalance(ONE_TINYBAR)
                .setKey(key)
                .setMaxTransactionFee(ONE_TINYBAR)
                .setReceiverSignatureRequired(true)
                .setTransactionMemo(actual.getTransactionMemo());

        assertAll(
                () -> assertThat(actual.getTransactionMemo()).contains("Mirror node created test account"),
                () -> assertThat(actual.getAccountMemo()).contains("Mirror node created test account"),
                () -> assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
        );
    }
}
