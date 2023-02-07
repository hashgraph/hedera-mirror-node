package com.hedera.mirror.common.domain.entity;

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

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@Getter
@RequiredArgsConstructor
public enum EntityType {

    UNKNOWN(0),
    ACCOUNT(1),
    CONTRACT(2),
    FILE(3),
    TOPIC(4),
    TOKEN(5),
    SCHEDULE(6);

    private static final Map<Integer, EntityType> ID_MAP = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(EntityType::getId, Function.identity()));

    private final int id;

    public static EntityType fromId(int id) {
        return ID_MAP.getOrDefault(id, UNKNOWN);
    }

    public String toDisplayString() {
        return StringUtils.capitalize(name().toLowerCase());
    }
}
