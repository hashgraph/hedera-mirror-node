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

import static com.hedera.mirror.common.util.DomainUtils.createSha384Digest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.ByteString;
import com.hedera.hapi.block.stream.output.protoc.BlockHeader;
import com.hedera.hapi.block.stream.output.protoc.TransactionResult;
import com.hedera.mirror.common.domain.DigestAlgorithm;
import com.hedera.mirror.common.domain.transaction.BlockFile;
import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.exception.ProtobufException;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import com.hedera.mirror.importer.parser.domain.BlockItemBuilder;
import com.hedera.mirror.importer.parser.domain.RecordItemBuilder;
import com.hedera.mirror.importer.parser.domain.RecordItemBuilder.TransferType;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.util.Version;

@RequiredArgsConstructor
class BlockFileTransformerTest extends ImporterIntegrationTest {

    private static final int HAPI_VERSION_MINOR = 57;
    private static final Version HAPI_VERSION = new Version(0, HAPI_VERSION_MINOR);

    private final BlockItemBuilder blockItemBuilder = new BlockItemBuilder();
    private final BlockFileTransformer blockFileTransformer;
    private final RecordItemBuilder recordItemBuilder = new RecordItemBuilder();

    @ParameterizedTest
    @EnumSource(value = TransferType.class)
    void cryptoTransfer(TransferType transferType) {
        // given
        var expectedRecordItem = recordItemBuilder
                .cryptoTransfer(transferType)
                .recordItem(r -> r.hapiVersion(HAPI_VERSION))
                .build();
        var expectedTransactionHash = getExpectedTransactionHash(expectedRecordItem);

        var expectedRecordItem2 = recordItemBuilder
                .cryptoTransfer(transferType)
                .recordItem(r -> r.hapiVersion(HAPI_VERSION).transactionIndex(1))
                .build();
        var expectedTransactionHash2 = getExpectedTransactionHash(expectedRecordItem2);

        var blockItem1 = blockItemBuilder.cryptoTransfer(expectedRecordItem).build();
        var expectedFees =
                blockItem1.transactionOutput().get(1).getCryptoTransfer().getAssessedCustomFeesList();
        var blockItem2 = blockItemBuilder.cryptoTransfer(expectedRecordItem2).build();
        var expectedFees2 =
                blockItem2.transactionOutput().get(1).getCryptoTransfer().getAssessedCustomFeesList();
        var blockFile = blockFile(List.of(blockItem1, blockItem2));

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> {
            assertThat(items).hasSize(2);
            assertThat(items)
                    .element(0)
                    .satisfies(recordItem -> assertRecordItem(recordItem, expectedRecordItem))
                    .returns(null, RecordItem::getPrevious)
                    .extracting(RecordItem::getTransactionRecord)
                    .returns(expectedTransactionHash, TransactionRecord::getTransactionHash)
                    .returns(expectedFees, TransactionRecord::getAssessedCustomFeesList);
            assertThat(items)
                    .element(1)
                    .satisfies(recordItem -> assertRecordItem(recordItem, expectedRecordItem2))
                    .returns(items.iterator().next(), RecordItem::getPrevious)
                    .extracting(RecordItem::getTransactionRecord)
                    .returns(expectedTransactionHash2, TransactionRecord::getTransactionHash)
                    .returns(expectedFees2, TransactionRecord::getAssessedCustomFeesList);
        });
    }

    @Test
    void corruptedTransactionBodyBytes() {
        // given
        var blockItem = BlockItem.builder()
                .transaction(Transaction.newBuilder()
                        .setSignedTransactionBytes(SignedTransaction.newBuilder()
                                .setBodyBytes(DomainUtils.fromBytes(domainBuilder.bytes(512)))
                                .build()
                                .toByteString())
                        .build())
                .transactionResult(TransactionResult.newBuilder().build())
                .transactionOutput(Collections.emptyList())
                .stateChanges(Collections.emptyList())
                .build();
        var blockFile = blockFile(List.of(blockItem));

        // when, then
        assertThatThrownBy(() -> blockFileTransformer.transform(blockFile)).isInstanceOf(ProtobufException.class);
    }

    @Test
    void corruptedSignedTransactionBytes() {
        // given
        var blockItem = BlockItem.builder()
                .transaction(Transaction.newBuilder()
                        .setSignedTransactionBytes(DomainUtils.fromBytes(domainBuilder.bytes(256)))
                        .build())
                .transactionResult(TransactionResult.newBuilder().build())
                .transactionOutput(Collections.emptyList())
                .stateChanges(Collections.emptyList())
                .build();
        var blockFile = blockFile(List.of(blockItem));

        // when, then
        assertThatThrownBy(() -> blockFileTransformer.transform(blockFile)).isInstanceOf(ProtobufException.class);
    }

    @Test
    void emptyBlockFile() {
        // given
        var blockFile = blockFile(Collections.emptyList());

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertThat(recordFile.getItems()).isEmpty();
        assertRecordFile(recordFile, blockFile, items -> {});
    }

    @Test
    void unknownTransform() {
        // given
        var expectedRecordItem = recordItemBuilder
                .unknown()
                .recordItem(r -> r.hapiVersion(HAPI_VERSION))
                .build();
        var blockItem = blockItemBuilder.unknown(expectedRecordItem).build();
        var blockFile = blockFile(List.of(blockItem));

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> {
            assertThat(items).hasSize(1).first().satisfies(item -> assertRecordItem(item, expectedRecordItem));
        });
    }

    @Test
    void fileAppendTransform() {
        // given
        var expectedRecordItem = recordItemBuilder
                .fileAppend()
                .recordItem(r -> r.hapiVersion(HAPI_VERSION))
                .build();

        var expectedTransactionHash = getExpectedTransactionHash(expectedRecordItem);

        var blockItem = blockItemBuilder.fileAppend(expectedRecordItem).build();
        var blockFie = blockFile(List.of(blockItem));

        // when
        var recordFile = blockFileTransformer.transform(blockFie);

        // then
        assertRecordFile(recordFile, blockFie, items -> assertThat(items)
                .hasSize(1)
                .first()
                .satisfies(item -> assertRecordItem(item, expectedRecordItem))
                .returns(null, RecordItem::getPrevious)
                .extracting(RecordItem::getTransactionRecord)
                .returns(expectedTransactionHash, TransactionRecord::getTransactionHash));
    }

    @Test
    void fileDeleteTransform() {
        // given
        var expectedRecordItem = recordItemBuilder
                .fileDelete()
                .recordItem(r -> r.hapiVersion(HAPI_VERSION))
                .build();
        var expectedTransactionHash = getExpectedTransactionHash(expectedRecordItem);
        var blockItem = blockItemBuilder.fileDelete(expectedRecordItem).build();
        var blockFie = blockFile(List.of(blockItem));

        // when
        var recordFile = blockFileTransformer.transform(blockFie);

        // then
        assertRecordFile(recordFile, blockFie, items -> assertThat(items)
                .hasSize(1)
                .first()
                .satisfies(item -> assertRecordItem(item, expectedRecordItem))
                .returns(null, RecordItem::getPrevious)
                .extracting(RecordItem::getTransactionRecord)
                .returns(expectedTransactionHash, TransactionRecord::getTransactionHash));
    }

    @ParameterizedTest
    @EnumSource(
            value = ResponseCodeEnum.class,
            mode = EnumSource.Mode.INCLUDE,
            names = {"FEE_SCHEDULE_FILE_PART_UPLOADED", "SUCCESS", "SUCCESS_BUT_MISSING_EXPECTED_OPERATION"})
    void fileCreateTransform(ResponseCodeEnum successfulStatus) {
        // given
        var expectedRecordItem = recordItemBuilder
                .fileCreate()
                .recordItem(r -> r.hapiVersion(HAPI_VERSION))
                .receipt(r -> r.setStatus(successfulStatus))
                .build();
        var expectedTransactionHash = getExpectedTransactionHash(expectedRecordItem);
        var blockItem = blockItemBuilder.fileCreate(expectedRecordItem).build();
        var expectedFileId = blockItem
                .stateChanges()
                .getFirst()
                .getStateChanges(3)
                .getMapUpdate()
                .getKey()
                .getFileIdKey()
                .getFileNum();
        var blockFie = blockFile(List.of(blockItem));

        // when
        var recordFile = blockFileTransformer.transform(blockFie);

        // then
        assertRecordFile(recordFile, blockFie, items -> assertThat(items)
                .hasSize(1)
                .first()
                .satisfies(item -> assertRecordItem(item, expectedRecordItem))
                .returns(null, RecordItem::getPrevious)
                .extracting(RecordItem::getTransactionRecord)
                .returns(expectedTransactionHash, TransactionRecord::getTransactionHash)
                .returns(
                        expectedFileId,
                        transactionRecord ->
                                transactionRecord.getReceipt().getFileID().getFileNum()));
    }

    @Test
    void fileCreateTransform_whenStatusIsNotSuccess() {
        // given
        var expectedRecordItem = recordItemBuilder
                .fileCreate()
                .recordItem(r -> r.hapiVersion(HAPI_VERSION))
                .receipt(r -> r.clearFileID().setStatus(ResponseCodeEnum.AUTHORIZATION_FAILED))
                .build();
        var expectedTransactionHash = getExpectedTransactionHash(expectedRecordItem);
        var blockItem = blockItemBuilder.fileCreate(expectedRecordItem).build();

        var blockFie = blockFile(List.of(blockItem));

        // when
        var recordFile = blockFileTransformer.transform(blockFie);

        // then
        assertRecordFile(recordFile, blockFie, items -> assertThat(items)
                .hasSize(1)
                .first()
                .satisfies(item -> assertRecordItem(item, expectedRecordItem))
                .returns(null, RecordItem::getPrevious)
                .extracting(RecordItem::getTransactionRecord)
                .returns(expectedTransactionHash, TransactionRecord::getTransactionHash)
                .returns(
                        0L,
                        transactionRecord ->
                                transactionRecord.getReceipt().getFileID().getFileNum()));
    }

    @Test
    void fileUpdateTransform() {
        // given
        var expectedRecordItem = recordItemBuilder
                .fileUpdate()
                .recordItem(r -> r.hapiVersion(HAPI_VERSION))
                .build();
        var expectedTransactionHash = getExpectedTransactionHash(expectedRecordItem);
        var blockItem = blockItemBuilder.fileUpdate(expectedRecordItem).build();
        var blockFie = blockFile(List.of(blockItem));

        // when
        var recordFile = blockFileTransformer.transform(blockFie);

        // then
        assertRecordFile(recordFile, blockFie, items -> assertThat(items)
                .hasSize(1)
                .first()
                .satisfies(item -> assertRecordItem(item, expectedRecordItem))
                .returns(null, RecordItem::getPrevious)
                .extracting(RecordItem::getTransactionRecord)
                .returns(expectedTransactionHash, TransactionRecord::getTransactionHash));
    }

    private void assertRecordFile(
            RecordFile actual, BlockFile blockFile, Consumer<Collection<RecordItem>> itemsAssert) {
        var hapiProtoVersion = blockFile.getBlockHeader().getHapiProtoVersion();
        var softwareVersion = blockFile.getBlockHeader().getSoftwareVersion();
        assertThat(actual)
                .returns(blockFile.getBytes(), RecordFile::getBytes)
                .returns(blockFile.getConsensusEnd(), RecordFile::getConsensusEnd)
                .returns(blockFile.getConsensusStart(), RecordFile::getConsensusStart)
                .returns(blockFile.getCount(), RecordFile::getCount)
                .returns(blockFile.getDigestAlgorithm(), RecordFile::getDigestAlgorithm)
                .returns(StringUtils.EMPTY, RecordFile::getFileHash)
                .returns(0L, RecordFile::getGasUsed)
                .returns(hapiProtoVersion.getMajor(), RecordFile::getHapiVersionMajor)
                .returns(hapiProtoVersion.getMinor(), RecordFile::getHapiVersionMinor)
                .returns(hapiProtoVersion.getPatch(), RecordFile::getHapiVersionPatch)
                .returns(blockFile.getHash(), RecordFile::getHash)
                .returns(blockFile.getIndex(), RecordFile::getIndex)
                .returns(null, RecordFile::getLoadEnd)
                .returns(blockFile.getLoadStart(), RecordFile::getLoadStart)
                .returns(null, RecordFile::getLogsBloom)
                .returns(null, RecordFile::getMetadataHash)
                .returns(blockFile.getName(), RecordFile::getName)
                .returns(blockFile.getNodeId(), RecordFile::getNodeId)
                .returns(blockFile.getPreviousHash(), RecordFile::getPreviousHash)
                .returns(blockFile.getRoundEnd(), RecordFile::getRoundEnd)
                .returns(blockFile.getRoundStart(), RecordFile::getRoundStart)
                .returns(0, RecordFile::getSidecarCount)
                .satisfies(r -> assertThat(r.getSidecars()).isEmpty())
                .returns(blockFile.getSize(), RecordFile::getSize)
                .returns(softwareVersion.getMajor(), RecordFile::getSoftwareVersionMajor)
                .returns(softwareVersion.getMinor(), RecordFile::getSoftwareVersionMinor)
                .returns(softwareVersion.getPatch(), RecordFile::getSoftwareVersionPatch)
                .returns(blockFile.getVersion(), RecordFile::getVersion)
                .extracting(RecordFile::getItems)
                .satisfies(itemsAssert);
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
                        "transactionRecord.transactionID_.transactionValidStart_.memoizedSize",
                        "transactionRecord.receipt_.fileID_.memoizedHashCode",
                        "transactionRecord.receipt_.fileID_.memoizedSize",
                        "transactionRecord.receipt_.fileID_.memoizedIsInitialized")
                .isEqualTo(expectedRecordItem);
    }

    private ByteString getExpectedTransactionHash(RecordItem recordItem) {
        var digest = createSha384Digest();
        return ByteString.copyFrom(
                digest.digest(DomainUtils.toBytes(recordItem.getTransaction().getSignedTransactionBytes())));
    }

    private BlockFile blockFile(List<BlockItem> blockItems) {
        long blockNumber = domainBuilder.number();
        byte[] bytes = domainBuilder.bytes(256);
        String filename = StringUtils.leftPad(Long.toString(blockNumber), 36, "0") + ".blk.gz";
        var firstConsensusTimestamp = blockItems.isEmpty()
                ? domainBuilder.protoTimestamp()
                : blockItems.getFirst().transactionResult().getConsensusTimestamp();
        byte[] previousHash = domainBuilder.bytes(48);
        long consensusStart = DomainUtils.timestampInNanosMax(firstConsensusTimestamp);
        long consensusEnd = blockItems.isEmpty()
                ? consensusStart
                : DomainUtils.timestampInNanosMax(
                        blockItems.getLast().transactionResult().getConsensusTimestamp());

        return BlockFile.builder()
                .blockHeader(BlockHeader.newBuilder()
                        .setFirstTransactionConsensusTime(firstConsensusTimestamp)
                        .setNumber(blockNumber)
                        .setPreviousBlockHash(DomainUtils.fromBytes(previousHash))
                        .setHapiProtoVersion(SemanticVersion.newBuilder().setMinor(HAPI_VERSION_MINOR))
                        .setSoftwareVersion(SemanticVersion.newBuilder()
                                .setMinor(HAPI_VERSION_MINOR)
                                .setPatch(1))
                        .build())
                .bytes(bytes)
                .consensusEnd(consensusEnd)
                .consensusStart(consensusStart)
                .count((long) blockItems.size())
                .digestAlgorithm(DigestAlgorithm.SHA_384)
                .hash(DomainUtils.bytesToHex(domainBuilder.bytes(48)))
                .index(blockNumber)
                .items(blockItems)
                .loadStart(System.currentTimeMillis())
                .name(filename)
                .nodeId(domainBuilder.number())
                .previousHash(DomainUtils.bytesToHex(previousHash))
                .roundEnd(blockNumber + 1)
                .roundStart(blockNumber + 1)
                .size(bytes.length)
                .version(7)
                .build();
    }
}
