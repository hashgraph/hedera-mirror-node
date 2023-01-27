package com.hedera.mirror.importer.downloader;

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

import static com.hedera.mirror.importer.downloader.CommonDownloaderProperties.SourceType;
import static com.hedera.mirror.importer.downloader.StreamSourceProperties.SourceCredentials;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.hedera.mirror.importer.MirrorProperties;

class CommonDownloaderPropertiesTest {

    @Test
    void getBucketName() {
        var mirrorProperties = new MirrorProperties();
        var properties = new CommonDownloaderProperties(mirrorProperties);
        assertThat(properties.getBucketName()).isEqualTo(mirrorProperties.getNetwork().getBucketName());

        var bucketName = "test";
        properties.setBucketName(bucketName);
        assertThat(properties.getBucketName()).isEqualTo(bucketName);
    }

    @Test
    void initNoSources() {
        var properties = new CommonDownloaderProperties(new MirrorProperties());
        properties.setCloudProvider(SourceType.S3);
        properties.setEndpointOverride("http://localhost");
        properties.setGcpProjectId("project1");
        properties.init();

        assertThat(properties.getSources())
                .hasSize(1)
                .first()
                .returns(null, StreamSourceProperties::getCredentials)
                .returns(properties.getCloudProvider(), StreamSourceProperties::getType)
                .returns(properties.getGcpProjectId(), StreamSourceProperties::getProjectId)
                .returns(properties.getRegion(), StreamSourceProperties::getRegion)
                .returns(URI.create(properties.getEndpointOverride()), StreamSourceProperties::getUri);
    }

    @Test
    void initSources() {
        var sourceProperties = new StreamSourceProperties();
        sourceProperties.setCredentials(new SourceCredentials());
        sourceProperties.setUri(URI.create("http://localhost"));
        sourceProperties.setType(SourceType.GCP);
        sourceProperties.setProjectId("project1");
        var properties = new CommonDownloaderProperties(new MirrorProperties());
        properties.getSources().add(sourceProperties);
        properties.init();

        assertThat(properties.getSources())
                .hasSize(1)
                .first()
                .returns(sourceProperties.getCredentials(), StreamSourceProperties::getCredentials)
                .returns(sourceProperties.getProjectId(), StreamSourceProperties::getProjectId)
                .returns(sourceProperties.getRegion(), StreamSourceProperties::getRegion)
                .returns(sourceProperties.getType(), StreamSourceProperties::getType)
                .returns(sourceProperties.getUri(), StreamSourceProperties::getUri);
    }

    @Test
    void initSourcesAndLegacySource() {
        var sourceProperties = new StreamSourceProperties();
        sourceProperties.setCredentials(new SourceCredentials());
        sourceProperties.setUri(URI.create("http://localhost"));
        sourceProperties.setType(SourceType.GCP);
        sourceProperties.setProjectId("project1");

        var properties = new CommonDownloaderProperties(new MirrorProperties());
        properties.setAccessKey("foo");
        properties.setCloudProvider(SourceType.S3);
        properties.setEndpointOverride("http://localhost");
        properties.setGcpProjectId("project1");
        properties.setSecretKey("bar");
        properties.getSources().add(sourceProperties);
        properties.init();

        var listAssert = assertThat(properties.getSources()).hasSize(2);

        listAssert.first()
                .returns(properties.getAccessKey(), s -> s.getCredentials().getAccessKey())
                .returns(properties.getCloudProvider(), StreamSourceProperties::getType)
                .returns(properties.getGcpProjectId(), StreamSourceProperties::getProjectId)
                .returns(properties.getRegion(), StreamSourceProperties::getRegion)
                .returns(properties.getSecretKey(), s -> s.getCredentials().getSecretKey())
                .returns(URI.create(properties.getEndpointOverride()), StreamSourceProperties::getUri);

        listAssert.element(1)
                .returns(sourceProperties.getCredentials(), StreamSourceProperties::getCredentials)
                .returns(sourceProperties.getProjectId(), StreamSourceProperties::getProjectId)
                .returns(sourceProperties.getRegion(), StreamSourceProperties::getRegion)
                .returns(sourceProperties.getType(), StreamSourceProperties::getType)
                .returns(sourceProperties.getUri(), StreamSourceProperties::getUri);
    }

    @CsvSource({
            "GCP, true, true",
            "GCP, false, true",
            "S3, true, true",
            "S3, false, false",
    })
    @ParameterizedTest
    void isStaticCredentials(SourceType sourceType, boolean credentials, boolean expected) {
        var properties = new StreamSourceProperties();
        properties.setCredentials(credentials ? new SourceCredentials() : null);
        properties.setType(sourceType);

        assertThat(properties.isStaticCredentials()).isEqualTo(expected);
    }
}
