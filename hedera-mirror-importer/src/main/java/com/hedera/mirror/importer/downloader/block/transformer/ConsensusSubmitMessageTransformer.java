package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.hapi.block.stream.output.protoc.StateIdentifier;
import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import jakarta.inject.Named;

@Named
public class ConsensusSubmitMessageTransformer extends AbstractBlockItemTransformer {
    @Override
    protected void updateTransactionRecord(BlockItem blockItem, TransactionRecord.Builder transactionRecordBuilder) {

        if(!blockItem.successful()) {
            return;
        }

        for (var stateChange : blockItem.stateChanges()) {
            for (var change : stateChange.getStateChangesList()) {
                if (change.getStateId() == StateIdentifier.STATE_ID_TOPICS.getNumber() && change.hasMapUpdate()) {
                    var value = change.getMapUpdate().getValue();
                    if (value.hasTopicValue()) {
                        transactionRecordBuilder.getReceiptBuilder().setTopicRunningHash(value.getTopicValue().getRunningHash());
                        transactionRecordBuilder.getReceiptBuilder().setTopicSequenceNumber(value.getTopicValue().getSequenceNumber());
                        return;
                    }
                }
            }
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CONSENSUSSUBMITMESSAGE;
    }
}
