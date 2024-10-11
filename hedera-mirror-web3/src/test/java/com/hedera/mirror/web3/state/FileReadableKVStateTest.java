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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.state.file.File;
import com.hedera.mirror.common.domain.file.FileData;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.repository.FileDataRepository;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.utils.EntityIdUtils;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class FileReadableKVStateTest {

    private static final long SHARD = 0L;
    private static final long REALM = 1L;
    private static final long FILE_NUM = 123L;
    private static final FileID FILE_ID = FileID.newBuilder()
            .shardNum(SHARD)
            .realmNum(REALM)
            .fileNum(FILE_NUM)
            .build();
    private static final long FILE_ID_LONG = EntityIdUtils.toEntityId(FILE_ID).getId();
    private static final Optional<Long> TIMESTAMP = Optional.of(1234L);
    private static MockedStatic<ContractCallContext> contextMockedStatic;
    private FileData fileData;

    @InjectMocks
    private FileReadableKVState fileReadableKVState;

    @Mock
    private FileDataRepository fileDataRepository;

    @Spy
    private ContractCallContext contractCallContext;

    @BeforeAll
    static void initStaticMocks() {
        contextMockedStatic = mockStatic(ContractCallContext.class);
    }

    @AfterAll
    static void closeStaticMocks() {
        contextMockedStatic.close();
    }

    @BeforeEach
    void setup() {
        fileData = new FileData();
        fileData.setFileData("Sample file data".getBytes());
        fileData.setConsensusTimestamp(TIMESTAMP.get());
        contextMockedStatic.when(ContractCallContext::get).thenReturn(contractCallContext);
    }

    @Test
    void fileFieldsMatchFileDataFields() {
        FileData fileData = new FileData();
        fileData.setConsensusTimestamp(TIMESTAMP.get());
        fileData.setFileData("file-contents".getBytes());

        long internalFileId = EntityIdUtils.toEntityId(FILE_ID).getId();

        when(contractCallContext.getTimestamp()).thenReturn(TIMESTAMP);
        when(fileDataRepository.getFileAtTimestamp(internalFileId, TIMESTAMP.get()))
                .thenReturn(Optional.of(fileData));

        File file = fileReadableKVState.get(FILE_ID);

        assertThat(file).isNotNull();
        assertThat(file.fileId()).isEqualTo(FILE_ID);
        assertThat(file.expirationSecond()).isEqualTo(fileData.getConsensusTimestamp());
        assertThat(file.contents()).isEqualTo(Bytes.wrap(fileData.getFileData()));
    }

    @Test
    void fileFieldsReturnNullWhenFileDataNotFound() {
        when(contractCallContext.getTimestamp()).thenReturn(TIMESTAMP);
        long fileIdLong = EntityIdUtils.toEntityId(FILE_ID).getId();
        when(fileDataRepository.getFileAtTimestamp(fileIdLong, TIMESTAMP.get())).thenReturn(Optional.empty());

        File file = fileReadableKVState.get(FILE_ID);

        assertThat(file).isNull();
    }

    @Test
    void readFromDataSourceWithTimestamp() {
        when(contractCallContext.getTimestamp()).thenReturn(TIMESTAMP);
        when(fileDataRepository.getFileAtTimestamp(FILE_ID_LONG, TIMESTAMP.get()))
                .thenReturn(Optional.of(fileData));

        File result = fileReadableKVState.readFromDataSource(FILE_ID);

        assertThat(result).isNotNull();
        assertThat(result.fileId()).isEqualTo(FILE_ID);
        assertThat(result.expirationSecond()).isEqualTo(fileData.getConsensusTimestamp());
        assertThat(result.contents()).isEqualTo(Bytes.wrap(fileData.getFileData()));
    }

    @Test
    void readFromDataSourceWithoutTimestamp() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(fileDataRepository.findById(FILE_ID_LONG)).thenReturn(Optional.of(fileData));

        File result = fileReadableKVState.readFromDataSource(FILE_ID);

        assertThat(result).isNotNull();
        assertThat(result.fileId()).isEqualTo(FILE_ID);
        assertThat(result.expirationSecond()).isEqualTo(fileData.getConsensusTimestamp());
        assertThat(result.contents()).isEqualTo(Bytes.wrap(fileData.getFileData()));
    }

    @Test
    void readFromDataSourceFileNotFound() {
        when(contractCallContext.getTimestamp()).thenReturn(TIMESTAMP);
        when(fileDataRepository.getFileAtTimestamp(FILE_ID_LONG, TIMESTAMP.get()))
                .thenReturn(Optional.empty());

        File result = fileReadableKVState.readFromDataSource(FILE_ID);

        assertThat(result).isNull();
    }

    @Test
    void sizeIsAlwaysEmpty() {
        assertThat(fileReadableKVState.size()).isZero();
    }

    @Test
    void iterateReturnsEmptyIterator() {
        assertThat(fileReadableKVState.iterateFromDataSource()).isEqualTo(Collections.emptyIterator());
    }
}
