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

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.restjava.RestJavaIntegrationTest;
import com.hedera.mirror.restjava.common.Constants;
import com.hedera.mirror.restjava.common.EntityIdNumParameter;
import com.hedera.mirror.restjava.common.EntityIdRangeParameter;
import com.hedera.mirror.restjava.common.RangeOperator;
import com.hedera.mirror.restjava.dto.TokenAirdropRequest;
import com.hedera.mirror.restjava.service.Bound;
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
        var request = TokenAirdropRequest.builder()
                .accountId(new EntityIdNumParameter(entityId))
                .build();
        assertThat(repository.findAllOutstanding(request, entityId)).contains(tokenAirdrop);
    }

    @Test
    void noMatch() {
        var tokenAirdrop = domainBuilder.tokenAirdrop(FUNGIBLE_COMMON).persist();
        var entityId = EntityId.of(tokenAirdrop.getSenderAccountId());
        var request = TokenAirdropRequest.builder()
                .accountId(new EntityIdNumParameter(entityId))
                .entityIds(new Bound(
                        List.of(new EntityIdRangeParameter(
                                RangeOperator.GT, EntityId.of(tokenAirdrop.getReceiverAccountId()))),
                        true,
                        Constants.ACCOUNT_ID))
                .build();
        assertThat(repository.findAllOutstanding(request, entityId)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void conditionalClausesByDirection(Direction order) {
        var sender = domainBuilder.entity().get();
        var receiver = domainBuilder.entity().get();
        var tokenId = 5000L;

        var receiverSpecifiedAirdrop = domainBuilder
                .tokenAirdrop(FUNGIBLE_COMMON)
                .customize(a -> a.senderAccountId(sender.getId()).receiverAccountId(receiver.getId()))
                .persist();
        var receiverSpecifiedAirdrop2 = domainBuilder
                .tokenAirdrop(FUNGIBLE_COMMON)
                .customize(a -> a.senderAccountId(sender.getId()).receiverAccountId(receiver.getId()))
                .persist();
        var tokenReceiverSpecifiedAirdrop = domainBuilder
                .tokenAirdrop(FUNGIBLE_COMMON)
                .customize(a -> a.senderAccountId(sender.getId())
                        .receiverAccountId(receiver.getId())
                        .tokenId(tokenId))
                .persist();
        var tokenReceiverSpecifiedAirdrop2 = domainBuilder
                .tokenAirdrop(FUNGIBLE_COMMON)
                .customize(a -> a.senderAccountId(sender.getId())
                        .receiverAccountId(receiver.getId())
                        .tokenId(tokenId + 1))
                .persist();
        var tokenSpecifiedAirdrop = domainBuilder
                .tokenAirdrop(FUNGIBLE_COMMON)
                .customize(a ->
                        a.senderAccountId(sender.getId()).receiverAccountId(1).tokenId(tokenId))
                .persist();
        domainBuilder
                .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                .customize(a -> a.senderAccountId(sender.getId())
                        .receiverAccountId(receiver.getId())
                        .serialNumber(5)
                        .tokenId(tokenId))
                .persist();

        // Default asc ordering by receiver, tokenId
        var allAirdrops = List.of(
                tokenSpecifiedAirdrop,
                receiverSpecifiedAirdrop,
                receiverSpecifiedAirdrop2,
                tokenReceiverSpecifiedAirdrop,
                tokenReceiverSpecifiedAirdrop2);
        var receiverSpecifiedAirdrops = List.of(
                receiverSpecifiedAirdrop,
                receiverSpecifiedAirdrop2,
                tokenReceiverSpecifiedAirdrop,
                tokenReceiverSpecifiedAirdrop2);
        var tokenReceiverAirdrops = List.of(tokenReceiverSpecifiedAirdrop, tokenReceiverSpecifiedAirdrop2);
        var tokenSpecifiedAirdrops =
                List.of(tokenSpecifiedAirdrop, tokenReceiverSpecifiedAirdrop, tokenReceiverSpecifiedAirdrop2);

        var orderedAirdrops = order.isAscending() ? allAirdrops : allAirdrops.reversed();
        var request = TokenAirdropRequest.builder()
                .accountId(new EntityIdNumParameter(sender.toEntityId()))
                .order(order)
                .build();
        assertThat(repository.findAllOutstanding(request, sender.toEntityId()))
                .containsExactlyElementsOf(orderedAirdrops);

        // With receiver id condition
        var receiverAirdrops = order.isAscending() ? receiverSpecifiedAirdrops : receiverSpecifiedAirdrops.reversed();
        request = TokenAirdropRequest.builder()
                .accountId(new EntityIdNumParameter(sender.toEntityId()))
                .order(order)
                .entityIds(new Bound(
                        List.of(new EntityIdRangeParameter(RangeOperator.EQ, EntityId.of(receiver.getId()))),
                        true,
                        Constants.ACCOUNT_ID))
                .build();
        assertThat(repository.findAllOutstanding(request, sender.toEntityId()))
                .containsExactlyElementsOf(receiverAirdrops);

        // With token id and receiver condition
        var tokenAirdrops = order.isAscending() ? tokenReceiverAirdrops : tokenReceiverAirdrops.reversed();
        request = TokenAirdropRequest.builder()
                .accountId(new EntityIdNumParameter(sender.toEntityId()))
                .entityIds(new Bound(
                        List.of(new EntityIdRangeParameter(RangeOperator.EQ, EntityId.of(receiver.getId()))),
                        true,
                        Constants.ACCOUNT_ID))
                .order(order)
                .tokenIds(new Bound(
                        List.of(new EntityIdRangeParameter(RangeOperator.GTE, EntityId.of(tokenId))),
                        false,
                        Constants.TOKEN_ID))
                .build();
        assertThat(repository.findAllOutstanding(request, sender.toEntityId()))
                .containsExactlyElementsOf(tokenAirdrops);

        // With token id condition as primary sort field and with receiver id
        request = TokenAirdropRequest.builder()
                .accountId(new EntityIdNumParameter(sender.toEntityId()))
                .order(order)
                .entityIds(new Bound(
                        List.of(new EntityIdRangeParameter(RangeOperator.GTE, EntityId.of(receiver.getId()))),
                        false,
                        Constants.ACCOUNT_ID))
                .tokenIds(new Bound(
                        List.of(new EntityIdRangeParameter(RangeOperator.GTE, EntityId.of(tokenId))),
                        true,
                        Constants.TOKEN_ID))
                .build();
        assertThat(repository.findAllOutstanding(request, sender.toEntityId()))
                .containsExactlyElementsOf(tokenAirdrops);

        // With token id condition but no receiver id
        var tokenIdAirdrops = order.isAscending() ? tokenSpecifiedAirdrops : tokenSpecifiedAirdrops.reversed();
        request = TokenAirdropRequest.builder()
                .accountId(new EntityIdNumParameter(sender.toEntityId()))
                .order(order)
                .tokenIds(new Bound(
                        List.of(new EntityIdRangeParameter(RangeOperator.GTE, EntityId.of(tokenId))),
                        false,
                        Constants.TOKEN_ID))
                .build();
        assertThat(repository.findAllOutstanding(request, sender.toEntityId()))
                .containsExactlyElementsOf(tokenIdAirdrops);
    }
}
