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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

import com.hedera.hashgraph.sdk.Hbar;
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

        assertThat(actual)
                .returns(MAX_TRANSACTION_FEE_HBAR, TransferTransaction::getMaxTransactionFee)
                .satisfies(a -> assertThat(a.getHbarTransfers())
                        .returns(ONE_TINYBAR.negated(), map -> map.get(ACCOUNT_ID))
                        .returns(ONE_TINYBAR, map -> map.get(ACCOUNT_ID_2)))
                .satisfies(a -> assertThat(a.getTransactionMemo()).contains("Mirror node created test transfer"));
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
        assertThat(actual)
                .returns(ONE_TINYBAR, TransferTransaction::getMaxTransactionFee)
                .satisfies(a -> assertThat(a.getHbarTransfers())
                        .returns(transferAmount.negated(), map -> map.get(ACCOUNT_ID))
                        .returns(transferAmount, map -> map.get(ACCOUNT_ID_2)))
                .satisfies(a -> assertThat(a.getTransactionMemo()).contains("Mirror node created test transfer"));
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

        assertThat(actual)
                .returns(ONE_TINYBAR, TransferTransaction::getMaxTransactionFee)
                .satisfies(a -> assertThat(a.getTransactionMemo()).contains("Mirror node created test transfer"))
                .extracting(TransferTransaction::getTokenTransfers)
                .returns(1, Map::size)
                .satisfies(transfers -> assertThat(transfers.get(TOKEN_ID))
                        .returns(-10L, map -> map.get(ACCOUNT_ID))
                        .returns(10L, map -> map.get(ACCOUNT_ID_2)));
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

        assertThat(actual)
                .returns(ONE_TINYBAR, TransferTransaction::getMaxTransactionFee)
                .satisfies(a -> assertThat(a.getTransactionMemo()).contains("Mirror node created test transfer"))
                .extracting(TransferTransaction::getTokenNftTransfers)
                .returns(1, Map::size)
                .extracting(transfers -> transfers.get(TOKEN_ID))
                .returns(2, List::size)
                .satisfies(transferList -> assertThat(transferList.get(0))
                        //TODO Swap these with getters
                        .returns(10L, transfer -> transfer.serial)
                        .returns(ACCOUNT_ID, transfer -> transfer.sender)
                        .returns(ACCOUNT_ID_2, transfer -> transfer.receiver))
                .satisfies(transferList -> assertThat(transferList.get(1))
                        .returns(11L, transfer -> transfer.serial)
                        .returns(ACCOUNT_ID, transfer -> transfer.sender)
                        .returns(ACCOUNT_ID_2, transfer -> transfer.receiver));

//
    }

    @Test
    void createWithCustomAllTransfer() {
        TokenId nftTokenId = TokenId.fromString("0.0.21");
        Hbar transferAmount = Hbar.fromTinybars(2);

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

        assertThat(actual)
                .returns(ONE_TINYBAR, TransferTransaction::getMaxTransactionFee)
                .satisfies(a -> assertThat(a.getTransactionMemo()).contains("Mirror node created test transfer"))
                .satisfies(a -> assertThat(a)
                        .extracting(TransferTransaction::getTokenNftTransfers)
                        .returns(1, Map::size)
                        .extracting(transfers -> transfers.get(nftTokenId))
                        .returns(2, List::size)
                        .satisfies(transferList -> assertThat(transferList.get(0))
                                //TODO Swap these with getters
                                .returns(10L, transfer -> transfer.serial)
                                .returns(ACCOUNT_ID, transfer -> transfer.sender)
                                .returns(ACCOUNT_ID_2, transfer -> transfer.receiver))
                        .satisfies(transferList -> assertThat(transferList.get(1))
                                .returns(11L, transfer -> transfer.serial)
                                .returns(ACCOUNT_ID, transfer -> transfer.sender)
                                .returns(ACCOUNT_ID_2, transfer -> transfer.receiver)))
                .satisfies(a -> assertThat(a.getTokenTransfers().get(TOKEN_ID))
                        .returns(-2L, map -> map.get(ACCOUNT_ID))
                        .returns(2L, map -> map.get(ACCOUNT_ID_2)))
                .satisfies(a -> assertThat(a.getHbarTransfers())
                        .returns(transferAmount.negated(), map -> map.get(ACCOUNT_ID))
                        .returns(transferAmount, map -> map.get(ACCOUNT_ID_2)));
    }
}
