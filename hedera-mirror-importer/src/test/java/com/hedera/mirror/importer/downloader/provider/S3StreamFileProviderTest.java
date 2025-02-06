/*
 * Copyright (C) 2022-2025 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.importer.TestUtils.S3_PROXY_PORT;
import static com.hedera.mirror.importer.downloader.provider.S3StreamFileProvider.SEPARATOR;
import static software.amazon.awssdk.core.client.config.SdkAdvancedAsyncClientOption.FUTURE_COMPLETION_EXECUTOR;

import com.hedera.mirror.importer.TestUtils;
import java.net.URI;
import java.util.concurrent.ForkJoinPool;
import lombok.SneakyThrows;
import org.gaul.s3proxy.S3Proxy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

class S3StreamFileProviderTest extends AbstractStreamFileProviderTest {

    private S3Proxy s3Proxy;

    @Override
    protected String providerPathSeparator() {
        return SEPARATOR;
    }

    @Override
    protected String targetRootPath() {
        return properties.getBucketName();
    }

    @BeforeEach
    @Override
    void setup() {
        super.setup();
        var s3AsyncClient = S3AsyncClient.builder()
                .asyncConfiguration(b -> b.advancedOption(FUTURE_COMPLETION_EXECUTOR, ForkJoinPool.commonPool()))
                .credentialsProvider(AnonymousCredentialsProvider.create())
                .endpointOverride(URI.create("http://localhost:" + S3_PROXY_PORT))
                .forcePathStyle(true)
                .region(Region.of(properties.getRegion()))
                .build();
        streamFileProvider = new S3StreamFileProvider(properties, s3AsyncClient);
        s3Proxy = TestUtils.startS3Proxy(dataPath);
    }

    @AfterEach
    @SneakyThrows
    void after() {
        if (s3Proxy != null) {
            s3Proxy.stop();
        }
    }
}
