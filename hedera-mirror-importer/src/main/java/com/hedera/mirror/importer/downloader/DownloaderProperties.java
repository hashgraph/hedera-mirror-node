package com.hedera.mirror.importer.downloader;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import java.nio.file.Path;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.StreamType;

public interface DownloaderProperties {

    int getBatchSize();

    CommonDownloaderProperties getCommon();

    MirrorProperties getMirrorProperties();

    /**
     * The number of current mainnet nodes used to download signatures in parallel. Should be adjusted when nodes
     * change
     */
    int getThreads();

    String getPrefix();

    Path getStreamPath();

    StreamType getStreamType();

    default Path getSignaturesPath() {
        return getStreamPath().resolve(getStreamType().getSignatures());
    }

    boolean isEnabled();

    void setBatchSize(int batchSize);

    void setEnabled(boolean enabled);

    boolean isKeepSignatures();

    void setKeepSignatures(boolean keepSignatures);
}
