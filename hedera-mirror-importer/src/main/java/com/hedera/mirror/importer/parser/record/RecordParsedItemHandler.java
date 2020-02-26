package com.hedera.mirror.importer.parser.record;

import com.hedera.mirror.importer.domain.ContractResult;
import com.hedera.mirror.importer.domain.CryptoTransfer;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.LiveHash;
import com.hedera.mirror.importer.domain.NonFeeTransfer;
import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.parser.ParsedItemHandler;

/**
 * Handlers for items parsed during processing of record stream.
 */
public interface RecordParsedItemHandler extends ParsedItemHandler {
    void onTransaction(Transaction transaction) throws ImporterException;

    void onCryptoTransferList(CryptoTransfer cryptoTransfer) throws ImporterException;

    void onNonFeeTransfer(NonFeeTransfer nonFeeTransfer) throws ImporterException;

    void onTopicMessage(TopicMessage topicMessage) throws ImporterException;

    void onContractResult(ContractResult contractResult) throws ImporterException;

    void onFileData(FileData fileData) throws ImporterException;

    void onLiveHash(LiveHash liveHash) throws ImporterException;
}
