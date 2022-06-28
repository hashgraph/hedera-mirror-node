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

import java.text.MessageFormat;
import java.util.Comparator;
import java.util.function.BiConsumer;
import java.util.function.Function;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

import com.hedera.mirror.common.domain.UpsertColumn;

@Value
class ColumnMetadata implements Comparable<ColumnMetadata> {

    private final Object defaultValue;
    private final Function<Object, Object> getter;
    private final boolean id;
    private final String name;
    private final boolean nullable;
    private final BiConsumer<Object, Object> setter;
    private final Class<?> type;
    private final boolean updatable;
    private final UpsertColumn upsertColumn;

    @Override
    public int compareTo(ColumnMetadata other) {
        return Comparator.comparing(ColumnMetadata::getName).compare(this, other);
    }

    String format(String pattern) {
        var coalesce = upsertColumn != null ? upsertColumn.coalesce() : null;

        if (pattern.contains("coalesce") && StringUtils.isNotBlank(coalesce)) {
            pattern = coalesce;
        }

        return MessageFormat.format(pattern, name, defaultValue);
    }
}
