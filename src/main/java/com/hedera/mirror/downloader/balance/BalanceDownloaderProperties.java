package com.hedera.mirror.downloader.balance;

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

import com.hedera.mirror.MirrorProperties;
import com.hedera.mirror.domain.StreamType;
import com.hedera.mirror.downloader.CommonDownloaderProperties;
import com.hedera.mirror.downloader.DownloaderProperties;

import java.nio.file.Path;
import java.time.Duration;
import javax.inject.Named;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Named
@Validated
@ConfigurationProperties("hedera.mirror.downloader.balance")
public class BalanceDownloaderProperties implements DownloaderProperties {

    private final MirrorProperties mirrorProperties;

    private final CommonDownloaderProperties common;

    @Min(1)
    private int batchSize = 15;

    private boolean enabled = true;

    @NotNull
    private Duration frequency = Duration.ofMillis(500L);

    @NotBlank
    private String prefix = "accountBalances/balance";

    @Min(1)
    private int threads = 13;

    public Path getStreamPath() {
        return mirrorProperties.getDataPath().resolve(getStreamType().getPath());
    }

    @Override
    public StreamType getStreamType() {
        return StreamType.BALANCE;
    }
}
