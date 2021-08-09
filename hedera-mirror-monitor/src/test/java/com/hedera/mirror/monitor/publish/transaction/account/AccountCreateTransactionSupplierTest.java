package com.hedera.mirror.monitor.publish.transaction.account;

import com.hedera.hashgraph.sdk.AccountCreateTransaction;

import com.hedera.hashgraph.sdk.Hbar;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class AccountCreateTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        AccountCreateTransactionSupplier accountCreateTransactionSupplier = new AccountCreateTransactionSupplier();
        AccountCreateTransaction actual = (AccountCreateTransaction) accountCreateTransactionSupplier.get();
        AccountCreateTransaction expected = new AccountCreateTransaction().setAccountMemo(actual.getAccountMemo()).setInitialBalance(Hbar.fromTinybars(10_000_000)).setKey(actual.getKey()).setMaxTransactionFee(Hbar.fromTinybars(1_000_000_000)).setReceiverSignatureRequired(false).setTransactionMemo(actual.getTransactionMemo());
        assertAll(
                () -> assertThat(actual.getKey()).isNotNull(),
                () -> assertThat(actual.getTransactionMemo()).contains("Mirror node created test account"),
                () -> assertThat(actual.getAccountMemo()).contains("Mirror node created test account"),
                () -> assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
        );
    }

    @Test
    void createWithCustomData() {
        AccountCreateTransactionSupplier accountCreateTransactionSupplier = new AccountCreateTransactionSupplier();
        AccountCreateTransaction actual = (AccountCreateTransaction) accountCreateTransactionSupplier.get();
        AccountCreateTransaction expected = new AccountCreateTransaction().setAccountMemo(actual.getAccountMemo()).setInitialBalance(Hbar.fromTinybars(10_000_000)).setKey(actual.getKey()).setMaxTransactionFee(Hbar.fromTinybars(1_000_000_000)).setReceiverSignatureRequired(false).setTransactionMemo(actual.getTransactionMemo());
        assertAll(
                () -> assertThat(actual.getKey()).isNotNull(),
                () -> assertThat(actual.getTransactionMemo()).contains("Mirror node created test account"),
                () -> assertThat(actual.getAccountMemo()).contains("Mirror node created test account"),
                () -> assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
        );
    }
}
