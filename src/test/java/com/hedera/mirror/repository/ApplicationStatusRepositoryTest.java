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

    @Resource
    private ApplicationStatusRepository applicationStatusRepository;

    @Test
    void updateStatusValue() {
        String expected = "value1";
        applicationStatusRepository.updateStatusValue(ApplicationStatusCode.LAST_PROCESSED_EVENT_HASH, expected);
        assertThat(applicationStatusRepository.findByStatusCode(ApplicationStatusCode.LAST_PROCESSED_EVENT_HASH)).isEqualTo(expected);

        // Check cache invalidation
        expected = "value2";
        applicationStatusRepository.updateStatusValue(ApplicationStatusCode.LAST_PROCESSED_EVENT_HASH, expected);
        assertThat(applicationStatusRepository.findByStatusCode(ApplicationStatusCode.LAST_PROCESSED_EVENT_HASH)).isEqualTo(expected);
    }
}
