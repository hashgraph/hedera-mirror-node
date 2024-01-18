/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.evm.properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockStatic;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.exception.MissingResultException;
import com.hedera.mirror.web3.repository.RecordFileRepository;
import java.time.Instant;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Hash;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StaticBlockMetaSourceTest {
    @Mock
    private RecordFileRepository repository;

    private MockedStatic<ContractCallContext> staticMock;

    @Mock
    private ContractCallContext contractCallContext;

    private StaticBlockMetaSource subject;
    private final DomainBuilder domainBuilder = new DomainBuilder();

    @BeforeEach
    void setUp() {
        subject = new StaticBlockMetaSource(repository);
        staticMock = mockStatic(ContractCallContext.class);
        staticMock.when(ContractCallContext::get).thenReturn(contractCallContext);
    }

    @AfterEach
    void clean() {
        staticMock.close();
    }

    @Test
    void getBlockHashReturnsCorrectValue() {
        final var fileHash =
                "37313862636664302d616365352d343861632d396430612d36393036316337656236626333336466323864652d346100";
        final var recordFile = new RecordFile();
        recordFile.setHash(fileHash);

        given(repository.findByIndex(1)).willReturn(Optional.of(recordFile));
        final var expected = Hash.fromHexString("0x37313862636664302d616365352d343861632d396430612d3639303631633765");
        assertThat(subject.getBlockHash(1)).isEqualTo(expected);
    }

    @Test
    void getBlockHashThrowsExceptionWhitMissingFileId() {
        given(repository.findByIndex(1)).willReturn(Optional.empty());
        assertThatThrownBy(() -> subject.getBlockHash(1)).isInstanceOf(MissingResultException.class);
    }

    @Test
    void computeBlockValuesWithCorrectValue() {
        final var recordFile = domainBuilder.recordFile().get();
        final var timeStamp = Instant.ofEpochSecond(0, recordFile.getConsensusStart());
        given(contractCallContext.getRecordFile()).willReturn(recordFile);
        final var result = subject.computeBlockValues(23L);
        assertThat(result.getGasLimit()).isEqualTo(23);
        assertThat(result.getNumber()).isEqualTo(recordFile.getIndex());
        assertThat(result.getTimestamp()).isEqualTo(timeStamp.getEpochSecond());
    }

    @Test
    void computeBlockValuesFailsFailsForMissingFileId() {
        given(ContractCallContext.get()).willReturn(contractCallContext);
        given(contractCallContext.getRecordFile()).willReturn(null);
        assertThatThrownBy(() -> subject.computeBlockValues(1)).isInstanceOf(MissingResultException.class);
    }

    @Test
    void testEthHashFromReturnsCorrectValue() {
        final var result = StaticBlockMetaSource.ethHashFrom(
                "37313862636664302d616365352d343861632d396430612d36393036316337656236626333336466323864652d346100");
        final var expected = Hash.wrap(
                Bytes32.wrap(Bytes.fromHexString("0x37313862636664302d616365352d343861632d396430612d3639303631633765")
                        .toArrayUnsafe()));
        assertThat(result).isEqualTo(expected);
    }
}
