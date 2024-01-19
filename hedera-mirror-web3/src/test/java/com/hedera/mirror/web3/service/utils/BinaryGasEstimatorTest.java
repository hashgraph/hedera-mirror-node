/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.service.utils;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.assertj.core.data.Percentage;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@RequiredArgsConstructor
class BinaryGasEstimatorTest extends Web3IntegrationTest {
    private final BinaryGasEstimator binaryGasEstimator;
    private final MirrorNodeEvmProperties properties;
    private final AtomicInteger iterations = new AtomicInteger(0);

    /**
     * @link BinaryGasEstimator is using slightly modified binary algorithm which is coupled to some exttend with the
     * gas used estimation and its metric updates. Here the logic of processing contact calls is replaced with a dummy
     * function, returning successful/failed HederaEvmTransactionProcessingResult with gasUsed value equal to the low
     * value, which corresponds to the intial gasUsed in the real world. The metric logic is replaced with a lambda
     * which counts the iterations count.
     */
    @DisplayName("binarySearch")
    @ParameterizedTest(name = "#{index} (low {0}, high {1}, iterationLimit{2}")
    @CsvSource({
        "23850, 100000, 6",
        "35000, 15_000_000, 14",
        "55555, 55555, 1",
        "77777, 77778, 1",
        "1_000_000, 1_000_000_000, 20",
        "21000, 15_000_000, 14",
        "1_000_000, 1_000_000_000, 20"
    })
    void binarySearch(final long low, final long high, final int iterationLimit) {
        // First call with no failing contract calls for gasUsed reference
        final var regularCall = binaryGasEstimator.search(
                (a, b) -> iterations.addAndGet(b), gas -> createTxnResult(low, true), low, high);

        assertThat(regularCall).as("result must not go out of bounds").isBetween(low, high);

        assertThat(regularCall)
                .as("result must be within the 20% range of the initial gasUsed(low param)")
                .isCloseTo(low, Percentage.withPercentage(20));

        assertThat(iterations.get()).as("iteration limit").isLessThanOrEqualTo(iterationLimit);
    }

    @DisplayName("binarySearchWithFailingCalls")
    @ParameterizedTest(name = "#{index} (low {0}, high {1}, regularCallGasUsage{2}")
    @CsvSource({
        "21000, 100000, 21617",
        "35000, 15_000_000, 35913",
        "1_000_000, 1_000_000_000, 1000952",
        "21000, 15_000_000, 21914",
        "1_000_000, 1_000_000_000, 1000952"
    })
    void binarySearchWithFailingCalls(final long low, final long high, final int regularCallGasUsage) {
        // Call where every second contract call fails
        final var callResult = binaryGasEstimator.search(
                (a, b) -> iterations.addAndGet(b), gas -> createTxnResult(low, failEverySecondCall()), low, high);

        assertThat(callResult).as("result must not go out of bounds").isBetween(low, high);
        assertThat(iterations.get())
                .as("iteration limit")
                .isLessThanOrEqualTo(properties.getMaxGasEstimateRetriesCount());

        // asserting that every revert while executing is treated like INSUFFICIENT_GAS
        assertThat(callResult)
                .as("estimated gas in search containing failed calls transactions is higher than the one with "
                        + "only successful transactions")
                .isGreaterThan(regularCallGasUsage);
    }

    @Test
    void searchDoesntExceedMaxIterations() {
        /*
         * With such values for low and high, iterations would over the maximum of 20, we test the
         * whether the condition in BinaryGasEstimator keeps the iteration under this 20 threshold.
         */
        final var low = 0;
        final var high = Long.MAX_VALUE;
        binaryGasEstimator.search((a, b) -> iterations.addAndGet(b), gas -> createTxnResult(0, false), low, high);

        assertThat(iterations.get())
                .as("iteration limit")
                .isLessThanOrEqualTo(properties.getMaxGasEstimateRetriesCount());
    }

    private HederaEvmTransactionProcessingResult createTxnResult(final long gasUsed, final boolean isSuccessful) {
        if (!isSuccessful) {
            return HederaEvmTransactionProcessingResult.failed(gasUsed, 0, 0, Optional.empty(), Optional.empty());
        }
        return HederaEvmTransactionProcessingResult.successful(null, gasUsed, 0, 0, null, Address.ZERO);
    }

    private boolean failEverySecondCall() {
        return iterations.get() > 0 && iterations.get() % 2 == 0;
    }
}
