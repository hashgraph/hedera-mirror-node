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

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.NftTransfer;
import com.hedera.mirror.common.domain.token.NftTransferId;

class NftTransferRepositoryTest extends AbstractRepositoryTest {

    private static final EntityId PAYER_ACCOUNT_ID = EntityId.of("0.0.1000", EntityType.ACCOUNT);

    @Resource
    NftTransferRepository repository;

    @Test
    void save() {
        NftTransfer nftTransfer = new NftTransfer();
        nftTransfer.setId(new NftTransferId(1, 1, EntityId.of("0.0.1", EntityType.TOKEN)));
        nftTransfer.setReceiverAccountId(EntityId.of("0.0.2", EntityType.ACCOUNT));
        nftTransfer.setSenderAccountId(EntityId.of("0.0.3", EntityType.ACCOUNT));
        nftTransfer.setPayerAccountId(PAYER_ACCOUNT_ID);
        NftTransfer saved = repository.save(nftTransfer);
        assertThat(repository.findById(saved.getId())).contains(saved);
    }

    @Test
    void saveMintTransfer() {
        NftTransfer nftTransfer = new NftTransfer();
        nftTransfer.setId(new NftTransferId(1, 1, EntityId.of("0.0.1", EntityType.TOKEN)));
        nftTransfer.setReceiverAccountId(EntityId.of("0.0.2", EntityType.ACCOUNT));
        nftTransfer.setPayerAccountId(PAYER_ACCOUNT_ID);
        NftTransfer saved = repository.save(nftTransfer);
        assertThat(repository.findById(saved.getId())).contains(saved);
    }

    @Test
    void saveBurnTransfer() {
        NftTransfer nftTransfer = new NftTransfer();
        nftTransfer.setId(new NftTransferId(1, 1, EntityId.of("0.0.1", EntityType.TOKEN)));
        nftTransfer.setSenderAccountId(EntityId.of("0.0.3", EntityType.ACCOUNT));
        nftTransfer.setPayerAccountId(PAYER_ACCOUNT_ID);
        NftTransfer saved = repository.save(nftTransfer);
        assertThat(repository.findById(saved.getId())).contains(saved);
    }
}
