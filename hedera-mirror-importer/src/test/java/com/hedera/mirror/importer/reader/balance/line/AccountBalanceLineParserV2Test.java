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

import com.google.common.base.Splitter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.TokenBalance;
import com.hedera.mirror.importer.exception.InvalidDatasetException;

class AccountBalanceLineParserV2Test {

    private static final long timestamp = 1596340377922333444L;
    private AccountBalanceLineParserV2 parser;
    private MirrorProperties mirrorProperties;

    @BeforeEach
    void setup() {
        mirrorProperties = new MirrorProperties();
        parser = new AccountBalanceLineParserV2(mirrorProperties);
    }

    @DisplayName("Parse account balance line")
    @ParameterizedTest(name = "from \"{0}\"")
    @CsvSource(value = {
            "'0,0,123,700,CggKAxjsBxCEBwoHCgMY7QcQGQoKCgMY7gcQwKilBAoICgMY8gcQhAcKBwoDGPMHEBkKCgoDGPQHEMCopQQ';false;" +
                    "0;123;700;1004=900,1005=25,1006=9000000,1010=900,1011=25,1012=9000000",
            "'0,0,123,700,';false;" +
                    "0;123;700;;",
            "'0,0,123,700,CggKAxjsBxCEBwoHCgMY7QcQGQoKCgMY7gcQwKilBAoICgMY8gcQhAcKBwoDGPMHEBkKCgo';true;;;;",
            "' 0,0,123,700,CggKAxjsBxCEBwoHCgMY7QcQGQoKCgMY7gcQwKilBAoICgMY8gcQhAcKBwoDGPMHEBkKCgoDGPQHEMCopQQ';" +
                    "false;0;123;700;1004=900,1005=25,1006=9000000,1010=900,1011=25,1012=9000000",
            "'0, 0,123,700,CggKAxjsBxCEBwoHCgMY7QcQGQoKCgMY7gcQwKilBAoICgMY8gcQhAcKBwoDGPMHEBkKCgoDGPQHEMCopQQ';" +
                    "false;0;123;700;1004=900,1005=25,1006=9000000,1010=900,1011=25,1012=9000000",
            "'0,0, 123,700,CggKAxjsBxCEBwoHCgMY7QcQGQoKCgMY7gcQwKilBAoICgMY8gcQhAcKBwoDGPMHEBkKCgoDGPQHEMCopQQ';" +
                    "false;0;123;700;1004=900,1005=25,1006=9000000,1010=900,1011=25,1012=9000000",
            "'0,0,123, 700,CggKAxjsBxCEBwoHCgMY7QcQGQoKCgMY7gcQwKilBAoICgMY8gcQhAcKBwoDGPMHEBkKCgoDGPQHEMCopQQ';" +
                    "false;0;123;700;1004=900,1005=25,1006=9000000,1010=900,1011=25,1012=9000000",
            "'0,0,123,700, CggKAxjsBxCEBwoHCgMY7QcQGQoKCgMY7gcQwKilBAoICgMY8gcQhAcKBwoDGPMHEBkKCgoDGPQHEMCopQQ';" +
                    "false;0;123;700;1004=900,1005=25,1006=9000000,1010=900,1011=25,1012=9000000",
            "'0,0,123,700,CggKAxjsBxCEBwoHCgMY7QcQGQoKCgMY7gcQwKilBAoICgMY8gcQhAcKBwoDGPMHEBkKCgoDGPQHEMCopQQ ';" +
                    "false;0;123;700;1004=900,1005=25,1006=9000000,1010=900,1011=25,1012=9000000",
            "'1,0,123,700,';true;;;;",
            "'x,0,123,700,';true;;;;",
            "'0,x,123,700,';true;;;;",
            "'0,0,x,700,';true;;;;",
            "'0,0,123,a00,';true;;;;",
            "'1000000000000000000000000000,0,123,700,';true;;;;",
            "'0,1000000000000000000000000000,123,700,';true;;;;",
            "'0,0,1000000000000000000000000000,700,';true;;;;",
            "'0,0,123,1000000000000000000000000000,';true;;;;",
            "'-1,0,123,700,';true;;;;",
            "'0,-1,123,700,';true;;;;",
            "'0,0,-1,700,';true;;;;",
            "'0,0,123,-1,';true;;;;",
            "'foobar';true;;;;",
            "'';true;;;;",
            ";true;;;;"
    }, delimiter = ';')
    void parse(String line, boolean expectThrow, Long expectedRealm, Long expectedAccount, Long expectedBalance,
               String tokenBalances) throws IOException {
        if (!expectThrow) {
            AccountBalance accountBalance = parser.parse(line, timestamp);
            var id = accountBalance.getId();

            assertThat(accountBalance.getBalance()).isEqualTo(expectedBalance);
            assertThat(id).isNotNull();
            assertThat(id.getAccountId().getRealmNum()).isEqualTo(expectedRealm);
            assertThat(id.getAccountId().getEntityNum()).isEqualTo(expectedAccount);
            assertThat(id.getConsensusTimestamp()).isEqualTo(timestamp);

            List<TokenBalance> actualTokenBalanceList = accountBalance.getTokenBalances();
            if (StringUtils.isNotBlank(tokenBalances)) {
                Map<Long, Long> expectedTokenBalances = Splitter.on(',').withKeyValueSeparator('=')
                        .split(tokenBalances).entrySet().stream().collect(Collectors
                                .toMap(entry -> Long.parseLong(entry.getKey()), entry -> Long
                                        .parseLong(entry.getValue())));
                assertThat(actualTokenBalanceList.size()).isEqualTo(expectedTokenBalances.size());
                for (int i = 0; i < actualTokenBalanceList.size(); i++) {
                    TokenBalance actualTokenBalance = actualTokenBalanceList.get(i);
                    TokenBalance.Id actualId = actualTokenBalance.getId();

                    assertThat(expectedTokenBalances).containsKey(actualId.getTokenId().getEntityNum());
                    assertThat(actualTokenBalance.getBalance())
                            .isEqualTo(expectedTokenBalances.get(actualId.getTokenId().getEntityNum()));
                    assertThat(actualId).isNotNull();
                    assertThat(actualId.getConsensusTimestamp()).isEqualTo(timestamp);
                    assertThat(actualId.getAccountId().getShardNum()).isEqualTo(mirrorProperties.getShard());
                    assertThat(actualId.getAccountId().getRealmNum()).isEqualTo(expectedRealm);
                    assertThat(actualId.getAccountId().getEntityNum()).isEqualTo(expectedAccount);

                    assertThat(actualId.getTokenId().getShardNum()).isEqualTo(mirrorProperties.getShard());
                    assertThat(actualId.getTokenId().getRealmNum()).isEqualTo(expectedRealm);
                    assertThat(actualId.getTokenId().getType()).isEqualTo(EntityTypeEnum.TOKEN);
                }
            } else {
                assertThat(actualTokenBalanceList.size()).isEqualTo(0);
            }
        } else {
            assertThrows(InvalidDatasetException.class, () -> {
                parser.parse(line, timestamp);
            });
        }
    }

    @Test
    void parseNullLine() {
        assertThrows(InvalidDatasetException.class, () -> {
            parser.parse(null, timestamp);
        });
    }
}
