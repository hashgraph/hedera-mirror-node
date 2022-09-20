package com.hedera.mirror.web3.evm.properties;

import com.hedera.mirror.web3.repository.RecordFileRepository;

import lombok.RequiredArgsConstructor;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.datatypes.Hash;

import com.hedera.mirror.web3.evm.exception.MissingResultException;

import javax.inject.Named;
import java.time.Instant;

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
