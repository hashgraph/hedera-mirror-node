package com.hedera.mirror.importer.downloader.provider;

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

import static com.hedera.mirror.importer.domain.StreamFilename.EPOCH;
import static com.hedera.mirror.importer.domain.StreamFilename.FileType.SIDECAR;
import static com.hedera.mirror.importer.domain.StreamFilename.FileType.SIGNATURE;

import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.RequestPayer;
import software.amazon.awssdk.services.s3.model.S3Object;

import com.hedera.mirror.importer.addressbook.ConsensusNode;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;

@CustomLog
@RequiredArgsConstructor
public final class S3StreamFileProvider implements StreamFileProvider {

    static final String SIDECAR_FOLDER = "sidecar/";
    private static final String SEPARATOR = "/";

    private final CommonDownloaderProperties commonDownloaderProperties;
    private final S3AsyncClient s3Client;

    public Mono<StreamFileData> get(ConsensusNode node, StreamFilename streamFilename) {
        var prefix = getPrefix(node, streamFilename);
        var s3Key = prefix + streamFilename.getFilename();

        var request = GetObjectRequest.builder()
                .bucket(commonDownloaderProperties.getBucketName())
                .key(s3Key)
                .requestPayer(RequestPayer.REQUESTER)
                .build();

        var responseFuture = s3Client.getObject(request, AsyncResponseTransformer.toBytes());
        return Mono.fromFuture(responseFuture)
                .map(r -> new StreamFileData(streamFilename, r.asByteArrayUnsafe(), r.response().lastModified()))
                .timeout(commonDownloaderProperties.getTimeout())
                .onErrorMap(NoSuchKeyException.class, TransientProviderException::new)
                .doOnSuccess(s -> log.debug("Finished downloading {}", s3Key));
    }

    @Override
    public Flux<StreamFileData> list(ConsensusNode node, StreamFilename lastFilename) {
        // Number of items we plan do download in a single batch times 2 for file + sig.
        int batchSize = commonDownloaderProperties.getBatchSize() * 2;
        var prefix = getPrefix(node, lastFilename);
        var startAfter = prefix + lastFilename.getFilenameAfter();

        var listRequest = ListObjectsV2Request.builder()
                .bucket(commonDownloaderProperties.getBucketName())
                .prefix(prefix)
                .delimiter(SEPARATOR)
                .startAfter(startAfter)
                .maxKeys(batchSize)
                .requestPayer(RequestPayer.REQUESTER)
                .build();

        return Mono.fromFuture(s3Client.listObjectsV2(listRequest))
                .timeout(commonDownloaderProperties.getTimeout())
                .doOnNext(l -> log.debug("Returned {} s3 objects", l.contents().size()))
                .flatMapIterable(ListObjectsV2Response::contents)
                .map(this::toStreamFilename)
                .filter(s -> s != EPOCH && s.getFileType() == SIGNATURE)
                .flatMapSequential(streamFilename -> get(node, streamFilename))
                .doOnSubscribe(s -> log.debug("Searching for the next {} files after {}/{}", batchSize,
                        commonDownloaderProperties.getBucketName(), startAfter));
    }

    private String getPrefix(ConsensusNode node, StreamFilename streamFilename) {
        var streamType = streamFilename.getStreamType();
        var nodeAccount = node.getNodeAccountId().toString();
        var prefix = streamType.getPath() + SEPARATOR + streamType.getNodePrefix() + nodeAccount + SEPARATOR;

        if (streamFilename.getFileType() == SIDECAR) {
            prefix += SIDECAR_FOLDER;
        }

        return prefix;
    }

    private StreamFilename toStreamFilename(S3Object s3Object) {
        var key = s3Object.key();

        try {
            var filename = key.substring(key.lastIndexOf(SEPARATOR) + 1);
            return new StreamFilename(filename);
        } catch (Exception e) {
            log.warn("Unable to parse stream filename for {}", key, e);
            return EPOCH; // Reactor doesn't allow null return values for map(), so use a sentinel that we filter later
        }
    }
}
