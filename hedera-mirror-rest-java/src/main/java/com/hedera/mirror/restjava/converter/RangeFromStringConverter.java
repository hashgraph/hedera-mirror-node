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
import jakarta.inject.Named;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.util.StringUtils;

@Named
@ConfigurationPropertiesBinding
@SuppressWarnings("java:S5842") // Upper and lower bounds in regex may be empty and must still match.
public class RangeFromStringConverter implements Converter<String, Range<Long>> {
    private static final String LOWER_CLOSED = "[";
    private static final String UPPER_CLOSED = "]";

    private static final String RANGE_REGEX = "^([\\[(])?(\\d*)?,(\\d*)?([])])$";
    private static final Pattern RANGE_PATTERN = Pattern.compile(RANGE_REGEX);

    @Override
    public Range<Long> convert(String source) {
        if (!StringUtils.hasText(source)) {
            return null;
        }

        var matcher = RANGE_PATTERN.matcher(source);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Range string is not valid, '%s'".formatted(source));
        }

        var lowerValueStr = matcher.group(2);
        var lowerValue = StringUtils.hasText(lowerValueStr) ? Long.parseLong(lowerValueStr) : null;

        var upperValueStr = matcher.group(3);
        var upperValue = StringUtils.hasText(upperValueStr) ? Long.parseLong(upperValueStr) : null;
        var upperBoundType = UPPER_CLOSED.equals(matcher.group(4)) ? BoundType.CLOSED : BoundType.OPEN;

        Range<Long> range;
        if (lowerValue != null) {
            var lowerBoundType = LOWER_CLOSED.equals(matcher.group(1)) ? BoundType.CLOSED : BoundType.OPEN;
            range = upperValue != null
                    ? Range.range(lowerValue, lowerBoundType, upperValue, upperBoundType)
                    : Range.downTo(lowerValue, lowerBoundType);
        } else {
            range = upperValue != null ? Range.upTo(upperValue, upperBoundType) : Range.all();
        }

        return range;
    }
}
