package com.hedera.services.transaction.legacy.core.jproto;

import com.hederahashgraph.api.proto.java.ContractID;

/** Maps to proto Key of type contractID. */
public class JContractIDKey extends JKey {
    private final long shardNum;
    private final long realmNum;
    private final long contractNum;

    public JContractIDKey(final ContractID contractID) {
        this.shardNum = contractID.getShardNum();
        this.realmNum = contractID.getRealmNum();
        this.contractNum = contractID.getContractNum();
    }

    public JContractIDKey(final long shardNum, final long realmNum, final long contractNum) {
        this.shardNum = shardNum;
        this.realmNum = realmNum;
        this.contractNum = contractNum;
    }

    @Override
    public JContractIDKey getContractIDKey() {
        return this;
    }

    @Override
    public boolean hasContractID() {
        return true;
    }

    public ContractID getContractID() {
        return ContractID.newBuilder()
                .setShardNum(shardNum)
                .setRealmNum(realmNum)
                .setContractNum(contractNum)
                .build();
    }

    public long getShardNum() {
        return shardNum;
    }

    public long getRealmNum() {
        return realmNum;
    }

    public long getContractNum() {
        return contractNum;
    }

    @Override
    public String toString() {
        return "<JContractID: " + shardNum + "." + realmNum + "." + contractNum + ">";
    }

    @Override
    public boolean isEmpty() {
        return (0 == contractNum);
    }

    @Override
    public boolean isValid() {
        return !isEmpty();
    }
}
