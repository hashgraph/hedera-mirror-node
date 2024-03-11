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

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.restjava.RestJavaIntegrationTest;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class JooqRepositoryTest extends RestJavaIntegrationTest {

    private final DomainBuilder domainBuilder;
    private final DSLContext dslContext;
    private final JooqRepository jooqRepository;

    @Test
    void nftAllowance() {
        // given
        var expected1 = domainBuilder.nftAllowance().persist();
        var expected2 = domainBuilder.nftAllowance().persist();

        // when
        var query = dslContext
                .selectFrom(NFT_ALLOWANCE)
                .orderBy(NFT_ALLOWANCE.OWNER.asc(), NFT_ALLOWANCE.SPENDER.asc())
                .getQuery();
        var actual = jooqRepository.getEntities(query, NftAllowance.class);

        // then
        assertThat(actual).containsExactly(expected1, expected2);

        // when
        query = dslContext
                .selectFrom(NFT_ALLOWANCE)
                .where(NFT_ALLOWANCE.OWNER.eq(expected1.getOwner()))
                .getQuery();
        var actualNftAllowance = jooqRepository.getEntity(query, NftAllowance.class);

        // then
        assertThat(actualNftAllowance).contains(expected1);

        // when
        query = dslContext
                .selectFrom(NFT_ALLOWANCE)
                .where(NFT_ALLOWANCE.OWNER.eq(domainBuilder.id()))
                .limit(1)
                .getQuery();
        actualNftAllowance = jooqRepository.getEntity(query, NftAllowance.class);

        // then
        assertThat(actualNftAllowance).isEmpty();
    }
}
