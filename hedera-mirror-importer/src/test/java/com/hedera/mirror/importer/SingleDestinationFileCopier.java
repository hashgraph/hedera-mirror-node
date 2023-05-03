/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;

@Log4j2
@Value
public class SingleDestinationFileCopier implements FileCopier {

    private static final Map<String, String> STREAM_TYPE_MAP = Map.of(
            StreamType.BALANCE.getNodePrefix(), StreamType.BALANCE.getNodeIdBasedSuffix(),
            StreamType.EVENT.getNodePrefix(), StreamType.EVENT.getNodeIdBasedSuffix(),
            StreamType.RECORD.getNodePrefix(), StreamType.RECORD.getNodeIdBasedSuffix());

    Path from;
    Path to;
    FileFilter dirFilter;
    FileFilter fileFilter;
    PathType pathType;
    StreamType streamType;
    Set<String> copyOnlyDirs;

    SingleDestinationFileCopier(
            @NonNull Path from,
            @NonNull Path to,
            @NonNull FileFilter dirFilter,
            @NonNull FileFilter fileFilter,
            @NonNull PathType pathType,
            @NonNull StreamType streamType,
            @NonNull Set<String> copyOnlyDirs) {

        if (pathType == PathType.AUTO) {
            throw new IllegalArgumentException("The path type cannot be AUTO");
        }
        this.from = from;
        this.to = to;
        this.dirFilter = dirFilter;
        this.fileFilter = fileFilter;
        this.pathType = pathType;
        this.streamType = streamType;
        this.copyOnlyDirs = copyOnlyDirs;
    }

    public static FileCopier create(
            Path from,
            Path to,
            FileFilter dirFilter,
            FileFilter fileFilter,
            PathType pathType,
            StreamType streamType,
            Set<String> copyOnlyDirs) {
        return new SingleDestinationFileCopier(from, to, dirFilter, fileFilter, pathType, streamType, copyOnlyDirs);
    }

    @Override
    public FileCopier from(Path source) {
        return new SingleDestinationFileCopier(
                from.resolve(source), to, dirFilter, fileFilter, pathType, streamType, copyOnlyDirs);
    }

    @Override
    public FileCopier from(String... source) {
        return from(Paths.get("", source));
    }

    @Override
    public FileCopier filterDirectories(FileFilter newDirFilter) {
        FileFilter andFilter =
                dirFilter == ALL_FILTER ? newDirFilter : f -> dirFilter.accept(f) || newDirFilter.accept(f);
        return new SingleDestinationFileCopier(from, to, andFilter, fileFilter, pathType, streamType, copyOnlyDirs);
    }

    @Override
    public FileCopier filterDirectories(String wildcardPattern) {
        return filterDirectories(new WildcardFileFilter(wildcardPattern));
    }

    @Override
    public FileCopier filterFiles(FileFilter newFileFilter) {
        FileFilter andFilter =
                fileFilter == ALL_FILTER ? newFileFilter : f -> fileFilter.accept(f) || newFileFilter.accept(f);
        return new SingleDestinationFileCopier(from, to, dirFilter, andFilter, pathType, streamType, copyOnlyDirs);
    }

    @Override
    public FileCopier filterFiles(String wildcardPattern) {
        return filterFiles(new WildcardFileFilter(wildcardPattern));
    }

    @Override
    public FileCopier to(Path target) {
        return new SingleDestinationFileCopier(
                from, to.resolve(target), dirFilter, fileFilter, pathType, streamType, copyOnlyDirs);
    }

    @Override
    public Path getTo(@NonNull ConsensusNode node) {
        return getTo();
    }

    @Override
    public FileCopier to(String... target) {
        return to(Paths.get("", target));
    }

    @Override
    public Path getNodePath(ConsensusNode node) {
        return TestUtils.nodePath(node, pathType, streamType);
    }

    @Override
    public void copy() {
        try {
            log.debug("Copying {} to {}", from, to);
            FileFilter combinedFilter = f -> f.isDirectory() ? dirFilter.accept(f) : fileFilter.accept(f);
            if (pathType == PathType.ACCOUNT_ID) {
                copyAccountIdFormat(from, to, combinedFilter);
            } else {
                copyNodeIdFormat(from, to, combinedFilter);
            }

            if (log.isTraceEnabled()) {
                try (Stream<Path> paths = Files.walk(to)) {
                    paths.forEach(p -> log.trace("Moved: {}", p));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> getCandidateDirectoryNames() throws IOException {
        try (Stream<Path> paths = Files.list(from)) {
            return paths.filter(Files::isDirectory)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(dir -> copyOnlyDirs.isEmpty() || copyOnlyDirs.contains(dir))
                    .toList();
        }
    }

    private void copyAccountIdFormat(Path from, Path to, FileFilter filter) throws IOException {
        if (copyOnlyDirs.isEmpty()) {
            FileUtils.copyDirectory(from.toFile(), to.toFile(), filter);
        } else {
            var accountIdDirectoryNames = getCandidateDirectoryNames();
            for (var directoryName : accountIdDirectoryNames) {
                var sourcePath = from.resolve(directoryName);
                var destinationPath = to.resolve(directoryName);
                FileUtils.copyDirectory(sourcePath.toFile(), destinationPath.toFile(), filter);
            }
        }
    }

    private void copyNodeIdFormat(Path from, Path to, FileFilter filter) throws IOException {

        /*
         * Directories present at the base of the repo test data from path are of the format xxxxn.n.n which
         * represents the stream type (balance, events_, record) followed by the shard, realm and the account
         * number. This is the legacy consensus node account ID format. E.g. balance0.0.3, events_0.0.4 etc.
         *
         * When the test cases define stub Consensus node instances the node ID is derived as the account number -3.
         * Therefore, balance0.0.3 has a node ID of 0. The stream files present in the from directories are copied
         * according to HIP-679:
         *                         {network}/{shard}/{nodeID}/balance/
         *                         {network}/{shard}/{nodeID}/event/
         *                         {network}/{shard}/{nodeID}/record/
         *
         * The network is a property value and has already been incorporated into the FileCopier to path. The remaining
         * items can be derived from the directory names.
         */
        var accountIdDirectoryNames = getCandidateDirectoryNames();
        for (var directoryName : accountIdDirectoryNames) {
            String[] accountIdParts = directoryName.split("\\.");
            if (accountIdParts.length != 3) {
                throw new RuntimeException(String.format(
                        "Source directory name '%s' is not of the form {stream}{shard}.{realm}.{account}",
                        directoryName));
            }

            try {
                var streamAndShard = accountIdParts[0];
                var stream = streamAndShard.substring(0, streamAndShard.length() - 1);
                var nodeIdSuffix = STREAM_TYPE_MAP.get(stream);
                if (nodeIdSuffix == null) {
                    throw new RuntimeException(String.format(
                            "Source directory name '%s' stream type '%s' is not valid", directoryName, stream));
                }

                var shard = Long.valueOf(streamAndShard.substring(streamAndShard.length() - 1));
                var nodeId = Long.valueOf(accountIdParts[2]).longValue() - 3L;
                var sourcePath = from.resolve(directoryName);
                var destinationPath = to.resolve(Path.of(shard.toString(), String.valueOf(nodeId), nodeIdSuffix));
                FileUtils.copyDirectory(sourcePath.toFile(), destinationPath.toFile(), filter);
            } catch (NumberFormatException ex) {
                throw new RuntimeException(
                        String.format(
                                "Source directory name '%s' shard or account is not a valid number", directoryName),
                        ex);
            } catch (IndexOutOfBoundsException ex) {
                throw new RuntimeException(
                        String.format("Source directory name '%s' is not in a valid format", directoryName), ex);
            }
        }
    }
}
