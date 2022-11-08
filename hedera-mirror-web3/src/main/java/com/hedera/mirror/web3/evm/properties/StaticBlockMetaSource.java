package com.hedera.mirror.web3.evm.properties;

import java.time.Instant;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.frame.BlockValues;

import com.hedera.mirror.web3.evm.exception.MissingResultException;
import com.hedera.mirror.web3.repository.RecordFileRepository;
import com.hedera.services.evm.contracts.execution.BlockMetaSource;
import com.hedera.services.evm.contracts.execution.HederaBlockValues;

@Named
@RequiredArgsConstructor
public class StaticBlockMetaSource implements BlockMetaSource {
    private final RecordFileRepository recordFileRepository;

    @Override
    public Hash getBlockHash(long blockNo) {
        final var recordFile = recordFileRepository.findByIndex(blockNo);
        return recordFile.map(file -> Hash.fromHexString(file.getHash())).orElseThrow(
                () -> new MissingResultException(String.format("No record file with index: %d", blockNo)));
    }

    @Override
    public BlockValues computeBlockValues(long gasLimit) {
        final var latestRecordFile = recordFileRepository.findLatest()
                .orElseThrow(() -> new MissingResultException("No record file available."));
        return new HederaBlockValues(gasLimit, latestRecordFile.getIndex(), Instant.ofEpochSecond(0,
                latestRecordFile.getConsensusStart()));
    }
}
