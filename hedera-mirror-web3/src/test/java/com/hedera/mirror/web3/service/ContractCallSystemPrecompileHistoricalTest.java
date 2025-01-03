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

package com.hedera.mirror.web3.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.Range;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.mirror.web3.web3j.generated.ExchangeRatePrecompileHistorical;
import com.hedera.mirror.web3.web3j.generated.PrngSystemContractHistorical;
import java.math.BigInteger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class ContractCallSystemPrecompileHistoricalTest extends AbstractContractCallServiceTest {

    @ParameterizedTest
    @CsvSource({"200", "150", "100", "50", "49"})
    void exchangeRatePrecompileTinycentsToTinybars(long blockNumber) throws Exception {
        // Given
        final var recordFile =
                domainBuilder.recordFile().customize(f -> f.index(blockNumber)).persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(blockNumber)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFile.getConsensusStart(), recordFile.getConsensusEnd()));
        final var contract = testWeb3jService.deploy(ExchangeRatePrecompileHistorical::deploy);
        // When
        final var result =
                contract.call_tinycentsToTinybars(BigInteger.valueOf(100L)).send();
        // Then
        assertThat(result).isEqualTo(BigInteger.valueOf(8L));
    }

    @ParameterizedTest
    @CsvSource({"200", "150", "100", "50", "49"})
    void exchangeRatePrecompileTinybarsToTinycents(long blockNumber) throws Exception {
        // Given
        final var recordFile =
                domainBuilder.recordFile().customize(f -> f.index(blockNumber)).persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(blockNumber)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFile.getConsensusStart(), recordFile.getConsensusEnd()));
        final var contract = testWeb3jService.deploy(ExchangeRatePrecompileHistorical::deploy);
        // When
        final var result =
                contract.call_tinybarsToTinycents(BigInteger.valueOf(100L)).send();
        // Then
        assertThat(result).isEqualTo(BigInteger.valueOf(1200L));
    }

    @ParameterizedTest
    @CsvSource({"200", "150", "100", "50", "49"})
    void pseudoRandomGeneratorPrecompileFunctionsTestEthCallHistorical(long blockNumber) throws Exception {
        // Given
        final var recordFile =
                domainBuilder.recordFile().customize(f -> f.index(blockNumber)).persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(blockNumber)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFile.getConsensusStart(), recordFile.getConsensusEnd()));
        final var contract = testWeb3jService.deploy(PrngSystemContractHistorical::deploy);
        // When
        final var result = contract.call_getPseudorandomSeed().send();
        // Then
        assertEquals(32, result.length, "The string should represent a 32-byte long array");
    }
}
