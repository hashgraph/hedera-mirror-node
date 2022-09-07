package com.hedera.services.transaction.legacy.core.jproto;

import static com.hedera.services.transaction.models.Account.EVM_ADDRESS_SIZE;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.ContractID;
import com.swirlds.common.utility.CommonUtils;

public class JContractAliasKey extends JKey {
    private final long shardNum;
    private final long realmNum;
    private final byte[] evmAddress;

    public JContractAliasKey(final ContractID contractID) {
        this.shardNum = contractID.getShardNum();
        this.realmNum = contractID.getRealmNum();
        this.evmAddress = contractID.getEvmAddress().toByteArray();
    }

    public JContractAliasKey(final long shardNum, final long realmNum, final byte[] evmAddress) {
        this.shardNum = shardNum;
        this.realmNum = realmNum;
        this.evmAddress = evmAddress;
    }

    @Override
    public JContractAliasKey getContractAliasKey() {
        return this;
    }

    @Override
    public boolean hasContractAlias() {
        return true;
    }

    public ContractID getContractID() {
        return ContractID.newBuilder()
                .setShardNum(shardNum)
                .setRealmNum(realmNum)
                .setEvmAddress(ByteString.copyFrom(evmAddress))
                .build();
    }

    public long getShardNum() {
        return shardNum;
    }

    public long getRealmNum() {
        return realmNum;
    }

    public byte[] getEvmAddress() {
        return evmAddress;
    }

    @Override
    public String toString() {
        return "<JContractAlias: "
                + shardNum
                + "."
                + realmNum
                + "."
                + CommonUtils.hex(evmAddress)
                + ">";
    }

    @Override
    public boolean isEmpty() {
        return evmAddress.length == 0;
    }

    @Override
    public boolean isValid() {
        return !isEmpty() && evmAddress.length == EVM_ADDRESS_SIZE;
    }
}
