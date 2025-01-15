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
import com.hedera.mirror.importer.ImporterIntegrationTest;
import com.hedera.mirror.importer.parser.domain.BlockItemBuilder;
import com.hedera.mirror.importer.parser.domain.RecordItemBuilder;
import com.hedera.mirror.importer.parser.domain.RecordItemBuilder.TransferType;
import com.hedera.mirror.importer.util.Utility;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class BlockFileTransformerTest extends ImporterIntegrationTest {

    private final BlockItemBuilder blockItemBuilder = new BlockItemBuilder();
    private final BlockFileTransformer blockFileTransformer = new BlockFileTransformer();
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
        var expectedRecordFile = expectedRecordFileBuilder
                .customize(r -> r.items(List.of(expectedRecordItem)))
                .get();
        var blockItem = blockItemBuilder.cryptoTransfer(expectedRecordItem).build();
        var blockFile =
                blockFileBuilder(expectedRecordFile).items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        var recordItem = recordFile.getItems().iterator().next();
        assertThat(recordItem)
                .usingRecursiveComparison()
                .ignoringFields(
                        "contractTransactionPredicate",
                        "entityTransactionPredicate",
                        "transactionRecord.receipt_.memoizedIsInitialized",
                        "transactionRecord.transactionHash_",
                        "transactionRecord.assessedCustomFees_",
                        "transactionRecord.transactionID_.accountID_.memoizedHashCode",
                        "transactionRecord.transactionID_.accountID_.memoizedIsInitialized",
                        "transactionRecord.transactionID_.accountID_.memoizedSize",
                        "transactionRecord.transactionID_.memoizedIsInitialized",
                        "transactionRecord.transactionID_.memoizedSize",
                        "transactionRecord.transactionID_.transactionValidStart_.memoizedIsInitialized",
                        "transactionRecord.transactionID_.transactionValidStart_.memoizedSize")
                .isEqualTo(expectedRecordItem);

        assertThat(recordFile.getItems()).hasSize(1);
        assertThat(recordFile.getLogsBloom()).isNull();
        assertThat(recordFile.getName()).isEqualTo(blockFile.getName());
        assertThat(recordFile.getSidecars()).isEmpty();
        assertThat(recordFile.getSidecarCount()).isZero();
        assertThat(recordFile.getVersion()).isEqualTo(7);
        assertThat(recordFile)
                .usingRecursiveComparison()
                .ignoringFields(
                        "fileHash", "gasUsed", "items", "logsBloom", "name", "sidecarCount", "sidecars", "version")
                .isEqualTo(expectedRecordFile);
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

        return BlockFile.builder()
                .blockHeader(BlockHeader.newBuilder()
                        .setFirstTransactionConsensusTime(timestamp)
                        .setHapiProtoVersion(hapiVersion)
                        .setNumber(recordFile.getIndex())
                        .setPreviousBlockHash(ByteString.copyFromUtf8(recordFile.getPreviousHash()))
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
                .roundEnd(recordFile.getRoundEnd())
                .roundStart(recordFile.getRoundStart())
                .size(recordFile.getSize())
                .version(7);
    }
}
