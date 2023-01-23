package com.hedera.mirror.graphql.util;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import com.google.common.base.Splitter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;
import lombok.experimental.UtilityClass;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.graphql.viewmodel.HbarUnit;
import com.hedera.mirror.graphql.viewmodel.Node;

@UtilityClass
public class GraphQlUtils {

    private static final Splitter SPLITTER = Splitter.on(':');

    public static Long convertCurrency(HbarUnit unit, Long tinybars) {
        if (tinybars == null) {
            return null;
        }

        if (unit == null) {
            return tinybars;
        }

        return switch (unit) {
            case TINYBAR -> tinybars;
            case MICROBAR -> tinybars / 100L;
            case MILIBAR -> tinybars / 100_000L;
            case HBAR -> tinybars / 100_000_000L;
            case KILOBAR -> tinybars / 100_000_000_000L;
            case MEGABAR -> tinybars / 100_000_000_000_000L;
            case GIGABAR -> tinybars / 100_000_000_000_000_000L;
        };
    }

    public static <T> T getId(Node node, Function<List<String>, T> converter) {
        var id = new String(Base64.getDecoder().decode(node.getId()), StandardCharsets.UTF_8);
        var parts = SPLITTER.splitToList(id);

        if (parts.size() <= 1) {
            throw new IllegalArgumentException("Invalid Node.id");
        }

        var clazz = node.getClass();
        if (!clazz.getSimpleName().equals(parts.get(0))) {
            throw new IllegalArgumentException("Invalid Node.id");
        }

        return converter.apply(parts.subList(1, parts.size()));
    }

    public static EntityId toEntityId(com.hedera.mirror.graphql.viewmodel.EntityIdInput entityId) {
        return EntityId.of(entityId.getShard(),
                entityId.getRealm(),
                entityId.getNum(),
                EntityType.UNKNOWN);
    }

    public static void validateOneOf(Object... values) {
        int nonNull = 0;

        for (var value : values) {
            if (value != null) {
                ++nonNull;
            }
        }

        if (nonNull != 1) {
            throw new IllegalArgumentException("Must provide exactly one input value but " + nonNull + " have been " +
                    "provided");
        }
    }
}
