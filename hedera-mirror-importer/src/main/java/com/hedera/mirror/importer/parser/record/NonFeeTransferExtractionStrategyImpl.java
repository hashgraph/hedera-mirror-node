package com.hedera.mirror.importer.parser.record;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2020 Hedera Hashgraph, LLC
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

import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.springframework.stereotype.Component;
import java.util.LinkedList;

/**
 * Extract non_fee_transfers requested by a transaction into an iterable list of transfers.
 */
@Component
public class NonFeeTransferExtractionStrategyImpl implements NonFeeTransferExtractionStrategy {
    /**
     * Return a list of non-fee transfer amounts for certain transaction types. These are explicitly requested
     * transfers.
     * @param payerAccountId
     * @param body
     * @param transactionRecord
     * @return
     */
    @Override
    public Iterable<AccountAmount> extractNonFeeTransfers(AccountID payerAccountId, TransactionBody body,
                                                          TransactionRecord transactionRecord) {
        LinkedList<AccountAmount> result = new LinkedList<>();
        if (body.hasCryptoTransfer()) {
            for (var accountAmount : body.getCryptoTransfer().getTransfers().getAccountAmountsList()) {
                result.add(accountAmount);
            }
        } else if (body.hasCryptoCreateAccount()) {
            var amount = body.getCryptoCreateAccount().getInitialBalance();
            result.add(AccountAmount.newBuilder().setAccountID(payerAccountId).setAmount(0 - amount).build());
            if (ResponseCodeEnum.SUCCESS == transactionRecord.getReceipt().getStatus()) {
                var newAccountId = transactionRecord.getReceipt().getAccountID();
                result.add(AccountAmount.newBuilder().setAccountID(newAccountId).setAmount(amount).build());
            }
        } else if (body.hasContractCreateInstance()) {
            var amount = body.getContractCreateInstance().getInitialBalance();
            result.add(AccountAmount.newBuilder().setAccountID(payerAccountId).setAmount(0 - amount).build());
            if (ResponseCodeEnum.SUCCESS == transactionRecord.getReceipt().getStatus()) {
                var contractAccountId = contractIdToAccountId(transactionRecord.getReceipt().getContractID());
                result.add(AccountAmount.newBuilder().setAccountID(contractAccountId).setAmount(amount).build());
            }
        } else if (body.hasContractCall()) {
            var amount = body.getContractCall().getAmount();
            var contractAccountId = contractIdToAccountId(body.getContractCall().getContractID());
            result.add(AccountAmount.newBuilder().setAccountID(contractAccountId).setAmount(amount).build());
            result.add(AccountAmount.newBuilder().setAccountID(payerAccountId).setAmount(0 - amount).build());
        }
        return result;
    }

    private AccountID contractIdToAccountId(ContractID contractId) {
        return AccountID.newBuilder()
                .setShardNum(contractId.getShardNum())
                .setRealmNum(contractId.getRealmNum())
                .setAccountNum(contractId.getContractNum())
                .build();
    }
}
