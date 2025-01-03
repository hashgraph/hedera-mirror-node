/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.hapi.node.state.token;

import static com.hedera.pbj.runtime.ProtoTestTools.LONG_TESTS_LIST;

import com.hedera.hapi.node.base.NftID;
import com.hedera.pbj.runtime.test.NoToStringWrapper;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class NftIDTest {

    /**
     * List of all valid arguments for testing, built as a static list, so we can reuse it.
     */
    public static final List<NftID> ARGUMENTS;

    static {
        final var tokenIdList = TokenIDTest.ARGUMENTS;
        final var serialNumberList = LONG_TESTS_LIST;

        // work out the longest of all the lists of args as that is how many test cases we need
        final int maxValues =
                IntStream.of(tokenIdList.size(), serialNumberList.size()).max().getAsInt();

        // create new stream of model objects using lists above as constructor params
        ARGUMENTS = (maxValues > 0 ? IntStream.range(0, maxValues) : IntStream.of(0))
                .mapToObj(i -> new NftID(
                        tokenIdList.get(Math.min(i, tokenIdList.size() - 1)),
                        serialNumberList.get(Math.min(i, serialNumberList.size() - 1))))
                .toList();
    }

    /**
     * Create a stream of all test permutations of the NftID class we are testing. This is reused by other tests
     * as well that have model objects with fields of this type.
     *
     * @return stream of model objects for all test cases
     */
    public static Stream<NoToStringWrapper<NftID>> createModelTestArguments() {
        return ARGUMENTS.stream().map(NoToStringWrapper::new);
    }
}
