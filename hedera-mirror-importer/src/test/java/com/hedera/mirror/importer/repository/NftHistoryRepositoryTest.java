/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import com.google.common.collect.Range;
import com.hedera.mirror.importer.IntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class NftHistoryRepositoryTest extends IntegrationTest {

    private final NftHistoryRepository repository;

    @Test
    void prune() {
        // given
        var nftHistory1 = domainBuilder.nftHistory().persist();
        var nftHistory2 = domainBuilder
                .nftHistory()
                .customize(n -> n.timestampRange(
                        Range.closedOpen(nftHistory1.getTimestampUpper(), nftHistory1.getTimestampUpper() + 5)))
                .persist();
        var nftHistory3 = domainBuilder
                .nftHistory()
                .customize(n -> n.timestampRange(
                        Range.closedOpen(nftHistory2.getTimestampUpper(), nftHistory2.getTimestampUpper() + 5)))
                .persist();

        // when
        repository.prune(nftHistory2.getTimestampLower());

        // then
        assertThat(repository.findAll()).containsExactlyInAnyOrder(nftHistory2, nftHistory3);

        // when
        repository.prune(nftHistory3.getTimestampLower() + 1);

        // then
        assertThat(repository.findAll()).containsExactly(nftHistory3);
    }

    @Test
    void save() {
        var nftHistory = domainBuilder.nftHistory().get();
        repository.save(nftHistory);
        assertThat(repository.findAll()).containsExactly(nftHistory);
        assertThat(repository.findById(nftHistory.getId())).get().isEqualTo(nftHistory);
    }
}
