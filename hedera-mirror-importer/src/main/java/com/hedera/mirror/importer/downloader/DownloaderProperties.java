/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.downloader;

import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.importer.MirrorProperties;
import java.nio.file.Path;
import java.time.Duration;

public interface DownloaderProperties {

    CommonDownloaderProperties getCommon();

    Duration getFrequency();

    MirrorProperties getMirrorProperties();

    Path getStreamPath();

    StreamType getStreamType();

    boolean isEnabled();

    void setEnabled(boolean enabled);

    boolean isPersistBytes();

    void setPersistBytes(boolean keepBytes);

    boolean isWriteFiles();

    void setWriteFiles(boolean keepFiles);

    boolean isWriteSignatures();

    void setWriteSignatures(boolean keepSignatures);
}
