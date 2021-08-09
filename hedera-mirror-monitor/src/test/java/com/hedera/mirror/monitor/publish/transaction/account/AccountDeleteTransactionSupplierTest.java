package com.hedera.mirror.monitor.publish.transaction.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.hedera.hashgraph.sdk.AccountDeleteTransaction;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;

class AccountDeleteTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        AccountDeleteTransactionSupplier accountDeleteTransactionSupplier = new AccountDeleteTransactionSupplier();
        accountDeleteTransactionSupplier.setAccountId(ACCOUNT_ID.toString());
        AccountDeleteTransaction actual = accountDeleteTransactionSupplier.get();

        AccountDeleteTransaction expected = new AccountDeleteTransaction()
                .setAccountId(ACCOUNT_ID)
                .setMaxTransactionFee(MAX_TRANSACTION_FEE_HBAR)
                .setTransferAccountId(RECIPIENT_ACCOUNT_ID)
                .setTransactionMemo(actual.getTransactionMemo());

        assertAll(
                () -> assertThat(actual.getTransactionMemo()).contains("Mirror node deleted test account"),
                () -> assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
        );
    }

    @Test
    void createWithCustomData() {
        AccountDeleteTransactionSupplier accountDeleteTransactionSupplier = new AccountDeleteTransactionSupplier();
        accountDeleteTransactionSupplier.setAccountId(ACCOUNT_ID.toString());
        accountDeleteTransactionSupplier.setTransferAccountId(RECIPIENT_ACCOUNT_ID.toString());
        AccountDeleteTransaction actual = accountDeleteTransactionSupplier.get();

        AccountDeleteTransaction expected = new AccountDeleteTransaction()
                .setAccountId(ACCOUNT_ID)
                .setMaxTransactionFee(Hbar.fromTinybars(1_000_000_000))
                .setTransferAccountId(RECIPIENT_ACCOUNT_ID)
                .setTransactionMemo(actual.getTransactionMemo());

        assertAll(
                () -> assertThat(actual.getTransactionMemo()).contains("Mirror node deleted test account"),
                () -> assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
        );
    }
}
