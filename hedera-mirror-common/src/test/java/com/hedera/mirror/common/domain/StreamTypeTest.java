/*
 * Copyright (C) 2021-2025 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class StreamTypeTest {

    private static final Map<StreamType, List<String>> DATA_EXTENSIONS =
            ImmutableMap.<StreamType, List<String>>builder()
                    .put(StreamType.BALANCE, List.of("csv", "pb"))
                    .put(StreamType.RECORD, List.of("rcd"))
                    .put(StreamType.BLOCK, List.of("blk"))
                    .build();
    private static final Map<StreamType, List<String>> SIGNATURE_EXTENSIONS =
            ImmutableMap.<StreamType, List<String>>builder()
                    .put(StreamType.BALANCE, List.of("csv_sig", "pb_sig"))
                    .put(StreamType.RECORD, List.of("rcd_sig"))
                    .build();

    private static Stream<Arguments> provideTypeAndExtensions(boolean isDataExtension) {
        Map<StreamType, List<String>> extensionsMap = isDataExtension ? DATA_EXTENSIONS : SIGNATURE_EXTENSIONS;
        List<Arguments> argumentsList = new ArrayList<>();
        for (StreamType streamType : StreamType.values()) {
            if (streamType == StreamType.BLOCK && !isDataExtension) {
                // Block streams have no signature files
                continue;
            }

            List<String> extensions = extensionsMap.get(streamType);
            if (extensions == null) {
                throw new IllegalArgumentException("Unknown StreamType " + streamType);
            }

            argumentsList.add(Arguments.of(streamType, extensions));
        }

        return argumentsList.stream();
    }

    private static Stream<Arguments> provideTypeAndDataExtensions() {
        return provideTypeAndExtensions(true);
    }

    private static Stream<Arguments> provideTypeAndSignatureExtensions() {
        return provideTypeAndExtensions(false);
    }

    @ParameterizedTest
    @MethodSource("provideTypeAndDataExtensions")
    void getDataExtensions(StreamType streamType, List<String> dataExtensions) {
        assertPriorities(streamType.getDataExtensions(), dataExtensions);
    }

    @ParameterizedTest
    @MethodSource("provideTypeAndSignatureExtensions")
    void getSignatureExtensions(StreamType streamType, List<String> signatureExtensions) {
        assertPriorities(streamType.getSignatureExtensions(), signatureExtensions);
    }

    void assertPriorities(SortedSet<StreamType.Extension> actual, List<String> expected) {
        // ensures extensions are ordered by priority
        assertThat(actual.stream().map(StreamType.Extension::getName)).containsExactlyElementsOf(expected);

        Stream<Integer> priorities = actual.stream().map(StreamType.Extension::getPriority);
        assertThat(priorities)
                .doesNotHaveDuplicates()
                .isSortedAccordingTo(Comparator.naturalOrder())
                .contains(0);
    }
}
