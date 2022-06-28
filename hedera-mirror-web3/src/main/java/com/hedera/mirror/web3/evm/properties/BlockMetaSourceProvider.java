package com.hedera.mirror.web3.evm.properties;

import java.time.Instant;
import javax.inject.Singleton;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.web3.exception.InvalidParametersException;
import com.hedera.mirror.web3.repository.RecordFileRepository;

@Singleton
public class BlockMetaSourceProvider {

    @Autowired
    private RecordFileRepository recordFileRepository;

    public BlockMetaSourceProvider(final RecordFileRepository recordFileRepository) {
        this.recordFileRepository = recordFileRepository;
    }

    public Hash getBlockHash(long blockNo) {
        final var recordFile = recordFileRepository.findByIndex(blockNo);
        return recordFile.map(file -> Hash.fromHexString(file.getFileHash())).orElseThrow(
                () -> new InvalidParametersException(String.format("No record file with id: %d", blockNo)));
    }

    public BlockValues computeBlockValues(long gasLimit) {
        final var latestRecordFile = recordFileRepository.findLatest().orElseThrow(() -> new RuntimeException("No record file available."));
        return new SimulatedBlockMetaSource(gasLimit, latestRecordFile.getIndex(), Instant.ofEpochSecond(latestRecordFile.getConsensusStart()));
    }
}
