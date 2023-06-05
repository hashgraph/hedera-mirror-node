/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.addressbook.ConsensusNode;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties.PathType;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

abstract class AbstractStreamFileProviderTest {

    @TempDir
    protected Path dataPath;

    protected Path bucketRootPath;

    protected FileCopier fileCopier;
    protected CommonDownloaderProperties properties;
    protected StreamFileProvider streamFileProvider;

    @BeforeEach
    void setup() throws Exception {
        var mirrorProperties = new MirrorProperties();
        mirrorProperties.setDataPath(dataPath);
        properties = new CommonDownloaderProperties(mirrorProperties);
        customizeProperties(properties);
        fileCopier = createFileCopier(dataPath);
        bucketRootPath = dataPath.resolve(properties.getBucketName());
    }

    protected abstract FileCopier createFileCopier(Path dataPath);

    protected abstract String getProviderPathSeparator();

    protected abstract String resolveProviderRelativePath(ConsensusNode node, String fileName);

    protected FileCopier getFileCopier(ConsensusNode node) {
        return fileCopier;
    }

    protected void customizeProperties(CommonDownloaderProperties properties) {
        // Do nothing by default
    }

    @Test
    void get() {
        var node = node("0.0.3");
        var fileCopier = getFileCopier(node);
        get(fileCopier, node);
    }

    @Test
    void getSidecar() {
        var node = node("0.0.3");
        var fileCopier = getFileCopier(node);
        getSidecar(fileCopier, node);
    }

    @Test
    void getNotFound() {
        var node = node("0.0.3");
        var fileCopier = getFileCopier(node);
        getNotFound(fileCopier, node);
    }

    @Test
    void getError() {
        var node = node("0.0.3");
        var fileCopier = getFileCopier(node);
        getError(fileCopier, node);
    }

    @Test
    void list() {
        var node = node("0.0.3");
        var fileCopier = getFileCopier(node);
        list(fileCopier, node);
    }

    @Test
    void listAfter() {
        var node = node("0.0.3");
        var fileCopier = getFileCopier(node);
        listAfter(fileCopier, node);
    }

    @Test
    void listNotFound() {
        var node = node("0.0.3");
        var fileCopier = getFileCopier(node);
        listNotFound(fileCopier, node);
    }

    @Test
    void listError() {
        var node = node("0.0.3");
        var fileCopier = getFileCopier(node);
        listError(fileCopier, node);
    }

    @Test
    void listInvalidFilename() throws Exception {
        var node = node("0.0.3");
        var fileCopier = getFileCopier(node);
        listInvalidFilename(fileCopier, node);
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
        StepVerifier.withVirtualTime(() -> streamFileProvider.get(node, data.getStreamFilename()))
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
        StepVerifier.withVirtualTime(() -> streamFileProvider.get(node, data.getStreamFilename()))
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
        var lastFilename = StreamFilename.of("2022-07-13T08_46_08.041986003Z.rcd_sig");
        var data = streamFileData(node, "2022-07-13T08_46_11.304284003Z.rcd_sig");
        StepVerifier.withVirtualTime(() -> streamFileProvider.list(node, lastFilename))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNext(data)
                .expectComplete()
                .verify(Duration.ofSeconds(10L));
    }

    protected final void listNotFound(FileCopier fileCopier, ConsensusNode node) {
        fileCopier.copy();
        var lastFilename = StreamFilename.of("2100-01-01T01_01_01.000000001Z.rcd_sig");
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
                    StreamFilename.of(resolveProviderRelativePath(node, filename), getProviderPathSeparator());
            var filePath = fileCopier
                    .getFrom()
                    .resolve(nodePath(node))
                    .resolve(streamFilename.getFileType() == SIDECAR ? SIDECAR_FOLDER : "")
                    .resolve(filename);
            var bytes = FileUtils.readFileToByteArray(filePath.toFile());
            return new StreamFileData(streamFilename, bytes, Instant.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected StreamFileData streamFileData(ConsensusNode node, String filename) {
        return streamFileData(node, getFileCopier(node), filename);
    }
}
