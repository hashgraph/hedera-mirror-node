package com.hedera.mirror.web3.evm.properties;

import java.time.Instant;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.frame.BlockValues;

import com.hedera.mirror.web3.evm.exception.InvalidException;
import com.hedera.mirror.web3.repository.RecordFileRepository;

@Singleton
@RequiredArgsConstructor
public class BlockMetaSourceProvider {

    private final RecordFileRepository recordFileRepository;

    public Hash getBlockHash(long blockNo) {
        final var recordFile = recordFileRepository.findByIndex(blockNo);
        return recordFile.map(file -> Hash.fromHexString(file.getHash())).orElseThrow(
                () -> new InvalidException(String.format("No record file with index: %d", blockNo)));
    }

    public BlockValues computeBlockValues(long gasLimit) {
        final var latestRecordFile = recordFileRepository.findLatest().orElseThrow(() -> new InvalidException("No record file available."));
        return new SimulatedBlockMetaSource(gasLimit, latestRecordFile.getIndex(), Instant.ofEpochSecond(0, latestRecordFile.getConsensusStart()));
    }
}
