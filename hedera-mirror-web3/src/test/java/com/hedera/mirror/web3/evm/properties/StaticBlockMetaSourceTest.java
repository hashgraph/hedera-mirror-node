/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.web3.evm.exception.MissingResultException;
import com.hedera.mirror.web3.repository.RecordFileRepository;
import java.time.Instant;
import java.util.Optional;
import org.hyperledger.besu.datatypes.Hash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StaticBlockMetaSourceTest {
    @Mock
    private RecordFileRepository repository;

    private StaticBlockMetaSource subject;
    private final DomainBuilder domainBuilder = new DomainBuilder();

    @BeforeEach
    void setUp() {
        subject = new StaticBlockMetaSource(repository);
    }

    @Test
    void getBlockHashReturnsCorrectValue() {
        final var fileHash = "0x00000000000000000000000000000000000000000000000000000000000004e4";
        given(repository.findHashByIndex(1)).willReturn(Optional.of(fileHash));
        assertThat(subject.getBlockHash(1)).isEqualTo(Hash.fromHexString(fileHash));
    }

    @Test
    void getBlockHashThrowsExceptionWhitMissingFileId() {
        given(repository.findHashByIndex(1)).willReturn(Optional.empty());
        assertThatThrownBy(() -> subject.getBlockHash(1)).isInstanceOf(MissingResultException.class);
    }

    @Test
    void computeBlockValuesWithCorrectValue() {
        final var recordFile = domainBuilder.recordFile().get();
        final var timeStamp = Instant.ofEpochSecond(0, recordFile.getConsensusStart());
        given(repository.findLatest()).willReturn(Optional.of(recordFile));
        final var result = subject.computeBlockValues(23L);
        assertThat(result.getGasLimit()).isEqualTo(23);
        assertThat(result.getNumber()).isEqualTo(recordFile.getIndex());
        assertThat(result.getTimestamp()).isEqualTo(timeStamp.getEpochSecond());
    }

    @Test
    void computeBlockValuesFailsFailsForMissingFileId() {
        given(repository.findLatest()).willReturn(Optional.empty());
        assertThatThrownBy(() -> subject.computeBlockValues(1)).isInstanceOf(MissingResultException.class);
    }
}
