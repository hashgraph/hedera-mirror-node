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

import static com.hedera.mirror.importer.MirrorProperties.HederaNetwork;
import static com.hedera.mirror.importer.domain.StreamFilename.EPOCH;
import static com.hedera.mirror.importer.domain.StreamFilename.FileType.SIDECAR;
import static com.hedera.mirror.importer.domain.StreamFilename.FileType.SIGNATURE;
import static com.hedera.mirror.importer.downloader.CommonDownloaderProperties.PathType.AUTO;
import static com.hedera.mirror.importer.downloader.CommonDownloaderProperties.PathType.NODE_ID;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.addressbook.ConsensusNode;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties.PathType;
import com.hedera.mirror.importer.exception.InvalidConfigurationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.CustomLog;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
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

@CustomLog
@RequiredArgsConstructor
public final class S3StreamFileProvider implements StreamFileProvider {
    static final String SIDECAR_FOLDER = "sidecar/";
    private static final String SEPARATOR = "/";
    private static final String TEMPLATE_NODE_ID_PREFIX =
            "%%s%s%%s%s%%s%s%%s%s%%s".formatted(SEPARATOR, SEPARATOR, SEPARATOR, SEPARATOR);
    private static final String TEMPLATE_ACCOUNT_ID_PREFIX = "%%s%s%%s%%s%s%%s".formatted(SEPARATOR, SEPARATOR);
    private final CommonDownloaderProperties commonDownloaderProperties;
    private final ConcurrentMap<ConsensusNode, PathParameterProperties> nodePathParamsMap = new ConcurrentHashMap<>();
    private final S3AsyncClient s3Client;

    private static String getSidecarFolder(StreamFilename streamFilename) {
        return streamFilename.getFileType() == SIDECAR ? SIDECAR_FOLDER : "";
    }

    private static boolean isAutoModeTransitionIntervalExpired(StreamFilePathInfo streamFilePathInfo) {
        return ((streamFilePathInfo.pathParams.pathType == PathType.AUTO)
                && (streamFilePathInfo.pathParams.pathExpirationTimestampMap() < System.currentTimeMillis()));
    }

    private static String getAccountIdBasedPrefix(ConsensusNode consensusNode, StreamFilename streamFilename) {
        var streamType = streamFilename.getStreamType();
        return TEMPLATE_ACCOUNT_ID_PREFIX.formatted(
                streamType.getPath(),
                streamType.getNodePrefix(),
                consensusNode.getNodeAccountId(),
                getSidecarFolder(streamFilename));
    }

    private static StreamFilename toStreamFilename(S3Object s3Object) {
        var key = s3Object.key();

        try {
            var filename = key.substring(key.lastIndexOf(SEPARATOR) + 1);
            return new StreamFilename(filename, key);
        } catch (Exception e) {
            log.warn("Unable to parse stream filename for {}", key, e);
            return EPOCH; // Reactor doesn't allow null return values for map(), so use a sentinel that we filter later
        }
    }

    private static String getNetworkPrefix(MirrorProperties mirrorProperties) {
        if (!StringUtils.isBlank(mirrorProperties.getNetworkPrefix())) {
            return mirrorProperties.getNetworkPrefix().toLowerCase();
        } else {
            if (mirrorProperties.getNetwork().equals(HederaNetwork.OTHER)) {
                throw new InvalidConfigurationException(
                        "Unable to retrieve the network prefix for network type " + mirrorProperties.getNetwork());
            }
            // Here (5713) we need to add logic to get the complete network prefix for resettable environments.
            return mirrorProperties.getNetwork().toString().toLowerCase();
        }
    }

    @Override
    public Flux<StreamFileData> list(ConsensusNode node, StreamFilename lastFilename) {
        // Number of items we plan do download in a single batch times 2 for file + sig.
        var batchSize = commonDownloaderProperties.getBatchSize() * 2;
        var streamFilePathInfo = getStartingPathInfo(node, lastFilename);
        var startAfter = streamFilePathInfo.prefix + lastFilename.getFilenameAfter();
        var countOfStartingPathObjects = new AtomicInteger();
        var countOfNodeIdPathObjects = new AtomicInteger();

        var listRequest = ListObjectsV2Request.builder()
                .bucket(commonDownloaderProperties.getBucketName())
                .prefix(streamFilePathInfo.prefix)
                .delimiter(SEPARATOR)
                .startAfter(startAfter)
                .maxKeys(batchSize)
                .requestPayer(RequestPayer.REQUESTER)
                .build();

        return Mono.fromFuture(s3Client.listObjectsV2(listRequest))
                .timeout(commonDownloaderProperties.getTimeout())
                .doOnNext(l -> {
                    countOfStartingPathObjects.set(l.contents().size());
                    log.debug("Returned {} s3 objects", l.contents().size());
                })
                .flatMapIterable(ListObjectsV2Response::contents)
                .switchIfEmpty(Flux.defer(() -> {
                    log.debug("No objects found in node account ID bucket structure after: {}", startAfter);
                    if (isAutoModeTransitionIntervalExpired(streamFilePathInfo)) {
                        var nodeIdPrefix = getNodeIdBasedPrefix(node, lastFilename);
                        var nodeIdStartAfter = nodeIdPrefix + lastFilename.getFilename();
                        log.debug("Trying node ID bucket structure: {}", nodeIdStartAfter);

                        var nodeIdListRequest = ListObjectsV2Request.builder()
                                .bucket(commonDownloaderProperties.getBucketName())
                                .prefix(nodeIdPrefix)
                                .delimiter(SEPARATOR)
                                .startAfter(nodeIdStartAfter)
                                .maxKeys(batchSize)
                                .requestPayer(RequestPayer.REQUESTER)
                                .build();

                        return Mono.fromFuture(s3Client.listObjectsV2(nodeIdListRequest))
                                .timeout(commonDownloaderProperties.getTimeout())
                                .doOnNext(l -> {
                                    countOfNodeIdPathObjects.set(l.contents().size());
                                    log.debug(
                                            "Node ID path returned {} s3 objects",
                                            l.contents().size());
                                })
                                .flatMapIterable(ListObjectsV2Response::contents);
                    }
                    return Flux.empty(); // Not AUTO mode/interval, so empty means empty
                }))
                .map(S3StreamFileProvider::toStreamFilename)
                .filter(s -> s != EPOCH && s.getFileType() == SIGNATURE)
                .flatMapSequential(this::getNative)
                .doOnSubscribe(s -> log.debug(
                        "Searching for the next {} files after {}/{}",
                        batchSize,
                        commonDownloaderProperties.getBucketName(),
                        startAfter))
                .doOnComplete(() -> {
                    if (streamFilePathInfo.pathParams.pathType == PathType.AUTO) {
                        updateNodePathStatus(node, countOfStartingPathObjects.get(), countOfNodeIdPathObjects.get());
                    }
                    log.debug("Finished searching after {}/{}", commonDownloaderProperties.getBucketName(), startAfter);
                });
    }

    @Override
    public Mono<StreamFileData> get(ConsensusNode node, StreamFilename streamFilename) {
        var streamFilePathInfo = getStartingPathInfo(node, streamFilename);

        var s3Key = streamFilePathInfo.prefix + streamFilename.getFilename();
        var request = GetObjectRequest.builder()
                .bucket(commonDownloaderProperties.getBucketName())
                .key(s3Key)
                .requestPayer(RequestPayer.REQUESTER)
                .build();

        var autoTransitionToNodeId = new AtomicBoolean();
        return Mono.fromFuture(s3Client.getObject(request, AsyncResponseTransformer.toBytes()))
                .timeout(commonDownloaderProperties.getTimeout())
                .onErrorResume(NoSuchKeyException.class, throwable -> {
                    log.debug("Object not found in node account ID bucket structure: {}", s3Key, throwable);
                    if (isAutoModeTransitionIntervalExpired(streamFilePathInfo)) {
                        var nodeIdS3Key = getNodeIdBasedPrefix(node, streamFilename) + streamFilename.getFilename();
                        log.debug("Trying node ID bucket structure: {}", nodeIdS3Key);
                        autoTransitionToNodeId.set(true);
                        var nodeIdRequest = GetObjectRequest.builder()
                                .bucket(commonDownloaderProperties.getBucketName())
                                .key(nodeIdS3Key)
                                .requestPayer(RequestPayer.REQUESTER)
                                .build();
                        return Mono.fromFuture(s3Client.getObject(nodeIdRequest, AsyncResponseTransformer.toBytes()));
                    }
                    return Mono.error(throwable);
                })
                .map(r -> new StreamFileData(
                        streamFilename, r.asByteArrayUnsafe(), r.response().lastModified()))
                .onErrorMap(NoSuchKeyException.class, TransientProviderException::new)
                .doOnSuccess(s -> {
                    if (streamFilePathInfo.pathParams.pathType == PathType.AUTO) {
                        updateNodePathStatus(node, autoTransitionToNodeId.get());
                    }
                    log.debug("Finished downloading {}", s3Key);
                });
    }

    private Mono<StreamFileData> getNative(@NonNull StreamFilename streamFilename) {
        var s3Key = streamFilename.getNativeKey();
        var request = GetObjectRequest.builder()
                .bucket(commonDownloaderProperties.getBucketName())
                .key(streamFilename.getNativeKey())
                .requestPayer(RequestPayer.REQUESTER)
                .build();

        return Mono.fromFuture(s3Client.getObject(request, AsyncResponseTransformer.toBytes()))
                .map(r -> new StreamFileData(
                        streamFilename, r.asByteArrayUnsafe(), r.response().lastModified()))
                .timeout(commonDownloaderProperties.getTimeout())
                .onErrorMap(NoSuchKeyException.class, TransientProviderException::new)
                .doOnSuccess(s -> log.debug("Finished downloading {}", s3Key));
    }

    private StreamFilePathInfo getStartingPathInfo(ConsensusNode consensusNode, StreamFilename streamFilename) {
        var pathParams = nodePathParamsMap.computeIfAbsent(
                consensusNode,
                nodeId -> new PathParameterProperties(
                        commonDownloaderProperties.getPathType(), computeAutoTransitionExpiration()));

        var prefix =
                switch (pathParams.pathType()) {
                    case ACCOUNT_ID, AUTO -> getAccountIdBasedPrefix(consensusNode, streamFilename);
                    case NODE_ID -> getNodeIdBasedPrefix(consensusNode, streamFilename);
                };

        return new StreamFilePathInfo(pathParams, prefix);
    }

    private void updateNodePathStatus(ConsensusNode node, int numStartingPathObjects, int numNodeIdPathObjects) {
        if (numNodeIdPathObjects > 0) {
            updateNodePathStatus(node, true);
        } else if (numStartingPathObjects > 0) {
            updateNodePathStatus(node, false);
        } // No new account ID based files found, but transition interval not yet expired
    }

    private void updateNodePathStatus(ConsensusNode node, boolean isAutoTransition) {
        if (isAutoTransition) {
            // At this point we are setting node ID as the path type so refresh interval becomes irrelevant
            nodePathParamsMap.put(node, new PathParameterProperties(NODE_ID, 0L));
        } else {
            // If files are available at the old bucket path continue using AUTO for another interval
            nodePathParamsMap.put(node, new PathParameterProperties(AUTO, computeAutoTransitionExpiration()));
        }
    }

    private long computeAutoTransitionExpiration() {
        return System.currentTimeMillis()
                + commonDownloaderProperties.getPathRefreshInterval().toMillis();
    }

    private String getNodeIdBasedPrefix(ConsensusNode consensusNode, StreamFilename streamFilename) {
        return TEMPLATE_NODE_ID_PREFIX.formatted(
                getNetworkPrefix(commonDownloaderProperties.getMirrorProperties()),
                commonDownloaderProperties.getMirrorProperties().getShard(),
                consensusNode.getNodeId(),
                streamFilename.getStreamType().getNodeIdBasedSuffix(),
                getSidecarFolder(streamFilename));
    }

    private record PathParameterProperties(
            CommonDownloaderProperties.PathType pathType, long pathExpirationTimestampMap) {}

    private record StreamFilePathInfo(PathParameterProperties pathParams, String prefix) {}
}
