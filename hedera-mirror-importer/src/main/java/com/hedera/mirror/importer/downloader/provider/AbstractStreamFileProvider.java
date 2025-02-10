/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.importer.addressbook.ConsensusNode;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties.PathType;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
abstract class AbstractStreamFileProvider implements StreamFileProvider {

    protected final CommonDownloaderProperties properties;

    @Override
    public final Mono<StreamFileData> get(ConsensusNode node, StreamFilename streamFilename) {
        if (streamFilename.getStreamType() == StreamType.BLOCK) {
            if (properties.getPathType() != PathType.NODE_ID) {
                throw new IllegalStateException("Path type must be NODE_ID for block streams");
            }

            String filePath = getBlockStreamFilePath(
                    properties.getImporterProperties().getShard(), node.getNodeId(), streamFilename.getFilename());
            streamFilename = StreamFilename.from(filePath);
        }

        return doGet(streamFilename);
    }

    protected abstract Mono<StreamFileData> doGet(StreamFilename streamFilename);

    protected abstract String getBlockStreamFilePath(long shard, long nodeId, String filename);
}
