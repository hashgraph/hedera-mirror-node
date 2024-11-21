/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.state;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.repository.NftRepository;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.utils.EntityIdUtils;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NftReadableKVStateTest {

    private static final TokenID TOKEN_ID =
            TokenID.newBuilder().shardNum(0L).realmNum(0L).tokenNum(1252L).build();
    private static final Optional<Long> timestamp = Optional.of(1726231985623004672L);
    private static final EntityId spender = EntityId.of(1L, 2L, 3L);
    private static final NftID NFT_ID = new NftID(TOKEN_ID, 1L);
    private static MockedStatic<ContractCallContext> contextMockedStatic;

    @InjectMocks
    private NftReadableKVState nftReadableKVState;

    @Mock
    private NftRepository nftRepository;

    private DomainBuilder domainBuilder;
    private Entity entity;

    @Spy
    private ContractCallContext contractCallContext;

    @BeforeAll
    static void initStaticMocks() {
        contextMockedStatic = mockStatic(ContractCallContext.class);
    }

    @AfterAll
    static void closeStaticMocks() {
        contextMockedStatic.close();
    }

    @BeforeEach
    void setup() {
        domainBuilder = new DomainBuilder();
        entity = domainBuilder
                .entity()
                .customize(e -> e.id(TOKEN_ID.tokenNum()).type(EntityType.TOKEN).createdTimestamp(timestamp.get()))
                .get();
        contextMockedStatic.when(ContractCallContext::get).thenReturn(contractCallContext);
    }

    @Test
    void getNftMappedValuesWithTimestamp() {
        when(ContractCallContext.getTimestamp()).thenReturn(timestamp);
        Nft nftDomain = setupNft(timestamp);
        assertThat(nftReadableKVState.readFromDataSource(NFT_ID)).satisfies(nft -> assertThat(nft)
                .returns(NFT_ID, com.hedera.hapi.node.state.token.Nft::nftId)
                .returns(
                        EntityIdUtils.toAccountId(nftDomain.getAccountId()),
                        com.hedera.hapi.node.state.token.Nft::ownerId)
                .returns(
                        EntityIdUtils.toAccountId(nftDomain.getSpender()),
                        com.hedera.hapi.node.state.token.Nft::spenderId)
                .returns(
                        convertToTimestamp(nftDomain.getCreatedTimestamp()),
                        com.hedera.hapi.node.state.token.Nft::mintTime)
                .returns(Bytes.wrap(nftDomain.getMetadata()), com.hedera.hapi.node.state.token.Nft::metadata)
                .returns(null, com.hedera.hapi.node.state.token.Nft::ownerPreviousNftId)
                .returns(null, com.hedera.hapi.node.state.token.Nft::ownerNextNftId));
    }

    @Test
    void getNftMappedValuesWithoutTimestamp() {
        when(ContractCallContext.getTimestamp()).thenReturn(Optional.empty());
        Nft nftDomain = setupNft(Optional.empty());
        assertThat(nftReadableKVState.readFromDataSource(NFT_ID)).satisfies(nft -> assertThat(nft)
                .returns(NFT_ID, com.hedera.hapi.node.state.token.Nft::nftId)
                .returns(
                        EntityIdUtils.toAccountId(nftDomain.getAccountId()),
                        com.hedera.hapi.node.state.token.Nft::ownerId)
                .returns(
                        EntityIdUtils.toAccountId(nftDomain.getSpender()),
                        com.hedera.hapi.node.state.token.Nft::spenderId)
                .returns(
                        convertToTimestamp(nftDomain.getCreatedTimestamp()),
                        com.hedera.hapi.node.state.token.Nft::mintTime)
                .returns(Bytes.wrap(nftDomain.getMetadata()), com.hedera.hapi.node.state.token.Nft::metadata)
                .returns(null, com.hedera.hapi.node.state.token.Nft::ownerPreviousNftId)
                .returns(null, com.hedera.hapi.node.state.token.Nft::ownerNextNftId));
    }

    @Test
    void getNftMappedValuesEmptyTokenId() {
        NftID nftId = new NftID(null, NFT_ID.serialNumber());
        assertThat(nftReadableKVState.readFromDataSource(nftId)).isNull();
    }

    @Test
    void getNftMappedValuesMissingEntity() {
        when(ContractCallContext.getTimestamp()).thenReturn(Optional.empty());
        assertThat(nftReadableKVState.readFromDataSource(NFT_ID)).isNull();
    }

    @Test
    void testIterateFromDataSource() {
        assertThat(nftReadableKVState.iterateFromDataSource()).isEqualTo(Collections.emptyIterator());
    }

    @Test
    void testSize() {
        assertThat(nftReadableKVState.size()).isZero();
    }

    private Nft setupNft(Optional<Long> timestamp) {
        Nft databaseNft = domainBuilder
                .nft()
                .customize(t -> t.tokenId(entity.getId())
                        .serialNumber(NFT_ID.serialNumber())
                        .spender(spender))
                .get();

        if (timestamp.isPresent()) {
            databaseNft.setCreatedTimestamp(timestamp.get());
            when(nftRepository.findActiveByIdAndTimestamp(entity.getId(), NFT_ID.serialNumber(), timestamp.get()))
                    .thenReturn(Optional.of(databaseNft));
        } else {
            when(nftRepository.findActiveById(entity.getId(), NFT_ID.serialNumber()))
                    .thenReturn(Optional.ofNullable(databaseNft));
        }
        return databaseNft;
    }

    private Timestamp convertToTimestamp(long timestamp) {
        var instant = Instant.ofEpochMilli(timestamp);
        return new Timestamp(instant.getEpochSecond(), instant.getNano());
    }
}
