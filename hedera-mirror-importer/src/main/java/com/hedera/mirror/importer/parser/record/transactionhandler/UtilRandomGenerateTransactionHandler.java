package com.hedera.mirror.importer.parser.record.transactionhandler;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.UtilRandomGenerate;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;

import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import javax.inject.Named;

@Log4j2
@Named
@RequiredArgsConstructor
class UtilRandomGenerateTransactionHandler implements TransactionHandler {

    private final EntityListener entityListener;

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return null;
    }

    @Override
    public TransactionType getType() {
        return TransactionType.UTILRANDOMGENERATE;
    }

    @Override
    public void updateTransaction(Transaction transaction, RecordItem recordItem) {
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        var range = recordItem.getTransactionBody().getRandomGenerate().getRange();
        var transactionRecord = recordItem.getRecord();

        var utilRandomGenerate = new UtilRandomGenerate();
        utilRandomGenerate.setConsensusTimestamp(consensusTimestamp);
        utilRandomGenerate.setRange(range);
        utilRandomGenerate.setPseudorandomNumber(transactionRecord.getPseudorandomNumber());
        utilRandomGenerate.setPseudorandomBytes(DomainUtils.toBytes(transactionRecord.getPseudorandomBytes()));

        entityListener.onUtilRandomGenerate(utilRandomGenerate);
    }
}
