package com.hedera.mirror.importer.parser.record.entity;

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

import javax.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConditionOnEntityRecordParser
@ConfigurationProperties("hedera.mirror.importer.parser.record.entity")
public class EntityProperties {

    @NotNull
    private PersistProperties persist = new PersistProperties();

    @Data
    public class PersistProperties {

        private boolean claims = false;

        private boolean contracts = true;

        private boolean cryptoTransferAmounts = true;

        private boolean files = true;

        private boolean nonFeeTransfers = false;

        private boolean schedules = true;

        private boolean systemFiles = true;

        private boolean tokens = true;

        /**
         * If configured the mirror node will store the raw transaction bytes on the transaction table
         */
        private boolean transactionBytes = false;
    }
}
