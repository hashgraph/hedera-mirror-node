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
import static com.hedera.mirror.importer.domain.EntityTypeEnum.TOKEN;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javax.annotation.Resource;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.NftTransfer;
import com.hedera.mirror.importer.domain.NftTransferId;

class NftTransferRepositoryTest extends AbstractRepositoryTest {

    @Resource
    private NftTransferRepository nftTransferRepository;

    @Test
    void save() {
        NftTransfer nftTransfer = new NftTransfer();
        nftTransfer.setId(new NftTransferId(1, 1, EntityId.of("0.0.1", EntityTypeEnum.TOKEN)));
        nftTransfer.setReceiverAccountId(EntityId.of("0.0.2", ACCOUNT));
        nftTransfer.setSenderAccountId(EntityId.of("0.0.3", ACCOUNT));
        NftTransfer saved = nftTransferRepository.save(nftTransfer);
        assertThat(nftTransferRepository.findById(saved.getId())).contains(saved);
    }

    @Test
    void saveMintTransfer() {
        NftTransfer nftTransfer = new NftTransfer();
        nftTransfer.setId(new NftTransferId(1, 1, EntityId.of("0.0.1", EntityTypeEnum.TOKEN)));
        nftTransfer.setReceiverAccountId(EntityId.of("0.0.2", ACCOUNT));
        NftTransfer saved = nftTransferRepository.save(nftTransfer);
        assertThat(nftTransferRepository.findById(saved.getId())).contains(saved);
    }

    @Test
    void saveBurnTransfer() {
        NftTransfer nftTransfer = new NftTransfer();
        nftTransfer.setId(new NftTransferId(1, 1, EntityId.of("0.0.1", EntityTypeEnum.TOKEN)));
        nftTransfer.setSenderAccountId(EntityId.of("0.0.3", ACCOUNT));
        NftTransfer saved = nftTransferRepository.save(nftTransfer);
        assertThat(nftTransferRepository.findById(saved.getId())).contains(saved);
    }

    @Test
    void getTimestampAtOffsetAfter() {
        // given
        EntityId account = EntityId.of("0.0.10", ACCOUNT);
        EntityId token = EntityId.of("0.0.200", TOKEN);
        nftTransferRepository.saveAll(List.of(
                nftTransfer(10L, account, null, 1L, token),
                nftTransfer(10L, account, null, 2L, token),
                nftTransfer(11L, account, null, 3L, token),
                nftTransfer(11L, account, null, 4L, token),
                nftTransfer(12L, account, null, 5L, token)
        ));

        // when, then
        assertThat(nftTransferRepository.getTimestampAtOffsetAfter(10, 2)).get().isEqualTo(12L);
    }

    @Test
    void getTimestampAtOffsetAfterEmptyNotPresent() {
        assertThat(nftTransferRepository.getTimestampAtOffsetAfter(0, 0)).isNotPresent();
    }

    @Test
    void getTimestampAtOffsetAfterOutOfBoundNotPresent() {
        // given
        EntityId account = EntityId.of("0.0.10", ACCOUNT);
        EntityId token = EntityId.of("0.0.200", TOKEN);
        nftTransferRepository.saveAll(List.of(
                nftTransfer(10L, account, null, 1L, token),
                nftTransfer(10L, account, null, 2L, token),
                nftTransfer(11L, account, null, 3L, token),
                nftTransfer(11L, account, null, 4L, token),
                nftTransfer(12L, account, null, 5L, token)
        ));

        // when, then
        assertThat(nftTransferRepository.getTimestampAtOffsetAfter(10, 3)).isNotPresent();
    }

    private NftTransfer nftTransfer(long consensusTimestamp, EntityId receiver, EntityId sender, long serialNumber,
                                    EntityId token) {
        NftTransfer nftTransfer = new NftTransfer();
        nftTransfer.setId(new NftTransferId(consensusTimestamp, serialNumber, token));
        nftTransfer.setReceiverAccountId(receiver);
        nftTransfer.setSenderAccountId(sender);
        return nftTransfer;
    }
}
