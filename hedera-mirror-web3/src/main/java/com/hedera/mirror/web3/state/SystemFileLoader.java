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
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
public class SystemFileLoader {

    private final NetworkInfo networkInfo;
    private final MirrorNodeEvmProperties properties;
    private final V0490FileSchema fileSchema = new V0490FileSchema();

    @Getter(lazy = true)
    private final Map<FileID, File> systemFiles = loadAll();

    public @Nullable File load(@Nonnull FileID key) {
        return getSystemFiles().get(key);
    }

    private Map<FileID, File> loadAll() {
        var configuration = properties.getVersionedConfiguration();

        var files = List.of(
                load(101, fileSchema.genesisAddressBook(networkInfo)),
                load(102, fileSchema.genesisNodeDetails(networkInfo)),
                load(111, fileSchema.genesisFeeSchedules(configuration)),
                load(112, fileSchema.genesisExchangeRates(configuration)),
                load(121, fileSchema.genesisNetworkProperties(configuration)),
                load(122, Bytes.EMPTY), // genesisHapiPermissions() fails to load files from the classpath
                load(123, fileSchema.genesisThrottleDefinitions(configuration)));

        return files.stream().collect(Collectors.toMap(File::fileId, Function.identity()));
    }

    private File load(int fileNum, Bytes contents) {
        var fileId = FileID.newBuilder().fileNum(fileNum).build();
        return File.newBuilder()
                .contents(contents)
                .deleted(false)
                .expirationSecond(maxExpiry())
                .fileId(fileId)
                .build();
    }

    private long maxExpiry() {
        var configuration = properties.getVersionedConfiguration();
        long maxLifetime = configuration.getConfigData(EntitiesConfig.class).maxLifetime();
        return Instant.now().getEpochSecond() + maxLifetime;
    }
}
