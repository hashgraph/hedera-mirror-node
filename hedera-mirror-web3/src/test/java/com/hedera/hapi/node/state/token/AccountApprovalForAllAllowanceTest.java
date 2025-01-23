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

import com.hedera.pbj.runtime.test.NoToStringWrapper;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class AccountApprovalForAllAllowanceTest {

    /**
     * List of all valid arguments for testing, built as a static list, so we can reuse it.
     */
    public static final List<AccountApprovalForAllAllowance> ARGUMENTS;

    static {
        final var tokenIdList = TokenIDTest.ARGUMENTS;
        final var spenderIdList = AccountIDTest.ARGUMENTS;

        // work out the longest of all the lists of args as that is how many test cases we need
        final int maxValues =
                IntStream.of(tokenIdList.size(), spenderIdList.size()).max().getAsInt();

        // create new stream of model objects using lists above as constructor params
        ARGUMENTS = (maxValues > 0 ? IntStream.range(0, maxValues) : IntStream.of(0))
                .mapToObj(i -> new AccountApprovalForAllAllowance(
                        tokenIdList.get(Math.min(i, tokenIdList.size() - 1)),
                        spenderIdList.get(Math.min(i, spenderIdList.size() - 1))))
                .toList();
    }

    /**
     * Create a stream of all test permutations of the AccountApprovalForAllAllowance class we are testing. This is reused by other tests
     * as well that have model objects with fields of this type.
     *
     * @return stream of model objects for all test cases
     */
    public static Stream<NoToStringWrapper<AccountApprovalForAllAllowance>> createModelTestArguments() {
        return ARGUMENTS.stream().map(NoToStringWrapper::new);
    }
}
