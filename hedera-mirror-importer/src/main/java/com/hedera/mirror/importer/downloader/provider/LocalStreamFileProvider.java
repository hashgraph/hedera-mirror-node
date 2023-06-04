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

import com.hedera.mirror.importer.addressbook.ConsensusNode;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;
import com.hedera.mirror.importer.exception.FileOperationException;
import java.io.File;
import java.nio.file.Files;
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
        return Mono.fromSupplier(() -> streamFilename.getFilePath())
                .map(File::new)
                .map(StreamFileData::from)
                .timeout(commonDownloaderProperties.getTimeout())
                .onErrorMap(FileOperationException.class, TransientProviderException::new);
    }

    @Override
    public Flux<StreamFileData> list(ConsensusNode node, StreamFilename last) {
        // Number of items we plan do download in a single batch times two for file plus signature.
        final int batchSize = commonDownloaderProperties.getBatchSize() * 2;
        final String lastFilename = last.getFilenameAfter();

        return Mono.fromSupplier(() -> getDirectory(node, last))
                .timeout(commonDownloaderProperties.getTimeout())
                .flatMapIterable(dir -> Arrays.asList(dir.listFiles(f -> matches(lastFilename, f))))
                .sort()
                .take(batchSize)
                .map(StreamFileData::from)
                .doOnSubscribe(s -> log.debug("Searching for the next {} files after {}", batchSize, lastFilename));
    }

    private File getDirectory(ConsensusNode node, StreamFilename streamFilename) {
        var streamType = streamFilename.getStreamType();
        var path = commonDownloaderProperties
                .getMirrorProperties()
                .getDataPath()
                .resolve(STREAMS)
                .resolve(streamType.getPath())
                .resolve(streamType.getNodePrefix() + node.getNodeAccountId());

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
