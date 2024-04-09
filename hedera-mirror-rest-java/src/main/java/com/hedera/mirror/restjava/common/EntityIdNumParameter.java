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
import java.util.regex.Pattern;

public record EntityIdNumParameter(EntityId id) implements EntityIdParameter {

    public static final String ENTITY_ID_REGEX = "^((\\d{1,5})\\.)?((\\d{1,5})\\.)?(\\d{1,10})$";
    public static final Pattern ENTITY_ID_PATTERN = Pattern.compile(ENTITY_ID_REGEX);

    @Override
    public Long shard() {
        return id().getShard();
    }

    @Override
    public Long realm() {
        return id().getRealm();
    }

    public static EntityIdNumParameter valueOf(String id) {

        var matcher = ENTITY_ID_PATTERN.matcher(id);

        if (!matcher.matches()) {
            return null;
        }

        Long realm = 0L;
        Long shard = 0L;

        if (matcher.group(3) != null) {
            // This gets the shard and realm value
            realm = Long.parseLong(matcher.group(4));
            shard = Long.parseLong(matcher.group(2));

        } else if (matcher.group(1) != null) {
            // This gets the realm value and shard will be null
            realm = Long.parseLong(matcher.group(2));
        }

        var num = Long.parseLong(matcher.group(5));

        return new EntityIdNumParameter(EntityId.of(shard, realm, num));
    }
}
