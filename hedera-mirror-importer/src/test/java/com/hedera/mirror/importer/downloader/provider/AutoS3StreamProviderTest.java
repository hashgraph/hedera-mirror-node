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
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties.PathType;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/*
 * Copy HIP-679 bucket structured files and configure the stream file provider to access S3 using
 * the new consensus node ID based hierarchy rather than the legacy node account ID mechanism.
 */
class AutoS3StreamProviderTest extends S3StreamFileProviderTest {

    private FileCopier nodeIdFileCopier;
    private Map<ConsensusNode, NodeInfo> nodePathTypeMap;

    @Override
    protected FileCopier createFileCopier(Path dataPath) {
        var accountIdFromPath = Path.of("data", "hip679", "provider-auto", "recordstreams");
        var accountIdFileCopier = FileCopier.create(
                        TestUtils.getResource(accountIdFromPath.toString()).toPath(), dataPath)
                .to(properties.getBucketName(), StreamType.RECORD.getPath());

        var nodeIdFromPath = Path.of("data", "hip679", "provider-auto", "demo");
        var network = properties.getMirrorProperties().getNetwork().toString().toLowerCase();
        nodeIdFileCopier = FileCopier.create(
                        TestUtils.getResource(nodeIdFromPath.toString()).toPath(), dataPath)
                .to(properties.getBucketName(), network);

        var accountIdNodeInfo = new NodeInfo(accountIdFileCopier, PathType.ACCOUNT_ID);
        var nodeIdNodeInfo = new NodeInfo(nodeIdFileCopier, PathType.NODE_ID);
        // Specify how individual node stream files are to be handled within the file system (source directories
        // files are copied form as well as copied to, created the S3 bucket structure.
        nodePathTypeMap = Map.of(
                TestUtils.nodeFromAccountId("0.0.3"), accountIdNodeInfo,
                TestUtils.nodeFromAccountId("0.0.4"), nodeIdNodeInfo);

        return accountIdFileCopier;
    }

    @Override
    protected void customizeProperties(CommonDownloaderProperties properties) {
        super.customizeProperties(properties);
        properties.setPathType(PathType.AUTO);
        properties.setPathRefreshInterval(Duration.ofSeconds(0L));
    }

    @Override
    protected FileCopier getFileCopier(ConsensusNode node) {
        var nodeInfo = getNodeInfo(node);
        return nodeInfo.fileCopier;
    }

    @Override
    protected Path nodePath(ConsensusNode node) {
        var nodeInfo = getNodeInfo(node);
        return TestUtils.nodePath(node, nodeInfo.pathType, StreamType.RECORD);
    }

    @Test
    void listAfterNodeId() {
        nodeIdFileCopier.copy();
        var node = node("0.0.4"); // Node ID 1
        var lastFilename = new StreamFilename("2022-07-13T08_46_08.041986003Z.rcd_sig");
        var data = streamFileData(node, "2022-07-13T08_46_11.304284003Z.rcd_sig");
        StepVerifier.withVirtualTime(() -> streamFileProvider.list(node, lastFilename))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNext(data)
                .expectComplete()
                .verify(Duration.ofSeconds(10L));
    }

    private NodeInfo getNodeInfo(ConsensusNode node) {
        var nodeInfo = nodePathTypeMap.get(node);
        if (nodeInfo == null) {
            throw new IllegalStateException("Node '%s' is not pre-defined in node to info map".formatted(node));
        }
        return nodeInfo;
    }

    private record NodeInfo(FileCopier fileCopier, PathType pathType) {}
}
