package com.hedera.mirror.importer.repository;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import javax.annotation.Resource;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.ScheduleSignature;

public class ScheduleSignatureRepositoryTest extends AbstractRepositoryTest {
    @Resource
    private ScheduleSignatureRepository scheduleSignatureRepository;

    @Test
    void save() {
        ScheduleSignature scheduleSignature = scheduleSignatureRepository.save(scheduleSignature(1));
        assertThat(scheduleSignatureRepository.findById(scheduleSignature.getScheduleSignatureId()))
                .get().isEqualTo(scheduleSignature);
    }

    private ScheduleSignature scheduleSignature(long consensusTimestamp) {
        ScheduleSignature scheduleSignature = new ScheduleSignature();
        scheduleSignature.setScheduleSignatureId(new ScheduleSignature.Id(
                consensusTimestamp,
                "signatory public key prefix".getBytes(),
                EntityId.of("0.0.789", EntityTypeEnum.SCHEDULE)));
        scheduleSignature.setSignature("scheduled transaction signature".getBytes());
        return scheduleSignature;
    }
}
