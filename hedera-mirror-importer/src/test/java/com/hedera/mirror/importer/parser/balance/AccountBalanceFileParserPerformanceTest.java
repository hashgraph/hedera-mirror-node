package com.hedera.mirror.importer.parser.balance;

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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.AccountBalanceFile;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.reader.balance.BalanceFileReader;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;

@Tag("performance")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountBalanceFileParserPerformanceTest extends IntegrationTest {

    @Value("classpath:data/accountBalances/v1/performance/*.csv")
    private Resource[] testFiles;

    @Autowired
    private AccountBalanceFileParser balanceFileParser;

    @Autowired
    private BalanceFileReader balanceFileReader;

    @Autowired
    private BalanceParserProperties balanceParserProperties;

    @Autowired
    private AccountBalanceFileRepository accountBalanceFileRepository;

    private final List<AccountBalanceFile> accountBalanceFiles = new ArrayList<>();

    @BeforeAll
    void setup() throws Exception {
        balanceParserProperties.setEnabled(true);
        EntityId nodeAccountId = EntityId.of(0L, 0L, 3L, EntityTypeEnum.ACCOUNT);
        for (Resource resource : testFiles) {
            AccountBalanceFile accountBalanceFile = balanceFileReader.read(StreamFileData.from(resource.getFile()));
            accountBalanceFile.setNodeAccountId(nodeAccountId);
            accountBalanceFiles.add(accountBalanceFile);
        }
    }

    @Test
    @Timeout(10)
    void parse() throws SQLException {
        AccountBalanceFileParser accountBalanceFileParser = balanceFileParser;
        for (AccountBalanceFile accountBalanceFile : accountBalanceFiles) {
            accountBalanceFileParser.parse(accountBalanceFile);
        }
        assertThat(accountBalanceFileRepository.count()).isEqualTo(accountBalanceFiles.size());
    }
}
