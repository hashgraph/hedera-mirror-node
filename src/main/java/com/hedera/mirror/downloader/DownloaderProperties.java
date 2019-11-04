package com.hedera.mirror.downloader;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

import com.hedera.mirror.domain.StreamType;
import com.hedera.utilities.Utility;

import javax.annotation.PostConstruct;
import java.nio.file.Path;
import java.time.Duration;

public interface DownloaderProperties {

    int getBatchSize();

    CommonDownloaderProperties getCommon();

    /**
     * The number of current mainnet nodes used to download signatures in parallel. Should be adjusted when nodes change
     */
    int getThreads();

    Duration getSteadyStatePollDelay();

    String getPrefix();

    Path getStreamPath();

    StreamType getStreamType();

    default Path getTempPath() {
        return getStreamPath().resolve(getStreamType().getTemp());
    }

    default Path getValidPath() {
        return getStreamPath().resolve(getStreamType().getValid());
    }

    boolean isEnabled();

    @PostConstruct
    default void init() {
        Utility.ensureDirectory(getTempPath());
        Utility.ensureDirectory(getValidPath());
        Utility.purgeDirectory(getTempPath());
    }
}
