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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.time.Duration;

public class DurationToStringSerializer extends StdSerializer<Duration> {

    private static final long serialVersionUID = 5848583700556532429L;

    protected DurationToStringSerializer() {
        super(Duration.class);
    }

    @Override
    public void serialize(Duration duration, JsonGenerator jsonGenerator, SerializerProvider provider) throws IOException {
        jsonGenerator.writeString(convert(duration));
    }

    public static String convert(Duration duration) {
        if (duration == null) {
            return null;
        }

        StringBuilder s = new StringBuilder();

        if (duration.toDaysPart() > 0) {
            s.append(duration.toDaysPart()).append("d");
        }

        if (duration.toHoursPart() > 0) {
            s.append(duration.toHoursPart()).append("h");
        }

        if (duration.toMinutesPart() > 0) {
            s.append(duration.toMinutesPart()).append("m");
        }

        if (duration.toSecondsPart() > 0 || s.length() == 0) {
            s.append(duration.toSecondsPart()).append("s");
        }

        return s.toString();
    }
}
