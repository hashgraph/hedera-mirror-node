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

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.AssertionsForClassTypes.within;

import com.hedera.mirror.web3.utils.BinarySearchIndexGenerator.Last;
import com.hedera.mirror.web3.utils.BinarySearchIndexGenerator.Next;
import com.hedera.mirror.web3.utils.BinarySearchIndexGenerator.State;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@ExtendWith(SoftAssertionsExtension.class)
class BinarySearchIndexGeneratorTest {

    @InjectSoftAssertions
    private SoftAssertions softly;

    enum Result {
        FOUND,
        NOT_FOUND
    };

    @DisplayName("testBinarySearch")
    @ParameterizedTest(name = "#{index} (low {0}, high {1}, goal {2}, limit {3}, succeeded {4}")
    @CsvSource({
        "0, 100, 20, 10, FOUND",
        "20, 120, 40, 10, FOUND",
        "-100, 100, 0, 10, FOUND",
        "-100, 100, 25, 10, FOUND",
        "2000, 15_000_000, 25_000, 26, FOUND",
        "2000, 15_000_000, 1_000_000, 26, FOUND",
        "555, 555, 555, 2, FOUND",
        "666, 666, 667, 2, FOUND",
        "777, 778, 778, 2, FOUND",
        "1_000_000, 1_000_000_000, 30_000_000, 33, FOUND",
        "2000, 15_000_000, 25_000, 20, NOT_FOUND",
        "1_000_000, 1_000_000_000, 30_000_000, 22, NOT_FOUND"
    })
    void testBinarySearch(
            final long low, final long high, final long goal, final int iterationLimit, final Result result) {

        final var sut = new BinarySearchIndexGenerator(low, high);
        int iterations = 0;

        Last last = Last.TOO_LOW;
        Next next;

        while (true) {
            next = sut.next(last);

            if (State.FINAL == next.state()) break;
            if (iterationLimit <= ++iterations) break;

            softly.assertThat(next.value())
                    .as("value must not go out of bounds")
                    .isBetween(low, high);

            last = goal > next.value() ? Last.TOO_LOW : Last.TOO_HIGH;
        }

        softly.assertThat(iterations).as("iteration limit").isLessThanOrEqualTo(iterationLimit);
        switch (result) {
            case FOUND -> {
                softly.assertThat(next.state())
                        .as("success case must end in FINAL")
                        .isEqualTo(State.FINAL);
                softly.assertThat(next.value())
                        .as("success case must have found goal value ±1")
                        .isCloseTo(goal, within(1L));
            }
            case NOT_FOUND -> {
                softly.assertThat(next.state())
                        .as("failure case must end in NEXT")
                        .isEqualTo(State.NEXT);
                softly.assertThat(next.value())
                        .as("failure case must not have found goal value")
                        .isNotEqualTo(goal);
                softly.assertThat(next.value())
                        .as("failure case value must not go out of bounds")
                        .isBetween(low, high);
            }
        }
    }

    @Test
    void binarySearchWithHighLessThanLowIsIllegal() {
        assertThatIllegalArgumentException().isThrownBy(() -> new BinarySearchIndexGenerator(25, 5));
    }
}
