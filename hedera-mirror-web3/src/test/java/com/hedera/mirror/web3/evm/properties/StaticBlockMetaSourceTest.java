package com.hedera.mirror.web3.evm.properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.Instant;
import java.util.Optional;
import org.hyperledger.besu.datatypes.Hash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.web3.evm.exception.MissingResultException;
import com.hedera.mirror.web3.repository.RecordFileRepository;

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
        assertThatThrownBy(() ->
                subject.getBlockHash(1))
                .isInstanceOf(MissingResultException.class);
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
        assertThatThrownBy(() ->
                subject.computeBlockValues(1))
                .isInstanceOf(MissingResultException.class);
    }
}
