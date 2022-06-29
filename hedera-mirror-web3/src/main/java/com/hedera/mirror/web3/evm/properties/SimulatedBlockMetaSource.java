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

    long gasLimit;
    long blockNo;
    Instant consTimestamp;

    @Override
    public long getGasLimit() {
        return gasLimit;
    }

    @Override
    public long getTimestamp() {
        return consTimestamp.getEpochSecond();
    }

    @Override
    public Optional<Wei> getBaseFee() {
        return Optional.of(Wei.ZERO);
    }

    @Override
    public Bytes getDifficultyBytes() {
        return UInt256.ZERO;
    }

    @Override
    public long getNumber() {
        return blockNo;
    }
}
