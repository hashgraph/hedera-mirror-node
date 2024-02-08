/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.common.domain.StreamType.SIGNATURE_SUFFIX;
import static com.hedera.mirror.importer.downloader.CommonDownloaderProperties.PathType.NODE_ID;
import static java.util.Objects.requireNonNullElse;

import com.hedera.mirror.importer.addressbook.ConsensusNode;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties.PathType;
import com.hedera.mirror.importer.exception.FileOperationException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.stream.Collectors;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@CustomLog
@RequiredArgsConstructor
public class LocalStreamFileProvider implements StreamFileProvider {

    private static final File[] EMPTY = new File[0];

    private final CommonDownloaderProperties properties;
    private final LocalStreamFileProperties localProperties;

    @Override
    public Mono<StreamFileData> get(ConsensusNode node, StreamFilename streamFilename) {
        var basePath = properties.getImporterProperties().getStreamPath().toFile();
        return Mono.fromSupplier(() -> new File(basePath, streamFilename.getFilePath()))
                .filter(f -> f.length() <= properties.getMaxSize())
                .map(StreamFileData::from)
                .timeout(properties.getTimeout())
                .onErrorMap(FileOperationException.class, TransientProviderException::new);
    }

    @Override
    public Flux<StreamFileData> list(ConsensusNode node, StreamFilename lastFilename) {
        var batchSize = properties.getBatchSize();
        var startAfter = lastFilename.getFilenameAfter();

        return listFiles(properties.getPathType(), node, lastFilename)
                .switchIfEmpty(listFiles(NODE_ID, node, lastFilename))
                .timeout(properties.getTimeout())
                .filter(r -> r.length() <= properties.getMaxSize())
                .sort()
                .take(batchSize)
                .map(this::toStreamFileData)
                .doOnSubscribe(s -> log.debug("Searching for the next {} files after {}", batchSize, startAfter));
    }

    private Flux<File> listFiles(PathType pathType, ConsensusNode node, StreamFilename streamFilename) {
        var pathTypeProp = properties.getPathType();
        var streamType = streamFilename.getStreamType();

        // Once a node ID based file has been processed, optimize performance by disabling auto path lookup.
        if (pathTypeProp == PathType.AUTO && streamFilename.isNodeId()) {
            properties.setPathType(NODE_ID);
        } // Skip when we fall back to listing by node ID, but we're not on auto.
        else if (pathTypeProp != pathType && pathTypeProp != PathType.AUTO) {
            return Flux.empty();
        }

        return getBasePaths(streamFilename)
                .map(basePath -> {
                    var prefix =
                            switch (pathType) {
                                case ACCOUNT_ID, AUTO -> Path.of(
                                        streamType.getPath(), streamType.getNodePrefix() + node.getNodeAccountId());
                                case NODE_ID -> Path.of(
                                        properties.getImporterProperties().getNetwork(),
                                        String.valueOf(properties
                                                .getImporterProperties()
                                                .getShard()),
                                        String.valueOf(node.getNodeId()),
                                        streamType.getNodeIdBasedSuffix());
                            };

                    return basePath.resolve(prefix).toFile();
                })
                .doOnNext(f -> log.debug("Listing files in {}", f))
                .filter(File::exists)
                .flatMapSequential(dir -> Flux.fromArray(
                        requireNonNullElse(dir.listFiles(f -> matches(streamFilename.getFilenameAfter(), f)), EMPTY)));
    }

    /*
     * Search YYYY-MM-DD sub-folders if present, otherwise just search streams directory.
     */
    private Flux<Path> getBasePaths(StreamFilename streamFilename) {
        var basePath = properties.getImporterProperties().getStreamPath();
        basePath.toFile().mkdirs();

        try (var subDirs = Files.list(basePath)) {
            var date = LocalDate.ofInstant(streamFilename.getInstant(), ZoneOffset.UTC)
                    .toString();
            var paths = subDirs.map(Path::toFile)
                    .filter(f -> f.isDirectory()
                            && f.getName().compareTo(date) >= 0
                            && f.getName().length() == 10)
                    .sorted()
                    .limit(2) // Current and next day
                    .map(File::toPath)
                    .collect(Collectors.toSet());

            if (paths.isEmpty()) {
                return Flux.just(basePath);
            }

            return Flux.fromIterable(paths);
        } catch (Exception e) {
            return Flux.error(new RuntimeException(e));
        }
    }

    private boolean matches(String lastFilename, File file) {
        if (!file.isFile() || !file.canRead()) {
            return false;
        }

        var name = file.getName();

        if (name.compareTo(lastFilename) < 0) {
            try {
                // Files before last file have been processed and can be deleted to optimize list + sort
                if (localProperties.isDeleteAfterProcessing()) {
                    Files.delete(file.toPath());
                }
            } catch (Exception e) {
                log.warn("Unable to delete file {}: {}", file, e.getMessage());
            }
            return false;
        }

        return name.contains(SIGNATURE_SUFFIX);
    }

    private StreamFileData toStreamFileData(File file) {
        var basePath = properties.getImporterProperties().getStreamPath();
        var filename = StreamFilename.from(basePath.relativize(file.toPath()).toString());
        return StreamFileData.from(basePath, filename);
    }
}
