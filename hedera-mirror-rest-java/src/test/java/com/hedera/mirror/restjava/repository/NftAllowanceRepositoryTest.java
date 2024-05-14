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

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.restjava.RestJavaIntegrationTest;
import com.hedera.mirror.restjava.common.EntityIdRangeParameter;
import com.hedera.mirror.restjava.common.RangeOperator;
import com.hedera.mirror.restjava.dto.NftAllowanceDto;
import com.hedera.mirror.restjava.service.NftAllowanceServiceImpl.Bound;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort.Direction;

@RequiredArgsConstructor
class NftAllowanceRepositoryTest extends RestJavaIntegrationTest {

    private final NftAllowanceRepository nftAllowanceRepository;

    private Map<Tuple, NftAllowance> nftAllowances;
    private Map<NftAllowanceDto, List<Tuple>> NftAllowanceDtos;

    private List<Long> owners;
    private List<Long> spenders;
    private List<Long> tokenIdBounds;

    @Test
    void findAll() {

        // given
        setupNftAllowances();
        populateNftRequestMap();

        // when, then
        assertNftAllowances();
    }

    @Test
    void findAllNoMatch() {
        // given
        setupNftAllowances();

        // when, then
        assertThat(nftAllowanceRepository.findAll(NftAllowanceDto.builder()
                        .isOwner(true)
                        .accountId(EntityId.of(owners.get(2) + 1))
                        .ownerOrSpenderIdBounds(new Bound(null, null))
                        .tokenIdBounds(new Bound(null, null))
                        .limit(10)
                        .order(Direction.ASC)
                        .build()))
                .isEmpty();

        // when, then
        assertThat(nftAllowanceRepository.findAll(NftAllowanceDto.builder()
                        .isOwner(true)
                        .accountId(EntityId.of(owners.get(0)))
                        .ownerOrSpenderIdBounds(new Bound(
                                new EntityIdRangeParameter(RangeOperator.EQ, EntityId.of(spenders.get(2) + 1)), null))
                        .tokenIdBounds(new Bound(null, null))
                        .limit(10)
                        .order(Direction.ASC)
                        .build()))
                .isEmpty();

        // when, then
        assertThat(nftAllowanceRepository.findAll(NftAllowanceDto.builder()
                        .isOwner(true)
                        .accountId(EntityId.of(owners.get(0)))
                        .ownerOrSpenderIdBounds(new Bound(
                                new EntityIdRangeParameter(RangeOperator.EQ, EntityId.of(spenders.get(0))), null))
                        .tokenIdBounds(new Bound(
                                new EntityIdRangeParameter(RangeOperator.EQ, EntityId.of(tokenIdBounds.get(2) + 1)),
                                null))
                        .limit(10)
                        .order(Direction.ASC)
                        .build()))
                .isEmpty();

        // when, then
        assertThat(nftAllowanceRepository.findAll(NftAllowanceDto.builder()
                        .isOwner(true)
                        .accountId(EntityId.of(owners.get(0)))
                        .ownerOrSpenderIdBounds(new Bound(
                                new EntityIdRangeParameter(RangeOperator.GT, EntityId.of(spenders.get(2))), null))
                        .tokenIdBounds(new Bound(
                                new EntityIdRangeParameter(RangeOperator.GT, EntityId.of(tokenIdBounds.get(0))), null))
                        .limit(10)
                        .order(Direction.ASC)
                        .build()))
                .isEmpty();

        // when, then
        assertThat(nftAllowanceRepository.findAll(NftAllowanceDto.builder()
                        .isOwner(true)
                        .accountId(EntityId.of(owners.get(0)))
                        .ownerOrSpenderIdBounds(new Bound(
                                new EntityIdRangeParameter(RangeOperator.LT, EntityId.of(spenders.get(0))), null))
                        .tokenIdBounds(new Bound(
                                new EntityIdRangeParameter(RangeOperator.LT, EntityId.of(tokenIdBounds.get(2))), null))
                        .limit(10)
                        .order(Direction.ASC)
                        .build()))
                .isEmpty();
    }

    private void setupNftAllowances() {
        // Set up 3 (owners) x 3 (spenders) x 3 (tokens) NFT allowances
        var entityIds =
                IntStream.range(0, 9).mapToLong(x -> domainBuilder.id()).boxed().toList();
        owners = entityIds.subList(0, 3);
        spenders = entityIds.subList(3, 6);
        tokenIdBounds = entityIds.subList(6, 9);
        nftAllowances = new HashMap<>();

        for (int ownerIndex = 0; ownerIndex < owners.size(); ownerIndex++) {
            long owner = owners.get(ownerIndex);
            for (int spenderIndex = 0; spenderIndex < spenders.size(); spenderIndex++) {
                long spender = spenders.get(spenderIndex);
                for (int tokenIndex = 0; tokenIndex < tokenIdBounds.size(); tokenIndex++) {
                    long tokenId = tokenIdBounds.get(tokenIndex);
                    // true if sum of index is even, otherwise false
                    boolean approvedForAll = (ownerIndex + spenderIndex + tokenIndex) % 2 == 0;
                    var nftAllowance = domainBuilder
                            .nftAllowance()
                            .customize(n -> n.approvedForAll(approvedForAll)
                                    .owner(owner)
                                    .spender(spender)
                                    .tokenId(tokenId))
                            .persist();
                    nftAllowances.put(new Tuple(ownerIndex, spenderIndex, tokenIndex), nftAllowance);
                }
            }
        }
    }

    private void populateNftRequestMap() {
        NftAllowanceDtos = new LinkedHashMap<>();

        NftAllowanceDtos.put(
                NftAllowanceDto.builder()
                        .isOwner(true)
                        .accountId(EntityId.of(owners.get(0)))
                        .ownerOrSpenderIdBounds(new Bound(null, null))
                        .tokenIdBounds(new Bound(null, null))
                        .limit(4)
                        .order(Direction.ASC)
                        .build(),
                List.of(new Tuple(0, 0, 0), new Tuple(0, 0, 1), new Tuple(0, 0, 2), new Tuple(0, 1, 0)));
        NftAllowanceDtos.put(
                NftAllowanceDto.builder()
                        .isOwner(true)
                        .accountId(EntityId.of(owners.get(0)))
                        .ownerOrSpenderIdBounds(new Bound(null, null))
                        .tokenIdBounds(new Bound(null, null))
                        .limit(4)
                        .order(Direction.DESC)
                        .build(),
                List.of(new Tuple(0, 2, 2), new Tuple(0, 2, 1), new Tuple(0, 2, 0), new Tuple(0, 1, 2)));
        NftAllowanceDtos.put(
                NftAllowanceDto.builder()
                        .isOwner(false)
                        .accountId(EntityId.of(spenders.get(1)))
                        .ownerOrSpenderIdBounds(new Bound(null, null))
                        .tokenIdBounds(new Bound(null, null))
                        .limit(4)
                        .order(Direction.ASC)
                        .build(),
                List.of(new Tuple(0, 1, 0), new Tuple(0, 1, 1), new Tuple(0, 1, 2), new Tuple(1, 1, 0)));
        NftAllowanceDtos.put(
                NftAllowanceDto.builder()
                        .isOwner(false)
                        .accountId(EntityId.of(spenders.get(1)))
                        .ownerOrSpenderIdBounds(new Bound(null, null))
                        .tokenIdBounds(new Bound(null, null))
                        .limit(4)
                        .order(Direction.DESC)
                        .build(),
                List.of(new Tuple(2, 1, 2), new Tuple(2, 1, 1), new Tuple(2, 1, 0), new Tuple(1, 1, 2)));
        NftAllowanceDtos.put(
                NftAllowanceDto.builder()
                        .isOwner(true)
                        .accountId(EntityId.of(owners.get(1)))
                        .ownerOrSpenderIdBounds(new Bound(
                                new EntityIdRangeParameter(RangeOperator.EQ, EntityId.of(spenders.get(0))), null))
                        .tokenIdBounds(new Bound(null, null))
                        .limit(4)
                        .order(Direction.ASC)
                        .build(),
                List.of(new Tuple(1, 0, 0), new Tuple(1, 0, 1), new Tuple(1, 0, 2)));
        NftAllowanceDtos.put(
                NftAllowanceDto.builder()
                        .isOwner(true)
                        .accountId(EntityId.of(owners.get(1)))
                        .ownerOrSpenderIdBounds(new Bound(
                                new EntityIdRangeParameter(RangeOperator.GTE, EntityId.of(spenders.get(0))), null))
                        .tokenIdBounds(new Bound(null, null))
                        .limit(4)
                        .order(Direction.ASC)
                        .build(),
                List.of(new Tuple(1, 0, 0), new Tuple(1, 0, 1), new Tuple(1, 0, 2), new Tuple(1, 1, 0)));
        NftAllowanceDtos.put(
                NftAllowanceDto.builder()
                        .isOwner(true)
                        .accountId(EntityId.of(owners.get(1)))
                        .ownerOrSpenderIdBounds(new Bound(
                                new EntityIdRangeParameter(RangeOperator.GTE, EntityId.of(spenders.get(0))), null))
                        .tokenIdBounds(new Bound(
                                new EntityIdRangeParameter(RangeOperator.GTE, EntityId.of(tokenIdBounds.get(0))), null))
                        .limit(4)
                        .order(Direction.ASC)
                        .build(),
                List.of(new Tuple(1, 0, 0), new Tuple(1, 0, 1), new Tuple(1, 0, 2), new Tuple(1, 1, 0)));
        NftAllowanceDtos.put(
                NftAllowanceDto.builder()
                        .isOwner(true)
                        .accountId(EntityId.of(owners.get(1)))
                        .ownerOrSpenderIdBounds(new Bound(
                                new EntityIdRangeParameter(RangeOperator.GTE, EntityId.of(spenders.get(0))), null))
                        .tokenIdBounds(new Bound(
                                new EntityIdRangeParameter(RangeOperator.GT, EntityId.of(tokenIdBounds.get(1))), null))
                        .limit(4)
                        .order(Direction.ASC)
                        .build(),
                List.of(new Tuple(1, 0, 2), new Tuple(1, 1, 0), new Tuple(1, 1, 1), new Tuple(1, 1, 2)));
        NftAllowanceDtos.put(
                NftAllowanceDto.builder()
                        .isOwner(true)
                        .accountId(EntityId.of(owners.get(1)))
                        .ownerOrSpenderIdBounds(new Bound(
                                new EntityIdRangeParameter(RangeOperator.LTE, EntityId.of(spenders.get(2))), null))
                        .tokenIdBounds(new Bound(
                                new EntityIdRangeParameter(RangeOperator.LT, EntityId.of(tokenIdBounds.get(2))), null))
                        .limit(4)
                        .order(Direction.DESC)
                        .build(),
                List.of(new Tuple(1, 2, 1), new Tuple(1, 2, 0), new Tuple(1, 1, 2), new Tuple(1, 1, 1)));
        NftAllowanceDtos.put(
                NftAllowanceDto.builder()
                        .isOwner(true)
                        .accountId(EntityId.of(owners.get(1)))
                        .ownerOrSpenderIdBounds(new Bound(
                                new EntityIdRangeParameter(RangeOperator.LTE, EntityId.of(spenders.get(2))),
                                new EntityIdRangeParameter(RangeOperator.GTE, EntityId.of(spenders.get(0)))))
                        .tokenIdBounds(new Bound(
                                new EntityIdRangeParameter(RangeOperator.LT, EntityId.of(tokenIdBounds.get(2))),
                                new EntityIdRangeParameter(RangeOperator.GT, EntityId.of(tokenIdBounds.get(0)))))
                        .limit(4)
                        .order(Direction.ASC)
                        .build(),
                List.of(new Tuple(1, 0, 1), new Tuple(1, 0, 2), new Tuple(1, 1, 0), new Tuple(1, 1, 1)));
        NftAllowanceDtos.put(
                NftAllowanceDto.builder()
                        .isOwner(true)
                        .accountId(EntityId.of(owners.get(1)))
                        .ownerOrSpenderIdBounds(new Bound(
                                new EntityIdRangeParameter(RangeOperator.LTE, EntityId.of(spenders.get(2))),
                                new EntityIdRangeParameter(RangeOperator.GT, EntityId.of(spenders.get(0)))))
                        .tokenIdBounds(new Bound(
                                new EntityIdRangeParameter(RangeOperator.LTE, EntityId.of(tokenIdBounds.get(0))), null))
                        .limit(6)
                        .order(Direction.DESC)
                        .build(),
                List.of(
                        new Tuple(1, 2, 2),
                        new Tuple(1, 2, 1),
                        new Tuple(1, 2, 0),
                        new Tuple(1, 1, 2),
                        new Tuple(1, 1, 1),
                        new Tuple(1, 1, 0)));
        NftAllowanceDtos.put(
                NftAllowanceDto.builder()
                        .isOwner(true)
                        .accountId(EntityId.of(owners.get(1)))
                        .ownerOrSpenderIdBounds(new Bound(
                                new EntityIdRangeParameter(RangeOperator.EQ, EntityId.of(spenders.get(0))), null))
                        .tokenIdBounds(new Bound(
                                new EntityIdRangeParameter(RangeOperator.EQ, EntityId.of(tokenIdBounds.get(1))), null))
                        .limit(4)
                        .order(Direction.ASC)
                        .build(),
                List.of(new Tuple(1, 0, 1)));
        NftAllowanceDtos.put(
                NftAllowanceDto.builder()
                        .isOwner(true)
                        .accountId(EntityId.of(owners.get(1)))
                        .ownerOrSpenderIdBounds(new Bound(
                                new EntityIdRangeParameter(RangeOperator.EQ, EntityId.of(spenders.get(0))), null))
                        .tokenIdBounds(new Bound(
                                new EntityIdRangeParameter(RangeOperator.GT, EntityId.of(tokenIdBounds.get(1))),
                                new EntityIdRangeParameter(RangeOperator.LTE, EntityId.of(tokenIdBounds.get(2)))))
                        .limit(4)
                        .order(Direction.ASC)
                        .build(),
                List.of(new Tuple(1, 0, 2)));
    }

    private void assertNftAllowances() {
        for (var entry : NftAllowanceDtos.entrySet()) {
            var expectedNftAllowances =
                    entry.getValue().stream().map(nftAllowances::get).toList();

            var key = entry.getKey();
            assertThat(nftAllowanceRepository.findAll(key)).containsExactlyElementsOf(expectedNftAllowances);
        }
    }

    private record Tuple(int ownerIndex, int spenderIndex, int tokenIndex) {}
}
