package com.hedera.mirror.importer.parser.record.transactionhandler;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.domain.transaction.UtilRandomGenerate;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;

@Log4j2
@Named
@RequiredArgsConstructor
class RandomGenerateTransactionHandler implements TransactionHandler {

    private final EntityListener entityListener;

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return null;
    }

    @Override
    public TransactionType getType() {
        return TransactionType.RANDOMGENERATE;
    }

    @Override
    public void updateTransaction(Transaction transaction, RecordItem recordItem) {
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        var range = recordItem.getTransactionBody().getRandomGenerate().getRange();
        if (!recordItem.isSuccessful() || range < 0) {
            return;
        }

        var transactionRecord = recordItem.getRecord();
        var randomGenerate = new UtilRandomGenerate();
        randomGenerate.setConsensusTimestamp(consensusTimestamp);
        randomGenerate.setRange(range);
        switch(transactionRecord.getEntropyCase()) {
            case PSEUDORANDOM_BYTES:
                randomGenerate.setPseudorandomBytes(DomainUtils.toBytes(transactionRecord.getPseudorandomBytes()));
                break;
            case PSEUDORANDOM_NUMBER:
                randomGenerate.setPseudorandomNumber(transactionRecord.getPseudorandomNumber());
                break;
            default:
                log.warn("Unsupported entropy case {} at consensus timestamp {}",
                        transactionRecord.getEntropyCase(), consensusTimestamp);
                return;
        }

        entityListener.onRandomGenerate(randomGenerate);
    }
}
