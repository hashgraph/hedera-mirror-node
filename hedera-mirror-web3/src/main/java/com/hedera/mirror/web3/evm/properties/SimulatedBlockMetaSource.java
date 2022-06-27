package com.hedera.mirror.web3.evm.properties;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.fluent.SimpleBlockValues;
import org.hyperledger.besu.evm.frame.BlockValues;

public class SimulatedBlockMetaSource {

    public Hash getBlockHash(long blockNo) {
        return Hash.EMPTY;
    }

    public BlockValues computeBlockValues(long gasLimit) {
        return new SimpleBlockValues();
    }
}
