/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.record.entity.staking;

import com.google.common.base.Stopwatch;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.EntityStakeRepository;
import jakarta.inject.Named;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;

@CustomLog
@Named
@RequiredArgsConstructor
public class EntityStakeCalculatorImpl implements EntityStakeCalculator {

    private final EntityProperties entityProperties;
    private final EntityRepository entityRepository;
    private final EntityStakeRepository entityStakeRepository;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Override
    public void calculate() {
        if (!entityProperties.getPersist().isPendingReward()) {
            return;
        }

        if (running.compareAndExchange(false, true)) {
            log.info("Skipping since the previous entity stake calculation is still running");
            return;
        }

        try {
            if (entityStakeRepository.updated()) {
                log.info("Skipping since the entity stake is up-to-date");
                return;
            }

            var stopwatch = Stopwatch.createStarted();
            entityRepository.refreshEntityStateStart();
            log.info("Refreshed entity_state_start in {}", stopwatch);
            int count = entityStakeRepository.updateEntityStake();
            log.info("Completed pending reward calculation for {} entities in {}", count, stopwatch);
        } finally {
            running.set(false);
        }
    }
}
