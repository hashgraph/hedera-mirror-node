/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.restjava.common.EntityIdParameter.DEFAULT_SHARD;

import com.google.common.base.Splitter;
import com.hedera.mirror.common.domain.entity.EntityId;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

public record EntityIdRangeParameter(RangeOperator operator, Long value) implements RangeParameter<Long> {

    public static final EntityIdRangeParameter EMPTY = new EntityIdRangeParameter(null, EntityId.EMPTY);

    public EntityIdRangeParameter(RangeOperator operator, EntityId entityId) {
        this(operator, entityId.getId());
    }

    public static EntityIdRangeParameter valueOf(String entityIdRangeParam) {
        if (StringUtils.isBlank(entityIdRangeParam)) {
            return EMPTY;
        }

        var splitVal = entityIdRangeParam.split(":");
        return switch (splitVal.length) {
            case 1 -> new EntityIdRangeParameter(RangeOperator.EQ, getEntityId(splitVal[0]));
            case 2 -> new EntityIdRangeParameter(RangeOperator.of(splitVal[0]), getEntityId(splitVal[1]));
            default -> throw new IllegalArgumentException(
                    "Invalid range operator %s. Should have format rangeOperator:Id".formatted(entityIdRangeParam));
        };
    }

    private static EntityId getEntityId(String entityId) {
        List<Long> parts = Splitter.on('.')
                .splitToStream(Objects.requireNonNullElse(entityId, ""))
                .map(Long::valueOf)
                .filter(n -> n >= 0)
                .toList();

        if (parts.size() != StringUtils.countMatches(entityId, ".") + 1) {
            throw new IllegalArgumentException("Invalid entity ID: " + entityId);
        }

        return switch (parts.size()) {
            case 1 -> EntityId.of(DEFAULT_SHARD, 0, parts.get(0));
            case 2 -> EntityId.of(DEFAULT_SHARD, parts.get(0), parts.get(1));
            case 3 -> EntityId.of(parts.get(0), parts.get(1), parts.get(2));
            default -> throw new IllegalArgumentException("Invalid entity ID: " + entityId);
        };
    }
}
