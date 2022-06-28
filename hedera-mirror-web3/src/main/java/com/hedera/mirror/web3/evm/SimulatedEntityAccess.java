package com.hedera.mirror.web3.evm;

import com.hederahashgraph.api.proto.java.AccountID;
import javax.inject.Singleton;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

import com.hedera.services.transaction.store.contracts.EntityAccess;

@Singleton
public class SimulatedEntityAccess implements EntityAccess {
    @Override
    public boolean isExtant(AccountID id) {
        return false;
    }

    @Override
    public void putStorage(AccountID id, UInt256 key, UInt256 value) {

    }

    @Override
    public UInt256 getStorage(AccountID id, UInt256 key) {
        return null;
    }

    @Override
    public void flushStorage() {

    }

    @Override
    public void storeCode(AccountID id, Bytes code) {

    }

    @Override
    public Bytes fetchCodeIfPresent(AccountID id) {
        return null;
    }
}
