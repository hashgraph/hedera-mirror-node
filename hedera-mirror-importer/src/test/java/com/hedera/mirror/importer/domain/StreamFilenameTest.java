package com.hedera.mirror.importer.domain;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import org.jclouds.atmos.domain.FileType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class StreamFilenameTest {

    @ParameterizedTest(name = "Create StreamFilename from '{0}'")
    @CsvSource({
            "2020-06-03T16_45_00.000000001Z_Balances.csv_sig, csv_sig, SIGNATURE, 2020-06-03T16:45:00.000000001Z, BALANCE"
    })
    void newStreamFile(String filename, String extension, FileType fileType, Instant instant, StreamType streamType) {
        StreamFilename streamFilename = new StreamFilename(filename);

        assertThat(streamFilename.getFilename()).isEqualTo(filename);
        assertThat(streamFilename.getExtension()).isEqualTo(extension);
        assertThat(streamFilename.getFileType()).isEqualTo(fileType);
        assertThat(streamFilename.getInstant()).isEqualTo(instant);
        assertThat(streamFilename.getStreamType()).isEqualTo(streamType);
    }
}
