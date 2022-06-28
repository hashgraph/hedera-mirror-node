package com.hedera.mirror.web3.evm.properties;

import java.time.Instant;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.BlockValues;

/**
 * Mirror-node adapted {@link BlockValues}
 */
public class SimulatedBlockMetaSource implements BlockValues {

    protected final long gasLimit;
    protected final long blockNo;
    protected final Instant consTimestamp;

    public SimulatedBlockMetaSource(final long gasLimit, final long blockNo, final Instant consTimestamp) {
        this.gasLimit = gasLimit;
        this.blockNo = blockNo;
        this.consTimestamp = consTimestamp;
    }

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
