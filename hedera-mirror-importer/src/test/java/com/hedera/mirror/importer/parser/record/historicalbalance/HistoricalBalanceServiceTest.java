/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.record.historicalbalance;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.mirror.common.domain.balance.AccountBalanceFile;
import com.hedera.mirror.importer.db.TimePartitionService;
import com.hedera.mirror.importer.downloader.balance.BalanceDownloaderProperties;
import com.hedera.mirror.importer.parser.record.RecordFileParsedEvent;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import com.hedera.mirror.importer.repository.AccountBalanceRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.TokenBalanceRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import org.awaitility.Durations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class HistoricalBalanceServiceTest {

    @Test
    void concurrentOnRecordFileParsed() {
        // given
        var accountBalanceFileRepository = mock(AccountBalanceFileRepository.class);
        var accountBalanceRepository = mock(AccountBalanceRepository.class);
        var balanceDownloaderProperties = mock(BalanceDownloaderProperties.class);
        var platformTransactionManager = mock(PlatformTransactionManager.class);
        var recordFileRepository = mock(RecordFileRepository.class);
        var timePartitionService = mock(TimePartitionService.class);
        var tokenBalanceRepository = mock(TokenBalanceRepository.class);
        var pool = Executors.newFixedThreadPool(2);

        long lastBalanceTimestamp = 100;
        when(accountBalanceFileRepository.findLatest())
                .thenReturn(Optional.of(AccountBalanceFile.builder()
                        .consensusTimestamp(lastBalanceTimestamp)
                        .build()));
        var semaphore = new Semaphore(0);
        when(recordFileRepository.findLatest()).thenAnswer(invocation -> {
            semaphore.acquire();
            return Optional.empty();
        });
        var historicalBalanceProperties = new HistoricalBalanceProperties(balanceDownloaderProperties);
        var service = new HistoricalBalanceService(
                accountBalanceFileRepository,
                accountBalanceRepository,
                new SimpleMeterRegistry(),
                platformTransactionManager,
                historicalBalanceProperties,
                recordFileRepository,
                timePartitionService,
                tokenBalanceRepository);

        // when
        var event = new RecordFileParsedEvent(
                this,
                lastBalanceTimestamp
                        + historicalBalanceProperties.getMinFrequency().toNanos());
        Runnable runnable = () -> service.onRecordFileParsed(event);
        var task1 = pool.submit(runnable);
        var task2 = pool.submit(runnable);

        // then
        // verify that only one task is done
        await().pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
                .atMost(Durations.TWO_SECONDS)
                .until(() -> task1.isDone() ^ task2.isDone());
        // unblock the remaining task
        semaphore.release();

        // then
        await().pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
                .atMost(Durations.TWO_SECONDS)
                .until(() -> task1.isDone() && task2.isDone());
        // only one findLatest call
        verify(recordFileRepository).findLatest();

        // when
        // run it again
        var task3 = pool.submit(runnable);
        semaphore.release();

        // then
        await().pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
                .atMost(Durations.TWO_SECONDS)
                .until(task3::isDone);
        verify(recordFileRepository, times(2)).findLatest();
    }
}
