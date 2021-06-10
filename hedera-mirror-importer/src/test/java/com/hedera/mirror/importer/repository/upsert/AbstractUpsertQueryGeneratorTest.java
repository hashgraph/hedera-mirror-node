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

import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.converter.NullableStringSerializer;
import com.hedera.mirror.importer.repository.AbstractRepositoryTest;

public abstract class AbstractUpsertQueryGeneratorTest extends AbstractRepositoryTest {

    protected abstract UpsertQueryGenerator getUpdatableDomainRepositoryCustom();

    protected abstract String getInsertQuery();

    protected abstract String getUpdateQuery();

    @Test
    void insert() {
        String insertQuery = getUpdatableDomainRepositoryCustom().getInsertQuery()
                .replaceAll(NullableStringSerializer.NULLABLE_STRING_REPLACEMENT, "<uuid>");
        assertThat(insertQuery).isEqualTo(getInsertQuery());
    }

    @Test
    void update() {
        String updateQuery = getUpdatableDomainRepositoryCustom().getUpdateQuery()
                .replaceAll(NullableStringSerializer.NULLABLE_STRING_REPLACEMENT, "<uuid>");
        assertThat(updateQuery).isEqualTo(getUpdateQuery());
    }
}
