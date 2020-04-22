/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.record;

import com.google.common.base.Stopwatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.hedera.mirror.importer.parser.CommonParserProperties;
import com.hedera.mirror.importer.repository.EntityRepository;

@Component
@Log4j2
@RequiredArgsConstructor
@ConditionalOnRecordParser
public class EntityIdCacheLoader {
    private final EntityRepository entityRepository;
    private final CommonParserProperties commonParserProperties;

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void load() {
        Stopwatch stopwatch = Stopwatch.createStarted();
        // Seed the cache
        var ids = entityRepository.findAllEntityIds(commonParserProperties.getEntityIdCacheSize());
        log.info("Cached {} entity id mappings in {}", ids.size(), stopwatch);
    }
}
