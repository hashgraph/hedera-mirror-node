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

import lombok.experimental.UtilityClass;

@UtilityClass
public class EntityIdUtils {

    public static Long[] parseIdFromString(String id) {
        var parts = id.split("\\.");
        if (parts.length == 1) {
            return new Long[] {0L, 0L, Long.parseLong(id)};
        }
        return new Long[] {Long.parseLong(parts[0]), Long.parseLong(parts[1]), Long.parseLong(parts[2])};
    }
}
