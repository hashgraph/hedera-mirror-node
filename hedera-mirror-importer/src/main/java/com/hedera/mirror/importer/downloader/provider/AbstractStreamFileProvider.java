/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
abstract class AbstractStreamFileProvider implements StreamFileProvider {

    protected final CommonDownloaderProperties properties;

    protected int getListBatchSize(int batchSize) {
        if (batchSize == USE_DEFAULT_BATCH_SIZE) {
            batchSize = properties.getBatchSize();
        }

        // Number of items we plan do download in a single batch times two for file plus signature.
        return batchSize * 2;
    }
}
