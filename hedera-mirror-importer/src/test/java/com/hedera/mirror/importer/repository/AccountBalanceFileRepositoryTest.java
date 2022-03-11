package com.hedera.mirror.importer.repository;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import javax.annotation.Resource;
import org.junit.jupiter.api.Test;

class AccountBalanceFileRepositoryTest extends AbstractRepositoryTest {

    @Resource
    private AccountBalanceFileRepository accountBalanceFileRepository;

    @Test
    void findLatest() {
        domainBuilder.accountBalanceFile().persist();
        domainBuilder.accountBalanceFile().persist();
        var latest = domainBuilder.accountBalanceFile().persist();
        assertThat(accountBalanceFileRepository.findLatest()).get().isEqualTo(latest);
    }

    @Test
    void findNextInRange() {
        var file1 = domainBuilder.accountBalanceFile().persist();
        var file2 = domainBuilder.accountBalanceFile().persist();
        domainBuilder.recordFile().persist();
        var file3 = domainBuilder.accountBalanceFile().persist();

        assertThat(accountBalanceFileRepository.findNextInRange(file3.getConsensusTimestamp(), Long.MAX_VALUE)).isEmpty();
        assertThat(accountBalanceFileRepository.findNextInRange(0L, file1.getConsensusTimestamp()))
                .get()
                .isEqualTo(file1);

        assertThat(accountBalanceFileRepository.findNextInRange(0L, Long.MAX_VALUE))
                .get()
                .isEqualTo(file1);

        assertThat(accountBalanceFileRepository.findNextInRange(file1.getConsensusTimestamp(), Long.MAX_VALUE))
                .get()
                .isEqualTo(file1);

        assertThat(accountBalanceFileRepository.findNextInRange(file2.getConsensusTimestamp(), Long.MAX_VALUE))
                .get()
                .isEqualTo(file2);

        assertThat(accountBalanceFileRepository.findNextInRange(file1.getConsensusTimestamp(),
                file3.getConsensusTimestamp()))
                .get()
                .isEqualTo(file1);
    }
}
