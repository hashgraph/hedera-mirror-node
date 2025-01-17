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

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.mirror.common.domain.DigestAlgorithm;
import com.hedera.mirror.common.domain.transaction.BlockFile;
import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.exception.ProtobufException;
import com.hedera.mirror.importer.downloader.StreamFileTransformer;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import jakarta.inject.Named;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.util.Version;

@Named
@RequiredArgsConstructor
public class BlockFileTransformer implements StreamFileTransformer<RecordFile, BlockFile> {

    private final MessageDigest digest = createDigest();
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

        var softwareVersion = blockHeader.getSoftwareVersion();
        return recordFileBuilder
                .bytes(blockFile.getBytes())
                .consensusEnd(blockFile.getConsensusEnd())
                .consensusStart(blockFile.getConsensusStart())
                .count(blockFile.getCount())
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
                .previousHash(blockFile.getPreviousHash())
                .roundEnd(blockFile.getRoundEnd())
                .roundStart(blockFile.getRoundStart())
                .size(blockFile.getSize())
                .softwareVersionMajor(softwareVersion.getMajor())
                .softwareVersionMinor(softwareVersion.getMinor())
                .softwareVersionPatch(softwareVersion.getPatch())
                .version(blockFile.getVersion())
                .build();
    }

    private List<RecordItem> getRecordItems(Collection<BlockItem> blockItems, Version hapiVersion) {
        RecordItem previousItem = null;
        var recordItems = new ArrayList<RecordItem>(blockItems.size());
        for (var blockItem : blockItems) {
            var transaction = blockItem.transaction();
            var signedBytes = transaction.getSignedTransactionBytes();
            var transactionHash = calculateTransactionHash(signedBytes.toByteArray());
            var signedTransaction = parseSignedTransaction(signedBytes);
            var transactionBody = parseTransactionBody(signedTransaction.getBodyBytes());
            int transactionTypeValue = transactionBody.getDataCase().getNumber();
            var transactionType = TransactionType.of(transactionTypeValue);
            var blockItemTransformer = transformerFactory.get(transactionType);
            var transactionRecord =
                    blockItemTransformer.getTransactionRecord(blockItem, transactionHash, transactionBody);

            var recordItem = RecordItem.builder()
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

    private static MessageDigest createDigest() {
        try {
            return MessageDigest.getInstance(DigestAlgorithm.SHA_384.getName());
        } catch (NoSuchAlgorithmException e) {
            throw new InvalidStreamFileException("SHA-384 algorithm not found", e);
        }
    }

    private ByteString calculateTransactionHash(byte[] signedBytes) {
        return ByteString.copyFrom(digest.digest(signedBytes));
    }

    private SignedTransaction parseSignedTransaction(ByteString signedBytes) {
        try {
            return SignedTransaction.parseFrom(signedBytes);
        } catch (InvalidProtocolBufferException e) {
            throw new ProtobufException("Error parsing signed transaction from transaction", e);
        }
    }

    private TransactionBody parseTransactionBody(ByteString signedTransactionBody) {
        try {
            return TransactionBody.parseFrom(signedTransactionBody);
        } catch (InvalidProtocolBufferException e) {
            throw new ProtobufException("Error parsing transaction body from signed transaction", e);
        }
    }
}
