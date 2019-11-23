package com.hedera.mirror.importer.parser.balance;

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

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

public class AccountBalancesDatasetV2Test {
    @Test
    public void positive() throws Exception {
        // The test has a 2 line header and 2 data lines.
        final var resource = new ClassPathResource("data/accountBalances/balance0.0.3/2019-08-30T18_15_00" +
                ".016002001Z_Balances.csv");
        final var datastream = new BufferedReader(new InputStreamReader(resource.getInputStream()));
        final var cut = new AccountBalancesDatasetV2(resource.getFilename(), datastream);
        assertAll(
                () -> assertEquals(1567188900, cut.getConsensusTimestamp().getEpochSecond())
                , () -> assertEquals(16002001, cut.getConsensusTimestamp().getNano())
                , () -> assertEquals(2, cut.getLineNumber()) // 2 line header
        );
        var i = cut.getRecordStream().iterator();
        var l1 = i.next();
        var l2 = i.next();
        assertAll(
                () -> assertEquals(3, l1.getLineNumber())
                , () -> assertEquals("0,0,1,250", l1.getValue())
                , () -> assertEquals(4, l2.getLineNumber())
                , () -> assertEquals("0,0,2,2588856875379417355", l2.getValue())
        );
    }
}
