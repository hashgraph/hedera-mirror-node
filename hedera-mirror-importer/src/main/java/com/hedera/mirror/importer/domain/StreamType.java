package com.hedera.mirror.importer.domain;

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

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FilenameUtils;

@Getter
@RequiredArgsConstructor
public enum StreamType {

    BALANCE("accountBalances", "balance", "_Balances", "csv"),
    EVENT("eventsStreams", "events_", "", "evts"),
    RECORD("recordstreams", "record", "", "rcd");

    private static final String PARSED = "parsed";
    private static final String SIGNATURES = "signatures";
    private static final String TEMP = "tmp";
    private static final String VALID = "valid";
    private static final String SIGNATURE_EXTENSION = "_sig";

    private final String path;
    private final String nodePrefix;
    private final String suffix;
    private final String extension;

    public String getSignatureExtension() {
        return extension + SIGNATURE_EXTENSION;
    }

    public String getParsed() {
        return PARSED;
    }

    public String getSignatures() {
        return SIGNATURES;
    }

    public String getTemp() {
        return TEMP;
    }

    public String getValid() {
        return VALID;
    }

    public static StreamType fromFilename(String filename) {
        String extension = FilenameUtils.getExtension(filename);

        for (StreamType streamType : values()) {
            if (streamType.getExtension().equals(extension) || streamType.getSignatureExtension().equals(extension)) {
                return streamType;
            }
        }

        return null;
    }
}
