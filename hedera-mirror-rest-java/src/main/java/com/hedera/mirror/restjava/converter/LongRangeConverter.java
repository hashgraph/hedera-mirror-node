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

package com.hedera.mirror.restjava.converter;

import com.google.common.collect.BoundType;
import com.google.common.collect.Range;
import jakarta.validation.constraints.NotNull;
import org.jooq.postgres.extensions.types.LongRange;
import org.springframework.core.convert.converter.Converter;

public class LongRangeConverter implements Converter<LongRange, Range<Long>> {

    public static final LongRangeConverter INSTANCE = new LongRangeConverter();

    @Override
    public Range<Long> convert(@NotNull LongRange source) {
        var lower = source.lower();
        var lowerType = source.lowerIncluding() ? BoundType.CLOSED : BoundType.OPEN;
        var upper = source.upper();
        var upperType = source.upperIncluding() ? BoundType.CLOSED : BoundType.OPEN;

        if (lower == null && upper == null) {
            return Range.all();
        } else if (upper == null) {
            return Range.downTo(lower, lowerType);
        } else if (lower == null) {
            return Range.upTo(upper, upperType);
        } else {
            return Range.range(lower, lowerType, upper, upperType);
        }
    }
}
