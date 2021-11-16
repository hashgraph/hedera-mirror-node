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

import java.text.MessageFormat;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.Value;

import com.hedera.mirror.importer.domain.Upsertable;

/**
 * Contains the metadata associated with an @Upsertable entity. Used to generate dynamic upsert SQL.
 */
@Value
class UpsertEntity {

    private final String tableName;
    private final Upsertable upsertable;
    private final Set<UpsertColumn> columns;

    public String columns(String pattern) {
        return columns(c -> true, pattern, ",");
    }

    public String columns(Predicate<UpsertColumn> filter, String pattern) {
        return columns(filter, pattern, ",");
    }

    public String columns(Predicate<UpsertColumn> filter, String pattern, String separator) {
        return columns.stream()
                .filter(filter)
                .map(c -> MessageFormat.format(pattern, c.getName(), c.getDefaultValue()))
                .collect(Collectors.joining(separator));
    }
}
