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

import static com.hedera.mirror.importer.domain.StreamFilename.EPOCH;
import static com.hedera.mirror.importer.domain.StreamFilename.FileType.SIDECAR;
import static com.hedera.mirror.importer.domain.StreamFilename.FileType.SIGNATURE;
import static com.hedera.mirror.importer.MirrorProperties.HederaNetwork;
import static com.hedera.mirror.importer.downloader.CommonDownloaderProperties.PathType.NODE_ID;
import static com.hedera.mirror.importer.downloader.CommonDownloaderProperties.PathType.AUTO;

import com.hedera.mirror.importer.MirrorProperties;

import com.hedera.mirror.importer.downloader.PathParameterProperties;
import com.hedera.mirror.importer.addressbook.ConsensusNode;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;
import com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor;
import com.hedera.mirror.importer.exception.InvalidConfigurationException;

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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@CustomLog
@RequiredArgsConstructor
public final class S3StreamFileProvider implements StreamFileProvider {

    static final String SIDECAR_FOLDER = "sidecar/";
    private static final String SEPARATOR = "/";

    private final CommonDownloaderProperties commonDownloaderProperties;

    private final Map<Long, PathParameterProperties> nodePathParamsMap = new ConcurrentHashMap<>();

    private final S3AsyncClient s3Client;

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
                .flatMapSequential(streamFilename -> get(node, streamFilename))
                .doOnSubscribe(s -> log.debug(
                        "Searching for the next {} files after {}/{}",
                        batchSize,
                        commonDownloaderProperties.getBucketName(),
                        startAfter));
    }

    /**
     * List from the given prefix and just check if they exist.
     * This method does not actually download the files.
     *
     * @param lastFilename last downloaded file name
     * @param prefix path to download files from
     */
    private Mono<ListObjectsV2Response> listAuto(StreamFilename lastFilename, String prefix) {
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

        return   Mono.fromFuture(s3Client.listObjectsV2(listRequest))
                .timeout(commonDownloaderProperties.getTimeout())
                .doOnNext(l -> l.contents());
    }

    /**
     * Get a file from the given prefix.
     * This method actually downloads the files.
     *
     * @param lastFilename last downloaded file name
     * @param prefix path to download files from
     */
    private Mono<StreamFileData> getAuto(StreamFilename streamFilename, String prefix) {
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

   private String getBucketPath(ConsensusNode consensusNode, StreamFilename streamFilename) {
        var pathParam = nodePathParamsMap.get(consensusNode.getNodeId());
        CommonDownloaderProperties.PathType pathType;
        if (pathParam == null) {
            nodePathParamsMap.put(consensusNode.getNodeId(), new PathParameterProperties(0L, commonDownloaderProperties.getPathType()));
            pathType = commonDownloaderProperties.getPathType();
        } else {
            pathType = pathParam.getPathType();
        }

        switch(pathType) {
            case NODE_ID:
                return getNodeIdBasedPrefix(consensusNode, streamFilename);
            case AUTO:
                return getAutoAlgorithmPrefix(consensusNode, streamFilename);
            default: // This is the ACCOUNT_ID case
                return getPrefix(consensusNode, streamFilename);
        }
    }

    private String getAutoAlgorithmPrefix(ConsensusNode consensusNode, StreamFilename streamFilename) {
        var currentTime = System.currentTimeMillis();
        var pathParam = nodePathParamsMap.get(consensusNode.getNodeId());
        var accountIdPrefix =  getPrefix(consensusNode, streamFilename);
        if ((pathParam!=null ? pathParam.getPathExpirationTimestampMap() : 0L) > currentTime ) {
           return accountIdPrefix;
        }
        try {
            // Listing files from accountNodeId path
            var listResponse = listAuto(streamFilename, accountIdPrefix)
                    .block();
            // If files are available at the old bucket path continue using AUTO
            if (listResponse!=null && listResponse.contents().size() > 0) {
                nodePathParamsMap.put(consensusNode.getNodeId(),
                        new PathParameterProperties((System.currentTimeMillis() + commonDownloaderProperties.getPathRefreshInterval().toMillis()), AUTO));
                return accountIdPrefix;
            }
        } catch (Exception e) {
            //check the types of exceptions.
            log.warn("Unable to list from account based bucket path {}", e);
        }

        var nodeIdPrefix = getNodeIdBasedPrefix(consensusNode, streamFilename);
        // List from the node id as well.Stay as auto and retry
        var countNodeId = listAuto(streamFilename, nodeIdPrefix)
                .block();
        if (countNodeId!=null && countNodeId.contents().size() > 0) {
            //At this point we are setting Node_Id as the path type so refresh interval becomes irrelevant
            nodePathParamsMap.put(consensusNode.getNodeId(),
                    new PathParameterProperties(0L, NODE_ID));
            return nodeIdPrefix;
        }
        return accountIdPrefix;
    }

    @NotNull
    private String getNodeIdBasedPrefix(ConsensusNode consensusNode, StreamFilename streamFilename) {
        var nodeId = consensusNode.getNodeId();
        var shardNum = commonDownloaderProperties.getMirrorProperties().getShard();
        var network = getNetworkPrefix(commonDownloaderProperties.getMirrorProperties());
        var streamType = streamFilename.getStreamType().toString().toLowerCase();
        var prefix = network + SEPARATOR+ shardNum+ SEPARATOR + nodeId + SEPARATOR + streamType + SEPARATOR;
        if (streamFilename.getFileType() == SIDECAR) {
            prefix += SIDECAR_FOLDER;
        }
        return prefix;
    }

    private String getNetworkPrefix(MirrorProperties mirrorProperties) {
        if (!StringUtils.isBlank(mirrorProperties.getNetworkPrefix())) {
            return mirrorProperties.getNetworkPrefix().toLowerCase();
        } else {
            if (mirrorProperties.getNetwork().equals(HederaNetwork.OTHER)) {
                throw new InvalidConfigurationException("Unable to retrieve the network prefix for network type " + mirrorProperties.getNetwork().toString());
            }
            // Here we need to add logic to get the complete network prefix for resettable environments.
            return mirrorProperties.getNetwork().toString().toLowerCase();
        }
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
