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
import static com.hedera.mirror.restjava.dto.TokenAirdropRequest.AirdropRequestType.OUTSTANDING;
import static com.hedera.mirror.restjava.dto.TokenAirdropRequest.AirdropRequestType.PENDING;
import static com.hedera.mirror.restjava.jooq.domain.tables.TokenAirdrop.TOKEN_AIRDROP;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.restjava.RestJavaIntegrationTest;
import com.hedera.mirror.restjava.common.Constants;
import com.hedera.mirror.restjava.common.EntityIdNumParameter;
import com.hedera.mirror.restjava.common.EntityIdRangeParameter;
import com.hedera.mirror.restjava.common.RangeOperator;
import com.hedera.mirror.restjava.dto.TokenAirdropRequest;
import com.hedera.mirror.restjava.dto.TokenAirdropRequest.AirdropRequestType;
import com.hedera.mirror.restjava.service.Bound;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
        assertThat(repository.findAll(request, entityId)).contains(tokenAirdrop);
    }

    @Test
    void findByReceiverId() {
        var tokenAirdrop = domainBuilder.tokenAirdrop(FUNGIBLE_COMMON).persist();
        var entityId = EntityId.of(tokenAirdrop.getReceiverAccountId());
        var request = TokenAirdropRequest.builder()
                .accountId(new EntityIdNumParameter(entityId))
                .type(PENDING)
                .build();
        assertThat(repository.findAll(request, entityId)).contains(tokenAirdrop);
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
                        Constants.ACCOUNT_ID,
                        TOKEN_AIRDROP.RECEIVER_ACCOUNT_ID))
                .build();
        assertThat(repository.findAll(request, entityId)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("provideArguments")
    void conditionalClausesByDirection(Direction order, AirdropRequestType type) {
        // Setup
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
        var senderTokenSpecifiedAirdrop = domainBuilder
                .tokenAirdrop(FUNGIBLE_COMMON)
                .customize(a ->
                        a.senderAccountId(sender.getId()).receiverAccountId(1).tokenId(tokenId))
                .persist();
        var receiverTokenSpecifiedAirdrop = domainBuilder
                .tokenAirdrop(FUNGIBLE_COMMON)
                .customize(a ->
                        a.senderAccountId(1).receiverAccountId(receiver.getId()).tokenId(tokenId))
                .persist();
        domainBuilder
                .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                .customize(a -> a.senderAccountId(sender.getId())
                        .receiverAccountId(receiver.getId())
                        .serialNumber(5)
                        .tokenId(tokenId))
                .persist();

        // Default asc ordering by receiver, tokenId for outstanding airdrops
        var allOutstandingAirdrops = List.of(
                senderTokenSpecifiedAirdrop,
                receiverSpecifiedAirdrop,
                receiverSpecifiedAirdrop2,
                tokenReceiverSpecifiedAirdrop,
                tokenReceiverSpecifiedAirdrop2);
        // Default asc ordering by sender, tokenId for pending airdrops
        var allPendingAirdrops = List.of(
                receiverTokenSpecifiedAirdrop,
                receiverSpecifiedAirdrop,
                receiverSpecifiedAirdrop2,
                tokenReceiverSpecifiedAirdrop,
                tokenReceiverSpecifiedAirdrop2);
        var outstandingReceiverSpecifiedAirdrops = List.of(
                receiverSpecifiedAirdrop,
                receiverSpecifiedAirdrop2,
                tokenReceiverSpecifiedAirdrop,
                tokenReceiverSpecifiedAirdrop2);
        var pendingSenderSpecifiedAirdrops = List.of(
                receiverSpecifiedAirdrop,
                receiverSpecifiedAirdrop2,
                tokenReceiverSpecifiedAirdrop,
                tokenReceiverSpecifiedAirdrop2);

        var tokenReceiverSpecifiedAirdrops = List.of(tokenReceiverSpecifiedAirdrop, tokenReceiverSpecifiedAirdrop2);
        var outstandingTokenSpecifiedAirdrops =
                List.of(senderTokenSpecifiedAirdrop, tokenReceiverSpecifiedAirdrop, tokenReceiverSpecifiedAirdrop2);
        var pendingTokenSpecifiedAirdrops =
                List.of(receiverTokenSpecifiedAirdrop, tokenReceiverSpecifiedAirdrop, tokenReceiverSpecifiedAirdrop2);

        var accountEntityId = type == OUTSTANDING ? sender.toEntityId() : receiver.toEntityId();
        var accountId = new EntityIdNumParameter(accountEntityId);
        var entity = type == OUTSTANDING ? receiver : sender;
        var entityIds = new Bound(
                List.of(new EntityIdRangeParameter(RangeOperator.EQ, EntityId.of(entity.getId()))),
                true,
                Constants.ACCOUNT_ID,
                type == OUTSTANDING ? TOKEN_AIRDROP.RECEIVER_ACCOUNT_ID : TOKEN_AIRDROP.SENDER_ACCOUNT_ID);
        var tokenIds = new Bound(
                List.of(new EntityIdRangeParameter(RangeOperator.GTE, EntityId.of(tokenId))),
                true,
                Constants.TOKEN_ID,
                TOKEN_AIRDROP.TOKEN_ID);

        var expectedResult = type == OUTSTANDING ? allOutstandingAirdrops : allPendingAirdrops;
        expectedResult = order.isAscending() ? expectedResult : expectedResult.reversed();

        // When
        var request = TokenAirdropRequest.builder()
                .accountId(accountId)
                .order(order)
                .type(type)
                .build();
        // Then
        assertThat(repository.findAll(request, accountEntityId)).containsExactlyElementsOf(expectedResult);

        // When receiver id condition for Outstanding Airdrops
        //   or sender id condition for Pending Airdrops
        expectedResult = type == OUTSTANDING ? outstandingReceiverSpecifiedAirdrops : pendingSenderSpecifiedAirdrops;
        expectedResult = order.isAscending() ? expectedResult : expectedResult.reversed();
        request = TokenAirdropRequest.builder()
                .accountId(accountId)
                .order(order)
                .entityIds(entityIds)
                .type(type)
                .build();
        // Then
        assertThat(repository.findAll(request, accountEntityId)).containsExactlyElementsOf(expectedResult);

        // When token id and receiver or sender condition
        expectedResult =
                order.isAscending() ? tokenReceiverSpecifiedAirdrops : tokenReceiverSpecifiedAirdrops.reversed();
        request = TokenAirdropRequest.builder()
                .accountId(accountId)
                .entityIds(entityIds)
                .order(order)
                .tokenIds(tokenIds)
                .type(type)
                .build();
        // Then
        assertThat(repository.findAll(request, accountEntityId)).containsExactlyElementsOf(expectedResult);

        // When token id condition as primary sort field and with receiver id
        request = TokenAirdropRequest.builder()
                .accountId(accountId)
                .order(order)
                .entityIds(entityIds)
                .tokenIds(tokenIds)
                .type(type)
                .build();
        // Then
        assertThat(repository.findAll(request, accountEntityId)).containsExactlyElementsOf(expectedResult);

        // When token id condition but no receiver id for outstanding airdrops nor receiver id for pending airdrops
        expectedResult = type == OUTSTANDING ? outstandingTokenSpecifiedAirdrops : pendingTokenSpecifiedAirdrops;
        expectedResult = order.isAscending() ? expectedResult : expectedResult.reversed();
        request = TokenAirdropRequest.builder()
                .accountId(accountId)
                .order(order)
                .tokenIds(tokenIds)
                .type(type)
                .build();
        // Then
        assertThat(repository.findAll(request, accountEntityId)).containsExactlyElementsOf(expectedResult);
    }

    private static Stream<Arguments> provideArguments() {
        return Stream.of(
                Arguments.of(Direction.ASC, OUTSTANDING),
                Arguments.of(Direction.DESC, OUTSTANDING),
                Arguments.of(Direction.ASC, PENDING),
                Arguments.of(Direction.DESC, PENDING));
    }
}
