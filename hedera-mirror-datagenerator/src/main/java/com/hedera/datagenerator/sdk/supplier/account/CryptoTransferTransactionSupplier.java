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

import java.util.Arrays;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

import com.hedera.datagenerator.common.Utility;
import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.datagenerator.sdk.supplier.TransactionSupplierException;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.account.TransferTransaction;
import com.hedera.hashgraph.sdk.token.TokenId;

@Builder
@Value
public class CryptoTransferTransactionSupplier implements TransactionSupplier<TransferTransaction> {

    private static final List<String> requiredFields = Arrays.asList("recipientAccountId", "senderAccountId");

    //Required
    private final String recipientAccountId;
    private final String senderAccountId;

    //Optional
    @Builder.Default
    private final long amount = 1;

    @Builder.Default
    private final long maxTransactionFee = 1_000_000;

    private final String tokenId;

    @Override
    public TransferTransaction get() {

        if (StringUtils.isBlank(recipientAccountId) || StringUtils.isBlank(senderAccountId)) {
            throw new TransactionSupplierException(this, requiredFields);
        }

        AccountId recipientId = AccountId.fromString(recipientAccountId);
        AccountId senderId = AccountId.fromString(recipientAccountId);

        TransferTransaction transferTransaction = new TransferTransaction()
                .addHbarTransfer(recipientId, amount)
                .addHbarTransfer(senderId, Math.negateExact(amount))
                .setMaxTransactionFee(maxTransactionFee)
                .setTransactionMemo(Utility.getMemo("Mirror node created test crypto transfer"));

        if (StringUtils.isNotBlank(tokenId)) {
            TokenId token = TokenId.fromString(tokenId);
            transferTransaction
                    .addTokenTransfer(token, recipientId, amount)
                    .addTokenTransfer(token, senderId, Math.negateExact(amount));
        }
        return transferTransaction;
    }
}
