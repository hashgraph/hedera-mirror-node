/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.restjava.repository;

import com.hedera.mirror.common.domain.entity.NftAllowance;
import jakarta.inject.Named;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.data.domain.Pageable;

@CustomLog
@Named
@RequiredArgsConstructor
class NftAllowanceRepositoryCustomImpl implements NftAllowanceRepositoryCustom {

    private final DSLContext dslContext;

    //    @Getter(lazy = true)
    //    private final Map<String, Field> fieldsMap = getFieldsForTable(NFT_ALLOWANCE);

    @Override
    public Collection<NftAllowance> findByFilters(List<Filter<?>> filters, Pageable pageable) {
        //        var whereStep = dslContext.selectFrom(NFT_ALLOWANCE)
        //                .where(NFT_ALLOWANCE.APPROVED_FOR_ALL.eq(false))
        //                .

        //        if (!filters.isEmpty()) {
        //            var fields = getFieldsMap();
        //            for (var filter : filters) {
        //                var field = fields.get(filter.getName());
        //                if (field == null) {
        //                    log.warn("Ignoring filter with unknown field: {}", filter.getName());
        //                    continue;
        //                }
        //
        //                field.eq(filter.getValue());
        //            }
        //        }
        //        NFT_ALLOWANCE.APPROVED_FOR_ALL.eq(false);
        //        dslContext.selectFrom(NFT_ALLOWANCE);
        //        not(NFT_ALLOWANCE.OWNER.eq(2L));
        return Collections.emptyList();
    }

    //    private static Map<String, Field> getFieldsForTable(Table<?> table) {
    //        return Arrays.stream(table.fields())
    //                        .collect(Collectors.toMap(field -> field.getUnqualifiedName().unquotedName().toString(),
    // field -> field));
    //    }
}
