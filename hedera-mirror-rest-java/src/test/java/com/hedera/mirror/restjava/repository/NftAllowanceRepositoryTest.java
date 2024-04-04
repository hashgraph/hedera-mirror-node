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

import static com.hedera.mirror.restjava.jooq.domain.Tables.NFT_ALLOWANCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.restjava.RestJavaIntegrationTest;
import com.hedera.mirror.restjava.common.Filter;
import com.hedera.mirror.restjava.common.RangeOperator;
import com.hedera.mirror.restjava.exception.InvalidFilterException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.jooq.Field;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

@RequiredArgsConstructor
class NftAllowanceRepositoryTest extends RestJavaIntegrationTest {

    private final NftAllowanceRepository nftAllowanceRepository;

    private Map<Tuple, NftAllowance> nftAllowances;
    private List<Long> owners;
    private List<Long> spenders;
    private List<Long> tokenIds;

    private static Stream<Arguments> provideFindAllArguments() {
        return Stream.of(
                // Only owner = ? filter, ASC
                Arguments.of(
                        true,
                        List.of(new IndexedFilter(NFT_ALLOWANCE.OWNER, 0, RangeOperator.EQ)),
                        4,
                        Direction.ASC,
                        List.of(new Tuple(0, 0, 0), new Tuple(0, 0, 1), new Tuple(0, 0, 2), new Tuple(0, 1, 0))),
                // Only owner = ? filter, DESC
                Arguments.of(
                        true,
                        List.of(new IndexedFilter(NFT_ALLOWANCE.OWNER, 0, RangeOperator.EQ)),
                        4,
                        Direction.DESC,
                        List.of(new Tuple(0, 2, 2), new Tuple(0, 2, 1), new Tuple(0, 2, 0), new Tuple(0, 1, 2))),
                // Only spender = ? filter, by spender, ASC
                Arguments.of(
                        false,
                        List.of(new IndexedFilter(NFT_ALLOWANCE.SPENDER, 1, RangeOperator.EQ)),
                        4,
                        Direction.ASC,
                        List.of(new Tuple(0, 1, 0), new Tuple(0, 1, 1), new Tuple(0, 1, 2), new Tuple(1, 1, 0))),
                // Only spender = ? filter, by spender, DESC
                Arguments.of(
                        false,
                        List.of(new IndexedFilter(NFT_ALLOWANCE.SPENDER, 1, RangeOperator.EQ)),
                        4,
                        Direction.DESC,
                        List.of(new Tuple(2, 1, 2), new Tuple(2, 1, 1), new Tuple(2, 1, 0), new Tuple(1, 1, 2))),
                // By owner, approved_for_all is true and owner = ? and spender = ? filter, ASC
                Arguments.of(
                        true,
                        List.of(
                                new IndexedFilter(NFT_ALLOWANCE.OWNER, 1, RangeOperator.EQ),
                                new IndexedFilter(NFT_ALLOWANCE.SPENDER, 0, RangeOperator.EQ)),
                        4,
                        Direction.ASC,
                        List.of(new Tuple(1, 0, 0), new Tuple(1, 0, 1), new Tuple(1, 0, 2))),
                // By owner, owner = ? and spender >= ? filter, ASC
                Arguments.of(
                        true,
                        List.of(
                                new IndexedFilter(NFT_ALLOWANCE.OWNER, 1, RangeOperator.EQ),
                                new IndexedFilter(NFT_ALLOWANCE.SPENDER, 0, RangeOperator.GTE)),
                        4,
                        Direction.ASC,
                        List.of(new Tuple(1, 0, 0), new Tuple(1, 0, 1), new Tuple(1, 0, 2), new Tuple(1, 1, 0))),
                // By owner, owner = ? and spender >= ? and token >= ? filter, ASC
                Arguments.of(
                        true,
                        List.of(
                                new IndexedFilter(NFT_ALLOWANCE.OWNER, 1, RangeOperator.EQ),
                                new IndexedFilter(NFT_ALLOWANCE.SPENDER, 0, RangeOperator.GTE),
                                new IndexedFilter(NFT_ALLOWANCE.TOKEN_ID, 1, RangeOperator.GTE)),
                        4,
                        Direction.ASC,
                        List.of(new Tuple(1, 0, 1), new Tuple(1, 0, 2), new Tuple(1, 1, 0), new Tuple(1, 1, 1))),
                // By owner, owner = ? and spender > ? and token > ? filter, ASC
                Arguments.of(
                        true,
                        List.of(
                                new IndexedFilter(NFT_ALLOWANCE.OWNER, 1, RangeOperator.EQ),
                                new IndexedFilter(NFT_ALLOWANCE.SPENDER, 0, RangeOperator.GT),
                                new IndexedFilter(NFT_ALLOWANCE.TOKEN_ID, 1, RangeOperator.GT)),
                        4,
                        Direction.ASC,
                        List.of(new Tuple(1, 1, 2), new Tuple(1, 2, 0), new Tuple(1, 2, 1), new Tuple(1, 2, 2))),
                // By owner, owner = ? and spender < ? and token < ? filter, DESC
                Arguments.of(
                        true,
                        List.of(
                                new IndexedFilter(NFT_ALLOWANCE.OWNER, 1, RangeOperator.EQ),
                                new IndexedFilter(NFT_ALLOWANCE.SPENDER, 2, RangeOperator.LT),
                                new IndexedFilter(NFT_ALLOWANCE.TOKEN_ID, 2, RangeOperator.LT)),
                        4,
                        Direction.DESC,
                        List.of(new Tuple(1, 1, 1), new Tuple(1, 1, 0), new Tuple(1, 0, 2), new Tuple(1, 0, 1))),
                // By owner, owner = ? and spender <= ? and token <= ? filter, DESC
                Arguments.of(
                        true,
                        List.of(
                                new IndexedFilter(NFT_ALLOWANCE.OWNER, 1, RangeOperator.EQ),
                                new IndexedFilter(NFT_ALLOWANCE.SPENDER, 2, RangeOperator.LTE),
                                new IndexedFilter(NFT_ALLOWANCE.TOKEN_ID, 1, RangeOperator.LTE)),
                        4,
                        Direction.DESC,
                        List.of(new Tuple(1, 2, 1), new Tuple(1, 2, 0), new Tuple(1, 1, 2), new Tuple(1, 1, 1))),
                // By owner, owner = ? and spender = ? and token = ? filter, ASC
                Arguments.of(
                        true,
                        List.of(
                                new IndexedFilter(NFT_ALLOWANCE.OWNER, 1, RangeOperator.EQ),
                                new IndexedFilter(NFT_ALLOWANCE.SPENDER, 0, RangeOperator.EQ),
                                new IndexedFilter(NFT_ALLOWANCE.TOKEN_ID, 1, RangeOperator.EQ)),
                        4,
                        Direction.ASC,
                        List.of(new Tuple(1, 0, 1))));
    }

    @Test
    void findBySpenderAndFilterByOwnerGtAndTokenGt() {
        var nftAllowance = domainBuilder.nftAllowance().persist();
        domainBuilder.nftAllowance().persist();
        domainBuilder.nftAllowance().persist();
        Pageable pageable =
                PageRequest.of(0, 1, Sort.by(Direction.ASC, "owner").and(Sort.by(Direction.ASC, "token_id")));

        assertThat(nftAllowanceRepository.findBySpenderAndFilterByOwnerAndToken(
                        nftAllowance.getSpender(),
                        nftAllowance.getOwner() - 1,
                        nftAllowance.getTokenId() - 1,
                        pageable))
                .containsExactly(nftAllowance);
    }

    @Test
    void findBySpenderAndFilterByOwnerGtAndTokenGtTokenNotPresent() {
        var nftAllowance = domainBuilder.nftAllowance().persist();
        domainBuilder.nftAllowance().persist();
        domainBuilder.nftAllowance().persist();
        Pageable pageable =
                PageRequest.of(0, 1, Sort.by(Direction.ASC, "owner").and(Sort.by(Direction.ASC, "token_id")));

        assertThat(nftAllowanceRepository.findBySpenderAndFilterByOwnerAndToken(
                        nftAllowance.getSpender(), nftAllowance.getOwner(), nftAllowance.getTokenId(), pageable))
                .isEmpty();
    }

    @Test
    void findByOwnerAndFilterBySpenderGtAndTokenGt() {
        var nftAllowance = domainBuilder.nftAllowance().persist();
        var nftAllowance1 = domainBuilder.nftAllowance().get();
        nftAllowance1.setOwner(nftAllowance.getOwner());
        nftAllowanceRepository.save(nftAllowance1);
        domainBuilder.nftAllowance().persist();
        domainBuilder.nftAllowance().persist();
        Pageable pageable =
                PageRequest.of(0, 2, Sort.by(Direction.ASC, "spender").and(Sort.by(Direction.ASC, "token_id")));

        assertThat(nftAllowanceRepository.findByOwnerAndFilterBySpenderAndToken(
                        nftAllowance.getOwner(),
                        nftAllowance.getSpender() - 2,
                        nftAllowance.getTokenId() - 2,
                        pageable))
                .containsExactlyInAnyOrder(nftAllowance, nftAllowance1);
    }

    @Test
    void findByOwnerAndFilterBySpenderGtAndTokenGtOwnerNotPresent() {
        var nftAllowance = domainBuilder.nftAllowance().persist();
        domainBuilder.nftAllowance().persist();
        Pageable pageable =
                PageRequest.of(0, 1, Sort.by(Direction.ASC, "spender").and(Sort.by(Direction.ASC, "token_id")));

        assertThat(nftAllowanceRepository.findByOwnerAndFilterBySpenderAndToken(
                        nftAllowance.getOwner() + 1, nftAllowance.getSpender(), nftAllowance.getTokenId(), pageable))
                .isEmpty();
    }

    @MethodSource("provideFindAllArguments")
    @ParameterizedTest
    void findAll(
            boolean byOwner, List<IndexedFilter> indexedFilters, int limit, Direction order, List<Tuple> expected) {
        // given
        setupNftAllowances();
        var filters = new ArrayList<Filter<?>>();
        indexedFilters.stream().map(this::toFilter).forEach(filters::add);
        var expectedNftAllowances = expected.stream().map(nftAllowances::get).toList();

        // when, then
        assertThat(nftAllowanceRepository.findAll(byOwner, filters, limit, order))
                .containsExactlyElementsOf(expectedNftAllowances);
    }

    @Test
    void findAllNoMatch() {
        // given
        setupNftAllowances();

        // when, then
        var filters = List.<Filter<?>>of(new Filter<>(NFT_ALLOWANCE.OWNER, RangeOperator.EQ, owners.get(2) + 1));
        assertThat(nftAllowanceRepository.findAll(true, filters, 10, Direction.ASC))
                .isEmpty();

        // when, then
        filters = List.of(
                new Filter<>(NFT_ALLOWANCE.OWNER, RangeOperator.EQ, owners.get(0)),
                new Filter<>(NFT_ALLOWANCE.SPENDER, RangeOperator.EQ, spenders.get(2) + 1));
        assertThat(nftAllowanceRepository.findAll(true, filters, 10, Direction.ASC))
                .isEmpty();

        // when, then
        filters = List.of(
                new Filter<>(NFT_ALLOWANCE.OWNER, RangeOperator.EQ, owners.get(0)),
                new Filter<>(NFT_ALLOWANCE.SPENDER, RangeOperator.EQ, spenders.get(0)),
                new Filter<>(NFT_ALLOWANCE.TOKEN_ID, RangeOperator.EQ, tokenIds.get(2) + 1));
        assertThat(nftAllowanceRepository.findAll(true, filters, 10, Direction.ASC))
                .isEmpty();

        // when, then
        filters = List.of(
                new Filter<>(NFT_ALLOWANCE.OWNER, RangeOperator.EQ, owners.get(0)),
                new Filter<>(NFT_ALLOWANCE.SPENDER, RangeOperator.GT, spenders.get(2)),
                new Filter<>(NFT_ALLOWANCE.TOKEN_ID, RangeOperator.GT, tokenIds.get(0)));
        assertThat(nftAllowanceRepository.findAll(true, filters, 10, Direction.ASC))
                .isEmpty();

        // when, then
        filters = List.of(
                new Filter<>(NFT_ALLOWANCE.OWNER, RangeOperator.EQ, owners.get(0)),
                new Filter<>(NFT_ALLOWANCE.SPENDER, RangeOperator.LT, spenders.get(0)),
                new Filter<>(NFT_ALLOWANCE.TOKEN_ID, RangeOperator.LT, tokenIds.get(2)));
        assertThat(nftAllowanceRepository.findAll(true, filters, 10, Direction.ASC))
                .isEmpty();
    }

    @Test
    void findAllThrowInvalidFilterException() {
        var emptyFilters = Collections.<Filter<?>>emptyList();
        assertThatThrownBy(() -> nftAllowanceRepository.findAll(true, emptyFilters, 10, Direction.ASC))
                .isInstanceOf(InvalidFilterException.class);

        var filters = List.<Filter<?>>of(
                new Filter<>(NFT_ALLOWANCE.OWNER, RangeOperator.EQ, 1L),
                new Filter<>(NFT_ALLOWANCE.TOKEN_ID, RangeOperator.EQ, 3L));
        assertThatThrownBy(() -> nftAllowanceRepository.findAll(true, filters, 10, Direction.ASC))
                .isInstanceOf(InvalidFilterException.class);
    }

    private void setupNftAllowances() {
        // Set up 3 (owners) x 3 (spenders) x 3 (tokens) NFT allowances
        var entityIds =
                IntStream.range(0, 9).mapToLong(x -> domainBuilder.id()).boxed().toList();
        owners = entityIds.subList(0, 3);
        spenders = entityIds.subList(3, 6);
        tokenIds = entityIds.subList(6, 9);
        nftAllowances = new HashMap<>();

        for (int ownerIndex = 0; ownerIndex < owners.size(); ownerIndex++) {
            long owner = owners.get(ownerIndex);
            for (int spenderIndex = 0; spenderIndex < spenders.size(); spenderIndex++) {
                long spender = spenders.get(spenderIndex);
                for (int tokenIndex = 0; tokenIndex < tokenIds.size(); tokenIndex++) {
                    long tokenId = tokenIds.get(tokenIndex);
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

    private Filter<Long> toFilter(IndexedFilter indexedFilter) {
        // All fields in the test have Long value type
        Long value = null;
        if (indexedFilter.field == NFT_ALLOWANCE.OWNER) {
            value = owners.get(indexedFilter.index);
        } else if (indexedFilter.field == NFT_ALLOWANCE.SPENDER) {
            value = spenders.get(indexedFilter.index);
        } else if (indexedFilter.field == NFT_ALLOWANCE.TOKEN_ID) {
            value = tokenIds.get(indexedFilter.index);
        }

        return new Filter<>(indexedFilter.field, indexedFilter.operator, value);
    }

    private record IndexedFilter(Field<Long> field, int index, RangeOperator operator) {}

    private record Tuple(int ownerIndex, int spenderIndex, int tokenIndex) {}
}
