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

import com.hedera.mirror.restjava.RestJavaProperties;
import org.apache.commons.lang3.StringUtils;

public sealed interface EntityIdParameter
        permits EntityIdNumParameter, EntityIdEvmAddressParameter, EntityIdAliasParameter {

    Long DEFAULT_SHARD =
            SpringApplicationContext.getBean(RestJavaProperties.class).getShard();

    static EntityIdParameter valueOf(String id) {
        if (StringUtils.isBlank(id)) {
            throw new IllegalArgumentException("Missing or empty ID");
        }

        EntityIdParameter entityId;

        if ((entityId = EntityIdNumParameter.valueOf(id)) != null) {
            return entityId;
        } else if ((entityId = EntityIdEvmAddressParameter.valueOf(id)) != null) {
            return entityId;
        } else if ((entityId = EntityIdAliasParameter.valueOf(id)) != null) {
            return entityId;
        } else {
            throw new IllegalArgumentException("Unsupported ID format: %s".formatted(id));
        }
    }

    long shard();

    long realm();
}
