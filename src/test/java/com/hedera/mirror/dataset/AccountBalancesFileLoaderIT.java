package com.hedera.mirror.dataset;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

import com.hedera.mirror.exception.InvalidDatasetException;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;

import java.io.FileNotFoundException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

@Sql(executionPhase= Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts="classpath:db/scripts/cleanup.sql") // Class manually commits so have to manually cleanup tables
public class AccountBalancesFileLoaderIT {
    @Test
    public void positiveSmallFile() throws FileNotFoundException, InvalidDatasetException, URISyntaxException, SQLException {
        // The test has a 2 line header and 2 data lines.
        final var fileName = "2019-08-20T21_30_00.147998006Z_Balances.csv";
        final var path = getClass().getResource(Paths.get("/account_balances", fileName).toString());
        final var cut = new AccountBalancesFileLoader(Paths.get(path.toURI()));
        boolean success = cut.loadAccountBalances();
        assertAll(
                () -> assertEquals(2, cut.getValidRowCount())
                ,() -> assertTrue(success)
        );
        // TODO assert the rows actually added to the database.
    }
}
