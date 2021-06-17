package com.hedera.mirror.importer.parser.event;

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

import io.micrometer.core.instrument.MeterRegistry;
import javax.inject.Named;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.annotation.Transactional;

import com.hedera.mirror.importer.domain.EventFile;
import com.hedera.mirror.importer.leader.Leader;
import com.hedera.mirror.importer.parser.AbstractStreamFileParser;
import com.hedera.mirror.importer.repository.StreamFileRepository;

@Named
public class EventFileParser extends AbstractStreamFileParser<EventFile> {

    public EventFileParser(MeterRegistry meterRegistry, EventParserProperties parserProperties,
                           StreamFileRepository<EventFile, Long> eventFileRepository) {
        super(meterRegistry, parserProperties, eventFileRepository);
    }

    @Override
    @Leader
    @Retryable(backoff = @Backoff(
            delayExpression = "#{@eventParserProperties.getRetry().getMinBackoff().toMillis()}",
            maxDelayExpression = "#{@eventParserProperties.getRetry().getMaxBackoff().toMillis()}",
            multiplierExpression = "#{@eventParserProperties.getRetry().getMultiplier()}"),
            maxAttemptsExpression = "#{@eventParserProperties.getRetry().getMaxAttempts()}")
    @Transactional
    public void parse(EventFile eventFile) {
        super.parse(eventFile);
    }

    @Override
    protected void doParse(EventFile eventFile) {
        streamFileRepository.save(eventFile);
    }
}
