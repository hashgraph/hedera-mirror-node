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

package com.hedera.mirror.importer;

import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.importer.addressbook.ConsensusNode;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties.PathType;
import java.io.FileFilter;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.filefilter.WildcardFileFilter;

@Log4j2
public class MultiDestinationFileCopier implements FileCopier {
    @Getter
    private final Path from;

    @Getter
    private final Path to;

    private final FileFilter dirFilter;
    private final FileFilter fileFilter;
    private final List<Destination> destinations;
    private final FileCopier defaultFileCopier;
    private final Map<ConsensusNode, NodeInfo> nodeToInfoMap;
    private final Map<PathType, FileCopier> pathTypeToFileCopierMap;

    @Builder(toBuilder = true)
    private MultiDestinationFileCopier(
            @NonNull Path from,
            @NonNull Path to,
            FileFilter dirFilter,
            FileFilter fileFilter,
            @NonNull @Singular List<Destination> destinations) {

        if (destinations.isEmpty()) {
            throw new IllegalArgumentException("At least one destination must be specified");
        }

        this.from = from;
        this.to = to;
        this.dirFilter = dirFilter != null ? dirFilter : ALL_FILTER;
        this.fileFilter = fileFilter != null ? fileFilter : ALL_FILTER;
        this.destinations = destinations;

        var tmpNodeToInfoMap = new HashMap<ConsensusNode, NodeInfo>();
        var tmpPathTypeToFileCopierMap = new HashMap<PathType, FileCopier>();

        for (var destination : destinations) {
            for (var nodeDir : destination.nodeDirs) {
                var node = nodeFromAccountIdDir(nodeDir);

                if (tmpNodeToInfoMap.put(node, new NodeInfo(destination.pathType, destination.streamType)) != null) {
                    throw new IllegalArgumentException(
                            "One or more node account IDs are present in more than one destination.");
                }

                tmpPathTypeToFileCopierMap.computeIfAbsent(destination.pathType, pathType -> FileCopier.create(
                                from, to, pathType, destination.streamType, destination.nodeDirs)
                        .to(destination.nodePath));
            }
        }
        this.pathTypeToFileCopierMap = Collections.unmodifiableMap(tmpPathTypeToFileCopierMap);
        this.defaultFileCopier = this.pathTypeToFileCopierMap.get(destinations.get(0).pathType);

        // Mutable map because a node's destination/path type can be changed.
        this.nodeToInfoMap = Collections.synchronizedMap(tmpNodeToInfoMap);
    }

    private static ConsensusNode nodeFromAccountIdDir(String nodeDir) {
        int idx = nodeDir.indexOf('.');
        if (idx < 1) {
            throw new IllegalArgumentException(
                    "Legacy account ID node directory '%s' not in valid format".formatted(nodeDir));
        }
        return TestUtils.nodeFromAccountId(nodeDir.substring(idx - 1));
    }

    public void setNodePathType(
            @NonNull ConsensusNode node, @NonNull PathType pathType, @NonNull StreamType streamType) {
        this.nodeToInfoMap.put(node, new NodeInfo(pathType, streamType));
    }

    @Override
    public FileCopier from(Path source) {
        return defaultFileCopier.from(source);
    }

    @Override
    public FileCopier from(String... source) {
        return defaultFileCopier.from(source);
    }

    @Override
    public FileCopier filterDirectories(FileFilter newDirFilter) {
        FileFilter andFilter =
                dirFilter == ALL_FILTER ? newDirFilter : f -> dirFilter.accept(f) || newDirFilter.accept(f);
        return toBuilder().dirFilter(andFilter).build();
    }

    @Override
    public FileCopier filterDirectories(String wildcardPattern) {
        return filterDirectories(new WildcardFileFilter(wildcardPattern));
    }

    @Override
    public FileCopier filterFiles(FileFilter newFileFilter) {
        FileFilter andFilter =
                fileFilter == ALL_FILTER ? newFileFilter : f -> fileFilter.accept(f) || newFileFilter.accept(f);
        return toBuilder().fileFilter(andFilter).build();
    }

    @Override
    public FileCopier filterFiles(String wildcardPattern) {
        return filterFiles(new WildcardFileFilter(wildcardPattern));
    }

    @Override
    public FileCopier to(Path target) {
        return defaultFileCopier.to(target);
    }

    @Override
    public FileCopier to(String... target) {
        return defaultFileCopier.to(target);
    }

    @Override
    public Path getTo(@NonNull ConsensusNode node) {
        var nodeInfo = getNodeInfo(node);

        var fileCopier = pathTypeToFileCopierMap.get(nodeInfo.pathType);
        if (fileCopier == null) {
            throw new IllegalStateException("Unmapped pathType '%s' for node '%s'".formatted(nodeInfo.pathType, node));
        }
        return fileCopier.getTo();
    }

    @Override
    public Path getNodePath(ConsensusNode node) {
        var nodeInfo = getNodeInfo(node);
        return TestUtils.nodePath(node, nodeInfo.pathType, nodeInfo.streamType);
    }

    @Override
    public void copy() {
        for (var fileCopier : pathTypeToFileCopierMap.values()) {
            fileCopier.copy();
        }
    }

    private NodeInfo getNodeInfo(ConsensusNode node) {
        var nodeInfo = nodeToInfoMap.get(node);
        if (nodeInfo == null) {
            throw new IllegalArgumentException("Node '%s' is not defined in any destination".formatted(node));
        }
        return nodeInfo;
    }

    public record Destination(Set<String> nodeDirs, PathType pathType, StreamType streamType, String... nodePath) {}

    private record NodeInfo(PathType pathType, StreamType streamType) {}
}
