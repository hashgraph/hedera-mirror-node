/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StreamFileSignatureTest {

    @ParameterizedTest
    @CsvSource({
        "2020-06-03T16_45_00.100200345Z_Balances.csv_sig, 5, 2020-06-03T16_45_00.100200345Z_Balances.csv",
        "2020-06-03T16_45_00.100200345Z_Balances.pb_sig, 6, 2020-06-03T16_45_00.100200345Z_Balances.pb.gz",
        "2020-06-03T16_45_00.100200345Z_Balances.pb_sig.gz, 5, 2020-06-03T16_45_00.100200345Z_Balances.pb.gz",
        "2020-06-03T16_45_00.100200345Z.rcd_sig, 5, 2020-06-03T16_45_00.100200345Z.rcd",
        "2020-06-03T16_45_00.100200345Z.rcd_sig, 6, 2020-06-03T16_45_00.100200345Z.rcd.gz"
    })
    void getDataFilename(String filename, byte version, String expected) {
        var streamFileSignature = new StreamFileSignature();
        streamFileSignature.setFilename(StreamFilename.from(filename));
        streamFileSignature.setVersion(version);
        assertThat(streamFileSignature.getDataFilename().getFilename()).isEqualTo(expected);
    }
}
