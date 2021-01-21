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
    protected ScheduleSignatureRepository scheduleSignatureRepository;

    private static final EntityId SCHEDULE_ID = EntityId.of("0.0.789", EntityTypeEnum.SCHEDULE);
    private static final byte[] PUBLIC_KEY_PREFIX = "signatory public key prefix".getBytes();
    private static final byte[] SIGNATURE = "scheduled transaction signature".getBytes();

    @Test
    void save() {
        ScheduleSignature scheduleSignature = scheduleSignatureRepository.save(scheduleSignature(1));
        assertThat(scheduleSignatureRepository.findById(scheduleSignature.getConsensusTimestamp())
                .get()).isEqualTo(scheduleSignature);
    }

    private ScheduleSignature scheduleSignature(long consensusTimestamp) {
        ScheduleSignature scheduleSignature = new ScheduleSignature();
        scheduleSignature.setConsensusTimestamp(consensusTimestamp);
        scheduleSignature.setPublicKeyPrefix(PUBLIC_KEY_PREFIX);
        scheduleSignature.setScheduleId(SCHEDULE_ID);
        scheduleSignature.setSignature(SIGNATURE);
        return scheduleSignature;
    }
}
