/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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
import com.hedera.mirror.common.domain.file.FileData;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.repository.FileDataRepository;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.utils.EntityIdUtils;
import com.swirlds.state.spi.ReadableKVStateBase;
import edu.umd.cs.findbugs.annotations.NonNull;
import jakarta.inject.Named;
import java.util.Collections;
import java.util.Iterator;

/**
 * This class serves as a repository layer between hedera app services read only state and the Postgres database in mirror-node
 * The object, which is read from DB is converted to the PBJ generated format, so that it can properly be utilized by the hedera app components
 * */
@Named
public class FileReadableKVState extends ReadableKVStateBase<FileID, File> {

    private final FileDataRepository fileDataRepository;

    public FileReadableKVState(final FileDataRepository fileDataRepository) {
        super("FILES");
        this.fileDataRepository = fileDataRepository;
    }

    @Override
    protected File readFromDataSource(@NonNull FileID key) {
        final var timestamp = ContractCallContext.get().getTimestamp();
        final var fileId = EntityIdUtils.toEntityId(key).getId();

        return timestamp
                .map(t -> fileDataRepository.getFileAtTimestamp(fileId, t))
                .orElseGet(() -> fileDataRepository.findById(fileId))
                .map(fileData -> mapToFile(fileData, key))
                .orElse(null);
    }

    @NonNull
    @Override
    protected Iterator<FileID> iterateFromDataSource() {
        return Collections.emptyIterator();
    }

    @Override
    public long size() {
        return 0;
    }

    private File mapToFile(final FileData fileData, final FileID key) {
        return File.newBuilder()
                .fileId(key)
                .expirationSecond(fileData.getConsensusTimestamp())
                .contents(Bytes.wrap(fileData.getFileData()))
                .build();
    }
}
