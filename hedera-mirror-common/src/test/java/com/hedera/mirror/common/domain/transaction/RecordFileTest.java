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

package com.hedera.mirror.common.domain.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.util.Version;

class RecordFileTest {

    @Test
    void testHapiVersion() {
        RecordFile recordFile = RecordFile.builder()
                .hapiVersionMajor(1)
                .hapiVersionMinor(23)
                .hapiVersionPatch(1)
                .build();
        assertThat(recordFile.getHapiVersion()).isEqualTo(new Version(1, 23, 1));
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                ",,", ",1,1", "1,,1", "1,1,",
            })
    void testHapiVersionNotSet(Integer major, Integer minor, Integer patch) {
        RecordFile recordFile = RecordFile.builder()
                .hapiVersionMajor(major)
                .hapiVersionMinor(minor)
                .hapiVersionPatch(patch)
                .build();
        assertThat(recordFile.getHapiVersion()).isEqualTo(RecordFile.HAPI_VERSION_NOT_SET);
    }
}
