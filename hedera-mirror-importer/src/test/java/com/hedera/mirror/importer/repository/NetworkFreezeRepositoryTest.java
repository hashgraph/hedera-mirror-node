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

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class NetworkFreezeRepositoryTest extends AbstractRepositoryTest {

    private final NetworkFreezeRepository networkFreezeRepository;

    @Test
    void prune() {
        domainBuilder.networkFreeze().persist();
        var networkFreeze2 = domainBuilder.networkFreeze().persist();
        var networkFreeze3 = domainBuilder.networkFreeze().persist();

        networkFreezeRepository.prune(networkFreeze2.getConsensusTimestamp());

        assertThat(networkFreezeRepository.findAll()).containsExactlyInAnyOrder(networkFreeze3);
    }

    @Test
    void save() {
        var networkFreeze = domainBuilder.networkFreeze().get();
        networkFreezeRepository.save(networkFreeze);
        assertThat(networkFreezeRepository.findById(networkFreeze.getConsensusTimestamp()))
                .get()
                .isEqualTo(networkFreeze);
    }
}
