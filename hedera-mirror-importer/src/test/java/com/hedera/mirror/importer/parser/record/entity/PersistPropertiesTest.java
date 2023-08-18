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

package com.hedera.mirror.importer.parser.record.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PersistPropertiesTest {

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
            , false
            0.0.0, false
            0.0.10, true
            0.0.98, false
            0.0.800, false
            """)
    void shouldPersistEntityTransaction(String entityIdStr, boolean expected) {
        var persistProperties = new EntityProperties.PersistProperties();
        persistProperties.setEntityTransactions(true);
        var entityId = entityIdStr != null ? EntityId.of(entityIdStr, EntityType.ACCOUNT) : null;
        assertThat(persistProperties.shouldPersistEntityTransaction(entityId)).isEqualTo(expected);
    }

    @Test
    void shouldPersistEntityTransactionWhenDisabled() {
        var persistProperties = new EntityProperties.PersistProperties();
        persistProperties.setEntityTransactions(false);
        assertThat(persistProperties.shouldPersistEntityTransaction(null)).isFalse();
        assertThat(persistProperties.shouldPersistEntityTransaction(EntityId.EMPTY))
                .isFalse();
        assertThat(persistProperties.shouldPersistEntityTransaction(EntityId.of(10, EntityType.ACCOUNT)))
                .isFalse();
        assertThat(persistProperties.shouldPersistEntityTransaction(EntityId.of(98, EntityType.ACCOUNT)))
                .isFalse();
        assertThat(persistProperties.shouldPersistEntityTransaction(EntityId.of(800, EntityType.ACCOUNT)))
                .isFalse();
    }

    @Test
    void shouldPersistEntityTransactionWithCustomExclusion() {
        var persistProperties = new EntityProperties.PersistProperties();
        persistProperties.setEntityTransactions(true);
        persistProperties.setEntityTransactionExclusion(Set.of(EntityId.of(10, EntityType.ACCOUNT)));
        assertThat(persistProperties.shouldPersistEntityTransaction(null)).isFalse();
        assertThat(persistProperties.shouldPersistEntityTransaction(EntityId.EMPTY))
                .isFalse();
        assertThat(persistProperties.shouldPersistEntityTransaction(EntityId.of(10, EntityType.ACCOUNT)))
                .isFalse();
        assertThat(persistProperties.shouldPersistEntityTransaction(EntityId.of(98, EntityType.ACCOUNT)))
                .isTrue();
        assertThat(persistProperties.shouldPersistEntityTransaction(EntityId.of(800, EntityType.ACCOUNT)))
                .isTrue();
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
            true, CRYPTOTRANSFER, true,
            true, CONSENSUSSUBMITMESSAGE, false,
            false, CRYPTOTRANSFER, false,
            false, CONSENSUSSUBMITMESSAGE, false,
            """)
    void shouldPersistTransactionHash(boolean transactionHash, TransactionType transactionType, boolean expected) {
        var persistProperties = new EntityProperties.PersistProperties();
        persistProperties.setTransactionHash(transactionHash);
        persistProperties.setTransactionHashTypes(Set.of(transactionType));
        assertThat(persistProperties.shouldPersistTransactionHash(TransactionType.CRYPTOTRANSFER))
                .isEqualTo(expected);
        assertThat(persistProperties.shouldPersistTransactionHash(TransactionType.CONTRACTCALL))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldPersistTransactionHashWhenEmptyFilter(boolean transactionHash) {
        var persistProperties = new EntityProperties.PersistProperties();
        persistProperties.setTransactionHash(transactionHash);
        persistProperties.setTransactionHashTypes(Collections.emptySet());
        assertThat(persistProperties.shouldPersistTransactionHash(TransactionType.CRYPTOTRANSFER))
                .isEqualTo(transactionHash);
        assertThat(persistProperties.shouldPersistTransactionHash(TransactionType.CONTRACTCALL))
                .isEqualTo(transactionHash);
    }
}
