package com.hedera.mirror.importer.repository;

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

import java.util.List;
import javax.annotation.Resource;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.AccountBalanceFile;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;

class AccountBalanceFileRepositoryTest extends AbstractRepositoryTest {

    @Resource
    private AccountBalanceFileRepository accountBalanceFileRepository;

    private long count = 0;

    @Test
    void findLatest() {
        AccountBalanceFile accountBalanceFile1 = accountBalanceFile();
        AccountBalanceFile accountBalanceFile2 = accountBalanceFile();
        AccountBalanceFile accountBalanceFile3 = accountBalanceFile();
        accountBalanceFileRepository.saveAll(List.of(accountBalanceFile1, accountBalanceFile2, accountBalanceFile3));
        assertThat(accountBalanceFileRepository.findLatest()).get().isEqualTo(accountBalanceFile3);
    }

    private AccountBalanceFile accountBalanceFile() {
        long id = ++count;
        return AccountBalanceFile.builder()
                .consensusTimestamp(id)
                .count(id)
                .fileHash("fileHash" + id)
                .loadEnd(id)
                .loadStart(id)
                .name(id + ".csv")
                .nodeAccountId(EntityId.of("0.0.3", EntityTypeEnum.ACCOUNT))
                .build();
    }
}
