package com.hedera.mirror.repository;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

import com.hedera.IntegrationTest;
import com.hedera.mirror.domain.ApplicationStatusCode;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.annotation.Resource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
public class ApplicationStatusRepositoryTest extends IntegrationTest {

    private final String EXPECTED = UUID.randomUUID().toString();

    @Resource
    private ApplicationStatusRepository applicationStatusRepository;

    @Test
    void updateStatusValue() throws Exception {
        applicationStatusRepository.updateStatusValue(ApplicationStatusCode.LAST_PROCESSED_EVENT_HASH, EXPECTED);
        assertThat(applicationStatusRepository.findByStatusCode(ApplicationStatusCode.LAST_PROCESSED_EVENT_HASH)).isEqualTo(EXPECTED);
        assertThat(applicationStatusRepository.findByStatusCode(ApplicationStatusCode.LAST_PROCESSED_EVENT_HASH)).isEqualTo(EXPECTED); // Should see no sql query in logs
    }

    @Test
    void updateBypassEventHashMismatchUntilAfter() throws Exception {
        applicationStatusRepository.updateBypassEventHashMismatchUntilAfter(EXPECTED);
        assertThat(applicationStatusRepository.getBypassEventHashMismatchUntilAfter()).isEqualTo(EXPECTED);
    }

    @Test
    void updateBypassRecordHashMismatchUntilAfter() throws Exception {
        applicationStatusRepository.updateBypassRecordHashMismatchUntilAfter(EXPECTED);
        assertThat(applicationStatusRepository.getBypassRecordHashMismatchUntilAfter()).isEqualTo(EXPECTED);
    }

    @Test
    void updateLastProcessedEventHash() throws Exception {
        applicationStatusRepository.updateLastProcessedEventHash(EXPECTED);
        assertThat(applicationStatusRepository.getLastProcessedEventHash()).isEqualTo(EXPECTED);
    }

    @Test
    void updateLastProcessedRecordHash() throws Exception {
        applicationStatusRepository.updateLastProcessedEventHash(EXPECTED);
        assertThat(applicationStatusRepository.getLastProcessedEventHash()).isEqualTo(EXPECTED);
    }

    @Test
    void updateLastValidDownloadedBalanceFileName() throws Exception {
        applicationStatusRepository.updateLastValidDownloadedBalanceFileName(EXPECTED);
        assertThat(applicationStatusRepository.getLastValidDownloadedBalanceFileName()).isEqualTo(EXPECTED);
    }

    @Test
    void updateLastValidDownloadedEventFileHash() throws Exception {
        applicationStatusRepository.updateLastValidDownloadedEventFileHash(EXPECTED);
        assertThat(applicationStatusRepository.getLastValidDownloadedEventFileHash()).isEqualTo(EXPECTED);
    }

    @Test
    void updateLastValidDownloadedEventFileName() throws Exception {
        applicationStatusRepository.updateLastValidDownloadedEventFileName(EXPECTED);
        assertThat(applicationStatusRepository.getLastValidDownloadedEventFileName()).isEqualTo(EXPECTED);
    }

    @Test
    void updateLastValidDownloadedRecordFileHash() throws Exception {
        applicationStatusRepository.updateLastValidDownloadedRecordFileHash(EXPECTED);
        assertThat(applicationStatusRepository.getLastValidDownloadedRecordFileHash()).isEqualTo(EXPECTED);
    }

    @Test
    void updateLastValidDownloadedRecordFileName() throws Exception {
        applicationStatusRepository.updateLastValidDownloadedRecordFileName(EXPECTED);
        assertThat(applicationStatusRepository.getLastValidDownloadedRecordFileName()).isEqualTo(EXPECTED);
    }
}
