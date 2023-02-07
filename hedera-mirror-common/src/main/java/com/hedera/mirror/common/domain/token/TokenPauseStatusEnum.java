package com.hedera.mirror.common.domain.token;

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

@Getter
@RequiredArgsConstructor
public enum TokenPauseStatusEnum {

    NOT_APPLICABLE(0),

    PAUSED(1),

    UNPAUSED(2);

    private final int id;

    private static final Map<Integer, TokenPauseStatusEnum> ID_MAP = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(TokenPauseStatusEnum::getId, Function
                    .identity()));

    public static TokenPauseStatusEnum fromId(int id) {
        return ID_MAP.getOrDefault(id, NOT_APPLICABLE);
    }
}
