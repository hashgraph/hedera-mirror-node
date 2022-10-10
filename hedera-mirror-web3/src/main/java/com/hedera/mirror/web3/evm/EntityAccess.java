package com.hedera.mirror.web3.evm;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountID;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
//FUTURE WORK move the definition of this interface to hedera-evm-api library
public interface EntityAccess {

    /* --- Account access --- */
    long getBalance(AccountID id);

    boolean isDeleted(Address address);

    boolean isExtant(AccountID id);

    boolean isTokenAccount(Address address);

    UInt256 getStorage(AccountID id, UInt256 key);

    Bytes fetchCodeIfPresent(AccountID id);
}

