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

import javax.annotation.Resource;

import com.hedera.mirror.importer.domain.EntityType;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.TransactionSignature;

class TransactionSignatureRepositoryTest extends AbstractRepositoryTest {
    @Resource
    private TransactionSignatureRepository transactionSignatureRepository;

    @Test
    void save() {
        TransactionSignature transactionSignature = transactionSignatureRepository.save(transactionSignature(1));
        assertThat(transactionSignatureRepository.findById(transactionSignature.getId()))
                .get().isEqualTo(transactionSignature);
    }

    private TransactionSignature transactionSignature(long consensusTimestamp) {
        TransactionSignature transactionSignature = new TransactionSignature();
        transactionSignature.setId(new TransactionSignature.Id(
                consensusTimestamp,
                "signatory public key prefix".getBytes()));
        transactionSignature.setEntityId(EntityId.of("0.0.789", EntityType.UNKNOWN));
        transactionSignature.setSignature("scheduled transaction signature".getBytes());
        return transactionSignature;
    }
}
