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

import static com.hedera.mirror.common.domain.StreamType.SIGNATURE_SUFFIX;
import static com.hedera.mirror.importer.domain.StreamFilename.SIDECAR_FOLDER;
import static com.hedera.mirror.importer.downloader.CommonDownloaderProperties.PathType.NODE_ID;

import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.importer.addressbook.ConsensusNode;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties.PathType;
import com.hedera.mirror.importer.exception.FileOperationException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@CustomLog
@RequiredArgsConstructor
public class LocalStreamFileProvider implements StreamFileProvider {

    static final String STREAMS = "streams";

    private final CommonDownloaderProperties commonDownloaderProperties;

    @Override
    public Mono<StreamFileData> get(ConsensusNode node, StreamFilename streamFilename) {
        var basePath =
                commonDownloaderProperties.getMirrorProperties().getDataPath().resolve(STREAMS);
        return Mono.fromSupplier(() -> StreamFileData.from(basePath, streamFilename))
                .timeout(commonDownloaderProperties.getTimeout())
                .onErrorMap(FileOperationException.class, TransientProviderException::new);
    }

    @Override
    public Flux<StreamFileData> list(ConsensusNode node, StreamFilename lastFilename) {
        // Number of items we plan do download in a single batch times two for file plus signature.
        var batchSize = commonDownloaderProperties.getBatchSize() * 2;
        var startAfter = lastFilename.getFilenameAfter();
        var streamType = lastFilename.getStreamType();

        var startingPathType = commonDownloaderProperties.getPathType();
        var basePath =
                commonDownloaderProperties.getMirrorProperties().getDataPath().resolve(STREAMS);
        var prefixPath = getPrefixPath(startingPathType, node, streamType);

        return Mono.fromSupplier(() -> getDirectory(basePath, prefixPath, lastFilename))
                .timeout(commonDownloaderProperties.getTimeout())
                .flatMapIterable(dir -> Arrays.asList(dir.listFiles(f -> matches(startAfter, f))))
                .switchIfEmpty(Flux.defer(() -> {
                    // Since local FS access is fast and cheap (unlike S3), no refresh interval, state nor
                    // complex logic is implemented for AUTO mode. Simply move on to the node ID based structure.
                    if (startingPathType == PathType.AUTO) {
                        log.debug("No files found in node account ID bucket structure after: {}", startAfter);
                        var nodeIdPrefixPath = getPrefixPath(NODE_ID, node, streamType);
                        var dir = getDirectory(basePath, nodeIdPrefixPath, lastFilename);
                        return Flux.fromArray(dir.listFiles(f -> matches(startAfter, f)));
                    }
                    return Flux.empty();
                }))
                .sort()
                .take(batchSize)
                .map(file -> StreamFilename.from(prefixPath.toString(), file.getName(), File.separator))
                .flatMapSequential(streamFilename -> get(node, streamFilename))
                .doOnSubscribe(s -> log.debug("Searching for the next {} files after {}", batchSize, startAfter));
    }

    Path getPrefixPath(PathType pathType, ConsensusNode node, StreamType streamType) {
        return switch (pathType) {
            case ACCOUNT_ID, AUTO -> Path.of(
                    streamType.getPath(), streamType.getNodePrefix() + node.getNodeAccountId());
            case NODE_ID -> Path.of(
                    commonDownloaderProperties.getMirrorProperties().getNetwork(),
                    String.valueOf(
                            commonDownloaderProperties.getMirrorProperties().getShard()),
                    streamType.getNodePrefix(),
                    node.getNodeAccountId().toString());
        };
    }

    private File getDirectory(Path basePath, Path prefixPath, StreamFilename streamFilename) {
        var path = basePath.resolve(prefixPath);
        if (streamFilename.getFileType() == StreamFilename.FileType.SIDECAR) {
            path = path.resolve(SIDECAR_FOLDER);
        }

        var file = path.toFile();
        if (!file.exists()) {
            var created = file.mkdirs();
            if (!created || !file.canRead() || !file.canExecute()) {
                throw new FileOperationException("Unable to read local stream directory " + path);
            }
        }

        return file;
    }

    private boolean matches(String lastFilename, File file) {
        if (!file.isFile() || !file.canRead()) {
            return false;
        }

        var name = file.getName();
        if (name.compareTo(lastFilename) < 0) {
            try {
                // Files before last file have been processed and can be deleted to optimize list + sort
                Files.delete(file.toPath());
            } catch (Exception e) {
                log.warn("Unable to delete file {}: {}", file, e.getMessage());
            }
            return false;
        }

        return name.endsWith(SIGNATURE_SUFFIX);
    }
}
