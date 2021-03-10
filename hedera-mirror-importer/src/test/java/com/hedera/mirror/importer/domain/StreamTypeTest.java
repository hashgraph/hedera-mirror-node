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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class StreamTypeTest {

    private static final Map<StreamType, List<String>> dataExtensions = ImmutableMap.<StreamType, List<String>>builder()
            .put(StreamType.BALANCE, List.of("pb.gz", "csv"))
            .put(StreamType.EVENT, List.of("evts"))
            .put(StreamType.RECORD, List.of("rcd"))
            .build();
    private static final Map<StreamType, List<String>> signatureExtensions =
            ImmutableMap.<StreamType, List<String>>builder()
            .put(StreamType.BALANCE, List.of("pb_sig.gz", "pb_sig", "csv_sig"))
            .put(StreamType.EVENT, List.of("evts_sig"))
            .put(StreamType.RECORD, List.of("rcd_sig"))
            .build();

    @ParameterizedTest
    @MethodSource("provideTypeAndDataExtensions")
    void getDataExtensions(StreamType streamType, List<String> dataExtensions) {
        assertThat(streamType.getDataExtensions()).containsExactlyElementsOf(dataExtensions);
    }

    @ParameterizedTest
    @MethodSource("provideTypeAndLastDataExtension")
    void getLastDataExtension(StreamType streamType, String dataExtension) {
        assertThat(streamType.getLastDataExtension()).isEqualTo(dataExtension);
    }

    @ParameterizedTest
    @MethodSource("provideTypeAndLastSignatureExtension")
    void getLastSignatureExtension(StreamType streamType, String signatureExtension) {
        assertThat(streamType.getLastSignatureExtension()).isEqualTo(signatureExtension);
    }

    @ParameterizedTest
    @MethodSource("provideTypeAndSignatureExtensions")
    void getSignatureExtensions(StreamType streamType, List<String> signatureExtensions) {
        assertThat(streamType.getSignatureExtensions()).containsExactlyElementsOf(signatureExtensions);
    }

    @ParameterizedTest
    @MethodSource("provideTypeAndSignatureToDataExtensionMap")
    void getSignatureToDataExtensionMap(StreamType streamType, Map<String, String> expected) {
        assertThat(streamType.getSignatureToDataExtensionMap()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideTypeAndExtensions(boolean isDataExtension, boolean onlyLast) {
        Map<StreamType, List<String>> extensionsMap = isDataExtension ? dataExtensions : signatureExtensions;
        List<Arguments> argumentsList = new ArrayList<>();
        for (StreamType streamType : StreamType.values()) {
            List<String> extensions = extensionsMap.get(streamType);
            if (extensions == null) {
                throw new IllegalArgumentException("Unknown StreamType " + streamType);
            }

            if (onlyLast) {
                argumentsList.add(Arguments.of(streamType, extensions.get(0)));
            } else {
                argumentsList.add(Arguments.of(streamType, extensions));
            }
        }

        return argumentsList.stream();
    }

    private static Stream<Arguments> provideTypeAndDataExtensions() {
        return provideTypeAndExtensions(true, false);
    }

    private static Stream<Arguments> provideTypeAndSignatureExtensions() {
        return provideTypeAndExtensions(false, false);
    }

    private static Stream<Arguments> provideTypeAndLastDataExtension() {
        return provideTypeAndExtensions(true, true);
    }

    private static Stream<Arguments> provideTypeAndLastSignatureExtension() {
        return provideTypeAndExtensions(false, true);
    }

    private static Stream<Arguments> provideTypeAndSignatureToDataExtensionMap() {
        List<Arguments> argumentsList = new ArrayList<>();

        for (StreamType streamType : StreamType.values()) {
            Map<String, String> extensionMap = new HashMap<>();
            switch (streamType) {
                case BALANCE:
                    extensionMap.put("pb_sig", "pb.gz");
                    extensionMap.put("pb_sig.gz", "pb.gz");
                    extensionMap.put("csv_sig", "csv");
                    break;
                case EVENT:
                    extensionMap.put("evts_sig", "evts");
                    break;
                case RECORD:
                    extensionMap.put("rcd_sig", "rcd");
                    break;
                default:
                    throw new IllegalArgumentException("Unknown StreamType " + streamType);
            }

            argumentsList.add(Arguments.of(streamType, extensionMap));
        }

        return argumentsList.stream();
    }
}
