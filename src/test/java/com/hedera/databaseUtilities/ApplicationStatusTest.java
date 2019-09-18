package com.hedera.databaseUtilities;

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
public class ApplicationStatusTest extends IntegrationTest {

    private final String EXPECTED = UUID.randomUUID().toString();

    @Resource
    private ApplicationStatus applicationStatus;

    @Test
    void updateStatusValue() throws Exception {
        applicationStatus.updateStatusValue(ApplicationStatusCode.LAST_PROCESSED_EVENT_HASH, EXPECTED);
        assertThat(applicationStatus.findByStatusCode(ApplicationStatusCode.LAST_PROCESSED_EVENT_HASH)).isEqualTo(EXPECTED);
        assertThat(applicationStatus.findByStatusCode(ApplicationStatusCode.LAST_PROCESSED_EVENT_HASH)).isEqualTo(EXPECTED); // Should see no sql query in logs
    }

    @Test
    void updateBypassEventHashMismatchUntilAfter() throws Exception {
        applicationStatus.updateBypassEventHashMismatchUntilAfter(EXPECTED);
        assertThat(applicationStatus.getBypassEventHashMismatchUntilAfter()).isEqualTo(EXPECTED);
    }

    @Test
    void updateBypassRecordHashMismatchUntilAfter() throws Exception {
        applicationStatus.updateBypassRecordHashMismatchUntilAfter(EXPECTED);
        assertThat(applicationStatus.getBypassRecordHashMismatchUntilAfter()).isEqualTo(EXPECTED);
    }

    @Test
    void updateLastProcessedEventHash() throws Exception {
        applicationStatus.updateLastProcessedEventHash(EXPECTED);
        assertThat(applicationStatus.getLastProcessedEventHash()).isEqualTo(EXPECTED);
    }

    @Test
    void updateLastProcessedRecordHash() throws Exception {
        applicationStatus.updateLastProcessedEventHash(EXPECTED);
        assertThat(applicationStatus.getLastProcessedEventHash()).isEqualTo(EXPECTED);
    }

    @Test
    void updateLastValidDownloadedBalanceFileName() throws Exception {
        applicationStatus.updateLastValidDownloadedBalanceFileName(EXPECTED);
        assertThat(applicationStatus.getLastValidDownloadedBalanceFileName()).isEqualTo(EXPECTED);
    }

    @Test
    void updateLastValidDownloadedEventFileHash() throws Exception {
        applicationStatus.updateLastValidDownloadedEventFileHash(EXPECTED);
        assertThat(applicationStatus.getLastValidDownloadedEventFileHash()).isEqualTo(EXPECTED);
    }

    @Test
    void updateLastValidDownloadedEventFileName() throws Exception {
        applicationStatus.updateLastValidDownloadedEventFileName(EXPECTED);
        assertThat(applicationStatus.getLastValidDownloadedEventFileName()).isEqualTo(EXPECTED);
    }

    @Test
    void updateLastValidDownloadedRecordFileHash() throws Exception {
        applicationStatus.updateLastValidDownloadedRecordFileHash(EXPECTED);
        assertThat(applicationStatus.getLastValidDownloadedRecordFileHash()).isEqualTo(EXPECTED);
    }

    @Test
    void updateLastValidDownloadedRecordFileName() throws Exception {
        applicationStatus.updateLastValidDownloadedRecordFileName(EXPECTED);
        assertThat(applicationStatus.getLastValidDownloadedRecordFileName()).isEqualTo(EXPECTED);
    }
}
