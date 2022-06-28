package com.hedera.services.transaction.store.contracts;

import com.hederahashgraph.api.proto.java.AccountID;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public interface EntityAccess {

    boolean isExtant(AccountID id);

    /* --- Storage access --- */
    void putStorage(AccountID id, UInt256 key, UInt256 value);

    //Will be needed for opcodes
    UInt256 getStorage(AccountID id, UInt256 key);

    void flushStorage();

    /* --- Bytecode access --- */
    void storeCode(AccountID id, Bytes code);

    /**
     * Returns the bytecode for the contract with the given account id; or null if there is no byte present for this
     * contract.
     *
     * @param id the account id of the target contract
     * @return the target contract's bytecode, or null if it is not present
     */
    //Will be needed for CodeCache
    Bytes fetchCodeIfPresent(AccountID id);
}
