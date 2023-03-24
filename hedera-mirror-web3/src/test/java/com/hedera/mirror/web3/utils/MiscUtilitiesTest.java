/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.utils;

import static com.hedera.mirror.web3.utils.MiscUtilities.requireAllNonNull;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import org.junit.jupiter.api.Test;

class MiscUtilitiesTest {

    final String notNull1 = "not null 1";
    final String notNull2 = "not null 2";
    final String notNull3 = "not null 3";
    final String notNull4 = "not null 4";
    final String notNull5 = "not null 5";
    final String null1 = "xyz 1";
    final String null2 = "xyz 2";
    final String null3 = "xyz 3";
    final String null4 = "xyz 4";

    @Test
    void requireAllNonNullHappyPaths() {

        assertThatNoException().isThrownBy(() -> requireAllNonNull(this, "this"));
        assertThatNoException().isThrownBy(() -> requireAllNonNull(this, "this", this, "that"));

        final var allSucceed = List.of(
                notNull1, notNull1, notNull2, notNull2, notNull3, notNull3, notNull4, notNull4, notNull5, notNull5);
        for (int i = 0; i < allSucceed.size(); i += 2) {
            final int finalI = i;
            assertThatNoException()
                    .isThrownBy(() ->
                            requireAllNonNull(allSucceed.subList(0, finalI).toArray()));
        }
    }

    @Test
    void requireAllNonNullOddArguments() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> requireAllNonNull(null, null, null))
                .withMessageContaining("odd number of arguments");
    }

    @Test
    void requiredAllNonNullEvenArgumentsMustBeNonNullStrings() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> requireAllNonNull(null, null))
                .withMessageContaining("even numbered arguments");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> requireAllNonNull(null, this))
                .withMessageContaining("even numbered arguments");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> requireAllNonNull(null, "foo", null, this))
                .withMessageContaining("even numbered arguments");
    }

    @Test
    void requireAllNonNullOneNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> requireAllNonNull(null, null1))
                .withMessageContaining("argument xyz 1 must not be null");
        assertThatNullPointerException()
                .isThrownBy(() -> requireAllNonNull(null, null1, notNull2, notNull2, notNull3, notNull3))
                .withMessage("argument xyz 1 must not be null");
        assertThatNullPointerException()
                .isThrownBy(() -> requireAllNonNull(notNull1, notNull1, null, null2, notNull3, notNull3))
                .withMessage("argument xyz 2 must not be null");
        assertThatNullPointerException()
                .isThrownBy(() -> requireAllNonNull(notNull1, notNull1, notNull2, notNull2, null, null3))
                .withMessage("argument xyz 3 must not be null");
    }

    @Test
    void requireAllNonNullMoreThanOneNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> requireAllNonNull(null, null1, null, null2))
                .withMessage("argument xyz 1 must not be null; argument xyz 2 must not be null");
        assertThatNullPointerException()
                .isThrownBy(() -> requireAllNonNull(null, null1, notNull2, notNull2, null, null3, notNull4, notNull4))
                .withMessage("argument xyz 1 must not be null; argument xyz 3 must not be null");
        assertThatNullPointerException()
                .isThrownBy(() -> requireAllNonNull(null, null1, notNull2, notNull2, notNull3, notNull3, null, null4))
                .withMessage("argument xyz 1 must not be null; argument xyz 4 must not be null");
        assertThatNullPointerException()
                .isThrownBy(() -> requireAllNonNull(null, null1, null, null2, null, null3, null, null4))
                .withMessage("argument xyz 1 must not be null; argument xyz 2 must not be null; "
                        + "argument xyz 3 must not be null; argument xyz 4 must not be null");
    }
}
