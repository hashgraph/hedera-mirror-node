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

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.domain.AccountBalanceFile;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.reader.balance.line.AccountBalanceLineParserV1;

class BalanceFileReaderImplV1Test extends CsvBalanceFileReaderTest {

    static final String FILE_PATH = getTestFilename("v1", "2019-08-30T18_15_00.016002001Z_Balances.csv");

    BalanceFileReaderImplV1Test() {
        super(BalanceFileReaderImplV1.class, AccountBalanceLineParserV1.class, FILE_PATH, 25391L);
    }

    @Test
    void supportsInvalidWhenWrongVersion() {
        StreamFileData streamFileData = StreamFileData
                .from(balanceFile.getName(), BalanceFileReaderImplV2.VERSION_HEADER);
        assertThat(balanceFileReader.supports(streamFileData)).isFalse();
    }

    @Test
    void readValidFileWithLeadingEmptyLine() throws IOException {
        List<String> lines = FileUtils.readLines(balanceFile, CsvBalanceFileReader.CHARSET);
        List<String> copy = new LinkedList<>();
        copy.add("");
        copy.addAll(lines);
        FileUtils.writeLines(testFile, copy);
        List<AccountBalance> accountBalances = new ArrayList<>();
        StreamFileData streamFileData = StreamFileData.from(testFile);
        AccountBalanceFile accountBalanceFile = balanceFileReader.read(streamFileData, accountBalances::add);
        assertAccountBalanceFile(accountBalanceFile);
        verifySuccess(testFile, accountBalances, 2);
    }
}
