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
import com.hedera.mirror.restjava.exception.InvalidParametersException;
import org.jetbrains.annotations.NotNull;

public record EntityIdRangeParameter(RangeOperator operator, EntityId value) implements RangeParameter<EntityId> {

    public static final EntityIdRangeParameter EMPTY = new EntityIdRangeParameter(null, null);

    public static EntityIdRangeParameter valueOf(String entityIdRangeParam) {

        if (entityIdRangeParam == null || entityIdRangeParam.isBlank()) {
            return EMPTY;
        }

        String[] splitVal = entityIdRangeParam.split(":");

        return switch (splitVal.length) {
            case 1 -> validateId(splitVal, 0, RangeOperator.EQ);
            case 2 -> validateId(splitVal, 1, RangeOperator.of(splitVal[0]));
            default -> throw new InvalidParametersException(
                    "Invalid range operator %s. Should have format rangeOperator:Id".formatted(entityIdRangeParam));
        };
    }

    @NotNull
    private static EntityIdRangeParameter validateId(String[] splitVal, int x, RangeOperator operator) {
        if (operator == RangeOperator.NE) {
            throw new InvalidParametersException("Invalid range operator ne. This operator is not supported");
        }
        return new EntityIdRangeParameter(operator, parseId(splitVal[x]));
    }
}
