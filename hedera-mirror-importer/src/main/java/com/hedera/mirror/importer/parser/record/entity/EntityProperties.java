/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.record.entity;

import static com.hedera.mirror.common.domain.transaction.TransactionType.CONSENSUSSUBMITMESSAGE;
import static com.hedera.mirror.common.domain.transaction.TransactionType.SCHEDULECREATE;
import static com.hedera.mirror.common.domain.transaction.TransactionType.SCHEDULESIGN;

import com.hedera.mirror.common.domain.transaction.TransactionType;
import jakarta.validation.constraints.NotNull;
import java.util.EnumSet;
import java.util.Set;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("hedera.mirror.importer.parser.record.entity")
public class EntityProperties {

    @NotNull
    private PersistProperties persist = new PersistProperties();

    @Data
    public static class PersistProperties {

        private boolean claims = false;

        private boolean contracts = true;

        private boolean contractResults = true;

        private boolean cryptoTransferAmounts = true;

        private boolean ethereumTransactions = true;

        private boolean files = true;

        private boolean nonFeeTransfers = false;

        private boolean pendingReward = true;

        private boolean schedules = true;

        private boolean syntheticContractLogs = true;

        private boolean syntheticContractResults = true;

        private boolean systemFiles = true;

        private boolean tokens = true;

        private boolean topics = true;

        private boolean topicMessageLookups = true;

        private boolean trackBalance = true;

        private boolean transactionHash = false;

        /**
         * A set of transaction types to persist transaction hash for. If empty and transactionHash is true, transaction
         * hash of all transaction types will be persisted
         */
        @NotNull
        private Set<TransactionType> transactionHashTypes = EnumSet.complementOf(EnumSet.of(CONSENSUSSUBMITMESSAGE));

        /**
         * If configured the mirror node will store the raw transaction bytes on the transaction table
         */
        private boolean transactionBytes = false;

        @NotNull
        private Set<TransactionType> transactionSignatures = EnumSet.of(SCHEDULECREATE, SCHEDULESIGN);

        public boolean shouldPersistTransactionHash(TransactionType transactionType) {
            return transactionHash
                    && (transactionHashTypes.isEmpty() || transactionHashTypes.contains(transactionType));
        }
    }
}
