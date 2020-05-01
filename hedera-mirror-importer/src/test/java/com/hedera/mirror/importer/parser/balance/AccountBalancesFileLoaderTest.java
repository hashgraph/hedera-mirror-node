package com.hedera.mirror.importer.parser.balance;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import javax.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import com.hedera.mirror.importer.IntegrationTest;

public class AccountBalancesFileLoaderTest extends IntegrationTest {

    @Value("classpath:data/accountBalances/balance0.0.3/2019-08-30T18_15_00.016002001Z_Balances.csv")
    private Path path;

    @Resource
    private BalanceParserProperties parserProperties;

    @Test
    public void positiveSmallFile() throws Exception {
        // The test has a 2 line header and 2 data lines.
        var cut = new AccountBalancesFileLoader(parserProperties, path);
        boolean success = cut.loadAccountBalances();
        assertAll(
                () -> assertEquals(25391, cut.getValidRowCount())
                , () -> assertTrue(success)
        );
        // TODO assert the rows actually added to the database.
    }
}
