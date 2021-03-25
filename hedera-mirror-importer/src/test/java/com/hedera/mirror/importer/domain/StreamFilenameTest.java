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

import static com.hedera.mirror.importer.domain.StreamFilename.FileType.DATA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.hedera.mirror.importer.exception.InvalidStreamFileException;

class StreamFilenameTest {

    @ParameterizedTest(name = "Create StreamFilename from {0}")
    @CsvSource({
            // @formatter:off
            "2020-06-03T16_45_00.1Z_Balances.csv_sig,, csv_sig, SIGNATURE, csv_sig, 2020-06-03T16:45:00.1Z, BALANCE",
            "2020-06-03T16_45_00.1Z_Balances.pb_sig,, pb_sig, SIGNATURE, pb_sig, 2020-06-03T16:45:00.1Z, BALANCE",
            "2020-06-03T16_45_00.1Z_Balances.pb_sig.gz, gz, pb_sig, SIGNATURE, pb_sig.gz, 2020-06-03T16:45:00.1Z, BALANCE",
            "2020-06-03T16_45_00.1Z_Balances.pb_sig.xz, xz, pb_sig, SIGNATURE, pb_sig.xz, 2020-06-03T16:45:00.1Z, BALANCE",
            "2020-06-03T16_45_00.1Z_Balances.csv,, csv, DATA, csv, 2020-06-03T16:45:00.1Z, BALANCE",
            "2020-06-03T16_45_00.1Z_Balances.pb.gz, gz, pb, DATA, pb.gz, 2020-06-03T16:45:00.1Z, BALANCE",
            "2020-06-03T16_45_00.1Z.evts_sig,, evts_sig, SIGNATURE, evts_sig, 2020-06-03T16:45:00.1Z, EVENT",
            "2020-06-03T16_45_00.1Z.evts,, evts, DATA, evts, 2020-06-03T16:45:00.1Z, EVENT",
            "2020-06-03T16_45_00.1Z.rcd_sig,,  rcd_sig, SIGNATURE, rcd_sig, 2020-06-03T16:45:00.1Z, RECORD",
            "2020-06-03T16_45_00.1Z.rcd,, rcd, DATA, rcd, 2020-06-03T16:45:00.1Z, RECORD",
            // @formatter:on
    })
    void newStreamFile(String filename, String compressor, String extension, StreamFilename.FileType fileType,
            String fullExtension, Instant instant, StreamType streamType) {
        StreamFilename streamFilename = new StreamFilename(filename);
        String[] fields = {"filename", "compressor", "extension.name", "fileType", "fullExtension", "instant",
                "streamType"};
        Object[] expected = {filename, compressor, extension, fileType, fullExtension, instant, streamType};

        assertThat(streamFilename).extracting(fields).containsExactly(expected);
    }

    @ParameterizedTest(name = "Exception creating StreamFilename from \"{0}\"")
    @ValueSource(strings = { "2020-06-03_Balances.csv_sig", "2020-06-03T16_45_00.1Z", "2020-06-03T16_45_00.1Z.stream",
            "2020-06-03T16_45_00.1Z.csv_sig"})
    void newStreamFileFromInvalidFilename(String filename) {
        assertThrows(InvalidStreamFileException.class, () -> new StreamFilename(filename));
    }

    @ParameterizedTest(name = "Get filename from streamType {0}, fileType {1}, and instant {2}")
    @CsvSource({
            "BALANCE, DATA, 2020-06-03T16:45:00.123Z, 2020-06-03T16_45_00.123Z_Balances.",
            "BALANCE, SIGNATURE, 2020-06-03T16:45:00.123Z, 2020-06-03T16_45_00.123Z_Balances.",
            "EVENT, DATA, 2020-06-03T16:45:00.123Z, 2020-06-03T16_45_00.123Z.",
            "EVENT, SIGNATURE, 2020-06-03T16:45:00.123Z, 2020-06-03T16_45_00.123Z.",
            "RECORD, DATA, 2020-06-03T16:45:00.123Z, 2020-06-03T16_45_00.123Z.",
            "RECORD, SIGNATURE, 2020-06-03T16:45:00.123Z, 2020-06-03T16_45_00.123Z."
    })
    void getFilename(StreamType streamType, StreamFilename.FileType fileType, Instant instant, String expectedPrefix) {
        String filename = StreamFilename.getFilename(streamType, fileType, instant);
        String extension = filename.substring(filename.lastIndexOf('.') + 1);

        Set<StreamType.Extension> extensions = fileType == DATA ? streamType.getDataExtensions() : streamType
                .getSignatureExtensions();
        List<String> names = extensions.stream().map(StreamType.Extension::getName).collect(Collectors.toList());
        assertThat(filename).startsWith(expectedPrefix);
        assertThat(extension).isIn(names);
    }

    @ParameterizedTest(name = "Get the filename after \"{0}\"")
    @CsvSource({
            "2020-06-03T16_45_00.1Z_Balances.csv_sig.gz, 2020-06-03T16_45_00.1Z_Balances_",
            "2020-06-03T16_45_00.1Z_Balances.csv_sig, 2020-06-03T16_45_00.1Z_Balances_",
            "2020-06-03T16_45_00.1Z_Balances.pb_sig, 2020-06-03T16_45_00.1Z_Balances_",
            "2020-06-03T16_45_00.1Z_Balances.csv, 2020-06-03T16_45_00.1Z_Balances_",
            "2020-06-03T16_45_00.1Z_Balances.pb, 2020-06-03T16_45_00.1Z_Balances_",
            "2020-06-03T16_45_00.1Z.evts_sig, 2020-06-03T16_45_00.1Z_",
            "2020-06-03T16_45_00.1Z.evts, 2020-06-03T16_45_00.1Z_",
            "2020-06-03T16_45_00.1Z.rcd_sig, 2020-06-03T16_45_00.1Z_",
            "2020-06-03T16_45_00.1Z.rcd, 2020-06-03T16_45_00.1Z_"
    })
    void getFilenameAfter(String filename, String expected) {
        StreamFilename streamFilename = new StreamFilename(filename);
        assertThat(streamFilename.getFilenameAfter()).isEqualTo(expected);
    }
}
