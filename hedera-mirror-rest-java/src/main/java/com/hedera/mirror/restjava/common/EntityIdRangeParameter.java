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

package com.hedera.mirror.restjava.common;

import com.hedera.mirror.common.domain.entity.EntityId;
import org.jooq.tools.StringUtils;

public record EntityIdRangeParameter(RangeOperator operator, EntityId value) implements RangeParameter<EntityId> {

    public static final EntityIdRangeParameter EMPTY = new EntityIdRangeParameter(null, null);

    public static EntityIdRangeParameter valueOf(String entityIdRangeParam) {

        if (StringUtils.isBlank(entityIdRangeParam)) {
            return EMPTY;
        }

        String[] splitVal = entityIdRangeParam.split(":");

        return switch (splitVal.length) {
            case 1 -> new EntityIdRangeParameter(RangeOperator.EQ, EntityId.of(splitVal[0]));
            case 2 -> new EntityIdRangeParameter(RangeOperator.of(splitVal[0]), EntityId.of(splitVal[1]));
            default -> throw new IllegalArgumentException(
                    "Invalid range operator %s. Should have format rangeOperator:Id".formatted(entityIdRangeParam));
        };
    }
}
