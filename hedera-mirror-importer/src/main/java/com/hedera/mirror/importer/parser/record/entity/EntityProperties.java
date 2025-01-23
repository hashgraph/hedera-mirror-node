/*
 * Copyright (C) 2019-2025 Hedera Hashgraph, LLC
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

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.EnumSet;
import java.util.Set;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@ConfigurationProperties("hedera.mirror.importer.parser.record.entity")
@Validated
public class EntityProperties {

    @NotNull
    @Valid
    private PersistProperties persist = new PersistProperties();

    @Data
    @Validated
    public static class PersistProperties {

        private boolean claims = false;

        private boolean contracts = true;
        private boolean contractResults = true;
        private boolean contractTransaction = true;
        private boolean contractTransactionHash = true;
        private boolean cryptoTransferAmounts = true;

        private boolean entityHistory = true;

        /**
         * A set of entity ids to exclude from entity_transaction table
         */
        @NotNull
        private Set<EntityId> entityTransactionExclusion = Set.of(EntityId.of(98), EntityId.of(800));

        private boolean entityTransactions = false;

        private boolean ethereumTransactions = true;

        private boolean files = true;

        private boolean itemizedTransfers = false;

        private boolean nodes = true;

        private boolean pendingReward = true;

        private boolean schedules = true;

        private boolean syntheticContractLogs = true;

        private boolean syntheticContractResults = false;

        private boolean systemFiles = true;

        private boolean tokens = true;

        private boolean tokenAirdrops = true;

        private boolean topics = true;

        private boolean topicMessageLookups = false;

        private boolean trackAllowance = true;

        private boolean trackBalance = true;

        private boolean trackNonce = true;

        private boolean transactionHash = true;

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

        /**
         * If configured the mirror node will store the raw transaction record bytes on the transaction table.
         */
        private boolean transactionRecordBytes = false;

        @NotNull
        private Set<TransactionType> transactionSignatures = EnumSet.of(SCHEDULECREATE, SCHEDULESIGN);

        public boolean isTokenAirdrops() {
            return tokenAirdrops && tokens;
        }

        public boolean shouldPersistEntityTransaction(EntityId entityId) {
            return entityTransactions && !EntityId.isEmpty(entityId) && !entityTransactionExclusion.contains(entityId);
        }

        public boolean shouldPersistTransactionHash(TransactionType transactionType) {
            return transactionHash
                    && (transactionHashTypes.isEmpty() || transactionHashTypes.contains(transactionType));
        }
    }
}
