/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties.PathType;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;

/*
 * Copy HIP-679 bucket structured files and configure the stream file provider to access S3 using
 * the new consensus node ID based hierarchy rather than the legacy node account ID mechanism.
 */
class NodeIdS3StreamProviderTest extends AbstractHip679S3StreamFileProviderTest {

    @Override
    @BeforeEach
    void setup() {
        super.setup();

        properties.setPathType(PathType.NODE_ID);
        properties.setPathRefreshInterval(Duration.ofSeconds(2L));
    }

    @Override
    protected FileCopier createDefaultFileCopier() {
        return createFileCopier(Path.of("data", "hip679", "provider-node-id"), importerProperties.getNetwork());
    }
}
