package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.mirror.common.domain.transaction.TransactionType;
import jakarta.inject.Named;

@Named
public class FileDeleteTransformer extends AbstractBlockItemTransformer {

    @Override
    public TransactionType getType() {
        return TransactionType.FILEDELETE;
    }
}
