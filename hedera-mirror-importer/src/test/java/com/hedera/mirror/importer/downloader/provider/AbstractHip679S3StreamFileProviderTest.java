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

import com.hedera.mirror.importer.downloader.CommonDownloaderProperties.PathType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

abstract class AbstractHip679S3StreamFileProviderTest extends S3StreamFileProviderTest {

    @SuppressWarnings("java:S2699")
    @Disabled("Doesn't apply to HIP-679")
    @Override
    @Test
    void getBlockFile() {}

    @SuppressWarnings("java:S2699")
    @Disabled("Doesn't apply to HIP-679")
    @Override
    @Test
    void getBlockFileNotFound() {}

    @SuppressWarnings("java:S2699")
    @Disabled("Doesn't apply to HIP-679")
    @Override
    @ParameterizedTest
    @EnumSource(PathType.class)
    void getBlockFileIncorrectPathType(PathType pathType) {}
}
