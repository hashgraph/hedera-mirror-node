/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.record.transactionhandler;

import com.hedera.mirror.common.domain.transaction.Prng;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import jakarta.inject.Named;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;

@CustomLog
@Named
@RequiredArgsConstructor
class UtilPrngTransactionHandler extends AbstractTransactionHandler {

    private final EntityListener entityListener;

    @Override
    public TransactionType getType() {
        return TransactionType.UTILPRNG;
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        var range = recordItem.getTransactionBody().getUtilPrng().getRange();
        if (!recordItem.isSuccessful()) {
            return;
        }

        var transactionRecord = recordItem.getTransactionRecord();
        var prng = new Prng();
        prng.setConsensusTimestamp(consensusTimestamp);
        prng.setPayerAccountId(recordItem.getPayerAccountId().getId());
        prng.setRange(range);
        switch (transactionRecord.getEntropyCase()) {
            case PRNG_BYTES -> prng.setPrngBytes(DomainUtils.toBytes(transactionRecord.getPrngBytes()));
            case PRNG_NUMBER -> prng.setPrngNumber(transactionRecord.getPrngNumber());
            default -> {
                log.warn(
                        "Unsupported entropy case {} at consensus timestamp {}",
                        transactionRecord.getEntropyCase(),
                        consensusTimestamp);
                return;
            }
        }

        entityListener.onPrng(prng);
    }
}
