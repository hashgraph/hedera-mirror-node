package com.hedera.mirror.monitor.converter;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

public class StringToDurationDeserializer extends StdDeserializer<Duration> {

    private static final Pattern PATTERN = Pattern.compile("(\\d+d)?(\\d+h)?(\\d+m)?(\\d+s)?");
    private static final long serialVersionUID = 3690958538780466689L;

    protected StringToDurationDeserializer() {
        super(Duration.class);
    }

    @Override
    public Duration deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String text = p.getValueAsString();
        if (StringUtils.isBlank(text)) {
            return null;
        }

        Matcher matcher = PATTERN.matcher(text);

        if (matcher.matches() && matcher.groupCount() > 0) {
            Duration duration = Duration.ZERO;
            String days = matcher.group(1);
            if (StringUtils.isNotBlank(days)) {
                duration = duration.plusDays(Long.valueOf(days.replace("d", "")));
            }

            String hours = matcher.group(2);
            if (StringUtils.isNotBlank(hours)) {
                duration = duration.plusHours(Long.valueOf(hours.replace("h", "")));
            }

            String minutes = matcher.group(3);
            if (StringUtils.isNotBlank(minutes)) {
                duration = duration.plusMinutes(Long.valueOf(minutes.replace("m", "")));
            }

            String seconds = matcher.group(4);
            if (StringUtils.isNotBlank(seconds)) {
                duration = duration.plusSeconds(Long.valueOf(seconds.replace("s", "")));
            }

            return duration;
        }

        return null;
    }
}
