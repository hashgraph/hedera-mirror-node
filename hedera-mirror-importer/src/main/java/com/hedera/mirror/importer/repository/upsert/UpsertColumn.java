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

import java.util.Comparator;
import lombok.Value;

@Value
class UpsertColumn implements Comparable<UpsertColumn> {

    private final Object defaultValue;
    private final boolean history;
    private final boolean id;
    private final String name;
    private final boolean updatable;

    @Override
    public int compareTo(UpsertColumn other) {
        return Comparator.comparing(UpsertColumn::getName).compare(this, other);
    }
}
