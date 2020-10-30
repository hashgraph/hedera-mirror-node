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

import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

import com.hedera.datagenerator.common.Utility;
import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.datagenerator.sdk.supplier.TransactionSupplierException;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.account.CryptoTransferTransaction;

@Builder
@Value
public class CryptoTransferTransactionSupplier implements TransactionSupplier<CryptoTransferTransaction> {

    //Required
    private final String recipientId;
    private final String senderId;

    //Optional
    @Builder.Default
    private final long amount = 1;

    @Builder.Default
    private final long maxTransactionFee = 1_000_000;

    @Override
    public CryptoTransferTransaction get() {

        if (StringUtils.isBlank(recipientId) || StringUtils.isBlank(senderId)) {
            throw new TransactionSupplierException(this.getClass()
                    .getName() + " requires a recipientId and a senderId be provided");
        }

        return new CryptoTransferTransaction()
                .addRecipient(AccountId.fromString(recipientId), amount)
                .addSender(AccountId.fromString(senderId), amount)
                .setMaxTransactionFee(maxTransactionFee)
                .setTransactionMemo(Utility.getMemo("Mirror node created test crypto transfer"));
    }
}
