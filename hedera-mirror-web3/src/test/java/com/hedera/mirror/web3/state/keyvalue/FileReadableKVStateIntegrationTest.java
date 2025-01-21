/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.state.keyvalue;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.state.file.File;
import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class FileReadableKVStateIntegrationTest extends Web3IntegrationTest {

    private final FileReadableKVState fileReadableKVState;

    @Test
    void testGetFile() {
        // Given
        final var fileDataLatest = domainBuilder.fileData().persist();
        // persist second file data with the same entity id but older timestamp
        domainBuilder
                .fileData()
                .customize(f -> f.consensusTimestamp(fileDataLatest.getConsensusTimestamp() - 1)
                        .entityId(fileDataLatest.getEntityId()))
                .persist();

        final var entityId = fileDataLatest.getEntityId();
        FileID fileID = new FileID(entityId.getShard(), entityId.getRealm(), entityId.getNum());
        File expected = new File(fileID, () -> null, null, Bytes.wrap(fileDataLatest.getFileData()), "", false, 0);

        // When
        File actual = fileReadableKVState.readFromDataSource(fileID);
        // Then
        assertThat(actual).isEqualTo(expected);
    }
}
