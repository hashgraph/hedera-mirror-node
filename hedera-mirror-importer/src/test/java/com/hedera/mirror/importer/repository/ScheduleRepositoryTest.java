/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
import static org.assertj.core.api.Assertions.from;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.schedule.Schedule;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;

class ScheduleRepositoryTest extends AbstractRepositoryTest {
    @Resource
    private ScheduleRepository scheduleRepository;

    @Test
    void save() {
        Schedule schedule = scheduleRepository.save(schedule(1));
        assertThat(scheduleRepository.findById(schedule.getScheduleId())).get().isEqualTo(schedule);
    }

    @Test
    void updateExecutedTimestamp() {
        Schedule schedule = scheduleRepository.save(schedule(1));
        long newExecutedTimestamp = 1000L;
        scheduleRepository.updateExecutedTimestamp(schedule.getScheduleId(), newExecutedTimestamp);
        assertThat(scheduleRepository.findById(schedule.getScheduleId()))
                .get()
                .returns(newExecutedTimestamp, from(Schedule::getExecutedTimestamp));
    }

    private Schedule schedule(long consensusTimestamp) {
        Schedule schedule = new Schedule();
        schedule.setConsensusTimestamp(consensusTimestamp);
        schedule.setCreatorAccountId(EntityId.of("0.0.123", EntityType.ACCOUNT));
        schedule.setPayerAccountId(EntityId.of("0.0.456", EntityType.ACCOUNT));
        schedule.setScheduleId(EntityId.of("0.0.789", EntityType.SCHEDULE));
        schedule.setTransactionBody("transaction body".getBytes());
        return schedule;
    }
}
