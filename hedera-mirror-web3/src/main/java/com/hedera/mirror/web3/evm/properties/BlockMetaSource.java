package com.hedera.mirror.web3.evm.properties;

import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.plugin.data.Hash;

public interface BlockMetaSource {
//    Hash UNAVAILABLE_BLOCK_HASH =
//            MerkleNetworkContext.ethHashFrom(
//                    new ImmutableHash(new byte[DigestType.SHA_384.digestLength()]));

    /**
     * Returns the hash of the given block number, or {@link BlockMetaSource UNAVAILABLE_BLOCK_HASH}
     * if unavailable.
     *
     * @param blockNo the block number of interest
     * @return its hash, if available
     */
    Hash getBlockHash(long blockNo);

    /**
     * Returns the in-scope block values, given an effective gas limit.
     *
     * @param gasLimit the effective gas limit
     * @return the scoped block values
     */
    BlockValues computeBlockValues(long gasLimit);
}

