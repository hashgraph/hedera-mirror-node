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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.ByteString;
import com.hedera.hapi.block.stream.input.protoc.EventHeader;
import com.hedera.hapi.block.stream.input.protoc.RoundHeader;
import com.hedera.hapi.block.stream.output.protoc.BlockHeader;
import com.hedera.hapi.block.stream.output.protoc.TransactionResult;
import com.hedera.hapi.block.stream.protoc.Block;
import com.hedera.hapi.block.stream.protoc.BlockItem;
import com.hedera.hapi.block.stream.protoc.BlockProof;
import com.hedera.hapi.block.stream.protoc.RecordFileItem;
import com.hedera.hapi.platform.event.legacy.EventTransaction;
import com.hedera.mirror.common.domain.DigestAlgorithm;
import com.hedera.mirror.common.domain.transaction.BlockFile;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;

class ProtoBlockFileReaderTest {

    private final ProtoBlockFileReader reader = new ProtoBlockFileReader();

    @ParameterizedTest(name = "{0}")
    @MethodSource("readTestArgumentsProvider")
    void read(String filename, StreamFileData streamFileData, BlockFile expected) {
        var actual = reader.read(streamFileData);
        assertThat(actual)
                .usingRecursiveComparison()
                .ignoringFields("blockHeader", "blockProof", "items")
                .isEqualTo(expected);
        assertThat(actual)
                .returns(expected.getCount(), a -> (long) a.getItems().size())
                .satisfies(a -> assertThat(a.getBlockHeader()).isNotNull())
                .satisfies(a -> assertThat(a.getBlockProof()).isNotNull());
    }

    @Test
    void readRecordFileItem() {
        // given
        var block = Block.newBuilder()
                .addItems(BlockItem.newBuilder()
                        .setRecordFile(RecordFileItem.getDefaultInstance())
                        .build())
                .build();
        byte[] bytes = gzip(block);
        var streamFileData = StreamFileData.from("000000000000000000000000000000000001.blk.gz", bytes);
        var expected = BlockFile.builder()
                .loadStart(streamFileData.getStreamFilename().getTimestamp())
                .name(streamFileData.getFilename())
                .recordFileItem(RecordFileItem.getDefaultInstance())
                .version(7)
                .build();

        // when
        var actual = reader.read(streamFileData);

        // then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void throwWhenMissingBlockHeader() {
        var block = Block.newBuilder().addItems(blockProof()).build();
        var streamFileData = StreamFileData.from("000000000000000000000000000000000001.blk.gz", gzip(block));
        assertThatThrownBy(() -> reader.read(streamFileData))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessageContaining("Missing block header");
    }

    @Test
    void throwWhenMissingBlockProof() {
        var block = Block.newBuilder().addItems(blockHeader()).build();
        var streamFileData = StreamFileData.from("000000000000000000000000000000000001.blk.gz", gzip(block));
        assertThatThrownBy(() -> reader.read(streamFileData))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessageContaining("Missing block proof");
    }

    @Test
    void throwWhenMissingTransactionResult() {
        var roundHeader = BlockItem.newBuilder().setRoundHeader(RoundHeader.getDefaultInstance());
        var eventHeader = BlockItem.newBuilder().setEventHeader(EventHeader.getDefaultInstance());
        var block = Block.newBuilder()
                .addItems(blockHeader())
                .addItems(roundHeader)
                .addItems(eventHeader)
                .addItems(eventTransaction())
                .addItems(blockProof())
                .build();
        var streamFileData = StreamFileData.from("000000000000000000000000000000000001.blk.gz", gzip(block));
        assertThatThrownBy(() -> reader.read(streamFileData))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessageContaining("Missing transaction result");
    }

    @Test
    void thrownWhenTransactionBytesCorrupted() {
        var roundHeader = BlockItem.newBuilder().setRoundHeader(RoundHeader.getDefaultInstance());
        var eventHeader = BlockItem.newBuilder().setEventHeader(EventHeader.getDefaultInstance());
        var eventTransaction = BlockItem.newBuilder()
                .setEventTransaction(EventTransaction.newBuilder()
                        .setApplicationTransaction(ByteString.copyFrom(TestUtils.generateRandomByteArray(32))));
        var transactionResult = BlockItem.newBuilder().setTransactionResult(TransactionResult.getDefaultInstance());
        var block = Block.newBuilder()
                .addItems(blockHeader())
                .addItems(roundHeader)
                .addItems(eventHeader)
                .addItems(eventTransaction)
                .addItems(eventTransaction)
                .addItems(transactionResult)
                .addItems(blockProof())
                .build();
        var streamFileData = StreamFileData.from("000000000000000000000000000000000001.blk.gz", gzip(block));
        assertThatThrownBy(() -> reader.read(streamFileData))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessageContaining("Failed to deserialize Transaction");
    }

    private BlockItem blockHeader() {
        return BlockItem.newBuilder()
                .setBlockHeader(BlockHeader.newBuilder()
                        .setPreviousBlockHash(ByteString.copyFrom(TestUtils.generateRandomByteArray(48))))
                .build();
    }

    private BlockItem blockProof() {
        return BlockItem.newBuilder()
                .setBlockProof(BlockProof.newBuilder()
                        .setStartOfBlockStateRootHash(ByteString.copyFrom(TestUtils.generateRandomByteArray(48))))
                .build();
    }

    private BlockItem eventTransaction() {
        var transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(SignedTransaction.newBuilder()
                        .setBodyBytes(TransactionBody.newBuilder()
                                .setCryptoTransfer(CryptoTransferTransactionBody.getDefaultInstance())
                                .build()
                                .toByteString())
                        .build()
                        .toByteString())
                .build()
                .toByteString();
        return BlockItem.newBuilder()
                .setEventTransaction(EventTransaction.newBuilder()
                        .setApplicationTransaction(transaction)
                        .build())
                .build();
    }

    @SneakyThrows
    private static byte[] gzip(Block block) {
        try (var bos = new ByteArrayOutputStream();
                var gos = new GzipCompressorOutputStream(bos)) {
            gos.write(block.toByteArray());
            gos.finish();
            return bos.toByteArray();
        }
    }

    @SneakyThrows
    private static Stream<Arguments> readTestArgumentsProvider() {
        List<Arguments> argumentsList = new ArrayList<>();

        String filename = "000000000000000000000000000007858853.blk.gz";
        long index = 7858853;
        long round = index + 1;
        var file = new ClassPathResource("data/blockstreams/" + filename).getFile();
        var streamFileData = StreamFileData.from(file);
        var expected = BlockFile.builder()
                .bytes(streamFileData.getBytes())
                .consensusStart(1736197012160646000L)
                .consensusEnd(1736197012160646001L)
                .count(2L)
                .digestAlgorithm(DigestAlgorithm.SHA_384)
                .hash(
                        "581caa8ab1fad535a0fac97957c5c0cf44c528ee55724353b4bab9093083fda32429f73248bc3128e329bbdfa1967d20")
                .index(index)
                .loadStart(streamFileData.getStreamFilename().getTimestamp())
                .name(filename)
                .previousHash(
                        "ba1a0222099d542425f6915053b7f15e3b75fd680b0d84ca6d41fbffcd38f8fb5ac6ab6a235e69f7ae23118d1996c7f1")
                .roundStart(round)
                .roundEnd(round)
                .size(streamFileData.getBytes().length)
                .version(7)
                .build();
        argumentsList.add(Arguments.of(filename, streamFileData, expected));

        // A block without event transactions, note consensusStart and consensusEnd are both null due to the bug that
        // BlockHeader.first_transaction_consensus_time is null
        filename = "000000000000000000000000000007858854.blk.gz";
        index = 7858854;
        round = index + 1;
        file = new ClassPathResource("data/blockstreams/" + filename).getFile();
        streamFileData = StreamFileData.from(file);
        // Verifies the calculated hash of the previous block matches the previous hash in this (the next) block file
        String previousHash = expected.getHash();
        expected = BlockFile.builder()
                .bytes(streamFileData.getBytes())
                .consensusStart(null)
                .consensusEnd(null)
                .count(0L)
                .digestAlgorithm(DigestAlgorithm.SHA_384)
                .hash(
                        "ef32f163bee6553087002310467b970b1de2c8cbec2eab46f0d0c58ff34043d080f43c9e3c759956fda19fc9f5a5966b")
                .index(index)
                .loadStart(streamFileData.getStreamFilename().getTimestamp())
                .name(filename)
                .previousHash(previousHash)
                .roundStart(round)
                .roundEnd(round)
                .size(streamFileData.getBytes().length)
                .version(7)
                .build();
        argumentsList.add(Arguments.of(filename, streamFileData, expected));

        return argumentsList.stream();
    }
}
