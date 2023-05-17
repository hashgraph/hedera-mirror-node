/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.web3.evm.store.accessor;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.token.NftId;
import com.hedera.mirror.web3.repository.NftRepository;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.UniqueToken;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UniqueTokenDatabaseAccessorTest {
    @InjectMocks
    private UniqueTokenDatabaseAccessor uniqueTokenDatabaseAccessor;

    @Mock
    private NftRepository nftRepository;

    @Test
    void get() {
        EntityId accountId = new EntityId(1L, 2L, 3L, EntityType.ACCOUNT);
        EntityId tokenId = new EntityId(4L, 5L, 6L, EntityType.TOKEN);
        EntityId spenderId = new EntityId(7L, 8L, 9L, EntityType.TOKEN);
        long serialNumber = 123L;
        int createdTimestampNanos = 13;
        long createdTimestampSecs = 12;
        byte[] metadata = "metadata1".getBytes();

        NftId nftId = new NftId();
        nftId.setTokenId(tokenId);
        nftId.setSerialNumber(serialNumber);

        Nft nft = new Nft();
        nft.setId(nftId);
        nft.setAccountId(accountId);
        nft.setDeleted(false);
        nft.setCreatedTimestamp(createdTimestampSecs * 1_000_000_000 + createdTimestampNanos);
        nft.setSpender(spenderId);
        nft.setMetadata(metadata);

        when(nftRepository.findById(nftId)).thenReturn(Optional.of(nft));

        assertThat(uniqueTokenDatabaseAccessor.get(nftId)).hasValueSatisfying(uniqueToken -> assertThat(uniqueToken)
                .returns(mapEntityIdToId(tokenId), UniqueToken::getTokenId)
                .returns(serialNumber, UniqueToken::getSerialNumber)
                .returns(new RichInstant(createdTimestampSecs, createdTimestampNanos), UniqueToken::getCreationTime)
                .returns(mapEntityIdToId(accountId), UniqueToken::getOwner)
                .returns(mapEntityIdToId(spenderId), UniqueToken::getSpender)
                .returns(metadata, UniqueToken::getMetadata));
    }

    private Id mapEntityIdToId(EntityId entityId) {
        return new Id(entityId.getShardNum(), entityId.getRealmNum(), entityId.getEntityNum());
    }
}
