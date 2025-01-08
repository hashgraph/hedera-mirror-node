/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.reader.block;

import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.EVENT_TRANSACTION;
import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.STATE_CHANGES;
import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.TRANSACTION_OUTPUT;
import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.TRANSACTION_RESULT;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.hapi.block.stream.protoc.Block;
import com.hedera.hapi.block.stream.protoc.BlockItem;
import com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase;
import com.hedera.mirror.common.domain.transaction.BlockFile;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;
import com.hedera.mirror.importer.exception.StreamFileReaderException;
import com.hederahashgraph.api.proto.java.Transaction;
import jakarta.inject.Named;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.CustomLog;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.apache.commons.codec.binary.Hex;

@CustomLog
@Named
public class ProtoBlockFileReader implements BlockFileReader {

    @Override
    public BlockFile read(StreamFileData streamFileData) {
        try (var inputStream = streamFileData.getInputStream()) {
            var block = Block.parseFrom(inputStream);
            var context = new ReaderContext(block.getItemsList(), streamFileData.getFilename());
            var blockFile = context.getBlockFile()
                    .loadStart(streamFileData.getStreamFilename().getTimestamp());

            var blockItem = context.readBlockItemFor(ItemCase.RECORD_FILE);
            if (blockItem != null) {
                return blockFile.recordFileItem(blockItem.getRecordFile()).build();
            }

            readBlockHeader(context);
            readRounds(context);
            readTrailingStateChanges(context);
            readBlockProof(context);

            String blockHash = context.getBlockRootHashDigest().digest();
            log.info(
                    "previous hash - {}",
                    Hex.encodeHexString(context.getBlockRootHashDigest().getPreviousHash()));
            log.info("current hash - {}", blockHash);

            return blockFile.hash(blockHash).build();
        } catch (Exception e) {
            if (e instanceof InvalidStreamFileException invalidStreamFileException) {
                throw invalidStreamFileException;
            } else if (e instanceof StreamFileReaderException streamFileReaderException) {
                throw streamFileReaderException;
            }

            throw new InvalidStreamFileException(streamFileData.getFilename(), e);
        }
    }

    private void readBlockHeader(ReaderContext context) {
        var blockItem = context.readBlockItemFor(ItemCase.BLOCK_HEADER);
        if (blockItem == null) {
            throw new InvalidStreamFileException(
                    String.format("Missing block header in block file %s", context.getFilename()));
        }

        var blockHeader = blockItem.getBlockHeader();
        context.getBlockFile().blockHeader(blockHeader);
        context.getBlockRootHashDigest()
                .setPreviousHash(blockHeader.getPreviousBlockHash().toByteArray());
    }

    private void readBlockProof(ReaderContext context) {
        var blockItem = context.readBlockItemFor(ItemCase.BLOCK_PROOF);
        if (blockItem == null) {
            throw new InvalidStreamFileException(
                    String.format("Missing block proof in file %s", context.getFilename()));
        }

        var blockProof = blockItem.getBlockProof();
        context.getBlockFile().blockProof(blockProof);
        context.getBlockRootHashDigest()
                .setStartOfBlockStateHash(
                        blockProof.getStartOfBlockStateRootHash().toByteArray());
    }

    private void readEvents(ReaderContext context) {
        while (context.hasNextBlockItem() && context.readBlockItemFor(ItemCase.EVENT_HEADER) != null) {
            readEventTransactions(context);
        }
    }

    private void readEventTransactions(ReaderContext context) {
        BlockItem protoBlockItem;
        while (context.hasNextBlockItem() && (protoBlockItem = context.readBlockItemFor(EVENT_TRANSACTION)) != null) {
            try {
                var eventTransaction = protoBlockItem.getEventTransaction();
                var transaction = eventTransaction.hasApplicationTransaction()
                        ? Transaction.parseFrom(eventTransaction.getApplicationTransaction())
                        : null;

                var transactionResultProtoBlockItem = context.readBlockItemFor(TRANSACTION_RESULT);
                if (transactionResultProtoBlockItem == null) {
                    throw new InvalidStreamFileException(
                            String.format("Missing transaction result in block file %s", context.getFilename()));
                }

                var transactionOutputs = new ArrayList<TransactionOutput>();
                while ((protoBlockItem = context.readBlockItemFor(TRANSACTION_OUTPUT)) != null) {
                    transactionOutputs.add(protoBlockItem.getTransactionOutput());
                }

                var stateChangesList = new ArrayList<StateChanges>();
                while ((protoBlockItem = context.readBlockItemFor(STATE_CHANGES)) != null) {
                    var stateChanges = protoBlockItem.getStateChanges();
                    stateChangesList.add(stateChanges);
                }

                if (transaction != null) {
                    var blockItem = com.hedera.mirror.common.domain.transaction.BlockItem.builder()
                            .transaction(transaction)
                            .transactionResult(transactionResultProtoBlockItem.getTransactionResult())
                            .transactionOutput(Collections.unmodifiableList(transactionOutputs))
                            .stateChanges(Collections.unmodifiableList(stateChangesList))
                            .build();
                    context.getBlockFile().addItem(blockItem);
                }
            } catch (InvalidProtocolBufferException e) {
                throw new InvalidStreamFileException(
                        String.format("Failed to deserialize Transaction from block file %s", context.getFilename()),
                        e);
            }
        }
    }

    private void readRounds(ReaderContext context) {
        BlockItem blockItem;
        while (context.hasNextBlockItem() && (blockItem = context.readBlockItemFor(ItemCase.ROUND_HEADER)) != null) {
            context.getBlockFile().startNewRound(blockItem.getRoundHeader().getRoundNumber());
            readEvents(context);
        }
    }

    /**
     * Read trailing state changes. There is no marker to distinguish between transactional and non-transactional
     * statechanges. This function reads those trailing non-transactional statechanges without immediately preceding
     * transactional statechanges.
     *
     * @param context - The reader context
     */
    private void readTrailingStateChanges(ReaderContext context) {
        while (context.hasNextBlockItem() && context.readBlockItemFor(STATE_CHANGES) != null) {
            // do nothing
        }
    }

    @Value
    private static class ReaderContext {
        private BlockFile.BlockFileBuilder blockFile;
        private List<BlockItem> blockItems;
        private BlockRootHashDigest blockRootHashDigest;
        private String filename;

        @NonFinal
        private int index;

        ReaderContext(@NotNull List<BlockItem> blockItems, @NotNull String filename) {
            this.blockFile = BlockFile.builder();
            this.blockItems = blockItems;
            this.blockRootHashDigest = new BlockRootHashDigest();
            this.filename = filename;
        }

        public boolean hasNextBlockItem() {
            return index < blockItems.size();
        }

        /**
         * Reads the current block item if it matches the itemCase, and advances the index. If no match, index is not
         * changed
         * @param itemCase - block item case
         * @return The matching block item, or null
         */
        public BlockItem readBlockItemFor(BlockItem.ItemCase itemCase) {
            var blockItem = blockItems.get(index);
            if (blockItem.getItemCase() != itemCase) {
                return null;
            }

            index++;
            switch (itemCase) {
                case STATE_CHANGES, TRANSACTION_OUTPUT, TRANSACTION_RESULT -> blockRootHashDigest.addOutputBlockItem(
                        blockItem);
                case EVENT_HEADER, EVENT_TRANSACTION -> blockRootHashDigest.addInputBlockItem(blockItem);
            }

            return blockItem;
        }
    }
}
