package com.hedera.mirror.importer.migration;

/*
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

import com.hedera.mirror.importer.downloader.balance.AccountBalancesDownloader;
import com.hedera.mirror.importer.downloader.event.EventFileDownloader;
import com.hedera.mirror.importer.downloader.record.RecordFileDownloader;
import com.hedera.mirror.importer.parser.record.entity.FlywayMigrationsCompleteEvent;

@SpringBootTest
class FlywayMigrationsCommandLineRunnerTest {

    @SpyBean
    FlywayMigrationsCommandLineRunner commandLineRunner;
    @SpyBean
    RecordFileDownloader recordFileDownloader;
    @SpyBean
    EventFileDownloader eventFileDownloader;
    @SpyBean
    AccountBalancesDownloader accountBalancesDownloader;

    @Test
    void verifyMigrationCompleteAndEventReceived() {
        verify(commandLineRunner).run(any());
        verify(recordFileDownloader).startDownloader(any(FlywayMigrationsCompleteEvent.class));
        verify(eventFileDownloader).startDownloader(any(FlywayMigrationsCompleteEvent.class));
        verify(accountBalancesDownloader).startDownloader(any(FlywayMigrationsCompleteEvent.class));
    }
}
