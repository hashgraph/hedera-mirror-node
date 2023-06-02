/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.common.domain;

import com.google.common.collect.ImmutableSortedSet;
import com.hedera.mirror.common.domain.balance.AccountBalanceFile;
import com.hedera.mirror.common.domain.event.EventFile;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;

@Getter
public enum StreamType {
    BALANCE(
            AccountBalanceFile::new,
            "accountBalances",
            "balance",
            "_Balances",
            List.of("csv", "pb"),
            Duration.ofMinutes(15L)),
    EVENT(EventFile::new, "eventsStreams", "events_", "", List.of("evts"), Duration.ofSeconds(5L)),
    RECORD(RecordFile::new, "recordstreams", "record", "", List.of("rcd"), Duration.ofSeconds(2L));

    public static final String SIGNATURE_SUFFIX = "_sig";

    private static final String PARSED = "parsed";
    private static final String SIGNATURES = "signatures";

    private final SortedSet<Extension> dataExtensions;
    private final String nodePrefix;
    private final String path;
    private final SortedSet<Extension> signatureExtensions;
    private final String suffix;
    private final String nodeIdBasedSuffix; // HIP-679
    private final Supplier<? extends StreamFile<?>> supplier;
    private final Duration fileCloseInterval;

    StreamType(
            Supplier<? extends StreamFile<?>> supplier,
            String path,
            String nodePrefix,
            String suffix,
            List<String> extensions,
            Duration fileCloseInterval) {
        this.supplier = supplier;
        this.path = path;
        this.nodePrefix = nodePrefix;
        this.suffix = suffix;
        this.nodeIdBasedSuffix = name().toLowerCase();
        this.fileCloseInterval = fileCloseInterval;

        dataExtensions = IntStream.range(0, extensions.size())
                .mapToObj(index -> Extension.of(extensions.get(index), index))
                .collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.naturalOrder()));
        signatureExtensions = dataExtensions.stream()
                .map(ext -> Extension.of(ext.getName() + SIGNATURE_SUFFIX, ext.getPriority()))
                .collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.naturalOrder()));
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

    @SuppressWarnings("unchecked")
    public <T extends StreamFile<?>> T newStreamFile() {
        return (T) supplier.get();
    }

    @Value(staticConstructor = "of")
    public static class Extension implements Comparable<Extension> {
        private static final Comparator<Extension> COMPARATOR = Comparator.comparing(Extension::getPriority);

        String name;

        @EqualsAndHashCode.Exclude
        int priority; // starting from 0, larger value means higher priority

        @Override
        public int compareTo(Extension other) {
            return COMPARATOR.compare(this, other);
        }
    }
}
