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

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;

@Getter
public enum StreamType {

    BALANCE(AccountBalanceFile.class, "accountBalances", "balance", "_Balances", List.of("pb", "csv")),
    EVENT(EventFile.class, "eventsStreams", "events_", "", List.of("evts")),
    RECORD(RecordFile.class, "recordstreams", "record", "", List.of("rcd"));

    public static final String SIGNATURE_SUFFIX = "_sig";

    private static final String PARSED = "parsed";
    private static final String SIGNATURES = "signatures";

    private final List<String> dataExtensions;
    private final String nodePrefix;
    private final String path;
    private final List<String> signatureExtensions;
    private final Class<? extends StreamFile> streamFileClass;
    private final String suffix;

    StreamType(Class<? extends StreamFile> streamFileClass, String path, String nodePrefix, String suffix,
            List<String> extensions) {
        this.streamFileClass = streamFileClass;
        this.path = path;
        this.nodePrefix = nodePrefix;
        this.suffix = suffix;

        this.dataExtensions = Collections.unmodifiableList(extensions);
        this.signatureExtensions = extensions.stream().map(ext -> ext + SIGNATURE_SUFFIX)
                .collect(Collectors.toUnmodifiableList());
    }

    public String getParsed() {
        return PARSED;
    }

    public String getSignatures() {
        return SIGNATURES;
    }

    public boolean isChained() {
        return this != BALANCE;
    }
}
