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

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

@Getter
public enum StreamType {

    BALANCE(AccountBalanceFile.class, "accountBalances", "balance", "_Balances",
            List.of(Extension.of("pb", true), Extension.of("csv", false))),
    EVENT(EventFile.class, "eventsStreams", "events_", "", List.of(Extension.of("evts", false))),
    RECORD(RecordFile.class, "recordstreams", "record", "", List.of(Extension.of("rcd", false)));

    public static final String GZ_EXTENSION = "gz";
    public static final String SIGNATURE_SUFFIX = "_sig";

    private static final String PARSED = "parsed";
    private static final String SIGNATURES = "signatures";

    private final List<String> dataExtensions;
    private final String lastDataExtension;
    private final String lastSignatureExtension;
    private final String nodePrefix;
    private final String path;
    private final List<String> signatureExtensions;
    private final Map<String, String> signatureToDataExtensionMap;
    private final Class<? extends StreamFile> streamFileClass;
    private final String suffix;

    StreamType(Class<? extends StreamFile> streamFileClass, String path, String nodePrefix, String suffix,
            List<Extension> extensions) {
        this.streamFileClass = streamFileClass;
        this.path = path;
        this.nodePrefix = nodePrefix;
        this.suffix = suffix;

        // build extensions and the map. extensions are passed in higher priority first order. For signature extensions,
        // the gzipped one comes first. The last*Extension is the alphabetically last
        List<String> dataExtensions = new ArrayList<>();
        List<String> signatureExtensions = new ArrayList<>();
        Map<String, String> extensionMap = new HashMap<>();

        for (Extension ext : extensions) {
            // signature file can be not gzipped when the data file is gzipped
            String dataExtension = ext.getName();
            String signatureExtension = StringUtils.join(ext.getName(), SIGNATURE_SUFFIX);

            if (ext.isGzipped()) {
                dataExtension = StringUtils.joinWith(".", dataExtension, GZ_EXTENSION);
                String gzippedSignatureExtension = StringUtils.joinWith(".", signatureExtension, GZ_EXTENSION);

                signatureExtensions.add(gzippedSignatureExtension);
                extensionMap.put(gzippedSignatureExtension, dataExtension);
            }

            dataExtensions.add(dataExtension);
            signatureExtensions.add(signatureExtension);
            extensionMap.put(signatureExtension, dataExtension);
        }

        this.lastDataExtension = dataExtensions.stream().max(Comparator.naturalOrder()).get();
        this.lastSignatureExtension = signatureExtensions.stream().max(Comparator.naturalOrder()).get();
        this.dataExtensions = List.copyOf(dataExtensions);
        this.signatureExtensions = List.copyOf(signatureExtensions);
        this.signatureToDataExtensionMap = ImmutableMap.copyOf(extensionMap);
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

    @Value(staticConstructor = "of")
    private static class Extension {
        String name;
        boolean gzipped;
    }
}
