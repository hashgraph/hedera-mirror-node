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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.hedera.mirror.importer.exception.InvalidStreamFileException;

class StreamFilenameTest {

    @ParameterizedTest(name = "Create StreamFilename from {0}")
    @CsvSource({
            // @formatter:off
            "2020-06-03T16_45_00.1Z_Balances.csv_sig, csv_sig, SIGNATURE, 2020-06-03T16:45:00.1Z, BALANCE, 2020-06-03T16:45:00.100000000Z",
            "2020-06-03T16_45_00.1Z_Balances.pb_sig, pb_sig, SIGNATURE, 2020-06-03T16:45:00.1Z, BALANCE, 2020-06-03T16:45:00.100000000Z",
            "2020-06-03T16_45_00.1Z_Balances.pb_sig.gz, pb_sig.gz, SIGNATURE, 2020-06-03T16:45:00.1Z, BALANCE, 2020-06-03T16:45:00.100000000Z",
            "2020-06-03T16_45_00.1Z_Balances.csv, csv, DATA, 2020-06-03T16:45:00.1Z, BALANCE, 2020-06-03T16:45:00.100000000Z",
            "2020-06-03T16_45_00.1Z_Balances.pb.gz, pb.gz, DATA, 2020-06-03T16:45:00.1Z, BALANCE, 2020-06-03T16:45:00.100000000Z",
            "2020-06-03T16_45_00.1Z.evts_sig, evts_sig, SIGNATURE, 2020-06-03T16:45:00.1Z, EVENT, 2020-06-03T16:45:00.100000000Z",
            "2020-06-03T16_45_00.1Z.evts, evts, DATA, 2020-06-03T16:45:00.1Z, EVENT, 2020-06-03T16:45:00.100000000Z",
            "2020-06-03T16_45_00.1Z.rcd_sig, rcd_sig, SIGNATURE, 2020-06-03T16:45:00.1Z, RECORD, 2020-06-03T16:45:00.100000000Z",
            "2020-06-03T16_45_00.1Z.rcd, rcd, DATA, 2020-06-03T16:45:00.1Z, RECORD, 2020-06-03T16:45:00.100000000Z",
            // @formatter:on
    })
    void newStreamFile(String filename, String extension, StreamFilename.FileType fileType,
            Instant instant, StreamType streamType, String timestamp) {
        StreamFilename streamFilename = new StreamFilename(filename);

        assertThat(streamFilename)
                .extracting("filename", "extension", "fileType", "instant", "streamType", "timestamp")
                .containsExactly(filename, extension, fileType, instant, streamType, timestamp);
    }

    @ParameterizedTest(name = "Exception creating StreamFilename from {0}")
    @ValueSource(strings = { "2020-06-03_Balances.csv_sig", "2020-06-03T16_45_00.1Z", "2020-06-03T16_45_00.1Z.stream",
            "2020-06-03T16_45_00.1Z.csv_sig" })
    void newStreamFileFromInvalidFilename(String filename) {
        assertThrows(InvalidStreamFileException.class, () -> new StreamFilename(filename));
    }

    @ParameterizedTest(name = "Get data filename from {0}")
    @CsvSource({
            "2020-06-03T16_45_00.1Z_Balances.csv_sig, 2020-06-03T16_45_00.1Z_Balances.csv",
            "2020-06-03T16_45_00.1Z_Balances.pb_sig, 2020-06-03T16_45_00.1Z_Balances.pb.gz",
            "2020-06-03T16_45_00.1Z_Balances.pb_sig.gz, 2020-06-03T16_45_00.1Z_Balances.pb.gz",
            "2020-06-03T16_45_00.1Z_Balances.csv, 2020-06-03T16_45_00.1Z_Balances.csv",
            "2020-06-03T16_45_00.1Z_Balances.pb.gz, 2020-06-03T16_45_00.1Z_Balances.pb.gz",
            "2020-06-03T16_45_00.1Z.evts_sig, 2020-06-03T16_45_00.1Z.evts",
            "2020-06-03T16_45_00.1Z.evts, 2020-06-03T16_45_00.1Z.evts",
            "2020-06-03T16_45_00.1Z.rcd_sig, 2020-06-03T16_45_00.1Z.rcd",
            "2020-06-03T16_45_00.1Z.rcd, 2020-06-03T16_45_00.1Z.rcd",
    })
    void getDataFilename(String filename, String expected) {
        assertThat(new StreamFilename(filename).getDataFilename()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "Get signature filename with last extension from {0}")
    @CsvSource({
            "2020-06-03T16_45_00.1Z_Balances.csv_sig, 2020-06-03T16_45_00.1Z_Balances.pb_sig.gz",
            "2020-06-03T16_45_00.1Z_Balances.pb_sig, 2020-06-03T16_45_00.1Z_Balances.pb_sig.gz",
            "2020-06-03T16_45_00.1Z_Balances.pb_sig.gz, 2020-06-03T16_45_00.1Z_Balances.pb_sig.gz",
            "2020-06-03T16_45_00.1Z_Balances.csv, 2020-06-03T16_45_00.1Z_Balances.pb_sig.gz",
            "2020-06-03T16_45_00.1Z_Balances.pb.gz, 2020-06-03T16_45_00.1Z_Balances.pb_sig.gz",
            "2020-06-03T16_45_00.1Z.evts_sig, 2020-06-03T16_45_00.1Z.evts_sig",
            "2020-06-03T16_45_00.1Z.evts, 2020-06-03T16_45_00.1Z.evts_sig",
            "2020-06-03T16_45_00.1Z.rcd_sig, 2020-06-03T16_45_00.1Z.rcd_sig",
            "2020-06-03T16_45_00.1Z.rcd, 2020-06-03T16_45_00.1Z.rcd_sig",
    })
    void getSignatureFilenameWithLastExtension(String filename, String expected) {
        assertThat(new StreamFilename(filename).getSignatureFilenameWithLastExtension()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "Get timestamp from filename {0}")
    @CsvSource({
            "2020-06-03T16_45_00.1Z.rcd, 2020-06-03T16:45:00.100000000Z",
            "2020-06-03T16_45_00.01Z.rcd, 2020-06-03T16:45:00.010000000Z",
            "2020-06-03T16_45_00.123456789Z.rcd, 2020-06-03T16:45:00.123456789Z",
    })
    void getTimestamp(String filename, String expected) {
        assertThat(new StreamFilename(filename).getTimestamp()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "Get data filename with last extension from {0} and {1}")
    @CsvSource({
            "BALANCE, 2020-06-03T16:45:00.123456789Z, 2020-06-03T16_45_00.123456789Z_Balances.pb.gz",
            "EVENT, 2020-06-03T16:45:00.123456789Z, 2020-06-03T16_45_00.123456789Z.evts",
            "RECORD, 2020-06-03T16:45:00.123456789Z, 2020-06-03T16_45_00.123456789Z.rcd",
    })
    void getDataFilenameWithLastExtension(StreamType streamType, Instant instant, String expected) {
        assertThat(StreamFilename.getDataFilenameWithLastExtension(streamType, instant)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "Get instant from filename ''{0}''")
    @CsvSource({
            "2020-06-03T16_45_00.100000000Z_Balances.pb.gz, 2020-06-03T16:45:00.1Z",
            "2020-06-03T16_45_00.010000000Z.evts, 2020-06-03T16:45:00.01Z",
            "2020-06-03T16_45_00.123456789Z.rcd, 2020-06-03T16:45:00.123456789Z",
            ", 1970-01-01T00:00:00Z",
            "'', 1970-01-01T00:00:00Z",
            "' ', 1970-01-01T00:00:00Z",
    })
    void getInstantFromStreamFilename(String filename, Instant expected) {
        assertThat(StreamFilename.getInstantFromStreamFilename(filename)).isEqualTo(expected);
    }
}
