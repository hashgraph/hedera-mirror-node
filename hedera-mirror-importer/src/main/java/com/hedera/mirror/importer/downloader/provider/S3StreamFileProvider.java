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
import com.hedera.mirror.importer.exception.InvalidConfigurationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
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

    @NotNull
    private static String getSidecarFolder(StreamFilename streamFilename) {
        return streamFilename.getFileType() == SIDECAR ? SIDECAR_FOLDER : "";
    }

    @Override
    public Mono<StreamFileData> get(ConsensusNode node, StreamFilename streamFilename) {
        var prefix = getBucketPath(node, streamFilename);
        return getAuto(streamFilename, prefix);
    }

    @Override
    public Flux<StreamFileData> list(ConsensusNode node, StreamFilename lastFilename) {
        // Number of items we plan do download in a single batch times 2 for file + sig.
        int batchSize = commonDownloaderProperties.getBatchSize() * 2;

        var prefix = getBucketPath(node, lastFilename);

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
                .flatMapSequential(streamFilename -> getAuto(streamFilename, prefix))
                .doOnSubscribe(s -> log.debug(
                        "Searching for the next {} files after {}/{}",
                        batchSize,
                        commonDownloaderProperties.getBucketName(),
                        startAfter));
    }

    /**
     * List from the given prefix and just check if they exist. This method does not actually download the files.
     *
     * @param lastFilename last downloaded file name
     * @param prefix       path to download files from
     */
    @NotNull
    private Mono<ListObjectsV2Response> listOnly(StreamFilename lastFilename, String prefix) {
        int batchSize = commonDownloaderProperties.getBatchSize() * 2;

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
                .doOnNext(ListObjectsV2Response::contents);
    }

    /**
     * Get a file from the given prefix. This method actually downloads the files.
     *
     * @param streamFilename last downloaded file name
     * @param prefix         path to download files from
     */
    @NotNull
    private Mono<StreamFileData> getAuto(StreamFilename streamFilename, String prefix) {
        var s3Key = prefix + streamFilename.getFilename();

        var request = GetObjectRequest.builder()
                .bucket(commonDownloaderProperties.getBucketName())
                .key(s3Key)
                .requestPayer(RequestPayer.REQUESTER)
                .build();

        var responseFuture = s3Client.getObject(request, AsyncResponseTransformer.toBytes());
        return Mono.fromFuture(responseFuture)
                .map(r -> new StreamFileData(
                        streamFilename, r.asByteArrayUnsafe(), r.response().lastModified()))
                .timeout(commonDownloaderProperties.getTimeout())
                .onErrorMap(NoSuchKeyException.class, TransientProviderException::new)
                .doOnSuccess(s -> log.debug("Finished downloading {}", s3Key));
    }

    @NotNull
    private String getBucketPath(ConsensusNode consensusNode, StreamFilename streamFilename) {
        var pathParam = nodePathParamsMap.computeIfAbsent(
                consensusNode, nodeId -> new PathParameterProperties(commonDownloaderProperties.getPathType(), 0L));

        return switch (pathParam.pathType()) {
            case ACCOUNT_ID -> getAccountIdBasedPrefix(consensusNode, streamFilename);
            case NODE_ID -> getNodeIdBasedPrefix(consensusNode, streamFilename);
            case AUTO -> getAutoAlgorithmPrefix(consensusNode, streamFilename, pathParam);
        };
    }

    @NotNull
    private String getAutoAlgorithmPrefix(
            ConsensusNode consensusNode, StreamFilename streamFilename, PathParameterProperties pathParam) {
        var currentTime = System.currentTimeMillis();
        var accountIdPrefix = getAccountIdBasedPrefix(consensusNode, streamFilename);
        if (pathParam.pathExpirationTimestampMap() > currentTime) {
            return accountIdPrefix;
        }

        try {
            // Listing files from accountNodeId path
            var listResponse = listOnly(streamFilename, accountIdPrefix).block();
            // If files are available at the old bucket path continue using AUTO
            if (listResponse != null && !listResponse.contents().isEmpty()) {
                nodePathParamsMap.put(
                        consensusNode,
                        new PathParameterProperties(
                                AUTO,
                                (System.currentTimeMillis()
                                        + commonDownloaderProperties
                                                .getPathRefreshInterval()
                                                .toMillis())));
                return accountIdPrefix;
            }
        } catch (Exception e) {
            // check the types of exceptions.
            log.warn("Unable to list from account based bucket path {}", e);
        }

        var nodeIdPrefix = getNodeIdBasedPrefix(consensusNode, streamFilename);
        // List from the node id as well. If no files present, stay as auto and retry
        var countNodeId = listOnly(streamFilename, nodeIdPrefix).block();
        if (countNodeId != null && !countNodeId.contents().isEmpty()) {
            // At this point we are setting Node_Id as the path type so refresh interval becomes irrelevant
            nodePathParamsMap.put(consensusNode, new PathParameterProperties(NODE_ID, 0L));
            return nodeIdPrefix;
        }
        return accountIdPrefix;
    }

    @NotNull
    private String getNodeIdBasedPrefix(ConsensusNode consensusNode, StreamFilename streamFilename) {
        return TEMPLATE_NODE_ID_PREFIX.formatted(
                getNetworkPrefix(commonDownloaderProperties.getMirrorProperties()),
                commonDownloaderProperties.getMirrorProperties().getShard(),
                consensusNode.getNodeId(),
                streamFilename.getStreamType().getNodeIdBasedSuffix(),
                getSidecarFolder(streamFilename));
    }

    @NotNull
    private String getNetworkPrefix(MirrorProperties mirrorProperties) {
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

    @NotNull
    private String getAccountIdBasedPrefix(ConsensusNode node, StreamFilename streamFilename) {
        var streamType = streamFilename.getStreamType();
        return TEMPLATE_ACCOUNT_ID_PREFIX.formatted(
                streamType.getPath(),
                streamType.getNodePrefix(),
                node.getNodeAccountId(),
                getSidecarFolder(streamFilename));
    }

    @NotNull
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

    private record PathParameterProperties(
            CommonDownloaderProperties.PathType pathType, long pathExpirationTimestampMap) {}
}
