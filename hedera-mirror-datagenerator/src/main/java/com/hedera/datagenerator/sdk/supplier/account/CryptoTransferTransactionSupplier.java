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

import static com.hedera.datagenerator.sdk.supplier.account.CryptoTransferTransactionSupplier.TransferType.CRYPTO;
import static com.hedera.datagenerator.sdk.supplier.account.CryptoTransferTransactionSupplier.TransferType.NFT;
import static com.hedera.datagenerator.sdk.supplier.account.CryptoTransferTransactionSupplier.TransferType.TOKEN;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
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
    private final String recipientAccountId;

    @NotBlank
    private final String senderAccountId;

    private final AtomicLong serialNumber = new AtomicLong(1); // The serial number to transfer.  Increments over time.

    private String tokenId;

    private String nftTokenId;

    @NotNull
    private Set<TransferType> transferTypes = new HashSet<>(Arrays.asList(CRYPTO));

    @Getter(lazy = true)
    private final AccountId recipientId = AccountId.fromString(recipientAccountId);

    @Getter(lazy = true)
    private final AccountId senderId = AccountId.fromString(senderAccountId);

    @Getter(lazy = true)
    private final TokenId transferTokenId = TokenId.fromString(tokenId);

    @Getter(lazy = true)
    private final TokenId transferNftTokenId = TokenId.fromString(nftTokenId);

    @Override
    public TransferTransaction get() {

        TransferTransaction transferTransaction = new TransferTransaction()
                .setMaxTransactionFee(Hbar.fromTinybars(maxTransactionFee));

        if (transferTypes.contains(CRYPTO)) {
            addCryptoTransfers(transferTransaction, getRecipientId(), getSenderId());
        }

        if (transferTypes.contains(TOKEN)) {
            addTokenTransfers(transferTransaction, getTransferTokenId(), getRecipientId(), getSenderId());
        }

        if (transferTypes.contains(NFT)) {
            addNftTransfers(transferTransaction, getTransferNftTokenId(), getRecipientId(), getSenderId());
        }

        transferTransaction
                .setTransactionMemo(Utility.getMemo("Mirror node created test transfer: " + transferTypes));

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
        for (int i = 0; i < amount; i++) {
            transferTransaction.addNftTransfer(new NftId(token, serialNumber.getAndIncrement()), senderId, recipientId);
        }
    }

    public enum TransferType {
        CRYPTO, TOKEN, NFT
    }
}
