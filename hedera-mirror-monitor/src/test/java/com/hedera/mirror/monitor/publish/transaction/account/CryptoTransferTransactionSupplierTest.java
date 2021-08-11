package com.hedera.mirror.monitor.publish.transaction.account;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import static com.hedera.mirror.monitor.publish.transaction.account.CryptoTransferTransactionSupplier.TransferType.CRYPTO;
import static com.hedera.mirror.monitor.publish.transaction.account.CryptoTransferTransactionSupplier.TransferType.NFT;
import static com.hedera.mirror.monitor.publish.transaction.account.CryptoTransferTransactionSupplier.TransferType.TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.NftId;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TransferTransaction;
import com.hedera.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;

class CryptoTransferTransactionSupplierTest extends AbstractTransactionSupplierTest {

    private static final Hbar MAX_TRANSACTION_FEE_HBAR = Hbar.fromTinybars(10_000_000L);

    @Test
    void createWithMinimumData() {
        CryptoTransferTransactionSupplier cryptoTransferTransactionSupplier = new CryptoTransferTransactionSupplier();
        cryptoTransferTransactionSupplier.setRecipientAccountId(ACCOUNT_ID_2.toString());
        cryptoTransferTransactionSupplier.setSenderAccountId(ACCOUNT_ID.toString());
        TransferTransaction actual = cryptoTransferTransactionSupplier.get();

        Hbar transferAmount = ONE_TINYBAR;
        TransferTransaction expected = new TransferTransaction()
                .addHbarTransfer(ACCOUNT_ID_2, transferAmount)
                .addHbarTransfer(ACCOUNT_ID, transferAmount.negated())
                .setMaxTransactionFee(MAX_TRANSACTION_FEE_HBAR)
                .setTransactionMemo(actual.getTransactionMemo());

        assertAll(
                () -> assertThat(actual.getTransactionMemo()).contains("Mirror node created test transfer"),
                () -> assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
        );
    }

    @Test
    void createWithCustomCryptoTransfer() {
        CryptoTransferTransactionSupplier cryptoTransferTransactionSupplier = new CryptoTransferTransactionSupplier();
        cryptoTransferTransactionSupplier.setAmount(10);
        cryptoTransferTransactionSupplier.setMaxTransactionFee(1);
        cryptoTransferTransactionSupplier.setRecipientAccountId(ACCOUNT_ID_2.toString());
        cryptoTransferTransactionSupplier.setSenderAccountId(ACCOUNT_ID.toString());
        cryptoTransferTransactionSupplier.setTransferTypes(Set.of(CRYPTO));
        TransferTransaction actual = cryptoTransferTransactionSupplier.get();

        Hbar transferAmount = Hbar.fromTinybars(10);
        TransferTransaction expected = new TransferTransaction()
                .addHbarTransfer(ACCOUNT_ID_2, transferAmount)
                .addHbarTransfer(ACCOUNT_ID, transferAmount.negated())
                .setMaxTransactionFee(ONE_TINYBAR)
                .setTransactionMemo(actual.getTransactionMemo());

        assertThat(actual)
                .satisfies(a -> assertThat(a.getTransactionMemo()).contains("Mirror node created test transfer"))
                .satisfies(a -> assertThat(a).usingRecursiveComparison().isEqualTo(expected));
    }

    @Test
    void createWithCustomTokenTransfer() {
        CryptoTransferTransactionSupplier cryptoTransferTransactionSupplier = new CryptoTransferTransactionSupplier();
        cryptoTransferTransactionSupplier.setAmount(10);
        cryptoTransferTransactionSupplier.setMaxTransactionFee(1);
        cryptoTransferTransactionSupplier.setRecipientAccountId(ACCOUNT_ID_2.toString());
        cryptoTransferTransactionSupplier.setSenderAccountId(ACCOUNT_ID.toString());
        cryptoTransferTransactionSupplier.setTokenId(TOKEN_ID.toString());
        cryptoTransferTransactionSupplier.setTransferTypes(Set.of(TOKEN));

        TransferTransaction actual = cryptoTransferTransactionSupplier.get();

        TransferTransaction expected = new TransferTransaction()
                .addTokenTransfer(TOKEN_ID, ACCOUNT_ID_2, 10)
                .addTokenTransfer(TOKEN_ID, ACCOUNT_ID, -10)
                .setMaxTransactionFee(ONE_TINYBAR)
                .setTransactionMemo(actual.getTransactionMemo());

        assertThat(actual)
                .satisfies(a -> assertThat(a.getTransactionMemo()).contains("Mirror node created test transfer"))
                .satisfies(a -> assertThat(a).usingRecursiveComparison().isEqualTo(expected));
    }

    @Test
    void createWithCustomNftTransfer() {
        CryptoTransferTransactionSupplier cryptoTransferTransactionSupplier = new CryptoTransferTransactionSupplier();
        cryptoTransferTransactionSupplier.setAmount(2);
        cryptoTransferTransactionSupplier.setMaxTransactionFee(1);
        cryptoTransferTransactionSupplier.setNftTokenId(TOKEN_ID.toString());
        cryptoTransferTransactionSupplier.setRecipientAccountId(ACCOUNT_ID_2.toString());
        cryptoTransferTransactionSupplier.setSenderAccountId(ACCOUNT_ID.toString());
        cryptoTransferTransactionSupplier.setSerialNumber(new AtomicLong(10));
        cryptoTransferTransactionSupplier.setTransferTypes(Set.of(NFT));

        TransferTransaction actual = cryptoTransferTransactionSupplier.get();

        TransferTransaction expected = new TransferTransaction()
                .addNftTransfer(new NftId(TOKEN_ID, 10), ACCOUNT_ID, ACCOUNT_ID_2)
                .addNftTransfer(new NftId(TOKEN_ID, 11), ACCOUNT_ID, ACCOUNT_ID_2)
                .setMaxTransactionFee(ONE_TINYBAR)
                .setTransactionMemo(actual.getTransactionMemo());

        assertThat(actual)
                .satisfies(a -> assertThat(a.getTransactionMemo()).contains("Mirror node created test transfer"))
                .satisfies(a -> assertThat(a).usingRecursiveComparison().isEqualTo(expected));
    }

    @Test
    void createWithCustomAllTransfer() {
        TokenId nftTokenId = TokenId.fromString("0.0.21");

        CryptoTransferTransactionSupplier cryptoTransferTransactionSupplier = new CryptoTransferTransactionSupplier();
        cryptoTransferTransactionSupplier.setAmount(2);
        cryptoTransferTransactionSupplier.setMaxTransactionFee(1);
        cryptoTransferTransactionSupplier.setNftTokenId(nftTokenId.toString());
        cryptoTransferTransactionSupplier.setRecipientAccountId(ACCOUNT_ID_2.toString());
        cryptoTransferTransactionSupplier.setSenderAccountId(ACCOUNT_ID.toString());
        cryptoTransferTransactionSupplier.setSerialNumber(new AtomicLong(10));
        cryptoTransferTransactionSupplier.setTokenId(TOKEN_ID.toString());
        cryptoTransferTransactionSupplier.setTransferTypes(Set.of(CRYPTO, NFT, TOKEN));

        TransferTransaction actual = cryptoTransferTransactionSupplier.get();

        Hbar transferAmount = Hbar.fromTinybars(2);
        TransferTransaction expected = new TransferTransaction()
                .addHbarTransfer(ACCOUNT_ID_2, transferAmount)
                .addHbarTransfer(ACCOUNT_ID, transferAmount.negated())
                .addNftTransfer(new NftId(nftTokenId, 10), ACCOUNT_ID, ACCOUNT_ID_2)
                .addNftTransfer(new NftId(nftTokenId, 11), ACCOUNT_ID, ACCOUNT_ID_2)
                .addTokenTransfer(TOKEN_ID, ACCOUNT_ID_2, 2)
                .addTokenTransfer(TOKEN_ID, ACCOUNT_ID, -2)
                .setMaxTransactionFee(ONE_TINYBAR)
                .setTransactionMemo(actual.getTransactionMemo());

        assertThat(actual)
                .satisfies(a -> assertThat(a.getTransactionMemo()).contains("Mirror node created test transfer"))
                .satisfies(a -> assertThat(a).usingRecursiveComparison().isEqualTo(expected));
    }
}
