/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.mirror.importer.ImporterIntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class HistoricalBalancePropertiesTest extends ImporterIntegrationTest {

    private final HistoricalBalanceProperties properties;

    @Test
    void conflictConfig() {
        properties.getBalanceDownloaderProperties().setEnabled(true);
        assertThatThrownBy(properties::init).isInstanceOf(IllegalArgumentException.class);
        properties.getBalanceDownloaderProperties().setEnabled(false);
    }
}
