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

package com.hedera.mirror.restjava.repository;

import static org.jooq.impl.DSL.noCondition;

import com.hedera.mirror.restjava.common.EntityIdRangeParameter;
import com.hedera.mirror.restjava.common.IntegerRangeParameter;
import com.hedera.mirror.restjava.common.RangeOperator;
import org.jooq.Condition;
import org.jooq.Field;

interface CustomRepository {

    default Condition getCondition(Field<Long> field, EntityIdRangeParameter param) {
        if (param == null) {
            return noCondition();
        }

        return getCondition(field, param.operator(), param.value().getId());
    }

    default Condition getCondition(Field<Long> field, IntegerRangeParameter param) {
        if (param == null) {
            return noCondition();
        }

        return getCondition(field, param.operator(), param.value().longValue());
    }

    default Condition getCondition(Field<Long> field, RangeOperator operator, Long value) {
        return operator.getFunction().apply(field, value);
    }
}
