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

import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.EVM_V_34_BLOCK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.mirror.web3.web3j.generated.EvmCodesHistorical;
import java.math.BigInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class ContractCallEvmCodesHistoricalTest extends AbstractContractCallServiceHistoricalTest {
    private RecordFile recordFileAfterEvm34;

    @BeforeEach
    void beforeEach() {
        recordFileAfterEvm34 = domainBuilder
                .recordFile()
                .customize(f -> f.index(EVM_V_34_BLOCK))
                .persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        senderPersistHistorical(recordFileAfterEvm34);
    }

    @ParameterizedTest
    @CsvSource({
        // function getCodeHash with parameter hedera system accounts or accounts that do not exist
        // expected to revert with INVALID_SOLIDITY_ADDRESS
        "0000000000000000000000000000000000000000000000000000000000000167",
        "0000000000000000000000000000000000000000000000000000000000000168",
        "0000000000000000000000000000000000000000000000000000000000000169",
        "00000000000000000000000000000000000000000000000000000000000005ee",
        "00000000000000000000000000000000000000000000000000000000000005e4",
    })
    void testSystemContractCodeHashPreVersion38(String input) {
        final var contract = testWeb3jService.deploy(EvmCodesHistorical::deploy);
        assertThatThrownBy(() -> contract.call_getCodeHash(input).send())
                .isInstanceOf(MirrorEvmTransactionException.class)
                .satisfies(ex -> assertEquals(ex.getMessage(), INVALID_SOLIDITY_ADDRESS.name()));
    }

    @Test
    void testBlockPrevrandao() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(EvmCodesHistorical::deploy);

        // When
        final var result = contract.call_getBlockPrevrandao().send();

        // Then
        assertThat(result).isNotNull();
        assertTrue(result.compareTo(BigInteger.ZERO) > 0);
    }

    @Test
    void getLatestBlockHashReturnsCorrectValue() throws Exception {
        // Given
        domainBuilder
                .recordFile()
                .customize(f -> f.index(recordFileAfterEvm34.getIndex() + 1))
                .persist();
        final var contract = testWeb3jService.deploy(EvmCodesHistorical::deploy);

        // When
        var result = contract.call_getLatestBlockHash().send();

        // Then
        var expectedResult = ByteString.fromHex(recordFileAfterEvm34.getHash().substring(0, 64))
                .toByteArray();
        assertThat(result).isEqualTo(expectedResult);
    }

    private void senderPersistHistorical(RecordFile recordFileHistorical) {
        final var senderHistorical = accountEntityPersistHistorical(
                Range.closedOpen(recordFileHistorical.getConsensusStart(), recordFileHistorical.getConsensusEnd()));
        testWeb3jService.setSender(toAddress(senderHistorical.toEntityId()).toHexString());
    }
}
