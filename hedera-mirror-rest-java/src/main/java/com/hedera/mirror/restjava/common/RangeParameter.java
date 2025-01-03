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

public interface RangeParameter<T> {

    RangeOperator operator();

    T value();

    // Considering EQ in the same category as GT,GTE as an assumption
    default boolean hasLowerBound() {
        return operator() == RangeOperator.GT || operator() == RangeOperator.GTE || operator() == RangeOperator.EQ;
    }

    default boolean hasUpperBound() {
        return operator() == RangeOperator.LT || operator() == RangeOperator.LTE;
    }

    default boolean isEmpty() {
        return operator() == null && value() == null;
    }
}
