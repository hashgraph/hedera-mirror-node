package com.hedera.mirror.importer.config;

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import javax.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.config.event.MirrorDateRangePropertiesProcessedEvent;
import com.hedera.mirror.importer.downloader.balance.AccountBalancesDownloader;
import com.hedera.mirror.importer.downloader.event.EventFileDownloader;
import com.hedera.mirror.importer.downloader.record.RecordFileDownloader;

public class MirrorDateRangePropertiesProcessorIntegrationTest extends IntegrationTest {

    @Resource
    private ApplicationEventPublisher applicationEventPublisher;

    @MockBean
    private AccountBalancesDownloader accountBalancesDownloader;

    @MockBean
    private RecordFileDownloader recordFileDownloader;

    @MockBean
    private EventFileDownloader eventFileDownloader;

    @Test
    void eventFiredAndListenerCalled() {
        verify(applicationEventPublisher, timeout(500).times(1))
                .publishEvent(any(MirrorDateRangePropertiesProcessedEvent.class));
        verify(accountBalancesDownloader, timeout(500)).onMirrorDateRangePropertiesProcessedEvent();
        verify(recordFileDownloader).onMirrorDateRangePropertiesProcessedEvent();
        verify(eventFileDownloader).onMirrorDateRangePropertiesProcessedEvent();
    }

    @TestConfiguration
    static class contextConfiguration {

        @Bean
        @Primary
        public ApplicationEventPublisher applicationEventPublisher(ApplicationEventPublisher publisher) {
            // ApplicationEventPublisher can't be mocked / spied with annotation
            return Mockito.spy(publisher);
        }
    }
}
