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

import static com.hedera.mirror.importer.config.IntegrationConfiguration.CHANNEL_EVENT;

import lombok.RequiredArgsConstructor;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.Poller;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.annotation.Transactional;

import com.hedera.mirror.importer.domain.EventFile;
import com.hedera.mirror.importer.parser.StreamFileParser;

@MessageEndpoint
@RequiredArgsConstructor
public class EventFileParser implements StreamFileParser<EventFile> {

    @Override
    @Retryable(backoff = @Backoff(delay = 200L, maxDelay = 10_000L, multiplier = 2), maxAttempts = Integer.MAX_VALUE)
    @ServiceActivator(inputChannel = CHANNEL_EVENT,
            poller = @Poller(fixedDelay = "${hedera.mirror.importer.parser.event.frequency:100}")
    )

    @Transactional
    public void parse(EventFile eventFile) {
        // Event parsing not implemented yet
    }
}
