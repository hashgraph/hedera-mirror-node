/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import com.hedera.mirror.restjava.RestJavaIntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

@RequiredArgsConstructor
class NftAllowanceRepositoryTest extends RestJavaIntegrationTest {

    private final NftAllowanceRepository nftAllowanceRepository;

    @Test
    void findBySpenderAndFilterByOwnerGtAndTokenGt() {
        var nftAllowance = domainBuilder.nftAllowance().get();
        nftAllowanceRepository.save(nftAllowance);
        nftAllowanceRepository.save(domainBuilder.nftAllowance().get());
        nftAllowanceRepository.save(domainBuilder.nftAllowance().get());
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
        var nftAllowance = domainBuilder.nftAllowance().get();
        nftAllowanceRepository.save(nftAllowance);
        nftAllowanceRepository.save(domainBuilder.nftAllowance().get());
        nftAllowanceRepository.save(domainBuilder.nftAllowance().get());
        Pageable pageable =
                PageRequest.of(0, 1, Sort.by(Direction.ASC, "owner").and(Sort.by(Direction.ASC, "token_id")));

        assertThat(nftAllowanceRepository.findBySpenderAndFilterByOwnerAndToken(
                        nftAllowance.getSpender(), nftAllowance.getOwner(), nftAllowance.getTokenId(), pageable))
                .isEmpty();
    }

    @Test
    void findByOwnerAndFilterBySpenderGtAndTokenGt() {
        var nftAllowance = domainBuilder.nftAllowance().get();
        var nftAllowance1 = domainBuilder.nftAllowance().get();
        nftAllowance1.setOwner(nftAllowance.getOwner());
        nftAllowanceRepository.save(nftAllowance);
        nftAllowanceRepository.save(nftAllowance1);
        nftAllowanceRepository.save(domainBuilder.nftAllowance().get());
        nftAllowanceRepository.save(domainBuilder.nftAllowance().get());
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
        var nftAllowance = domainBuilder.nftAllowance().get();
        nftAllowanceRepository.save(nftAllowance);
        nftAllowanceRepository.save(domainBuilder.nftAllowance().get());
        Pageable pageable =
                PageRequest.of(0, 1, Sort.by(Direction.ASC, "spender").and(Sort.by(Direction.ASC, "token_id")));

        assertThat(nftAllowanceRepository.findByOwnerAndFilterBySpenderAndToken(
                        nftAllowance.getOwner() + 1, nftAllowance.getSpender(), nftAllowance.getTokenId(), pageable))
                .isEmpty();
    }
}
