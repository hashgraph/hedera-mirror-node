package com.hedera.mirror.importer.parser.performance;

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

import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Log4j2
@Tag("largedbperf")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RestoreClientIntegrationTest extends PerformanceIntegrationTest {

    @Container
    private GenericContainer customContainer;

    @BeforeAll
    void warmUp() {
        customContainer = createRestoreContainer("100k");
    }

    @Test
    public void parseAndIngestTransactions() throws Exception {
        checkSeededTablesArePresent();
        clearLastProcessedRecordHash();
        parse();
    }
}
