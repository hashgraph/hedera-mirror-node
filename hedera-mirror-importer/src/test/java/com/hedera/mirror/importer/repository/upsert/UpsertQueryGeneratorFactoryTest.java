package com.hedera.mirror.importer.repository.upsert;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.annotation.Resource;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.schedule.Schedule;
import com.hedera.mirror.importer.IntegrationTest;

class UpsertQueryGeneratorFactoryTest extends IntegrationTest {

    @Resource
    private UpsertQueryGeneratorFactory factory;

    @Resource
    private ScheduleUpsertQueryGenerator scheduleUpsertQueryGenerator;

    @Test
    void unsupportedClass() {
        assertThatThrownBy(() -> factory.get(Object.class))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("not annotated with @Upsertable");
    }

    @Test
    void getExistingGenerator() {
        assertThat(factory.get(Schedule.class)).isEqualTo(scheduleUpsertQueryGenerator);
    }

    @Test
    void getGenericGenerator() {
        assertThat(factory.get(Contract.class)).isInstanceOf(GenericUpsertQueryGenerator.class);
    }

    @Test
    void contract() {
        String allColumns = "auto_renew_period,created_timestamp,deleted,expiration_timestamp,file_id,id,key," +
                "memo,num,obtainer_id,proxy_account_id,public_key,realm,shard,timestamp_range,type";

        String updatableColumns = "auto_renew_period,deleted,expiration_timestamp,key,memo,obtainer_id," +
                "proxy_account_id,public_key,timestamp_range";

        UpsertEntity upsertEntity = factory.createEntity(Contract.class);
        assertThat(upsertEntity)
                .isNotNull()
                .returns("contract", UpsertEntity::getTableName)
                .returns(true, e -> e.getUpsertable().history())
                .returns("id", e -> e.columns(UpsertColumn::isId, "{0}"))
                .returns("timestamp_range", e -> e.columns(UpsertColumn::isHistory, "{0}"))
                .returns(allColumns, e -> e.columns("{0}"))
                .returns(updatableColumns, e -> e.columns(UpsertColumn::isUpdatable, "{0}"))
                .extracting(UpsertEntity::getColumns, InstanceOfAssertFactories.ITERABLE)
                .hasSize(16);
    }

    @Test
    void entity() {
        String allColumns = "alias,auto_renew_account_id,auto_renew_period,created_timestamp,deleted," +
                "expiration_timestamp,id,key,max_automatic_token_associations,memo,num,proxy_account_id,public_key," +
                "realm,receiver_sig_required,shard,submit_key,timestamp_range,type";

        String updatableColumns = "auto_renew_account_id,auto_renew_period,deleted,expiration_timestamp,key," +
                "max_automatic_token_associations,memo,proxy_account_id,public_key,receiver_sig_required,submit_key," +
                "timestamp_range";

        UpsertEntity upsertEntity = factory.createEntity(Entity.class);
        assertThat(upsertEntity)
                .isNotNull()
                .returns("entity", UpsertEntity::getTableName)
                .returns(true, e -> e.getUpsertable().history())
                .returns("id", e -> e.columns(UpsertColumn::isId, "{0}"))
                .returns("timestamp_range", e -> e.columns(UpsertColumn::isHistory, "{0}"))
                .returns(allColumns, e -> e.columns("{0}"))
                .returns(updatableColumns, e -> e.columns(UpsertColumn::isUpdatable, "{0}"))
                .extracting(UpsertEntity::getColumns, InstanceOfAssertFactories.ITERABLE)
                .hasSize(19);
    }
}
