package com.hedera.mirror.importer.migration;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import com.google.common.base.Stopwatch;
import com.google.common.collect.Iterables;
import java.util.Collection;
import javax.inject.Named;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;

import com.hedera.mirror.importer.repository.EntityRepository;

@CustomLog
@Named
@RequiredArgsConstructor(onConstructor_ = {@Lazy})
public class EntityContractTypeMigration {

    private static final int BATCH_LIMIT = 32767;
    private final EntityRepository entityRepository;

    public void doMigrate(final Collection<Long> contractIds) {
        if (contractIds == null || contractIds.isEmpty()) {
            return;
        }

        int count = 0;
        var partitions = Iterables.partition(contractIds, BATCH_LIMIT);
        var stopwatch = Stopwatch.createStarted();
        for (var partition : partitions) {
            count += entityRepository.updateContractType(partition);
        }
        log.info("Updated {} entities in {}", count, stopwatch);
    }
}
