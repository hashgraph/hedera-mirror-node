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

package com.hedera.mirror.web3.state;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.state.file.File;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.node.app.service.file.impl.schemas.V0490FileSchema;
import com.hedera.node.config.data.EntitiesConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.lifecycle.info.NetworkInfo;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
public class SystemFileLoader {

    private final NetworkInfo networkInfo;
    private final MirrorNodeEvmProperties properties;
    private final V0490FileSchema fileSchema = new V0490FileSchema();
    private final Map<FileID, File> systemFiles = new ConcurrentHashMap<>();

    public @Nullable File load(@Nonnull FileID key) {
        return systemFiles.computeIfAbsent(key, this::create);
    }

    private File create(FileID fileId) {
        final var contents = getContents((int) fileId.fileNum());

        if (contents != null) {
            return File.newBuilder()
                    .contents(contents)
                    .deleted(false)
                    .expirationSecond(maxExpiry())
                    .fileId(fileId)
                    .build();
        }

        return null;
    }

    private Bytes getContents(int fileNum) {
        var configuration = properties.getVersionedConfiguration();

        return switch (fileNum) {
            case 101 -> fileSchema.genesisAddressBook(networkInfo);
            case 102 -> fileSchema.genesisNodeDetails(networkInfo);
            case 111 -> fileSchema.genesisFeeSchedules(configuration);
            case 112 -> fileSchema.genesisExchangeRates(configuration);
            case 121 -> fileSchema.genesisNetworkProperties(configuration);
            case 122 -> Bytes.EMPTY; // genesisHapiPermissions() attempts to load non-existent files from classpath
            case 123 -> fileSchema.genesisThrottleDefinitions(configuration);
            default -> null;
        };
    }

    private long maxExpiry() {
        var configuration = properties.getVersionedConfiguration();
        long maxLifetime = configuration.getConfigData(EntitiesConfig.class).maxLifetime();
        return Instant.now().getEpochSecond() + maxLifetime;
    }
}
