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

import static com.hedera.services.utils.EntityIdUtils.idFromEntityId;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.web3.repository.NftRepository;
import com.hedera.services.state.submerkle.RichInstant;
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

    private final DomainBuilder domainBuilder = new DomainBuilder();

    @Test
    void get() {
        int createdTimestampNanos = 13;
        long createdTimestampSecs = 12;

        Nft nft = domainBuilder
                .nft()
                .customize(n -> n.createdTimestamp(createdTimestampSecs * 1_000_000_000 + createdTimestampNanos))
                .get();

        when(nftRepository.findActiveById(
                        nft.getId().getTokenId().getId(), nft.getId().getSerialNumber()))
                .thenReturn(Optional.of(nft));

        assertThat(uniqueTokenDatabaseAccessor.get(nft.getId())).hasValueSatisfying(uniqueToken -> assertThat(
                        uniqueToken)
                .returns(idFromEntityId(nft.getId().getTokenId()), UniqueToken::getTokenId)
                .returns(nft.getId().getSerialNumber(), UniqueToken::getSerialNumber)
                .returns(new RichInstant(createdTimestampSecs, createdTimestampNanos), UniqueToken::getCreationTime)
                .returns(idFromEntityId(nft.getAccountId()), UniqueToken::getOwner)
                .returns(idFromEntityId(nft.getSpender()), UniqueToken::getSpender)
                .returns(nft.getMetadata(), UniqueToken::getMetadata));
    }

    @Test
    void missingRichInstantWhenNoCreatedTimestamp() {
        Nft nft = domainBuilder.nft().customize(n -> n.createdTimestamp(null)).get();

        when(nftRepository.findActiveById(anyLong(), anyLong())).thenReturn(Optional.of(nft));

        assertThat(uniqueTokenDatabaseAccessor.get(nft.getId()))
                .hasValueSatisfying(uniqueToken ->
                        assertThat(uniqueToken.getCreationTime()).isEqualTo(RichInstant.MISSING_INSTANT));
    }
}
