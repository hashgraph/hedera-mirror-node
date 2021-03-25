package com.hedera.mirror.importer.reader.balance;

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

import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.reader.balance.line.AccountBalanceLineParserV2;

class BalanceFileReaderImplV2Test extends CsvBalanceFileReaderTest {

    static final String FILE_PATH = getTestFilename("v2", "2020-09-22T04_25_00.083212003Z_Balances.csv");

    BalanceFileReaderImplV2Test() {
        super(BalanceFileReaderImplV2.class, AccountBalanceLineParserV2.class, FILE_PATH, 106L);
    }

    @Test
    void supportsInvalidWhenWrongVersion() {
        StreamFileData streamFileData = StreamFileData
                .from(balanceFile.getName(), BalanceFileReaderImplV1.TIMESTAMP_HEADER_PREFIX);
        assertThat(balanceFileReader.supports(streamFileData)).isFalse();
    }
}
