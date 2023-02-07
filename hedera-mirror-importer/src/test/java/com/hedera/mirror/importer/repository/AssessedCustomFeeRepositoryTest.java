package com.hedera.mirror.importer.repository;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.importer.IntegrationTest;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class AssessedCustomFeeRepositoryTest extends IntegrationTest {

    private final AssessedCustomFeeRepository assessedCustomFeeRepository;

    @Test
    void prune() {
        domainBuilder.assessedCustomFee().persist();
        var assessedCustomFee2 = domainBuilder.assessedCustomFee().persist();
        var assessedCustomFee3 = domainBuilder.assessedCustomFee().persist();

        assessedCustomFeeRepository.prune(assessedCustomFee2.getId().getConsensusTimestamp());

        assertThat(assessedCustomFeeRepository.findAll()).containsExactly(assessedCustomFee3);
    }

    @Test
    void save() {
        var assessedCustomFee = domainBuilder.assessedCustomFee().get();
        assessedCustomFeeRepository.save(assessedCustomFee);
        assertThat(assessedCustomFeeRepository.findById(assessedCustomFee.getId())).get().isEqualTo(assessedCustomFee);
    }
}
