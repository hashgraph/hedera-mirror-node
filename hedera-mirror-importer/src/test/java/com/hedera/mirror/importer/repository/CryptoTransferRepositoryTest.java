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

import static com.hedera.mirror.importer.domain.EntityTypeEnum.ACCOUNT;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.CryptoTransfer;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.Transaction;

public class CryptoTransferRepositoryTest extends AbstractRepositoryTest {
    @Test
    void findByConsensusTimestampAndEntityNum() {
        Transaction transaction = insertTransaction("CRYPTOTRANSFER");

        long consensusNs = transaction.getConsensusNs();
        EntityId entity = EntityId.of(0L, 1L, 2L, ACCOUNT);
        long amount = 40L;
        CryptoTransfer cryptoTransfer = new CryptoTransfer(consensusNs, amount, entity);

        cryptoTransferRepository.save(cryptoTransfer);

        assertThat(cryptoTransferRepository.findById(cryptoTransfer.getId()))
                .get()
                .isEqualTo(cryptoTransfer);
    }
}
