package com.hedera.mirror.importer.repository;

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

import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.List;
import javax.inject.Named;
import javax.persistence.metamodel.SingularAttribute;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import com.hedera.mirror.importer.domain.Schedule_;

@Component
@Named
@RequiredArgsConstructor
public class ScheduleRepositoryCustomImpl extends AbstractUpdatableDomainRepositoryCustom<Schedule_> {
    public static final String TABLE = "schedule";
    public static final String TEMP_TABLE = "schedule_temp";
    private static final List<String> conflictTargetColumns = List.of(Schedule_.SCHEDULE_ID);
    private static final List<String> nullableColumns = List.of(Schedule_.EXECUTED_TIMESTAMP);
    private static final List<SingularAttribute> updatableColumns = Lists.newArrayList(Schedule_.executedTimestamp);

    @Override
    public String getTableName() {
        return TABLE;
    }

    @Override
    public String getTemporaryTableName() {
        return TEMP_TABLE;
    }

    @Override
    public List<String> getConflictIdColumns() {
        return conflictTargetColumns;
    }

    @Getter // using Lombok getter to implement getSelectableColumns, null or empty list implies select all fields
    // JPAMetaModelEntityProcessor does not expand embeddedId fields, as such they need to be explicitly referenced
    public List<SingularAttribute> selectableColumns = Collections.emptyList();
//    @Override
//    public List<SingularAttribute> getSelectableColumns() {
//        // null or empty list implies select all fields
//        return Collections.emptyList();
//    }

    @Override
    public List<SingularAttribute> getUpdatableColumns() {
        return updatableColumns;
    }

    @Override
    public boolean isNullableColumn(String columnName) {
        return nullableColumns.contains(columnName);
    }

    @Override
    public boolean shouldUpdateOnConflict() {
        return true;
    }
}
