package com.hedera.mirror.importer.downloader.provider;

import static com.hedera.mirror.common.domain.StreamType.SIGNATURE_SUFFIX;
import static com.hedera.mirror.importer.downloader.provider.S3StreamFileProvider.SIDECAR_FOLDER;

import java.io.File;
import java.util.Arrays;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.hedera.mirror.importer.addressbook.ConsensusNode;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;
import com.hedera.mirror.importer.exception.FileOperationException;

@CustomLog
@RequiredArgsConstructor
public class LocalStreamFileProvider implements StreamFileProvider {

    static final String SIDECAR = "sidecar";
    static final String STREAMS = "streams";

    private final CommonDownloaderProperties commonDownloaderProperties;

    @Override
    public Mono<StreamFileData> get(ConsensusNode node, StreamFilename streamFilename) {
        return Mono.fromSupplier(() -> getDirectory(node, streamFilename))
                .map(dir -> dir.toPath().resolve(streamFilename.getFilename()).toFile())
                .map(file -> StreamFileData.from(file))
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
        var path = commonDownloaderProperties.getMirrorProperties()
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
            file.delete(); // Files before last file have been processed and can be deleted to optimize find + sort
            return false;
        }

        return name.endsWith(SIGNATURE_SUFFIX);
    }
}
