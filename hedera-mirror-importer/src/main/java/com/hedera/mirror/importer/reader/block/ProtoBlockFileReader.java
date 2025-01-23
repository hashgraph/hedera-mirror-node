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

import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.BLOCK_HEADER;
import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.BLOCK_PROOF;
import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.EVENT_HEADER;
import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.EVENT_TRANSACTION;
import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.RECORD_FILE;
import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.ROUND_HEADER;
import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.STATE_CHANGES;
import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.TRANSACTION_OUTPUT;
import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.TRANSACTION_RESULT;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.hapi.block.stream.output.protoc.TransactionResult;
import com.hedera.hapi.block.stream.protoc.Block;
import com.hedera.hapi.block.stream.protoc.BlockItem;
import com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase;
import com.hedera.mirror.common.domain.DigestAlgorithm;
import com.hedera.mirror.common.domain.transaction.BlockFile;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;
import com.hederahashgraph.api.proto.java.BlockHashAlgorithm;
import com.hederahashgraph.api.proto.java.Transaction;
import jakarta.inject.Named;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Value;
import lombok.experimental.NonFinal;

@Named
public class ProtoBlockFileReader implements BlockFileReader {

    private static final int VERSION = 7;

    @Override
    public BlockFile read(StreamFileData streamFileData) {
        String filename = streamFileData.getFilename();

        try (var inputStream = streamFileData.getInputStream()) {
            var block = Block.parseFrom(inputStream);
            var context = new ReaderContext(block.getItemsList(), filename);
            var blockFileBuilder = context.getBlockFile()
                    .loadStart(streamFileData.getStreamFilename().getTimestamp())
                    .name(filename)
                    .version(VERSION);

            var blockItem = context.readBlockItemFor(RECORD_FILE);
            if (blockItem != null) {
                return blockFileBuilder
                        .recordFileItem(blockItem.getRecordFile())
                        .build();
            }

            readBlockHeader(context);
            readRounds(context);
            readTrailingStateChanges(context);
            readBlockProof(context);

            var blockFile = blockFileBuilder.build();
            var bytes = streamFileData.getBytes();
            blockFile.setBytes(bytes);
            blockFile.setCount((long) blockFile.getItems().size());
            blockFile.setHash(context.getBlockRootHashDigest().digest());
            blockFile.setSize(bytes.length);

            return blockFile;
        } catch (InvalidStreamFileException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidStreamFileException("Failed to read " + filename, e);
        }
    }

    private Long getTransactionConsensusTimestamp(TransactionResult transactionResult) {
        return DomainUtils.timestampInNanosMax(transactionResult.getConsensusTimestamp());
    }

    private void readBlockHeader(ReaderContext context) {
        var blockItem = context.readBlockItemFor(BLOCK_HEADER);
        if (blockItem == null) {
            throw new InvalidStreamFileException("Missing block header in block file " + context.getFilename());
        }

        var blockFileBuilder = context.getBlockFile();
        var blockHeader = blockItem.getBlockHeader();

        if (blockHeader.getHashAlgorithm().equals(BlockHashAlgorithm.SHA2_384)) {
            blockFileBuilder.digestAlgorithm(DigestAlgorithm.SHA_384);
        } else {
            throw new InvalidStreamFileException(String.format(
                    "Unsupported hash algorithm %s in block header of block file %s",
                    blockHeader.getHashAlgorithm(), context.getFilename()));
        }

        var previousHash = DomainUtils.toBytes(blockHeader.getPreviousBlockHash());
        blockFileBuilder.blockHeader(blockHeader);
        blockFileBuilder.index(blockHeader.getNumber());
        blockFileBuilder.previousHash(DomainUtils.bytesToHex(previousHash));
        context.getBlockRootHashDigest().setPreviousHash(previousHash);
    }

    private void readBlockProof(ReaderContext context) {
        var blockItem = context.readBlockItemFor(BLOCK_PROOF);
        if (blockItem == null) {
            throw new InvalidStreamFileException("Missing block proof in file " + context.getFilename());
        }

        var blockProof = blockItem.getBlockProof();
        context.getBlockFile().blockProof(blockProof);
        context.getBlockRootHashDigest()
                .setStartOfBlockStateHash(DomainUtils.toBytes(blockProof.getStartOfBlockStateRootHash()));
    }

    private void readEvents(ReaderContext context) {
        while (context.readBlockItemFor(EVENT_HEADER) != null) {
            readEventTransactions(context);
        }
    }

    private void readEventTransactions(ReaderContext context) {
        BlockItem protoBlockItem;
        while ((protoBlockItem = context.readBlockItemFor(EVENT_TRANSACTION)) != null) {
            try {
                var eventTransaction = protoBlockItem.getEventTransaction();
                var transaction = eventTransaction.hasApplicationTransaction()
                        ? Transaction.parseFrom(eventTransaction.getApplicationTransaction())
                        : null;

                var transactionResultProtoBlockItem = context.readBlockItemFor(TRANSACTION_RESULT);
                if (transactionResultProtoBlockItem == null) {
                    throw new InvalidStreamFileException(
                            "Missing transaction result in block file " + context.getFilename());
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
                    var transactionResult = transactionResultProtoBlockItem.getTransactionResult();
                    var blockItem = com.hedera.mirror.common.domain.transaction.BlockItem.builder()
                            .transaction(transaction)
                            .transactionResult(transactionResult)
                            .transactionOutput(Collections.unmodifiableList(transactionOutputs))
                            .stateChanges(Collections.unmodifiableList(stateChangesList))
                            .build();
                    context.getBlockFile()
                            .item(blockItem)
                            .onNewTransaction(getTransactionConsensusTimestamp(transactionResult));
                }
            } catch (InvalidProtocolBufferException e) {
                throw new InvalidStreamFileException(
                        "Failed to deserialize Transaction from block file " + context.getFilename(), e);
            }
        }
    }

    private void readRounds(ReaderContext context) {
        BlockItem blockItem;
        while ((blockItem = context.readBlockItemFor(ROUND_HEADER)) != null) {
            context.getBlockFile().onNewRound(blockItem.getRoundHeader().getRoundNumber());
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
        while (context.readBlockItemFor(STATE_CHANGES) != null) {
            // read all trailing statechanges
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

        /**
         * Returns the current block item if it matches the itemCase, and advances the index. If no match, index is not
         * changed
         * @param itemCase - block item case
         * @return The matching block item, or null
         */
        public BlockItem readBlockItemFor(ItemCase itemCase) {
            if (index >= blockItems.size()) {
                return null;
            }

            var blockItem = blockItems.get(index);
            if (blockItem.getItemCase() != itemCase) {
                return null;
            }

            index++;
            switch (itemCase) {
                case EVENT_HEADER, EVENT_TRANSACTION -> blockRootHashDigest.addInputBlockItem(blockItem);
                case STATE_CHANGES, TRANSACTION_OUTPUT, TRANSACTION_RESULT -> blockRootHashDigest.addOutputBlockItem(
                        blockItem);
                default -> {
                    // other block items aren't considered input / output
                }
            }

            return blockItem;
        }
    }
}
