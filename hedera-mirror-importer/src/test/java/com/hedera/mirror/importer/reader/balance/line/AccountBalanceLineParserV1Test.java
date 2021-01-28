package com.hedera.mirror.importer.reader.balance.line;

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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.exception.InvalidDatasetException;

class AccountBalanceLineParserV1Test {
    private static final long timestamp = 1596340377922333444L;
    private static final long systemShardNum = 0;
    private AccountBalanceLineParserV1 parser;

    @BeforeEach
    void setup() {
        parser = new AccountBalanceLineParserV1();
    }

    @DisplayName("Parse account balance line")
    @ParameterizedTest(name = "from \"{0}\"")
    @CsvSource(value = {
            "'0,0,123,700';false;0;123;700",
            "' 0,0,123,700';false;0;123;700",
            "'0, 0,123,700';false;0;123;700",
            "'0,0, 123,700';false;0;123;700",
            "'0,0,123, 700';false;0;123;700",
            "'0,0,123,700 ';false;0;123;700",
            "'1,0,123,700';true;;;",
            "'x,0,123,700';true;;;",
            "'0,x,123,700';true;;;",
            "'0,0,x,700';true;;;",
            "'0,0,123,a00';true;;;",
            "'1000000000000000000000000000,0,123,700';true;;;",
            "'0,1000000000000000000000000000,123,700';true;;;",
            "'0,0,1000000000000000000000000000,700';true;;;",
            "'0,0,123,1000000000000000000000000000';true;;;",
            "'-1,0,123,700';true;;;",
            "'0,-1,123,700';true;;;",
            "'0,0,-1,700';true;;;",
            "'0,0,123,-1';true;;;",
            "'foobar';true;;;",
            "'';true;;;",
            ";true;;;"
    }, delimiter = ';')
    void parse(String line, boolean expectThrow, Long expectedRealm, Long expectedAccount, Long expectedBalance) {
        if (!expectThrow) {
            AccountBalance accountBalance = parser.parse(line, timestamp, systemShardNum);
            var id = accountBalance.getId();

            assertThat(accountBalance.getBalance()).isEqualTo(expectedBalance);
            assertThat(id.getAccountId().getRealmNum()).isEqualTo(expectedRealm);
            assertThat(id.getAccountId().getEntityNum()).isEqualTo(expectedAccount);
            assertThat(id.getConsensusTimestamp()).isEqualTo(timestamp);
        } else {
            assertThrows(InvalidDatasetException.class, () -> {
                parser.parse(line, timestamp, systemShardNum);
            });
        }
    }

    @Test
    void parseNullLine() {
        assertThrows(InvalidDatasetException.class, () -> {
            parser.parse(null, timestamp, systemShardNum);
        });
    }
}
