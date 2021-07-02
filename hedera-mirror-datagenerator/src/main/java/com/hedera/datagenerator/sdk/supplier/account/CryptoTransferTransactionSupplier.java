package com.hedera.datagenerator.sdk.supplier.account;

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

import java.util.stream.Stream;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.Getter;

import com.hedera.datagenerator.common.Utility;
import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.NftId;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TransferTransaction;

@Data
public class CryptoTransferTransactionSupplier implements TransactionSupplier<TransferTransaction> {

    @Min(1)
    private long amount = 1;

    @Min(1)
    private long maxTransactionFee = 10_000_000L;

    @NotBlank
    private String recipientAccountId;

    @NotBlank
    private String senderAccountId;

    private String tokenId;

    @NotNull
    private TransferType transferType = TransferType.CRYPTO;

    private AccountId recipientId = AccountId.fromString(recipientAccountId);

    private AccountId senderId = AccountId.fromString(senderAccountId);

    @Getter(lazy = true)
    private final TokenId transferTokenId = TokenId.fromString(tokenId);

    @Override
    public TransferTransaction get() {

        TransferTransaction transferTransaction = new TransferTransaction()
                .setMaxTransactionFee(Hbar.fromTinybars(maxTransactionFee));

        switch (transferType) {
            case CRYPTO:
                addCryptoTransfers(transferTransaction, getRecipientId(), getSenderId());
                transferTransaction.setTransactionMemo(Utility.getMemo("Mirror node created test crypto transfer"));
                break;
            case TOKEN:
                addTokenTransfers(transferTransaction, getTransferTokenId(), getRecipientId(), getSenderId());
                transferTransaction.setTransactionMemo(Utility.getMemo("Mirror node created test token transfer"));
                break;
            case NFT:
                addNftTransfers(transferTransaction, getTransferTokenId(), getRecipientId(), getSenderId());
                transferTransaction.setTransactionMemo(Utility.getMemo("Mirror node created test nft transfer"));
                break;
            case ALL:
                addTokenTransfers(transferTransaction, getTransferTokenId(), getRecipientId(), getSenderId());
                addCryptoTransfers(transferTransaction, getRecipientId(), getSenderId());
                transferTransaction
                        .setTransactionMemo(Utility.getMemo("Mirror node created test crypto and token transfer"));
                break;
        }
        return transferTransaction;
    }

    private void addCryptoTransfers(TransferTransaction transferTransaction, AccountId recipientId,
                                    AccountId senderId) {
        Hbar hbarAmount = Hbar.fromTinybars(amount);
        transferTransaction
                .addHbarTransfer(recipientId, hbarAmount)
                .addHbarTransfer(senderId, hbarAmount.negated());
    }

    private void addTokenTransfers(TransferTransaction transferTransaction, TokenId token, AccountId recipientId,
                                   AccountId senderId) {
        transferTransaction
                .addTokenTransfer(token, recipientId, amount)
                .addTokenTransfer(token, senderId, Math.negateExact(amount));
    }

    private void addNftTransfers(TransferTransaction transferTransaction, TokenId token, AccountId recipientId,
                                 AccountId senderId) {
        Stream.iterate(1, n -> n + 1)
                .limit(amount + 1)
                .forEach(x -> transferTransaction.addTokenNftTransfer(new NftId(token, x), senderId, recipientId));
        swapAccounts();
    }

    private synchronized void swapAccounts() {
        AccountId temp = recipientId;
        recipientId = senderId;
        senderId = temp;
    }

    public enum TransferType {
        CRYPTO, TOKEN, NFT, ALL
    }
}
