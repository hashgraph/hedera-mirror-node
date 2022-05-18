package com.hedera.mirror.importer.repository.upsert;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import lombok.RequiredArgsConstructor;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.common.domain.Upsertable;
import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.importer.IntegrationTest;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class EntityMetadataRegistryTest extends IntegrationTest {

    private final EntityMetadataRegistry registry;

    @Test
    void lookup() {
        var all = "auto_renew_account_id,auto_renew_period,created_timestamp,deleted,evm_address," +
                "expiration_timestamp,file_id,id,initcode,key,max_automatic_token_associations,memo,num,obtainer_id," +
                "permanent_removal,proxy_account_id,public_key,realm,shard,timestamp_range,type";
        var nullable = "auto_renew_account_id,auto_renew_period,created_timestamp,deleted,evm_address," +
                "expiration_timestamp,file_id,initcode,key,max_automatic_token_associations,obtainer_id," +
                "permanent_removal,proxy_account_id,public_key";
        var updatable = "auto_renew_account_id,auto_renew_period,deleted,expiration_timestamp,key," +
                "max_automatic_token_associations,memo,obtainer_id,permanent_removal,proxy_account_id,public_key," +
                "timestamp_range";

        var contract = domainBuilder.contract().get();
        var newValue = -99999L;
        var metadata = registry.lookup(Contract.class);

        assertThat(metadata)
                .isNotNull()
                .returns("contract", EntityMetadata::getTableName)
                .returns(true, e -> e.getUpsertable().history())
                .returns("id", e -> e.columns(ColumnMetadata::isId, "{0}"))
                .returns(all, e -> e.columns("{0}"))
                .returns(nullable, e -> e.columns(ColumnMetadata::isNullable, "{0}"))
                .returns(updatable, e -> e.columns(ColumnMetadata::isUpdatable, "{0}"))
                .extracting(EntityMetadata::getColumns, InstanceOfAssertFactories.ITERABLE)
                .hasSize(21)
                .first(InstanceOfAssertFactories.type(ColumnMetadata.class))
                .returns("auto_renew_account_id", ColumnMetadata::getName)
                .returns(Long.class, ColumnMetadata::getType)
                .returns(false, ColumnMetadata::isId)
                .returns(true, ColumnMetadata::isNullable)
                .returns(null, ColumnMetadata::getDefaultValue)
                .returns(true, ColumnMetadata::isUpdatable)
                .satisfies(cm -> assertThat(cm.getGetter().apply(contract)).isEqualTo(contract.getAutoRenewAccountId()))
                .satisfies(cm -> assertThatCode(() -> cm.getSetter().accept(contract, newValue))
                        .satisfies(d -> assertThat(contract.getAutoRenewAccountId()).isEqualTo(newValue)));
    }

    @Test
    void notUpsertable() {
        assertThatThrownBy(() -> registry.lookup(Object.class)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void notEntity() {
        assertThatThrownBy(() -> registry.lookup(NonEntity.class)).isInstanceOf(IllegalArgumentException.class);
    }

    @Upsertable
    private static class NonEntity {
    }
}
