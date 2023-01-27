package com.hedera.mirror.importer.parser.balance;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

import com.hedera.mirror.common.domain.balance.AccountBalanceFile;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.reader.balance.BalanceFileReader;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;

@Tag("performance")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class AccountBalanceFileParserPerformanceTest extends IntegrationTest {

    private final AccountBalanceFileRepository accountBalanceFileRepository;
    private final AccountBalanceFileParser balanceFileParser;
    private final BalanceFileReader balanceFileReader;
    private final BalanceParserProperties balanceParserProperties;
    @Value("classpath:data/accountBalances/v1/performance/*.csv")
    private final Resource[] testFiles;

    private final List<AccountBalanceFile> accountBalanceFiles = new ArrayList<>();

    @BeforeAll
    void setup() throws Exception {
        balanceParserProperties.setEnabled(true);
        for (Resource resource : testFiles) {
            AccountBalanceFile accountBalanceFile = balanceFileReader.read(StreamFileData.from(resource.getFile()));
            accountBalanceFile.setNodeId(0L);
            accountBalanceFiles.add(accountBalanceFile);
        }
    }

    @Test
    @Timeout(10)
    void parse() {
        accountBalanceFiles.forEach(balanceFileParser::parse);
        assertThat(accountBalanceFileRepository.count()).isEqualTo(accountBalanceFiles.size());
    }
}
