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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import com.hedera.hapi.block.stream.output.protoc.BlockHeader;
import com.hedera.mirror.common.domain.transaction.BlockFile;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import com.hedera.mirror.importer.parser.domain.BlockItemBuilder;
import com.hedera.mirror.importer.parser.domain.RecordItemBuilder;
import com.hedera.mirror.importer.parser.domain.RecordItemBuilder.TransferType;
import com.hedera.mirror.importer.util.Utility;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@RequiredArgsConstructor
public class BlockFileTransformerTest extends ImporterIntegrationTest {

    private final BlockItemBuilder blockItemBuilder = new BlockItemBuilder();
    private final BlockFileTransformer blockFileTransformer;
    private final RecordItemBuilder recordItemBuilder = new RecordItemBuilder();

    @ParameterizedTest
    @EnumSource(value = TransferType.class)
    void cryptoTransfer(TransferType transferType) {
        // given
        var expectedRecordFileBuilder = domainBuilder.recordFile();
        var expectedRecordItem = recordItemBuilder
                .cryptoTransfer(transferType)
                // Update the hapi version to match that of the record file
                .recordItem(r -> r.hapiVersion(expectedRecordFileBuilder.get().getHapiVersion()))
                .build();
        var expectedTransactionHash = getExpectedTransactionHash(expectedRecordItem);

        var expectedRecordItem2 = recordItemBuilder
                .cryptoTransfer(transferType)
                .recordItem(r -> r.hapiVersion(expectedRecordFileBuilder.get().getHapiVersion())
                        .transactionIndex(1))
                .build();
        var expectedTransactionHash2 = getExpectedTransactionHash(expectedRecordItem2);

        var expectedRecordFile = expectedRecordFileBuilder
                .customize(r -> r.count(2L).items(List.of(expectedRecordItem, expectedRecordItem2)))
                .get();
        var blockItem = blockItemBuilder.cryptoTransfer(expectedRecordItem).build();
        var expectedFees =
                blockItem.transactionOutput().get(1).getCryptoTransfer().getAssessedCustomFeesList();
        var blockItem2 = blockItemBuilder.cryptoTransfer(expectedRecordItem2).build();
        var expectedFees2 =
                blockItem2.transactionOutput().get(1).getCryptoTransfer().getAssessedCustomFeesList();
        var blockFile = blockFileBuilder(expectedRecordFile)
                .items(List.of(blockItem, blockItem2))
                .build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);
        var iterator = recordFile.getItems().iterator();
        var recordItem = iterator.next();
        var recordItem2 = iterator.next();

        // then
        assertRecordItem(recordItem, expectedRecordItem);
        assertRecordItem(recordItem2, expectedRecordItem2);
        assertThat(recordItem.getTransactionRecord().getAssessedCustomFeesList())
                .isEqualTo(expectedFees);
        assertThat(recordItem.getPrevious()).isNull();
        assertThat(recordItem.getTransactionRecord().getTransactionHash()).isEqualTo(expectedTransactionHash);
        assertThat(recordItem2.getTransactionRecord().getAssessedCustomFeesList())
                .isEqualTo(expectedFees2);
        assertThat(recordItem2.getPrevious()).isEqualTo(recordItem);
        assertThat(recordItem2.getTransactionRecord().getTransactionHash()).isEqualTo(expectedTransactionHash2);
        assertThat(recordFile.getItems()).hasSize(2);
        assertThat(recordFile.getCount()).isEqualTo(2);
        assertRecordFile(recordFile, expectedRecordFile, blockFile.getName());
    }

    @Test
    void emptyBlockFile() {
        // given
        var expectedRecordFileBuilder = domainBuilder.recordFile();
        var expectedRecordItem = recordItemBuilder
                .cryptoTransfer()
                // Update the hapi version to match that of the record file
                .recordItem(r -> r.hapiVersion(expectedRecordFileBuilder.get().getHapiVersion()))
                .build();
        var expectedRecordFile = expectedRecordFileBuilder
                .customize(r -> r.items(List.of(expectedRecordItem)))
                .get();
        var blockFile = blockFileBuilder(expectedRecordFile).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertThat(recordFile.getItems()).isEmpty();
        assertRecordFile(recordFile, expectedRecordFile, blockFile.getName());
    }

    @Test
    void unknownTransform() {
        // given
        var expectedRecordFileBuilder = domainBuilder.recordFile();
        var expectedRecordItem = recordItemBuilder
                .unknown()
                // Update the hapi version to match that of the record file
                .recordItem(r -> r.hapiVersion(expectedRecordFileBuilder.get().getHapiVersion()))
                .build();
        var expectedRecordFile = expectedRecordFileBuilder
                .customize(r -> r.items(List.of(expectedRecordItem)))
                .get();
        var blockItem = blockItemBuilder.unknown(expectedRecordItem).build();
        var blockFile =
                blockFileBuilder(expectedRecordFile).items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);
        var recordItem = recordFile.getItems().iterator().next();

        // then
        assertRecordItem(recordItem, expectedRecordItem);
        assertThat(recordFile.getItems()).hasSize(1);
        assertThat(recordFile.getCount()).isOne();
        assertRecordFile(recordFile, expectedRecordFile, blockFile.getName());
    }

    private void assertRecordFile(RecordFile recordFile, RecordFile expectedRecordFile, String blockFileName) {
        assertThat(recordFile.getLogsBloom()).isNull();
        assertThat(recordFile.getName()).isEqualTo(blockFileName);
        assertThat(recordFile.getSidecars()).isEmpty();
        assertThat(recordFile.getSidecarCount()).isZero();
        assertThat(recordFile.getVersion()).isEqualTo(7);
        assertThat(recordFile)
                .usingRecursiveComparison()
                .ignoringFields(
                        "count",
                        "fileHash",
                        "gasUsed",
                        "items",
                        "logsBloom",
                        "name",
                        "sidecarCount",
                        "sidecars",
                        "version")
                .isEqualTo(expectedRecordFile);
    }

    private void assertRecordItem(RecordItem recordItem, RecordItem expectedRecordItem) {
        assertThat(recordItem.getTransactionRecord().getMemo())
                .isEqualTo(expectedRecordItem.getTransactionRecord().getMemo());
        assertThat(recordItem)
                .usingRecursiveComparison()
                .ignoringFields(
                        "contractTransactionPredicate",
                        "entityTransactionPredicate",
                        "previous",
                        // Memo omitted here as this compares a ByteString to a String.
                        // The end result of the parsed memo value in persistence is equivalent whether the record file
                        // contains memoBytes or a memo String value.
                        "transactionRecord.memo_",
                        "transactionRecord.receipt_.memoizedIsInitialized",
                        "transactionRecord.assessedCustomFees_",
                        "transactionRecord.scheduleRef_",
                        "transactionRecord.parentConsensusTimestamp_",
                        // Record file builder transaction hash is not generated based on transaction bytes, so these
                        // will not match
                        "transactionRecord.transactionHash_",
                        "transactionRecord.transactionID_.accountID_.memoizedHashCode",
                        "transactionRecord.transactionID_.accountID_.memoizedIsInitialized",
                        "transactionRecord.transactionID_.accountID_.memoizedSize",
                        "transactionRecord.transactionID_.memoizedIsInitialized",
                        "transactionRecord.transactionID_.memoizedSize",
                        "transactionRecord.transactionID_.transactionValidStart_.memoizedIsInitialized",
                        "transactionRecord.transactionID_.transactionValidStart_.memoizedSize")
                .isEqualTo(expectedRecordItem);
    }

    @SneakyThrows
    private ByteString getExpectedTransactionHash(RecordItem recordItem) {
        var digest = MessageDigest.getInstance("SHA-384");
        return ByteString.copyFrom(digest.digest(
                recordItem.getTransaction().getSignedTransactionBytes().toByteArray()));
    }

    private BlockFile.BlockFileBuilder blockFileBuilder(RecordFile recordFile) {
        var hapiVersion = SemanticVersion.newBuilder()
                .setMajor(recordFile.getHapiVersionMajor())
                .setMinor(recordFile.getHapiVersionMinor())
                .setPatch(recordFile.getHapiVersionPatch())
                .build();
        var recordItem = recordFile.getItems().iterator().next();
        var instant = Instant.ofEpochSecond(0, recordItem.getConsensusTimestamp());
        var timestamp = Utility.instantToTimestamp(instant);

        var previousHash = recordFile.getPreviousHash();
        return BlockFile.builder()
                .blockHeader(BlockHeader.newBuilder()
                        .setFirstTransactionConsensusTime(timestamp)
                        .setHapiProtoVersion(hapiVersion)
                        .setNumber(recordFile.getIndex())
                        .setPreviousBlockHash(ByteString.copyFromUtf8(previousHash))
                        .setSoftwareVersion(hapiVersion)
                        .build())
                .bytes(recordFile.getBytes())
                .count(recordFile.getCount())
                .consensusEnd(recordFile.getConsensusEnd())
                .consensusStart(recordFile.getConsensusStart())
                .digestAlgorithm(recordFile.getDigestAlgorithm())
                .hash(recordFile.getHash())
                .nodeId(recordFile.getNodeId())
                .loadEnd(recordFile.getLoadEnd())
                .loadStart(recordFile.getLoadStart())
                .name("000000000000000000000000000000000001.blk.gz")
                .previousHash(previousHash)
                .roundEnd(recordFile.getRoundEnd())
                .roundStart(recordFile.getRoundStart())
                .size(recordFile.getSize())
                .version(7);
    }
}
