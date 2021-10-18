package com.hedera.mirror.monitor.publish.transaction.token;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.hedera.hashgraph.sdk.TokenUnpauseTransaction;
import com.hedera.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;

class TokenUnpauseTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        TokenUnpauseTransactionSupplier tokenUnpauseTransactionSupplier = new TokenUnpauseTransactionSupplier();

        tokenUnpauseTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenUnpauseTransaction actual = tokenUnpauseTransactionSupplier.get();

        assertThat(actual)
                .returns(MAX_TRANSACTION_FEE_HBAR, TokenUnpauseTransaction::getMaxTransactionFee)
                .returns(TOKEN_ID, TokenUnpauseTransaction::getTokenId);
    }

    @Override
    protected Class getSupplierClass() {
        return TokenFreezeTransactionSupplier.class;
    }
}
