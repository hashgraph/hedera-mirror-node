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
import static org.assertj.core.api.Assertions.from;

import javax.annotation.Resource;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.Schedule;

public class ScheduleRepositoryTest extends AbstractRepositoryTest {
    @Resource
    protected ScheduleRepository scheduleRepository;

    private static final EntityId CREATOR_ACCOUNT = EntityId.of("0.0.123", EntityTypeEnum.ACCOUNT);
    private static final EntityId PAYER_ACCOUNT = EntityId.of("0.0.456", EntityTypeEnum.ACCOUNT);
    private static final EntityId SCHEDULE_ID = EntityId.of("0.0.789", EntityTypeEnum.SCHEDULE);
    private static final byte[] TRANSACTION_BODY = "transaction memo".getBytes();

    @Test
    void save() {
        Schedule schedule = scheduleRepository.save(schedule(1));
        assertThat(scheduleRepository.findById(schedule.getConsensusTimestamp())
                .get()).isEqualTo(schedule);
    }

    @Test
    void updateExecutedTimestamp() {
        Schedule schedule = scheduleRepository.save(schedule(1));
        long newExecutedTimestamp = 1000L;
        scheduleRepository.updateExecutedTimestamp(schedule.getScheduleId(), newExecutedTimestamp);
        assertThat(scheduleRepository.findById(schedule.getConsensusTimestamp()).get())
                .returns(newExecutedTimestamp, from(Schedule::getExecutedTimestamp));
    }

    private Schedule schedule(long consensusTimestamp) {
        Schedule schedule = new Schedule();
        schedule.setConsensusTimestamp(consensusTimestamp);
        schedule.setCreatorAccountId(CREATOR_ACCOUNT);
        schedule.setPayerAccountId(PAYER_ACCOUNT);
        schedule.setScheduleId(SCHEDULE_ID);
        schedule.setTransactionBody(TRANSACTION_BODY);
        return schedule;
    }
}
