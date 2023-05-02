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
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;

/*
 *  Test cases to verify proper functionality of the S3StreamFileProvider when it is configured for
 *  PathType.AUTO, or the transition from the legacy consensus node account ID based S3 bucket
 *  hierarchy to the consensus node ID based structure (HIP-679).
 *
 *  When the mirror node is configured via its properties to operate in AUTO mode, the observed path
 *  type is maintained per node. For as long as stream files are found in the legacy node account ID
 *  structure, AUTO mode continues for that mode. Once files are not found in the legacy structure, and
 *  are instead found in the new node ID based hierarchy, that node is considered to be in node ID mode and
 *  files are only downloaded from the new structure.
 *
 *  Per consensus node path type mode status is maintained in memory only. Upon start up, the mirror node
 *  will quickly figure out the optimal manner to download stream files per node. Once the HIP-679 transition
 *  period has completed, the mirror node properties will be set to the node ID type, by which all consensus
 *  nodes are expected to upload stream files using only the new bucket structure.
 */
public class AutoS3StreamProviderTest extends S3StreamFileProviderTest {

    @BeforeEach
    void setup() throws Exception {
        super.setup();
    }

    @Override
    protected FileCopier getFileCopier(Path fromPath, Path toPath) {
        // Legacy account ID based stream files
        var accountIdFileCopier = FileCopier.create(
                        TestUtils.getResource(fromPath.toString()).toPath(),
                        toPath,
                        PathType.ACCOUNT_ID,
                        Set.of("record0.0.3", "record0.0.4"))
                .to(properties.getBucketName(), StreamType.RECORD.getPath());

        // HIP-679 node ID based stream files
        String network =
                properties.getMirrorProperties().getNetwork().toString().toLowerCase();
        return FileCopier.create(
                        TestUtils.getResource(fromPath.toString()).toPath(),
                        toPath,
                        PathType.NODE_ID,
                        Set.of("record0.0.5", "record0.0.6"),
                        accountIdFileCopier)
                .to(properties.getBucketName(), network);
    }

    @Override
    protected void customizeProperties(CommonDownloaderProperties properties) {
        super.customizeProperties(properties);
        properties.setPathType(PathType.AUTO);
        properties.setPathRefreshInterval(Duration.ofSeconds(2L));
    }

    @Override
    protected Path getNodePath(ConsensusNode node) {
        return Path.of("0", String.valueOf(node.getNodeId()), StreamType.RECORD.getNodeIdBasedSuffix());
    }
}
