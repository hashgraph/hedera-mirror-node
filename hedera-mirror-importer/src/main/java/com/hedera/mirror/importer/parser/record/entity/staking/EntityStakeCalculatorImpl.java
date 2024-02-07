/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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
import com.hedera.mirror.importer.repository.EntityStakeRepository;
import jakarta.inject.Named;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.support.TransactionOperations;

@CustomLog
@Named
@RequiredArgsConstructor
public class EntityStakeCalculatorImpl implements EntityStakeCalculator {

    private final EntityProperties entityProperties;
    private final EntityStakeRepository entityStakeRepository;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final TransactionOperations transactionOperations;

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
            while (true) {
                if (entityStakeRepository.updated()) {
                    log.info("Skipping since the entity stake is up-to-date");
                    return;
                }

                var stopwatch = Stopwatch.createStarted();
                var lastEndStakePeriod =
                        entityStakeRepository.getEndStakePeriod().orElse(0L);
                transactionOperations.executeWithoutResult(s -> {
                    entityStakeRepository.lockFromConcurrentUpdates();
                    entityStakeRepository.createEntityStateStart();
                    log.info("Created entity_state_start in {}", stopwatch);
                    entityStakeRepository.updateEntityStake();
                });

                var endStakePeriod = entityStakeRepository.getEndStakePeriod();
                if (endStakePeriod
                        .filter(stakePeriod -> stakePeriod > lastEndStakePeriod)
                        .isPresent()) {
                    log.info(
                            "Completed pending reward calculation of end stake period {} in {}",
                            endStakePeriod.get(),
                            stopwatch);
                } else {
                    log.warn(
                            "Failed to calculate pending reward in {}, last end stake period is {}, and the end stake period afterwards is {}",
                            stopwatch,
                            lastEndStakePeriod,
                            endStakePeriod.orElse(null));
                    break;
                }
            }
        } catch (Exception e) {
            log.error("Failed to update entity stake", e);
            throw e;
        } finally {
            running.set(false);
        }
    }
}
