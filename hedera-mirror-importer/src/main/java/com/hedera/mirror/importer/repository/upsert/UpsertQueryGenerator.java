package com.hedera.mirror.importer.repository.upsert;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

public interface UpsertQueryGenerator {

    String TEMP_SUFFIX = "_temp";

    String getCreateTempIndexQuery();

    default String getCreateTempTableQuery() {
        return String.format("create temporary table if not exists %s on commit drop as table %s limit 0",
                getTemporaryTableName(), getFinalTableName());
    }

    String getFinalTableName();

    default String getTemporaryTableName() {
        return getFinalTableName() + TEMP_SUFFIX;
    }

    String getUpsertQuery();
}
