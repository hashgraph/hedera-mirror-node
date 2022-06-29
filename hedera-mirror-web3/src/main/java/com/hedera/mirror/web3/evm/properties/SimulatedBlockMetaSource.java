package com.hedera.mirror.web3.evm.properties;

import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.BlockValues;

/**
 * Mirror-node adapted {@link BlockValues}
 */
@Value
@RequiredArgsConstructor
public class SimulatedBlockMetaSource implements BlockValues {
    
    Bytes difficultyBytes = UInt256.ZERO;
    Optional<Wei> baseFee = Optional.of(Wei.ZERO);
    final long gasLimit;
    final long number;
    final Instant consTimestamp;
    long timestamp = consTimestamp.getEpochSecond();
}
