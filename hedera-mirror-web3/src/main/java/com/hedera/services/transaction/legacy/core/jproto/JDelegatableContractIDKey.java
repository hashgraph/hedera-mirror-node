package com.hedera.services.transaction.legacy.core.jproto;

import com.hederahashgraph.api.proto.java.ContractID;

/** Maps to proto Key of type contractID. */
public class JDelegatableContractIDKey extends JContractIDKey {
    public JDelegatableContractIDKey(final ContractID contractID) {
        super(contractID);
    }

    public JDelegatableContractIDKey(
            final long shardNum, final long realmNum, final long contractNum) {
        super(shardNum, realmNum, contractNum);
    }

    @Override
    public JDelegatableContractIDKey getDelegatableContractIdKey() {
        return this;
    }

    @Override
    public boolean hasDelegatableContractId() {
        return true;
    }

    @Override
    public boolean hasContractID() {
        return false;
    }

    @Override
    public JContractIDKey getContractIDKey() {
        return null;
    }

    @Override
    public String toString() {
        return "<JDelegatableContractId: "
                + getShardNum()
                + "."
                + getRealmNum()
                + "."
                + getContractNum()
                + ">";
    }
}
