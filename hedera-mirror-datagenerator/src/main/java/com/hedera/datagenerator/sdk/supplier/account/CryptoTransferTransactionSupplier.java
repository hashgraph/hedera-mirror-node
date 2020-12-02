package com.hedera.datagenerator.sdk.supplier.account;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import lombok.Data;

import com.hedera.datagenerator.common.Utility;
import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.account.TransferTransaction;
import com.hedera.hashgraph.sdk.token.TokenId;

@Data
public class CryptoTransferTransactionSupplier implements TransactionSupplier<TransferTransaction> {

    @Min(1)
    private long amount = 1;

    @Min(1)
    private long maxTransactionFee = 1_000_000;

    @NotBlank
    private String recipientAccountId;

    @NotBlank
    private String senderAccountId;

    private String tokenId;

    private TransferType transferType = TransferType.CRYPTO;

    @Override
    public TransferTransaction get() {

        AccountId recipientId = AccountId.fromString(recipientAccountId);
        AccountId senderId = AccountId.fromString(senderAccountId);
        TokenId transferTokenId = TokenId.fromString(tokenId);

        TransferTransaction transferTransaction = new TransferTransaction()
                .setMaxTransactionFee(maxTransactionFee);

        switch (transferType) {
            case CRYPTO:
                addCryptoTransfers(transferTransaction, recipientId, senderId);
                transferTransaction.setTransactionMemo(Utility.getMemo("Mirror node created test crypto transfer"));
                break;
            case TOKEN:
                addTokenTransfers(transferTransaction, transferTokenId, recipientId, senderId);
                transferTransaction.setTransactionMemo(Utility.getMemo("Mirror node created test token transfer"));
                break;
            case BOTH:
                addTokenTransfers(transferTransaction, transferTokenId, recipientId, senderId);
                addCryptoTransfers(transferTransaction, recipientId, senderId);
                transferTransaction
                        .setTransactionMemo(Utility.getMemo("Mirror node created test crypto and token transfer"));
                break;
        }
        return transferTransaction;
    }

    private void addCryptoTransfers(TransferTransaction transferTransaction, AccountId recipientId,
                                    AccountId senderId) {
        transferTransaction
                .addHbarTransfer(recipientId, amount)
                .addHbarTransfer(senderId, Math.negateExact(amount));
    }

    private void addTokenTransfers(TransferTransaction transferTransaction, TokenId token, AccountId recipientId,
                                   AccountId senderId) {
        transferTransaction
                .addTokenTransfer(token, recipientId, amount)
                .addTokenTransfer(token, senderId, Math.negateExact(amount));
    }

    public enum TransferType {
        CRYPTO, TOKEN, BOTH
    }
}
