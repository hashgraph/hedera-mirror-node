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

class ScheduleRepositoryTest extends AbstractRepositoryTest {
    @Resource
    private ScheduleRepository scheduleRepository;

    @Test
    void save() {
        Schedule schedule = scheduleRepository.save(schedule(1));
        assertThat(scheduleRepository.findById(schedule.getConsensusTimestamp()))
                .get().isEqualTo(schedule);
    }

    @Test
    void updateExecutedTimestamp() {
        Schedule schedule = scheduleRepository.save(schedule(1));
        long newExecutedTimestamp = 1000L;
        scheduleRepository.updateExecutedTimestamp(schedule.getScheduleId(), newExecutedTimestamp);
        assertThat(scheduleRepository.findById(schedule.getConsensusTimestamp())).get()
                .returns(newExecutedTimestamp, from(Schedule::getExecutedTimestamp));
    }

    @Test
    void findByScheduleId() {
        Schedule schedule = scheduleRepository.save(schedule(1));
        assertThat(scheduleRepository.findByScheduleId(schedule.getScheduleId())).get()
                .isEqualTo(schedule);
    }

    private Schedule schedule(long consensusTimestamp) {
        Schedule schedule = new Schedule();
        schedule.setConsensusTimestamp(consensusTimestamp);
        schedule.setCreatorAccountId(EntityId.of("0.0.123", EntityTypeEnum.ACCOUNT));
        schedule.setPayerAccountId(EntityId.of("0.0.456", EntityTypeEnum.ACCOUNT));
        schedule.setScheduleId(EntityId.of("0.0.789", EntityTypeEnum.SCHEDULE));
        schedule.setTransactionBody("transaction body".getBytes());
        return schedule;
    }
}
