/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.entity.NftAllowanceHistory;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class NftAllowanceHistoryRepositoryTest extends AbstractRepositoryTest {

    private final NftAllowanceHistoryRepository nftAllowanceHistoryRepository;

    @Test
    void prune() {
        domainBuilder.nftAllowanceHistory().persist();
        var nftAllowanceHistory2 = domainBuilder.nftAllowanceHistory().persist();
        var nftAllowanceHistory3 = domainBuilder.nftAllowanceHistory().persist();

        nftAllowanceHistoryRepository.prune(nftAllowanceHistory2.getTimestampUpper());

        assertThat(nftAllowanceHistoryRepository.findAll()).containsExactly(nftAllowanceHistory3);
    }

    @Test
    void save() {
        NftAllowanceHistory nftAllowanceHistory =
                domainBuilder.nftAllowanceHistory().persist();
        assertThat(nftAllowanceHistoryRepository.findById(nftAllowanceHistory.getId()))
                .get()
                .isEqualTo(nftAllowanceHistory);
    }
}
