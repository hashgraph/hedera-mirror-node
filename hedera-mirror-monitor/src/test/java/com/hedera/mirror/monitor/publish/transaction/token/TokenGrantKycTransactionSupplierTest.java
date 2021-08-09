package com.hedera.mirror.monitor.publish.transaction.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.hedera.hashgraph.sdk.TokenGrantKycTransaction;
import com.hedera.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;

class TokenGrantKycTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        TokenGrantKycTransactionSupplier tokenGrantKycTransactionSupplier = new TokenGrantKycTransactionSupplier();
        tokenGrantKycTransactionSupplier.setAccountId(ACCOUNT_ID.toString());
        tokenGrantKycTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenGrantKycTransaction actual = tokenGrantKycTransactionSupplier.get();

        TokenGrantKycTransaction expected = new TokenGrantKycTransaction()
                .setAccountId(ACCOUNT_ID)
                .setMaxTransactionFee(MAX_TRANSACTION_FEE_HBAR)
                .setTokenId(TOKEN_ID)
                .setTransactionMemo(actual.getTransactionMemo());

        assertAll(
                () -> assertThat(actual.getTransactionMemo()).contains("Mirror node granted kyc to test token"),
                () -> assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
        );
    }

    @Test
    void createWithCustomData() {
        TokenGrantKycTransactionSupplier tokenGrantKycTransactionSupplier = new TokenGrantKycTransactionSupplier();
        tokenGrantKycTransactionSupplier.setAccountId(ACCOUNT_ID.toString());
        tokenGrantKycTransactionSupplier.setMaxTransactionFee(1);
        tokenGrantKycTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenGrantKycTransaction actual = tokenGrantKycTransactionSupplier.get();

        TokenGrantKycTransaction expected = new TokenGrantKycTransaction()
                .setAccountId(ACCOUNT_ID)
                .setMaxTransactionFee(ONE_TINYBAR)
                .setTokenId(TOKEN_ID)
                .setTransactionMemo(actual.getTransactionMemo());

        assertAll(
                () -> assertThat(actual.getTransactionMemo()).contains("Mirror node granted kyc to test token"),
                () -> assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
        );
    }
}
