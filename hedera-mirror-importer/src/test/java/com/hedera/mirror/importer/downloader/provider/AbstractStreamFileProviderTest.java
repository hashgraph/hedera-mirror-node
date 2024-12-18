/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.downloader.provider;

import static com.hedera.mirror.importer.domain.StreamFilename.FileType.SIDECAR;
import static com.hedera.mirror.importer.domain.StreamFilename.SIDECAR_FOLDER;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.ImporterProperties;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.addressbook.ConsensusNode;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.domain.StreamFileSignature;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties.PathType;
import com.hedera.mirror.importer.exception.InvalidDatasetException;
import com.hedera.mirror.importer.reader.signature.ProtoSignatureFileReader;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.bouncycastle.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

abstract class AbstractStreamFileProviderTest {

    protected Path bucketRootPath;

    @TempDir
    protected Path dataPath;

    protected FileCopier fileCopier;
    protected CommonDownloaderProperties properties;
    protected StreamFileProvider streamFileProvider;

    @BeforeEach
    @SuppressWarnings("java:S1130") // Exception is thrown in the child classes setup method
    void setup() throws Exception {
        var mirrorProperties = new ImporterProperties();
        mirrorProperties.setDataPath(dataPath);
        properties = new CommonDownloaderProperties(mirrorProperties);
        customizeProperties(properties);
        fileCopier = createFileCopier(dataPath);
        bucketRootPath = dataPath.resolve(properties.getBucketName());
    }

    protected abstract FileCopier createFileCopier(Path dataPath);

    protected abstract String getProviderPathSeparator();

    protected abstract String resolveProviderRelativePath(ConsensusNode node, String fileName);

    @SuppressWarnings("java:S1172") // node is used in the child classes implementations
    protected FileCopier getFileCopier(ConsensusNode node) {
        return fileCopier;
    }

    protected void customizeProperties(CommonDownloaderProperties properties) {
        // Do nothing by default
    }

    @Test
    void get() {
        var node = node("0.0.3");
        var nodeFileCopier = getFileCopier(node);
        get(nodeFileCopier, node);
    }

    @Test
    void getLargeFile() {
        var node = node("0.0.3");
        var nodeFileCopier = getFileCopier(node);
        nodeFileCopier.copy();
        var data = streamFileData(node, "2022-07-13T08_46_08.041986003Z.rcd_sig");
        StepVerifier.create(streamFileProvider.get(node, data.getStreamFilename()))
                .thenAwait(Duration.ofSeconds(2L))
                .expectNext(data)
                .expectComplete()
                .verify(Duration.ofSeconds(4000L));

        // Increase data2 1 byte beyond the max size
        long maxSize = data.getBytes().length;
        properties.setMaxSize(maxSize);
        replaceContents(data, Arrays.append(data.getBytes(), (byte) 1));

        StepVerifier.withVirtualTime(() -> streamFileProvider.get(node, data.getStreamFilename()))
                .thenAwait(Duration.ofSeconds(10L))
                .expectError(InvalidDatasetException.class)
                .verify(Duration.ofSeconds(10L));
    }

    @Test
    void getSidecar() {
        var node = node("0.0.3");
        var nodeFileCopier = getFileCopier(node);
        getSidecar(nodeFileCopier, node);
    }

    @Test
    void getNotFound() {
        var node = node("0.0.3");
        var nodeFileCopier = getFileCopier(node);
        getNotFound(nodeFileCopier, node);
    }

    @Test
    void getError() {
        var node = node("0.0.3");
        var nodeFileCopier = getFileCopier(node);
        getError(nodeFileCopier, node);
    }

    @Test
    void list() {
        var node = node("0.0.3");
        var nodeFileCopier = getFileCopier(node);
        list(nodeFileCopier, node);
    }

    @Test
    void listThenGet() {
        var node = node("0.0.3");
        var nodeFileCopier = getFileCopier(node);
        nodeFileCopier.copy();
        var last = StreamFilename.from("2022-07-13T08_46_08.041986003Z.rcd_sig");
        var recordFile = streamFileData(node, "2022-07-13T08_46_11.304284003Z.rcd.gz");
        var sidecar = streamFileData(node, "2022-07-13T08_46_11.304284003Z_01.rcd.gz");

        var files = streamFileProvider
                .list(node, last)
                .map(StreamFileData::getStreamFilename)
                .map(s -> StreamFileSignature.builder()
                        .filename(s)
                        .version(ProtoSignatureFileReader.VERSION)
                        .build())
                .map(StreamFileSignature::getDataFilename)
                .flatMap(s -> streamFileProvider.get(node, s))
                .collectList()
                .block();
        assertThat(files)
                .hasSize(1)
                .first()
                .isEqualTo(recordFile)
                .extracting(StreamFileData::getDecompressedBytes)
                .isEqualTo(recordFile.getDecompressedBytes());

        var sidecars = Flux.fromIterable(files)
                .map(StreamFileData::getStreamFilename)
                .flatMap(s -> streamFileProvider.get(node, StreamFilename.from(s, s.getSidecarFilename(1))))
                .collectList()
                .block();
        assertThat(sidecars)
                .hasSize(1)
                .first()
                .isEqualTo(sidecar)
                .extracting(StreamFileData::getDecompressedBytes)
                .isEqualTo(sidecar.getDecompressedBytes());
    }

    @Test
    void listWhenBatchSizeLessThanFilesAvailable() {
        var node = node("0.0.3");
        var nodeFileCopier = getFileCopier(node);
        nodeFileCopier.copy();
        streamFileData(node, "2022-07-13T08_46_08.041986003Z.rcd.gz");
        var sig1 = streamFileData(node, "2022-07-13T08_46_08.041986003Z.rcd_sig");
        streamFileData(node, "2022-07-13T08_46_11.304284003Z.rcd.gz");
        streamFileData(node, "2022-07-13T08_46_11.304284003Z.rcd_sig");
        properties.setBatchSize(1);
        StepVerifier.withVirtualTime(() -> streamFileProvider.list(node, StreamFilename.EPOCH))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNext(sig1)
                .expectComplete()
                .verify(Duration.ofSeconds(10L));
    }

    @Test
    void listAfter() {
        var node = node("0.0.3");
        var nodeFileCopier = getFileCopier(node);
        listAfter(nodeFileCopier, node);
    }

    @Test
    void listNotFound() {
        var node = node("0.0.3");
        var nodeFileCopier = getFileCopier(node);
        listNotFound(nodeFileCopier, node);
    }

    @Test
    void listError() {
        var node = node("0.0.3");
        var nodeFileCopier = getFileCopier(node);
        listError(nodeFileCopier, node);
    }

    @Test
    void listInvalidFilename() throws Exception {
        var node = node("0.0.3");
        var nodeFileCopier = getFileCopier(node);
        listInvalidFilename(nodeFileCopier, node);
    }

    @Test
    void listLargeFiles() {
        var node = node("0.0.3");
        var nodeFileCopier = getFileCopier(node);
        nodeFileCopier.copy();
        var data1 = streamFileData(node, "2022-07-13T08_46_08.041986003Z.rcd_sig");
        var data2 = streamFileData(node, "2022-07-13T08_46_11.304284003Z.rcd_sig");

        StepVerifier.withVirtualTime(() -> streamFileProvider.list(node, StreamFilename.EPOCH))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNext(data1)
                .expectNext(data2)
                .expectComplete()
                .verify(Duration.ofSeconds(10L));

        // Increase data2 1 byte beyond the max size
        long maxSize = data2.getBytes().length;
        properties.setMaxSize(maxSize);
        replaceContents(data2, Arrays.append(data2.getBytes(), (byte) 1));

        StepVerifier.withVirtualTime(() -> streamFileProvider.list(node, StreamFilename.EPOCH))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNext(data1)
                .expectComplete()
                .verify(Duration.ofSeconds(10L));
    }

    @SneakyThrows
    private void replaceContents(StreamFileData streamFileData, byte[] contents) {
        var file = fileCopier
                .getTo()
                .getParent()
                .resolve(streamFileData.getFilePath())
                .toFile();
        FileUtils.writeByteArrayToFile(file, contents);
    }

    protected final void get(FileCopier fileCopier, ConsensusNode node) {
        fileCopier.copy();
        var data = streamFileData(node, "2022-07-13T08_46_08.041986003Z.rcd_sig");
        StepVerifier.withVirtualTime(() -> streamFileProvider.get(node, data.getStreamFilename()))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNext(data)
                .expectComplete()
                .verify(Duration.ofSeconds(10L));
    }

    protected final void getSidecar(FileCopier fileCopier, ConsensusNode node) {
        fileCopier.copy();
        var data = streamFileData(node, "2022-07-13T08_46_11.304284003Z_01.rcd.gz");
        StepVerifier.withVirtualTime(() -> streamFileProvider
                        .get(node, data.getStreamFilename())
                        .doOnNext(sfd -> assertThat(sfd.getBytes()).isEqualTo(data.getBytes())))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNext(data)
                .expectComplete()
                .verify(Duration.ofSeconds(10L));
    }

    protected final void getNotFound(FileCopier fileCopier, ConsensusNode node) {
        fileCopier.copy();
        StepVerifier.withVirtualTime(() -> streamFileProvider.get(node, StreamFilename.EPOCH))
                .thenAwait(Duration.ofSeconds(10L))
                .expectError(TransientProviderException.class)
                .verify(Duration.ofSeconds(10L));
    }

    protected final void getError(FileCopier fileCopier, ConsensusNode node) {
        fileCopier.copy();
        dataPath.toFile().setExecutable(false);
        var data = streamFileData(node, "2022-07-13T08_46_08.041986003Z.rcd_sig");
        StepVerifier.withVirtualTime(() ->
                        streamFileProvider.get(node, data.getStreamFilename()).map(StreamFileData::getBytes))
                .thenAwait(Duration.ofSeconds(10L))
                .expectError(RuntimeException.class)
                .verify(Duration.ofSeconds(10L));
    }

    protected final void list(FileCopier fileCopier, ConsensusNode node) {
        fileCopier.copy();
        var data1 = streamFileData(node, "2022-07-13T08_46_08.041986003Z.rcd_sig");
        var data2 = streamFileData(node, "2022-07-13T08_46_11.304284003Z.rcd_sig");
        StepVerifier.withVirtualTime(() -> streamFileProvider.list(node, StreamFilename.EPOCH))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNext(data1)
                .expectNext(data2)
                .expectComplete()
                .verify(Duration.ofSeconds(10L));
    }

    protected final void listAfter(FileCopier fileCopier, ConsensusNode node) {
        fileCopier.copy();
        var lastFilename = StreamFilename.from("2022-07-13T08_46_08.041986003Z.rcd_sig");
        var data = streamFileData(node, "2022-07-13T08_46_11.304284003Z.rcd_sig");
        StepVerifier.withVirtualTime(() -> streamFileProvider.list(node, lastFilename))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNext(data)
                .expectComplete()
                .verify(Duration.ofSeconds(10L));
    }

    protected final void listNotFound(FileCopier fileCopier, ConsensusNode node) {
        fileCopier.copy();
        var lastFilename = StreamFilename.from("2100-01-01T01_01_01.000000001Z.rcd_sig");
        StepVerifier.withVirtualTime(() -> streamFileProvider.list(node, lastFilename))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNextCount(0)
                .expectComplete()
                .verify(Duration.ofSeconds(10L));
    }

    protected final void listError(FileCopier fileCopier, ConsensusNode node) {
        fileCopier.copy();
        dataPath.toFile().setExecutable(false);
        StepVerifier.withVirtualTime(() -> streamFileProvider.list(node, StreamFilename.EPOCH))
                .thenAwait(Duration.ofSeconds(10L))
                .expectError(RuntimeException.class)
                .verify(Duration.ofSeconds(10L));
    }

    protected final void listInvalidFilename(FileCopier fileCopier, ConsensusNode node) throws Exception {
        fileCopier.copy();
        fileCopier
                .getTo()
                .resolve(nodePath(node))
                .resolve("Invalid.file")
                .toFile()
                .createNewFile();
        var data1 = streamFileData(node, "2022-07-13T08_46_08.041986003Z.rcd_sig");
        var data2 = streamFileData(node, "2022-07-13T08_46_11.304284003Z.rcd_sig");
        StepVerifier.withVirtualTime(() -> streamFileProvider.list(node, StreamFilename.EPOCH))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNext(data1)
                .expectNext(data2)
                .expectComplete()
                .verify(Duration.ofSeconds(10L));
    }

    protected Path nodePath(ConsensusNode node) {
        return TestUtils.nodePath(node, PathType.ACCOUNT_ID, StreamType.RECORD);
    }

    protected ConsensusNode node(String nodeAccountId) {
        return TestUtils.nodeFromAccountId(nodeAccountId);
    }

    protected StreamFileData streamFileData(ConsensusNode node, FileCopier fileCopier, String filename) {
        try {
            var streamFilename =
                    StreamFilename.from(resolveProviderRelativePath(node, filename), getProviderPathSeparator());
            var repoDataPath = fileCopier
                    .getFrom()
                    .resolve(nodePath(node))
                    .resolve(streamFilename.getFileType() == SIDECAR ? SIDECAR_FOLDER : "")
                    .resolve(filename);
            var bytes = FileUtils.readFileToByteArray(repoDataPath.toFile());
            return new StreamFileData(streamFilename, () -> bytes, Instant.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected StreamFileData streamFileData(ConsensusNode node, String filename) {
        return streamFileData(node, getFileCopier(node), filename);
    }
}
