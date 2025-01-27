package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import jakarta.inject.Named;

@Named
public class CryptoDeleteLiveHashTransformer extends AbstractBlockItemTransformer{

    @Override
    protected void updateTransactionRecord(BlockItem blockItem, TransactionRecord.Builder transactionRecordBuilder) {
        var transactionBody = blockItem.transaction().getBody();
        if (!transactionBody.hasCryptoAddLiveHash()){
            return;
        }
        var liveHash = transactionBody.getCryptoDeleteLiveHash().getLiveHashToDelete();
        transactionRecordBuilder.setMemoBytes(liveHash);
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CRYPTODELETELIVEHASH;
    }
}
