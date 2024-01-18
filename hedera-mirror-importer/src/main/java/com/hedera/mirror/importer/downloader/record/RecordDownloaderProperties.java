/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.downloader.record;

import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;
import com.hedera.mirror.importer.downloader.DownloaderProperties;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component("recordDownloaderProperties")
@ConfigurationProperties("hedera.mirror.importer.downloader.record")
@Data
@RequiredArgsConstructor
@Validated
public class RecordDownloaderProperties implements DownloaderProperties {

    private final CommonDownloaderProperties common;

    private boolean enabled = true;

    @NotNull
    private Duration frequency = Duration.ofMillis(500L);

    private boolean persistBytes = false;

    private boolean writeFiles = false;

    private boolean writeSignatures = false;

    @Override
    public StreamType getStreamType() {
        return StreamType.RECORD;
    }
}
