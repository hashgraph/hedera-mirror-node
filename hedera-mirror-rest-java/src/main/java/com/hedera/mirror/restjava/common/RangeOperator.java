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

import java.util.function.BiFunction;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.Field;

@Getter
@RequiredArgsConstructor
@SuppressWarnings({"rawtypes", "unchecked"})
public enum RangeOperator {
    EQ("=", Field::eq),
    GT(">", Field::gt),
    GTE(">=", Field::ge),
    LT("<", Field::lt),
    LTE("<=", Field::le),
    NE("!=", Field::ne);

    private final String operator;
    private final BiFunction<Field, Object, Condition> function;

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
