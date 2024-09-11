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

package com.hedera.mirror.restjava.repository;

import static com.hedera.mirror.common.domain.token.TokenTypeEnum.FUNGIBLE_COMMON;
import static com.hedera.mirror.common.domain.token.TokenTypeEnum.NON_FUNGIBLE_UNIQUE;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.restjava.RestJavaIntegrationTest;
import com.hedera.mirror.restjava.common.EntityIdNumParameter;
import com.hedera.mirror.restjava.common.EntityIdRangeParameter;
import com.hedera.mirror.restjava.common.NumberRangeParameter;
import com.hedera.mirror.restjava.common.RangeOperator;
import com.hedera.mirror.restjava.dto.TokenAirdropRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.domain.Sort.Direction;

@RequiredArgsConstructor
class TokenAirdropRepositoryTest extends RestJavaIntegrationTest {

    private final TokenAirdropRepository repository;

    @Test
    void findById() {
        var tokenAirdrop = domainBuilder.tokenAirdrop(FUNGIBLE_COMMON).persist();
        assertThat(repository.findById(tokenAirdrop.getId())).get().isEqualTo(tokenAirdrop);
    }

    @Test
    void findBySenderId() {
        var tokenAirdrop = domainBuilder.tokenAirdrop(FUNGIBLE_COMMON).persist();
        var entityId = EntityId.of(tokenAirdrop.getSenderId());
        var request = TokenAirdropRequest.builder()
                .accountId(new EntityIdNumParameter(entityId))
                .build();
        assertThat(repository.findAllOutstanding(request, entityId)).contains(tokenAirdrop);
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findBySenderIdOrder(Direction order) {
        var tokenAirdrop = domainBuilder
                .tokenAirdrop(FUNGIBLE_COMMON)
                .customize(a -> a.timestampRange(Range.atLeast(1000L)))
                .persist();
        var tokenAirdrop2 = domainBuilder
                .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                .customize(a -> a.senderId(tokenAirdrop.getSenderId()).timestampRange(Range.atLeast(2000L)))
                .persist();
        var entityId = EntityId.of(tokenAirdrop.getSenderId());
        var outstandingTokenAirdropRequest = TokenAirdropRequest.builder()
                .accountId(new EntityIdNumParameter(entityId))
                .order(order)
                .build();

        var expected =
                order.isAscending() ? List.of(tokenAirdrop, tokenAirdrop2) : List.of(tokenAirdrop2, tokenAirdrop);
        assertThat(repository.findAllOutstanding(outstandingTokenAirdropRequest, entityId))
                .containsExactlyElementsOf(expected);
    }

    @Test
    void noMatch() {
        var tokenAirdrop = domainBuilder.tokenAirdrop(FUNGIBLE_COMMON).persist();
        var entityId = EntityId.of(tokenAirdrop.getSenderId());
        var request = TokenAirdropRequest.builder()
                .accountId(new EntityIdNumParameter(entityId))
                .entityId(new EntityIdRangeParameter(RangeOperator.GT, EntityId.of(tokenAirdrop.getReceiverId())))
                .build();
        assertThat(repository.findAllOutstanding(request, entityId)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void conditionalClauses(Direction order) {
        var sender = domainBuilder.entity().get();
        var receiver = domainBuilder.entity().get();
        var token = domainBuilder.token().get();
        var serialNumber = 5L;

        var tokenAirdrop = domainBuilder
                .tokenAirdrop(FUNGIBLE_COMMON)
                .customize(a -> a.senderId(sender.getId()).receiverId(receiver.getId()))
                .persist();
        var tokenAirdrop2 = domainBuilder
                .tokenAirdrop(FUNGIBLE_COMMON)
                .customize(a -> a.senderId(sender.getId()).tokenId(token.getTokenId()))
                .persist();
        var nftAirdrop = domainBuilder
                .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                .customize(a -> a.senderId(sender.getId())
                        .receiverId(receiver.getId())
                        .serialNumber(serialNumber)
                        .tokenId(token.getTokenId()))
                .persist();
        var nftAirdrop2 = domainBuilder
                .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                .customize(a -> a.senderId(sender.getId()).serialNumber(serialNumber))
                .persist();

        // Default asc ordering by receiver, tokenId
        var allAirdrops = List.of(nftAirdrop, tokenAirdrop, tokenAirdrop2, nftAirdrop2);
        var receiverSpecifiedAirdrops = List.of(nftAirdrop, tokenAirdrop);
        var tokenSpecifiedAirdrops = List.of(nftAirdrop, tokenAirdrop2);
        var serialNumberAirdrops = List.of(nftAirdrop, nftAirdrop2);

        var orderedAirdrops = order.isAscending() ? allAirdrops : allAirdrops.reversed();
        var request = TokenAirdropRequest.builder()
                .accountId(new EntityIdNumParameter(sender.toEntityId()))
                .order(order)
                .build();
        assertThat(repository.findAllOutstanding(request, sender.toEntityId()))
                .containsExactlyElementsOf(orderedAirdrops);

        // With token id condition
        var tokenIdAirdrops = order.isAscending() ? tokenSpecifiedAirdrops : tokenSpecifiedAirdrops.reversed();
        request = TokenAirdropRequest.builder()
                .accountId(new EntityIdNumParameter(sender.toEntityId()))
                .order(order)
                .tokenId(new EntityIdRangeParameter(RangeOperator.EQ, EntityId.of(token.getTokenId())))
                .build();
        assertThat(repository.findAllOutstanding(request, sender.toEntityId()))
                .containsExactlyElementsOf(tokenIdAirdrops);

        // With receiver id condition
        var receiverIdAirdrops = order.isAscending() ? receiverSpecifiedAirdrops : receiverSpecifiedAirdrops.reversed();
        request = TokenAirdropRequest.builder()
                .accountId(new EntityIdNumParameter(sender.toEntityId()))
                .order(order)
                .entityId(new EntityIdRangeParameter(RangeOperator.EQ, EntityId.of(receiver.getId())))
                .build();
        assertThat(repository.findAllOutstanding(request, sender.toEntityId()))
                .containsExactlyElementsOf(receiverIdAirdrops);

        // With serial number condition
        var serialNumberAirdropsOrdered = order.isAscending() ? serialNumberAirdrops : serialNumberAirdrops.reversed();
        request = TokenAirdropRequest.builder()
                .accountId(new EntityIdNumParameter(sender.toEntityId()))
                .order(order)
                .serialNumber(new NumberRangeParameter(RangeOperator.EQ, serialNumber))
                .build();
        assertThat(repository.findAllOutstanding(request, sender.toEntityId()))
                .containsExactlyElementsOf(serialNumberAirdropsOrdered);
    }

    @Test
    void serialNumber() {
        var sender = 1000;
        var receiver = 3000;
        var receiver2 = 4000;
        var tokenId = 5000;
        var nftTokenId = 6000;
        var nftTokenId2 = 7000;
        var serialNumber = 5;
        var serialNumber2 = 10;

        var airdrop1 = domainBuilder
                .tokenAirdrop(FUNGIBLE_COMMON)
                .customize(a -> a.senderId(sender).receiverId(receiver).tokenId(tokenId))
                .persist();
        var serialAirdrop1 = domainBuilder
                .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                .customize(a -> a.senderId(sender)
                        .receiverId(receiver)
                        .serialNumber(serialNumber)
                        .tokenId(nftTokenId))
                .persist();
        var serialAirdrop2 = domainBuilder
                .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                .customize(a -> a.senderId(sender)
                        .receiverId(receiver)
                        .serialNumber(serialNumber2)
                        .tokenId(nftTokenId))
                .persist();
        var serialAirdrop3 = domainBuilder
                .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                .customize(a -> a.senderId(sender)
                        .receiverId(receiver2)
                        .serialNumber(serialNumber)
                        .tokenId(nftTokenId2))
                .persist();

        var expectedAirdrops = List.of(serialAirdrop1, serialAirdrop2, serialAirdrop3);
        var request = TokenAirdropRequest.builder()
                .accountId(new EntityIdNumParameter(EntityId.of(sender)))
                .serialNumber(new NumberRangeParameter(RangeOperator.GT, 4L))
                .build();
        assertThat(repository.findAllOutstanding(request, EntityId.of(sender)))
                .containsExactlyElementsOf(expectedAirdrops);

        expectedAirdrops = List.of(serialAirdrop1, serialAirdrop3);
        request = TokenAirdropRequest.builder()
                .accountId(new EntityIdNumParameter(EntityId.of(sender)))
                .serialNumber(new NumberRangeParameter(RangeOperator.LT, 10L))
                .build();
        assertThat(repository.findAllOutstanding(request, EntityId.of(sender)))
                .containsExactlyElementsOf(expectedAirdrops);

        expectedAirdrops = List.of(serialAirdrop1, serialAirdrop2);
        request = TokenAirdropRequest.builder()
                .accountId(new EntityIdNumParameter(EntityId.of(sender)))
                .tokenId(new EntityIdRangeParameter(RangeOperator.EQ, EntityId.of(nftTokenId)))
                .build();
        assertThat(repository.findAllOutstanding(request, EntityId.of(sender)))
                .containsExactlyElementsOf(expectedAirdrops);

        expectedAirdrops = List.of(serialAirdrop1);
        request = TokenAirdropRequest.builder()
                .accountId(new EntityIdNumParameter(EntityId.of(sender)))
                .tokenId(new EntityIdRangeParameter(RangeOperator.EQ, EntityId.of(nftTokenId)))
                .serialNumber(new NumberRangeParameter(RangeOperator.LTE, 5L))
                .build();
        assertThat(repository.findAllOutstanding(request, EntityId.of(sender)))
                .containsExactlyElementsOf(expectedAirdrops);

        expectedAirdrops = List.of(serialAirdrop2);
        request = TokenAirdropRequest.builder()
                .accountId(new EntityIdNumParameter(EntityId.of(sender)))
                .tokenId(new EntityIdRangeParameter(RangeOperator.EQ, EntityId.of(nftTokenId)))
                .serialNumber(new NumberRangeParameter(RangeOperator.GTE, 10L))
                .build();
        assertThat(repository.findAllOutstanding(request, EntityId.of(sender)))
                .containsExactlyElementsOf(expectedAirdrops);

        expectedAirdrops = List.of(airdrop1, serialAirdrop1, serialAirdrop2);
        request = TokenAirdropRequest.builder()
                .accountId(new EntityIdNumParameter(EntityId.of(sender)))
                .entityId(new EntityIdRangeParameter(RangeOperator.EQ, EntityId.of(receiver)))
                .build();
        assertThat(repository.findAllOutstanding(request, EntityId.of(sender)))
                .containsExactlyElementsOf(expectedAirdrops);

        expectedAirdrops = List.of(serialAirdrop1);
        request = TokenAirdropRequest.builder()
                .accountId(new EntityIdNumParameter(EntityId.of(sender)))
                .entityId(new EntityIdRangeParameter(RangeOperator.EQ, EntityId.of(receiver)))
                .serialNumber(new NumberRangeParameter(RangeOperator.LTE, 5L))
                .build();
        assertThat(repository.findAllOutstanding(request, EntityId.of(sender)))
                .containsExactlyElementsOf(expectedAirdrops);
    }
}
