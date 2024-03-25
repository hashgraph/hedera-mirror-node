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

package com.hedera.mirror.restjava.mapper;

import com.hedera.mirror.common.domain.entity.NftAllowance;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(config = RestJavaMapperConfig.class)
public interface NftAllowanceMapper {

    com.hedera.mirror.rest.model.NftAllowance map(NftAllowance source);

    default List<com.hedera.mirror.rest.model.NftAllowance> map(Collection<NftAllowance> source) {
        if (source == null) {
            return Collections.emptyList();
        }

        List<com.hedera.mirror.rest.model.NftAllowance> list = new ArrayList<>(source.size());
        for (NftAllowance allowance : source) {
            list.add(map(allowance));
        }

        return list;
    }
}
