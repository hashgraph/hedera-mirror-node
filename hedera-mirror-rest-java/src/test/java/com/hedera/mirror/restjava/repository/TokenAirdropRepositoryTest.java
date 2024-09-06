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
import com.hedera.mirror.restjava.common.IntegerRangeParameter;
import com.hedera.mirror.restjava.common.RangeOperator;
import com.hedera.mirror.restjava.dto.OutstandingTokenAirdropRequest;
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
        var entityId = EntityId.of(tokenAirdrop.getSenderAccountId());
        var request = OutstandingTokenAirdropRequest.builder()
                .senderId(new EntityIdNumParameter(entityId))
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
                .customize(a ->
                        a.senderAccountId(tokenAirdrop.getSenderAccountId()).timestampRange(Range.atLeast(2000L)))
                .persist();
        var entityId = EntityId.of(tokenAirdrop.getSenderAccountId());
        var outstandingTokenAirdropRequest = OutstandingTokenAirdropRequest.builder()
                .senderId(new EntityIdNumParameter(entityId))
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
        var entityId = EntityId.of(tokenAirdrop.getSenderAccountId());
        var request = OutstandingTokenAirdropRequest.builder()
                .senderId(new EntityIdNumParameter(entityId))
                .receiverId(
                        new EntityIdRangeParameter(RangeOperator.GT, EntityId.of(tokenAirdrop.getReceiverAccountId())))
                .build();
        assertThat(repository.findAllOutstanding(request, entityId)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void conditionalClauses(Direction order) {
        var sender = domainBuilder.entity().get();
        var receiver = domainBuilder.entity().get();
        var token = domainBuilder.token().get();
        var serialNumber = 5;

        var tokenAirdrop = domainBuilder
                .tokenAirdrop(FUNGIBLE_COMMON)
                .customize(a -> a.senderAccountId(sender.getId()).receiverAccountId(receiver.getId()))
                .persist();
        var tokenAirdrop2 = domainBuilder
                .tokenAirdrop(FUNGIBLE_COMMON)
                .customize(a -> a.senderAccountId(sender.getId()).tokenId(token.getTokenId()))
                .persist();
        var nftAirdrop = domainBuilder
                .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                .customize(a -> a.senderAccountId(sender.getId())
                        .receiverAccountId(receiver.getId())
                        .serialNumber(serialNumber)
                        .tokenId(token.getTokenId()))
                .persist();
        var nftAirdrop2 = domainBuilder
                .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                .customize(a -> a.senderAccountId(sender.getId()).serialNumber(serialNumber))
                .persist();

        // Default asc ordering by receiver, tokenId
        var allAirdrops = List.of(nftAirdrop, tokenAirdrop, tokenAirdrop2, nftAirdrop2);
        var receiverSpecifiedAirdrops = List.of(nftAirdrop, tokenAirdrop);
        var tokenSpecifiedAirdrops = List.of(nftAirdrop, tokenAirdrop2);
        var serialNumberAirdrops = List.of(nftAirdrop, nftAirdrop2);

        var orderedAirdrops = order.isAscending() ? allAirdrops : allAirdrops.reversed();
        var request = OutstandingTokenAirdropRequest.builder()
                .senderId(new EntityIdNumParameter(sender.toEntityId()))
                .order(order)
                .build();
        assertThat(repository.findAllOutstanding(request, sender.toEntityId()))
                .containsExactlyElementsOf(orderedAirdrops);

        // With token id condition
        var tokenIdAirdrops = order.isAscending() ? tokenSpecifiedAirdrops : tokenSpecifiedAirdrops.reversed();
        request = OutstandingTokenAirdropRequest.builder()
                .senderId(new EntityIdNumParameter(sender.toEntityId()))
                .order(order)
                .tokenId(new EntityIdRangeParameter(RangeOperator.EQ, EntityId.of(token.getTokenId())))
                .build();
        assertThat(repository.findAllOutstanding(request, sender.toEntityId()))
                .containsExactlyElementsOf(tokenIdAirdrops);

        // With receiver id condition
        var receiverIdAirdrops = order.isAscending() ? receiverSpecifiedAirdrops : receiverSpecifiedAirdrops.reversed();
        request = OutstandingTokenAirdropRequest.builder()
                .senderId(new EntityIdNumParameter(sender.toEntityId()))
                .order(order)
                .receiverId(new EntityIdRangeParameter(RangeOperator.EQ, EntityId.of(receiver.getId())))
                .build();
        assertThat(repository.findAllOutstanding(request, sender.toEntityId()))
                .containsExactlyElementsOf(receiverIdAirdrops);

        // With serial number condition
        var serialNumberAirdropsOrdered = order.isAscending() ? serialNumberAirdrops : serialNumberAirdrops.reversed();
        request = OutstandingTokenAirdropRequest.builder()
                .senderId(new EntityIdNumParameter(sender.toEntityId()))
                .order(order)
                .serialNumber(new IntegerRangeParameter(RangeOperator.EQ, serialNumber))
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
                .customize(a ->
                        a.senderAccountId(sender).receiverAccountId(receiver).tokenId(tokenId))
                .persist();
        var serialAirdrop1 = domainBuilder
                .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                .customize(a -> a.senderAccountId(sender)
                        .receiverAccountId(receiver)
                        .serialNumber(serialNumber)
                        .tokenId(nftTokenId))
                .persist();
        var serialAirdrop2 = domainBuilder
                .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                .customize(a -> a.senderAccountId(sender)
                        .receiverAccountId(receiver)
                        .serialNumber(serialNumber2)
                        .tokenId(nftTokenId))
                .persist();
        var serialAirdrop3 = domainBuilder
                .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                .customize(a -> a.senderAccountId(sender)
                        .receiverAccountId(receiver2)
                        .serialNumber(serialNumber)
                        .tokenId(nftTokenId2))
                .persist();

        // serialNumber gt 4 -> serialAirdrop1, serialAirdrop2, serialAirdrop3
        var expectedAirdrops = List.of(serialAirdrop1, serialAirdrop2, serialAirdrop3);
        var request = OutstandingTokenAirdropRequest.builder()
                .senderId(new EntityIdNumParameter(EntityId.of(sender)))
                .serialNumber(new IntegerRangeParameter(RangeOperator.GT, 4))
                .build();
        assertThat(repository.findAllOutstanding(request, EntityId.of(sender)))
                .containsExactlyElementsOf(expectedAirdrops);

        // serialNumber lt 10 -> serialAirdrop1, serialAirdrop3
        expectedAirdrops = List.of(serialAirdrop1, serialAirdrop3);
        request = OutstandingTokenAirdropRequest.builder()
                .senderId(new EntityIdNumParameter(EntityId.of(sender)))
                .serialNumber(new IntegerRangeParameter(RangeOperator.LT, 10))
                .build();
        assertThat(repository.findAllOutstanding(request, EntityId.of(sender)))
                .containsExactlyElementsOf(expectedAirdrops);

        // tokenId eq nftTokenId -> serialAirdrop1, serialAirdrop2
        expectedAirdrops = List.of(serialAirdrop1, serialAirdrop2);
        request = OutstandingTokenAirdropRequest.builder()
                .senderId(new EntityIdNumParameter(EntityId.of(sender)))
                .tokenId(new EntityIdRangeParameter(RangeOperator.EQ, EntityId.of(nftTokenId)))
                .build();
        assertThat(repository.findAllOutstanding(request, EntityId.of(sender)))
                .containsExactlyElementsOf(expectedAirdrops);

        // tokenId eq nftTokenId && serialNumber lte 5 -> serialAirdrop1
        expectedAirdrops = List.of(serialAirdrop1);
        request = OutstandingTokenAirdropRequest.builder()
                .senderId(new EntityIdNumParameter(EntityId.of(sender)))
                .tokenId(new EntityIdRangeParameter(RangeOperator.EQ, EntityId.of(nftTokenId)))
                .serialNumber(new IntegerRangeParameter(RangeOperator.LTE, 5))
                .build();
        assertThat(repository.findAllOutstanding(request, EntityId.of(sender)))
                .containsExactlyElementsOf(expectedAirdrops);

        // tokenId eq nftTokenId && serialNumber gte 10 -> serialAirdrop2
        expectedAirdrops = List.of(serialAirdrop2);
        request = OutstandingTokenAirdropRequest.builder()
                .senderId(new EntityIdNumParameter(EntityId.of(sender)))
                .tokenId(new EntityIdRangeParameter(RangeOperator.EQ, EntityId.of(nftTokenId)))
                .serialNumber(new IntegerRangeParameter(RangeOperator.GTE, 10))
                .build();
        assertThat(repository.findAllOutstanding(request, EntityId.of(sender)))
                .containsExactlyElementsOf(expectedAirdrops);

        // receiver eq 3000 -> airdrop1, serialAirdrop1, serialAirdrop2
        expectedAirdrops = List.of(airdrop1, serialAirdrop1, serialAirdrop2);
        request = OutstandingTokenAirdropRequest.builder()
                .senderId(new EntityIdNumParameter(EntityId.of(sender)))
                .receiverId(new EntityIdRangeParameter(RangeOperator.EQ, EntityId.of(receiver)))
                .build();
        assertThat(repository.findAllOutstanding(request, EntityId.of(sender)))
                .containsExactlyElementsOf(expectedAirdrops);

        // receiver eq 3000 && serialNumber lte 5 -> serialAirdrop1
        expectedAirdrops = List.of(serialAirdrop1);
        request = OutstandingTokenAirdropRequest.builder()
                .senderId(new EntityIdNumParameter(EntityId.of(sender)))
                .receiverId(new EntityIdRangeParameter(RangeOperator.EQ, EntityId.of(receiver)))
                .serialNumber(new IntegerRangeParameter(RangeOperator.LTE, 5))
                .build();
        assertThat(repository.findAllOutstanding(request, EntityId.of(sender)))
                .containsExactlyElementsOf(expectedAirdrops);
    }
}
