package com.hedera.mirror.monitor.publish.transaction.token;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.hedera.hashgraph.sdk.TokenPauseTransaction;
import com.hedera.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;

class TokenPauseTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        TokenPauseTransactionSupplier tokenPauseTransactionSupplier = new TokenPauseTransactionSupplier();

        tokenPauseTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenPauseTransaction actual = tokenPauseTransactionSupplier.get();

        assertThat(actual)
                .returns(MAX_TRANSACTION_FEE_HBAR, TokenPauseTransaction::getMaxTransactionFee)
                .returns(TOKEN_ID, TokenPauseTransaction::getTokenId);
    }

    @Override
    protected Class getSupplierClass() {
        return TokenFreezeTransactionSupplier.class;
    }
}
