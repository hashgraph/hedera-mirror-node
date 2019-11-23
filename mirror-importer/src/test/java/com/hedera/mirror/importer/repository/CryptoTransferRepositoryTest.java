package com.hedera.mirror.importer.repository;

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

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.Transaction;

import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.CryptoTransfer;

public class CryptoTransferRepositoryTest extends AbstractRepositoryTest {
    @Test
    void findByConsensusTimestampAndEntityNum() {
        RecordFile recordfile = insertRecordFile();
        Entities entity = insertAccountEntity();
        Transaction transaction = insertTransaction(recordfile.getId(), entity.getId(), "CRYPTOTRANSFER");

        final long consensusNs = transaction.getConsensusNs();
        final long accountNum = 2;
        CryptoTransfer cryptoTransfer = new CryptoTransfer();
        cryptoTransfer.setConsensusTimestamp(consensusNs);
        cryptoTransfer.setRealmNum(1L);
        cryptoTransfer.setEntityNum(accountNum);
        cryptoTransfer.setAmount(40L);

        cryptoTransfer = cryptoTransferRepository.save(cryptoTransfer);

        assertThat(cryptoTransferRepository.findByConsensusTimestampAndEntityNum(consensusNs, accountNum).get())
                .isNotNull()
                .isEqualTo(cryptoTransfer);
    }
}
