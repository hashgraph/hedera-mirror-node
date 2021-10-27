package com.hedera.mirror.importer.parser.record.pubsub;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.integration.MessageTimeoutException;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;

import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.parser.domain.PubSubMessage;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.parser.record.NonFeeTransferExtractionStrategy;
import com.hedera.mirror.importer.parser.record.RecordItemListener;
import com.hedera.mirror.importer.parser.record.transactionhandler.TransactionHandler;
import com.hedera.mirror.importer.parser.record.transactionhandler.TransactionHandlerFactory;
import com.hedera.mirror.importer.repository.FileDataRepository;
import com.hedera.mirror.importer.util.Utility;

@Log4j2
@Named
@RequiredArgsConstructor
@ConditionalOnPubSubRecordParser
public class PubSubRecordItemListener implements RecordItemListener {

    private final PubSubProperties pubSubProperties;
    private final MessageChannel pubsubOutputChannel;
    private final AddressBookService addressBookService;
    private final FileDataRepository fileDataRepository;
    private final NonFeeTransferExtractionStrategy nonFeeTransfersExtractor;
    private final TransactionHandlerFactory transactionHandlerFactory;

    @Override
    public void onItem(RecordItem recordItem) throws ImporterException {
        TransactionBody body = recordItem.getTransactionBody();
        TransactionRecord txRecord = recordItem.getRecord();
        TransactionTypeEnum transactionType = TransactionTypeEnum.of(recordItem.getTransactionType());
        TransactionHandler transactionHandler = transactionHandlerFactory.get(transactionType);
        log.trace("Storing transaction body: {}", () -> Utility.printProtoMessage(body));
        long consensusTimestamp = Utility.timeStampInNanos(txRecord.getConsensusTimestamp());
        EntityId entity = transactionHandler.getEntity(recordItem);
        PubSubMessage pubSubMessage = buildPubSubMessage(consensusTimestamp, entity, recordItem);
        try {
            sendPubSubMessage(pubSubMessage);
        } catch (Exception e) {
            // This will make RecordFileParser to retry whole file, thus sending duplicates of previous transactions
            // in this file. In needed in future, this can be optimized to resend only the txns with consensusTimestamp
            // greater than that of last correctly sent txn.
            throw new ParserException("Error sending transaction to pubsub", e);
        }
        log.debug("Published transaction : {}", consensusTimestamp);

        if (addressBookService.isAddressBook(entity)) {
            FileID fileID = null;
            byte[] fileBytes = null;

            if (body.hasFileAppend()) {
                fileID = body.getFileAppend().getFileID();
                fileBytes = body.getFileAppend().getContents().toByteArray();
            } else if (body.hasFileCreate()) {
                fileID = txRecord.getReceipt().getFileID();
                fileBytes = body.getFileCreate().getContents().toByteArray();
            } else if (body.hasFileUpdate()) {
                fileID = body.getFileUpdate().getFileID();
                fileBytes = body.getFileUpdate().getContents().toByteArray();
            }

            FileData fileData = new FileData(consensusTimestamp, fileBytes, EntityId.of(fileID), recordItem
                    .getTransactionType());
            fileDataRepository.save(fileData);
            addressBookService.update(fileData);
        }
    }

    // Publishes the PubSubMessage while retrying if a retryable error is encountered.
    private void sendPubSubMessage(PubSubMessage pubSubMessage) {
        for (int numTries = 0; numTries < pubSubProperties.getMaxSendAttempts(); numTries++) {
            try {
                pubsubOutputChannel.send(MessageBuilder
                        .withPayload(pubSubMessage)
                        .setHeader("consensusTimestamp", pubSubMessage.getConsensusTimestamp())
                        .build());
            } catch (MessageTimeoutException e) {
                log.warn("Attempt {} to send message to PubSub timed out", numTries + 1);
                continue;
            }
            return;
        }
    }

    private PubSubMessage buildPubSubMessage(long consensusTimestamp, EntityId entity, RecordItem recordItem) {
        var nonFeeTransfers = addNonFeeTransfers(recordItem.getTransactionBody(), recordItem.getRecord());
        return new PubSubMessage(consensusTimestamp, entity, recordItem.getTransactionType(),
                new PubSubMessage.Transaction(recordItem.getTransactionBody(), recordItem.getSignatureMap()),
                recordItem.getRecord(), nonFeeTransfers);
    }

    /**
     * Set of explicit transfers in the transaction.
     */
    private Iterable<AccountAmount> addNonFeeTransfers(TransactionBody body, TransactionRecord transactionRecord) {
        var nonFeeTransfers = nonFeeTransfersExtractor.extractNonFeeTransfers(body, transactionRecord);
        if (!nonFeeTransfers.iterator().hasNext()) { // return null if empty
            return null;
        }
        return nonFeeTransfers;
    }
}

