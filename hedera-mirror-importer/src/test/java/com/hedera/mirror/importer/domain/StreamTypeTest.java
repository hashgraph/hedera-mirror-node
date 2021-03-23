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
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class StreamTypeTest {

    private static final Map<StreamType, List<String>> dataExtensions = ImmutableMap.<StreamType, List<String>>builder()
            .put(StreamType.BALANCE, List.of("pb", "csv"))
            .put(StreamType.EVENT, List.of("evts"))
            .put(StreamType.RECORD, List.of("rcd"))
            .build();
    private static final Map<StreamType, List<String>> signatureExtensions =
            ImmutableMap.<StreamType, List<String>>builder()
            .put(StreamType.BALANCE, List.of("pb_sig", "csv_sig"))
            .put(StreamType.EVENT, List.of("evts_sig"))
            .put(StreamType.RECORD, List.of("rcd_sig"))
            .build();

    @ParameterizedTest
    @MethodSource("provideTypeAndDataExtensions")
    void getDataExtensions(StreamType streamType, List<String> dataExtensions) {
        assertThat(streamType.getDataExtensions()).containsExactlyElementsOf(dataExtensions);
    }

    @ParameterizedTest
    @MethodSource("provideTypeAndSignatureExtensions")
    void getSignatureExtensions(StreamType streamType, List<String> signatureExtensions) {
        assertThat(streamType.getSignatureExtensions()).containsExactlyElementsOf(signatureExtensions);
    }

    private static Stream<Arguments> provideTypeAndExtensions(boolean isDataExtension) {
        Map<StreamType, List<String>> extensionsMap = isDataExtension ? dataExtensions : signatureExtensions;
        List<Arguments> argumentsList = new ArrayList<>();
        for (StreamType streamType : StreamType.values()) {
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
}
