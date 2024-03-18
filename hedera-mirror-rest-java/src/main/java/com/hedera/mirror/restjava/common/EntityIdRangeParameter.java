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

import static com.hedera.mirror.restjava.common.Utils.parseId;

import com.hedera.mirror.common.domain.entity.EntityId;
import org.jetbrains.annotations.NotNull;

public record EntityIdRangeParameter(RangeOperator operator, EntityId value) implements RangeParameter<EntityId> {

    public static final EntityIdRangeParameter EMPTY = new EntityIdRangeParameter(null, null);

    public static EntityIdRangeParameter valueOf(String entityIdRangeParam) {

        if (entityIdRangeParam == null || entityIdRangeParam.isBlank()) {
            return EMPTY;
        }

        String[] splitVal = entityIdRangeParam.split(":");

        if (splitVal.length == 1) {
            // No operator specified. Just use "eq:"
            return validateId(splitVal, 0, RangeOperator.EQ);
        }
        return validateId(splitVal, 1, RangeOperator.valueOf(splitVal[0].toUpperCase()));
    }

    @NotNull
    private static EntityIdRangeParameter validateId(String[] splitVal, int x, RangeOperator eq) {
        var id = parseId(splitVal[x]);
        return new EntityIdRangeParameter(eq, EntityId.of(id[0], id[1], id[2]));
    }

    @Override
    public RangeOperator getOperator() {
        return operator;
    }

    @Override
    public EntityId getValue() {
        return value;
    }
}
