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

import static org.awaitility.Awaitility.await;
import static software.amazon.awssdk.core.client.config.SdkAdvancedAsyncClientOption.FUTURE_COMPLETION_EXECUTOR;

import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.TestUtils;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.ForkJoinPool;
import org.gaul.s3proxy.S3Proxy;
import org.gaul.shaded.org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStoreContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

class S3StreamFileProviderTest extends AbstractStreamFileProviderTest {

    private static final int S3_PROXY_PORT = 8001;

    private S3Proxy s3Proxy;

    @BeforeEach
    void setup() throws Exception {
        super.setup();
        var s3AsyncClient = S3AsyncClient.builder()
                .asyncConfiguration(b -> b.advancedOption(FUTURE_COMPLETION_EXECUTOR, ForkJoinPool.commonPool()))
                .credentialsProvider(AnonymousCredentialsProvider.create())
                .endpointOverride(URI.create("http://localhost:" + S3_PROXY_PORT))
                .forcePathStyle(true)
                .region(Region.of(properties.getRegion()))
                .build();
        streamFileProvider = new S3StreamFileProvider(properties, s3AsyncClient);
        startS3Proxy();
    }

    @Override
    protected FileCopier createFileCopier(Path dataPath) {
        var fromPath = Path.of("data", "recordstreams", "v6");
        return FileCopier.create(TestUtils.getResource(fromPath.toString()).toPath(), dataPath)
                .to(properties.getBucketName(), StreamType.RECORD.getPath());
    }

    private void startS3Proxy() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(
                "jclouds.filesystem.basedir", dataPath.toAbsolutePath().toString());

        var context =
                ContextBuilder.newBuilder("filesystem").overrides(properties).build(BlobStoreContext.class);

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
        if (s3Proxy != null) {
            s3Proxy.stop();
        }
    }
}
