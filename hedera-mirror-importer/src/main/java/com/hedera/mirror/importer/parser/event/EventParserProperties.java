package com.hedera.mirror.importer.parser.event;

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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.parser.AbstractParserProperties;

@Component("eventParserProperties")
@Data
@Validated
@ConfigurationProperties("hedera.mirror.importer.parser.event")
public class EventParserProperties extends AbstractParserProperties {

    private final MirrorProperties mirrorProperties;

    public EventParserProperties(MirrorProperties mirrorProperties) {
        this.mirrorProperties = mirrorProperties;
        setEnabled(false);
    }

    @Override
    public StreamType getStreamType() {
        return StreamType.EVENT;
    }
}
