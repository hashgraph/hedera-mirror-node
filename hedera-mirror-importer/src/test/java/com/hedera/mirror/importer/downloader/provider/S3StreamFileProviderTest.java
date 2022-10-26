package com.hedera.mirror.importer.downloader.provider;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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
import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import org.apache.commons.io.FileUtils;
import org.gaul.s3proxy.S3Proxy;
import org.gaul.shaded.org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStoreContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

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

class S3StreamFileProviderTest {

    private static final int S3_PROXY_PORT = 8001;

    @TempDir
    private Path s3Path;

    private S3Proxy s3Proxy;
    private CommonDownloaderProperties properties;
    private FileCopier fileCopier;
    private S3AsyncClient s3AsyncClient;
    private S3StreamFileProvider streamFileProvider;

    @BeforeEach
    void setup() throws Exception {
        properties = new CommonDownloaderProperties(new MirrorProperties());
        s3AsyncClient = S3AsyncClient.builder()
                .credentialsProvider(AnonymousCredentialsProvider.create())
                .endpointOverride(URI.create("http://localhost:" + S3_PROXY_PORT))
                .forcePathStyle(true)
                .region(Region.of(properties.getRegion()))
                .build();
        streamFileProvider = new S3StreamFileProvider(properties, s3AsyncClient);

        var dataPath = Path.of("data", "recordstreams", "v6").toString();
        fileCopier = FileCopier.create(TestUtils.getResource(dataPath).toPath(), s3Path)
                .to(properties.getBucketName(), StreamType.RECORD.getPath());

        startS3Proxy();
    }

    private void startS3Proxy() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("jclouds.filesystem.basedir", s3Path.toAbsolutePath().toString());

        var context = ContextBuilder
                .newBuilder("filesystem")
                .overrides(properties)
                .build(BlobStoreContext.class);

        s3Proxy = S3Proxy.builder()
                .blobStore(context.getBlobStore())
                .endpoint(URI.create("http://localhost:" + S3_PROXY_PORT))
                .ignoreUnknownHeaders(true)
                .build();
        s3Proxy.start();

        await().atMost(Duration.ofSeconds(2L))
                .pollInterval(Duration.ofMillis(100L))
                .until(() -> s3Proxy.getState().equals(AbstractLifeCycle.STARTED));
    }

    @AfterEach
    void after() throws Exception {
        s3AsyncClient.close();
        if (s3Proxy != null) {
            s3Proxy.stop();
        }
    }

    @Test
    void get() {
        fileCopier.copy();
        var node = node("0.0.3");
        var data = streamFileData(node, "2022-07-13T08_46_08.041986003Z.rcd_sig");
        streamFileProvider.get(node, data.getStreamFilename())
                .as(StepVerifier::create)
                .expectNext(data)
                .expectComplete()
                .verify(Duration.ofMillis(250));
    }

    @Test
    void getSidecar() {
        fileCopier.copy();
        var node = node("0.0.3");
        var data = streamFileData(node, "2022-07-13T08_46_11.304284003Z_01.rcd.gz");
        streamFileProvider.get(node, data.getStreamFilename())
                .as(StepVerifier::create)
                .expectNext(data)
                .expectComplete()
                .verify(Duration.ofMillis(250));
    }

    @Test
    void getNotFound() {
        fileCopier.copy();
        var node = node("0.0.3");
        streamFileProvider.get(node, StreamFilename.EPOCH)
                .as(StepVerifier::create)
                .expectError(NoSuchKeyException.class)
                .verify(Duration.ofMillis(250));
    }

    @Test
    void getError() {
        fileCopier.copy();
        properties.setBucketName("invalid");
        var node = node("0.0.3");
        var data = streamFileData(node, "2022-07-13T08_46_08.041986003Z.rcd_sig");
        streamFileProvider.get(node, data.getStreamFilename())
                .as(StepVerifier::create)
                .expectError(NoSuchBucketException.class)
                .verify(Duration.ofMillis(250));
    }

    @Test
    void list() {
        fileCopier.copy();
        var node = node("0.0.3");
        var data1 = streamFileData(node, "2022-07-13T08_46_08.041986003Z.rcd_sig");
        var data2 = streamFileData(node, "2022-07-13T08_46_11.304284003Z.rcd_sig");
        streamFileProvider.list(node, StreamFilename.EPOCH)
                .as(StepVerifier::create)
                .expectNext(data1)
                .expectNext(data2)
                .expectComplete()
                .verify(Duration.ofMillis(250));
    }

    @Test
    void listAfter() {
        fileCopier.copy();
        var node = node("0.0.3");
        var lastFilename = new StreamFilename("2022-07-13T08_46_08.041986003Z.rcd_sig");
        var data = streamFileData(node, "2022-07-13T08_46_11.304284003Z.rcd_sig");
        streamFileProvider.list(node, lastFilename)
                .as(StepVerifier::create)
                .expectNext(data)
                .expectComplete()
                .verify(Duration.ofMillis(250));
    }

    @Test
    void listNotFound() {
        fileCopier.copy();
        var node = node("0.0.3");
        var lastFilename = new StreamFilename("2100-01-01T01_01_01.000000001Z.rcd_sig");
        streamFileProvider.list(node, lastFilename)
                .as(StepVerifier::create)
                .expectNextCount(0)
                .expectComplete()
                .verify(Duration.ofMillis(250));
    }

    @Test
    void listError() {
        fileCopier.copy();
        var node = node("0.0.3");
        properties.setBucketName("invalid");
        streamFileProvider.list(node, StreamFilename.EPOCH)
                .as(StepVerifier::create)
                .expectError(NoSuchBucketException.class)
                .verify(Duration.ofMillis(250));
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
        streamFileProvider.list(node, StreamFilename.EPOCH)
                .as(StepVerifier::create)
                .expectNext(data1)
                .expectNext(data2)
                .expectComplete()
                .verify(Duration.ofMillis(250));
    }

    private ConsensusNode node(String nodeAccountId) {
        var entityId = EntityId.of(nodeAccountId, ACCOUNT);
        return ConsensusNodeStub.builder()
                .nodeAccountId(entityId)
                .nodeId(entityId.getEntityNum() - 3)
                .build();
    }

    private StreamFileData streamFileData(ConsensusNode node, String filename) {
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
