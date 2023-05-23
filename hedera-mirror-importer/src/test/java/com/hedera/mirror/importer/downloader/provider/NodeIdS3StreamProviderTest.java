/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.addressbook.ConsensusNode;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties.PathType;
import java.nio.file.Path;
import java.time.Duration;

/*
 * Copy HIP-679 bucket structured files and configure the stream file provider to access S3 using
 * the new consensus node ID based hierarchy rather than the legacy node account ID mechanism.
 */
public class NodeIdS3StreamProviderTest extends S3StreamFileProviderTest {

    @Override
    protected FileCopier createFileCopier(Path dataPath) {
        String network =
                properties.getMirrorProperties().getNetwork().toString().toLowerCase();
        var fromPath = Path.of("data", "hip679", "provider-node-id");

        return FileCopier.create(TestUtils.getResource(fromPath.toString()).toPath(), dataPath)
                .to(properties.getBucketName(), network);
    }

    @Override
    protected void customizeProperties(CommonDownloaderProperties properties) {
        super.customizeProperties(properties);
        properties.setPathType(PathType.NODE_ID);
        properties.setPathRefreshInterval(Duration.ofSeconds(2L));
    }

    @Override
    protected Path nodePath(ConsensusNode node) {
        return TestUtils.nodePath(node, PathType.NODE_ID, StreamType.RECORD);
    }
}
