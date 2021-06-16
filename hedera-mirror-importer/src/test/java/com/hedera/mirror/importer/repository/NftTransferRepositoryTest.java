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
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.NftTransfer;
import com.hedera.mirror.importer.domain.NftTransferId;

class NftTransferRepositoryTest extends AbstractRepositoryTest {

    @Resource
    NftTransferRepository repository;

    @Test
    void save() {
        NftTransfer nftTransfer = new NftTransfer();
        nftTransfer.setId(new NftTransferId(1, 1, EntityId.of("0.0.1", EntityTypeEnum.TOKEN)));
        nftTransfer.setReceiverAccountId(EntityId.of("0.0.2", EntityTypeEnum.ACCOUNT));
        nftTransfer.setSenderAccountId(EntityId.of("0.0.3", EntityTypeEnum.ACCOUNT));
        NftTransfer saved = repository.save(nftTransfer);
        assertThat(repository.findById(saved.getId()).get()).isEqualTo(saved);
    }

    @Test
    void saveMintTransfer() {
        NftTransfer nftTransfer = new NftTransfer();
        nftTransfer.setId(new NftTransferId(1, 1, EntityId.of("0.0.1", EntityTypeEnum.TOKEN)));
        nftTransfer.setReceiverAccountId(EntityId.of("0.0.2", EntityTypeEnum.ACCOUNT));
        NftTransfer saved = repository.save(nftTransfer);
        assertThat(repository.findById(saved.getId()).get()).isEqualTo(saved);
    }

    @Test
    void saveBurnTransfer() {
        NftTransfer nftTransfer = new NftTransfer();
        nftTransfer.setId(new NftTransferId(1, 1, EntityId.of("0.0.1", EntityTypeEnum.TOKEN)));
        nftTransfer.setSenderAccountId(EntityId.of("0.0.3", EntityTypeEnum.ACCOUNT));
        NftTransfer saved = repository.save(nftTransfer);
        assertThat(repository.findById(saved.getId()).get()).isEqualTo(saved);
    }
}
