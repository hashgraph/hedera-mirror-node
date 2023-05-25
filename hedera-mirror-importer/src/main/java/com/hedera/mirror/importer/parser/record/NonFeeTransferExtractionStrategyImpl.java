/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.importer.parser.record;

import static com.hedera.mirror.importer.util.Utility.RECOVERABLE_ERROR;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.importer.domain.EntityIdService;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collections;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;

/**
 * Non-fee transfers are explicitly requested transfers. This implementation extracts non_fee_transfer requested by a
 * transaction into an iterable of transfers.
 */
@CustomLog
@Named
@RequiredArgsConstructor
public class NonFeeTransferExtractionStrategyImpl implements NonFeeTransferExtractionStrategy {

    private final EntityIdService entityIdService;

    /**
     * @return iterable of transfers. If transaction has no non-fee transfers, then iterable will have no elements.
     */
    @Override
    public Iterable<AccountAmount> extractNonFeeTransfers(TransactionBody body, TransactionRecord transactionRecord) {
        AccountID payerAccountId = body.getTransactionID().getAccountID();

        if (body.hasCryptoTransfer()) {
            return body.getCryptoTransfer().getTransfers().getAccountAmountsList();
        } else if (body.hasCryptoCreateAccount()) {
            return extractForCreateEntity(
                    body.getCryptoCreateAccount().getInitialBalance(),
                    payerAccountId,
                    transactionRecord.getReceipt().getAccountID(),
                    transactionRecord);
        } else if (body.hasContractCreateInstance()) {
            return extractForCreateEntity(
                    body.getContractCreateInstance().getInitialBalance(),
                    payerAccountId,
                    contractIdToAccountId(transactionRecord.getReceipt().getContractID()),
                    transactionRecord);
        } else if (body.hasContractCall()) {

            EntityId contractId = entityIdService
                    .lookup(
                            transactionRecord.getReceipt().getContractID(),
                            body.getContractCall().getContractID())
                    .orElse(EntityId.EMPTY);
            if (EntityId.isEmpty(contractId)) {
                log.error(RECOVERABLE_ERROR + "Contract ID not found at {}", transactionRecord.getConsensusTimestamp());
                return Collections.emptyList();
            }

            var result = new ArrayList<AccountAmount>();
            var amount = body.getContractCall().getAmount();

            var contractAccountId = AccountID.newBuilder()
                    .setShardNum(contractId.getShardNum())
                    .setRealmNum(contractId.getRealmNum())
                    .setAccountNum(contractId.getEntityNum())
                    .build();
            result.add(AccountAmount.newBuilder()
                    .setAccountID(contractAccountId)
                    .setAmount(amount)
                    .setIsApproval(false)
                    .build());
            result.add(AccountAmount.newBuilder()
                    .setAccountID(payerAccountId)
                    .setAmount(-amount)
                    .setIsApproval(false)
                    .build());
            return result;
        } else {
            return Collections.emptyList();
        }
    }

    private Iterable<AccountAmount> extractForCreateEntity(
            long initialBalance, AccountID payerAccountId, AccountID createdEntity, TransactionRecord txRecord) {
        var result = new ArrayList<AccountAmount>();
        result.add(AccountAmount.newBuilder()
                .setAccountID(payerAccountId)
                .setAmount(-initialBalance)
                .setIsApproval(false)
                .build());

        if (ResponseCodeEnum.SUCCESS == txRecord.getReceipt().getStatus()) {
            result.add(AccountAmount.newBuilder()
                    .setAccountID(createdEntity)
                    .setAmount(initialBalance)
                    .setIsApproval(false)
                    .build());
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
