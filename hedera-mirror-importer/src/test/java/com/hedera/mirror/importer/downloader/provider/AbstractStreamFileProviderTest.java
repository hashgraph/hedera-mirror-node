package com.hedera.mirror.importer.downloader.provider;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static com.hedera.mirror.importer.domain.StreamFilename.FileType.SIDECAR;
import static com.hedera.mirror.importer.downloader.provider.S3StreamFileProvider.SIDECAR_FOLDER;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.addressbook.ConsensusNode;
import com.hedera.mirror.importer.domain.ConsensusNodeStub;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;

abstract class AbstractStreamFileProviderTest {

    @TempDir
    protected Path dataPath;

    protected CommonDownloaderProperties properties;
    protected FileCopier fileCopier;
    protected StreamFileProvider streamFileProvider;

    @BeforeEach
    void setup() throws Exception {
        var mirrorProperties = new MirrorProperties();
        mirrorProperties.setDataPath(dataPath);
        properties = new CommonDownloaderProperties(mirrorProperties);

        var path = Path.of("data", "recordstreams", "v6").toString();
        fileCopier = FileCopier.create(TestUtils.getResource(path).toPath(), dataPath)
                .to(getDirectory(), StreamType.RECORD.getPath());
    }

    protected abstract String getDirectory();

    @Test
    void get() {
        fileCopier.copy();
        var node = node("0.0.3");
        var data = streamFileData(node, "2022-07-13T08_46_08.041986003Z.rcd_sig");
        StepVerifier.withVirtualTime(() -> streamFileProvider.get(node, data.getStreamFilename()))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNext(data)
                .expectComplete()
                .verify(Duration.ofSeconds(10L));
    }

    @Test
    void getSidecar() {
        fileCopier.copy();
        var node = node("0.0.3");
        var data = streamFileData(node, "2022-07-13T08_46_11.304284003Z_01.rcd.gz");
        StepVerifier.withVirtualTime(() -> streamFileProvider.get(node, data.getStreamFilename()))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNext(data)
                .expectComplete()
                .verify(Duration.ofSeconds(10L));
    }

    @Test
    void getNotFound() {
        fileCopier.copy();
        var node = node("0.0.3");
        StepVerifier.withVirtualTime(() -> streamFileProvider.get(node, StreamFilename.EPOCH))
                .thenAwait(Duration.ofSeconds(10L))
                .expectError(TransientProviderException.class)
                .verify(Duration.ofSeconds(10L));
    }

    @Test
    void getError() {
        fileCopier.copy();
        dataPath.toFile().setExecutable(false);
        var node = node("0.0.3");
        var data = streamFileData(node, "2022-07-13T08_46_08.041986003Z.rcd_sig");
        StepVerifier.withVirtualTime(() -> streamFileProvider.get(node, data.getStreamFilename()))
                .thenAwait(Duration.ofSeconds(10L))
                .expectError(RuntimeException.class)
                .verify(Duration.ofSeconds(10L));
    }

    @Test
    void list() {
        fileCopier.copy();
        var node = node("0.0.3");
        var data1 = streamFileData(node, "2022-07-13T08_46_08.041986003Z.rcd_sig");
        var data2 = streamFileData(node, "2022-07-13T08_46_11.304284003Z.rcd_sig");
        StepVerifier.withVirtualTime(() -> streamFileProvider.list(node, StreamFilename.EPOCH))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNext(data1)
                .expectNext(data2)
                .expectComplete()
                .verify(Duration.ofSeconds(10L));
    }

    @Test
    void listAfter() {
        fileCopier.copy();
        var node = node("0.0.3");
        var lastFilename = new StreamFilename("2022-07-13T08_46_08.041986003Z.rcd_sig");
        var data = streamFileData(node, "2022-07-13T08_46_11.304284003Z.rcd_sig");
        StepVerifier.withVirtualTime(() -> streamFileProvider.list(node, lastFilename))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNext(data)
                .expectComplete()
                .verify(Duration.ofSeconds(10L));
    }

    @Test
    void listNotFound() {
        fileCopier.copy();
        var node = node("0.0.3");
        var lastFilename = new StreamFilename("2100-01-01T01_01_01.000000001Z.rcd_sig");
        StepVerifier.withVirtualTime(() -> streamFileProvider.list(node, lastFilename))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNextCount(0)
                .expectComplete()
                .verify(Duration.ofSeconds(10L));
    }

    @Test
    void listError() {
        fileCopier.copy();
        var node = node("0.0.3");
        dataPath.toFile().setExecutable(false);
        StepVerifier.withVirtualTime(() -> streamFileProvider.list(node, StreamFilename.EPOCH))
                .thenAwait(Duration.ofSeconds(10L))
                .expectError(RuntimeException.class)
                .verify(Duration.ofSeconds(10L));
    }

    @Test
    void listInvalidFilename() throws Exception {
        var node = node("0.0.3");
        fileCopier.copy();
        fileCopier.getTo()
                .resolve(StreamType.RECORD.getNodePrefix() + node.getNodeAccountId())
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

    protected ConsensusNode node(String nodeAccountId) {
        var entityId = EntityId.of(nodeAccountId, ACCOUNT);
        return ConsensusNodeStub.builder()
                .nodeAccountId(entityId)
                .nodeId(entityId.getEntityNum() - 3)
                .build();
    }

    protected StreamFileData streamFileData(ConsensusNode node, String filename) {
        try {
            var streamFilename = new StreamFilename(filename);
            var filePath = fileCopier.getFrom()
                    .resolve(StreamType.RECORD.getNodePrefix() + node.getNodeAccountId())
                    .resolve(streamFilename.getFileType() == SIDECAR ? SIDECAR_FOLDER : "")
                    .resolve(filename);
            var bytes = FileUtils.readFileToByteArray(filePath.toFile());
            return new StreamFileData(streamFilename, bytes, Instant.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
