package com.hedera.mirror.importer.parser.record.transactionhandler;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.UtilRandomGenerate;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;

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
        var transactionBody = recordItem.getTransactionBody().getRandomGenerate();
        var utilRandomGenerate = new UtilRandomGenerate();
        utilRandomGenerate.setConsensusTimestamp(consensusTimestamp);
        utilRandomGenerate.setRange(transactionBody.getRange());

        // Todo, add random number or bytes once they are provided by the transactionBody
        utilRandomGenerate.setPseudorandomNumber(8);

        entityListener.onUtilRandomGenerate(utilRandomGenerate);
    }
}
