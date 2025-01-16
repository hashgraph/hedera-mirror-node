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

package com.hedera.mirror.importer.downloader.block;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.BlockFile;
import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.downloader.StreamFileTransformer;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties.PersistProperties;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.data.util.Version;

@Named
@RequiredArgsConstructor
public class BlockFileTransformer implements StreamFileTransformer<RecordFile, BlockFile> {

    private final PersistProperties persistProperties = new PersistProperties();
    private final Predicate<EntityId> entityTransactionPredicate = persistProperties::shouldPersistEntityTransaction;
    private final Predicate<EntityId> contractTransactionPredicate = e -> persistProperties.isContractTransaction();

    private final TransformerFactory transformerFactory;

    @Override
    public RecordFile transform(BlockFile blockFile) {
        var blockHeader = blockFile.getBlockHeader();
        var hapiProtoVersion = blockHeader.getHapiProtoVersion();
        var major = hapiProtoVersion.getMajor();
        var minor = hapiProtoVersion.getMinor();
        var patch = hapiProtoVersion.getPatch();

        var recordFileBuilder = RecordFile.builder();
        var blockItems = blockFile.getItems();
        if (!blockItems.isEmpty()) {
            var hapiVersion = new Version(major, minor, patch);
            recordFileBuilder.items(getRecordItems(blockItems, hapiVersion));
        }

        return recordFileBuilder
                .bytes(blockFile.getBytes())
                .consensusEnd(blockFile.getConsensusEnd())
                .consensusStart(blockFile.getConsensusStart())
                .count((long) blockItems.size())
                .digestAlgorithm(blockFile.getDigestAlgorithm())
                .hapiVersionMajor(major)
                .hapiVersionMinor(minor)
                .hapiVersionPatch(patch)
                .loadEnd(blockFile.getLoadEnd())
                .loadStart(blockFile.getLoadStart())
                .hash(blockFile.getHash())
                .index(blockHeader.getNumber())
                .name(blockFile.getName())
                .nodeId(blockFile.getNodeId())
                .previousHash(blockHeader.getPreviousBlockHash().toStringUtf8())
                .roundEnd(blockFile.getRoundEnd())
                .roundStart(blockFile.getRoundStart())
                .size(blockFile.getSize())
                .softwareVersionMajor(major)
                .softwareVersionMinor(minor)
                .softwareVersionPatch(patch)
                .version(blockFile.getVersion())
                .build();
    }

    private List<RecordItem> getRecordItems(Collection<BlockItem> blockItems, Version hapiVersion) {
        RecordItem previousItem = null;
        var recordItems = new ArrayList<RecordItem>(blockItems.size());
        for (var blockItem : blockItems) {
            var transaction = blockItem.transaction();
            var transactionBody = getTransactionBody(transaction);
            int transactionTypeValue = transactionBody.getDataCase().getNumber();
            var transactionType = TransactionType.of(transactionTypeValue);
            var blockItemTransformer = transformerFactory.get(transactionType);
            var transactionRecord = blockItemTransformer.getTransactionRecord(blockItem, transactionBody);
            var recordItem = RecordItem.builder()
                    .contractTransactionPredicate(contractTransactionPredicate)
                    .entityTransactionPredicate(entityTransactionPredicate)
                    .hapiVersion(hapiVersion)
                    .previous(previousItem)
                    .transaction(transaction)
                    .transactionIndex(recordItems.size())
                    .transactionRecord(transactionRecord)
                    .build();
            recordItems.add(recordItem);
            previousItem = recordItem;
        }

        return recordItems;
    }

    @SneakyThrows
    private TransactionBody getTransactionBody(Transaction transaction) {
        var signedTransaction = SignedTransaction.parseFrom(transaction.getSignedTransactionBytes());
        return TransactionBody.parseFrom(signedTransaction.getBodyBytes());
    }
}
