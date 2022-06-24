package com.hedera.mirror.web3.transaction.properties;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.fluent.SimpleBlockValues;
import org.hyperledger.besu.evm.frame.BlockValues;

public class SimulatedBlockMetaSource implements BlockMetaSource {
    @Override
    public Hash getBlockHash(long blockNo) {
        return Hash.EMPTY;
    }

    @Override
    public BlockValues computeBlockValues(long gasLimit) {
        return new SimpleBlockValues();
    }
}
