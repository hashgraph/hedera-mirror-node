package com.hedera.services.transaction.legacy.core.jproto;

import static com.swirlds.common.utility.CommonUtils.hex;

import com.hederahashgraph.api.proto.java.ContractID;

public class JDelegatableContractAliasKey extends JContractAliasKey {
    public JDelegatableContractAliasKey(final ContractID contractID) {
        super(contractID);
    }

    public JDelegatableContractAliasKey(
            final long shard, final long realm, final byte[] evmAddress) {
        super(shard, realm, evmAddress);
    }

    @Override
    public JDelegatableContractAliasKey getDelegatableContractAliasKey() {
        return this;
    }

    @Override
    public boolean hasDelegatableContractAlias() {
        return true;
    }

    @Override
    public boolean hasContractAlias() {
        return false;
    }

    @Override
    public String toString() {
        return "<JDelegatableContractAlias: "
                + getShardNum()
                + "."
                + getRealmNum()
                + "."
                + hex(getEvmAddress())
                + ">";
    }
}
