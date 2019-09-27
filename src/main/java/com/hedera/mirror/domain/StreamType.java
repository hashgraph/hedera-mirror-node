package com.hedera.mirror.domain;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StreamType {

    // TODO: Use a common parsed directory name 'parsed'
    BALANCE("accountBalances", "parsedBalanceFiles"),
    EVENT("eventsStreams", "parsedEventStreamFiles"),
    RECORD("recordstreams", "parsedRecordFiles");

    public static final String TEMP = "tmp";
    public static final String VALID = "valid";

    private final String path;
    private final String parsed;

    public String getTemp() {
        return TEMP;
    }

    public String getValid() {
        return VALID;
    }
}
