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

package com.hedera.mirror.restjava.mapper;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.rest.model.TimestampRange;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.Mapper;
import org.mapstruct.MappingInheritanceStrategy;

import static com.hedera.mirror.restjava.common.Constants.NANO_DIGITS;

@Mapper(mappingInheritanceStrategy = MappingInheritanceStrategy.AUTO_INHERIT_FROM_CONFIG)
public interface CommonMapper {

    default String mapEntityId(Long source) {
        if (source == null || source == 0) {
            return null;
        }

        var eid = EntityId.of(source);
        return mapEntityId(eid);
    }

    default String mapEntityId(com.hedera.mirror.common.domain.entity.EntityId source) {
        return source != null ? source.toString() : null;
    }

    default TimestampRange mapRange(Range<Long> source) {
        if (source == null) {
            return null;
        }

        var target = new TimestampRange();
        if (source.hasLowerBound()) {
            target.setFrom(mapTimestamp(source.lowerEndpoint()));
        }

        if (source.hasUpperBound()) {
            target.setTo(mapTimestamp(source.upperEndpoint()));
        }

        return target;
    }

    default String mapTimestamp(long timestamp) {
        if (timestamp == 0) {
            return "0.0";
        }

        var timestampString = StringUtils.leftPad(String.valueOf(timestamp), 10, '0');

        var nanos = timestampString.substring(timestampString.length() - NANO_DIGITS);
        var secs = timestampString.substring(0, timestampString.length() - NANO_DIGITS);
        return secs + "." + nanos;
    }
}
