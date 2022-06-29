package com.hedera.mirror.web3.evm.properties;

import java.time.Instant;
import java.util.Optional;
import lombok.Value;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.BlockValues;

/**
 * Mirror-node adapted {@link BlockValues}
 */
@Value
public class SimulatedBlockMetaSource implements BlockValues {

    Instant consTimestamp;
    Bytes difficultyBytes = UInt256.ZERO;
    Optional<Wei> baseFee = Optional.of(Wei.ZERO);
    long gasLimit;
    long number;
    long timestamp = consTimestamp.getEpochSecond();
}
