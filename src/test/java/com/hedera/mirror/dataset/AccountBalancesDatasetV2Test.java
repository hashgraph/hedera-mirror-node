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

import java.io.FileNotFoundException;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class AccountBalancesDatasetV2Test {
    @Test
    public void positive() throws FileNotFoundException, InvalidDatasetException {
        // The test has a 2 line header and 2 data lines.
        final var fileName = "2019-08-20T21_30_00.147998006Z_Balances.csv";
        final var path = Paths.get("/account_balances", fileName);
        final var datastream = getClass().getResourceAsStream(path.toString());
        final var cut = new AccountBalancesDatasetV2(path.toString(), datastream);
        assertAll(
                () -> assertEquals(1566336600, cut.getConsensusTimestamp().getEpochSecond())
                ,() -> assertEquals(147998006, cut.getConsensusTimestamp().getNano())
                ,() -> assertEquals(2, cut.getLineNumber()) // 2 line header
        );
        var i = cut.getRecordStream().iterator();
        var l1 = i.next();
        var l2 = i.next();
        assertAll(
                () -> assertEquals(3, l1.getLineNumber())
                ,() -> assertEquals("0,0,1,0", l1.getValue())
                ,() -> assertEquals(4, l2.getLineNumber())
                ,() -> assertEquals("0,0,2,4999970459167843402", l2.getValue())
        );
    }
}
