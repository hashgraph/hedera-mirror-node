package com.hedera.mirror.web3.evm.properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import java.util.Optional;
import org.hyperledger.besu.datatypes.Hash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.common.domain.DigestAlgorithm;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.web3.evm.exception.MissingResultException;
import com.hedera.mirror.web3.repository.RecordFileRepository;

@ExtendWith(MockitoExtension.class)
class StaticBlockMetaSourceTest {
    @Mock
    RecordFileRepository repository;
    @Mock
    RecordFile recordFile;

    private StaticBlockMetaSource subject;

    @BeforeEach
    void setUp() {
        subject = new StaticBlockMetaSource(repository);
    }

    @Test
    void getBlockHashReturnsCorrectValue() {
        given(recordFile.getHash()).willReturn(String.valueOf(Hash.EMPTY));
        given(repository.findByIndex(0)).willReturn(Optional.of(recordFile));
        assertEquals(Hash.EMPTY, subject.getBlockHash(0));
    }

    @Test
    void getBlockHashReturnsThrowsWhenRecordIsMissing() {
        given(repository.findByIndex(0)).willReturn(Optional.empty());
        assertThrows(
                MissingResultException.class, () -> subject.getBlockHash(0));
    }

    @Test
    void computeBlockValuesThrowsWhenFileIsMissing() {
        assertThrows(
                MissingResultException.class, () -> subject.computeBlockValues(0));
    }

    @Test
    void computeBlockValuesWithCorrectValue() {
        given(repository.findLatest()).willReturn(Optional.of(recordFile));
        given(recordFile.getIndex()).willReturn(1L);
        given(recordFile.getConsensusStart()).willReturn(1L);
        final var result = subject.computeBlockValues(23L);
        assertEquals(23,result.getGasLimit());
        assertEquals(1,result.getNumber());
        assertEquals(0,result.getTimestamp());

    }

    private RecordFile recordFile(long timestamp) {
        RecordFile recordFile = new RecordFile();
        recordFile.setConsensusStart(timestamp);
        recordFile.setConsensusEnd(timestamp + 1);
        recordFile.setCount(1L);
        recordFile.setDigestAlgorithm(DigestAlgorithm.SHA_384);
        recordFile.setFileHash(String.valueOf(Hash.EMPTY));
        recordFile.setHash(String.valueOf(Hash.EMPTY));
        recordFile.setIndex(timestamp);
        recordFile.setLoadEnd(timestamp + 1);
        recordFile.setLoadStart(timestamp);
        recordFile.setName(timestamp + ".rcd");
        recordFile.setNodeId(0L);
        recordFile.setPreviousHash(String.valueOf(timestamp - 1));
        return recordFile;
    }
}

//    @Test
//    void blockValuesAreForCurrentBlockAfterSync() {
//        given(networkCtx.getAlignmentBlockNo()).willReturn(someBlockNo);
//        given(networkCtx.firstConsTimeOfCurrentBlock()).willReturn(then);
//
//        final var ans = subject.computeBlockValues(gasLimit);
//
//        assertEquals(gasLimit, ans.getGasLimit());
//        assertEquals(someBlockNo, ans.getNumber());
//        assertEquals(then.getEpochSecond(), ans.getTimestamp());
//    }
//
//    @Test
//    void usesNowIfFirstConsTimeIsStillSomehowNull() {
//        given(networkCtx.getAlignmentBlockNo()).willReturn(someBlockNo);
//
//        final var ans = subject.computeBlockValues(gasLimit);
//
//        assertEquals(gasLimit, ans.getGasLimit());
//        assertEquals(someBlockNo, ans.getNumber());
//        assertNotEquals(0, ans.getTimestamp());
//    }
