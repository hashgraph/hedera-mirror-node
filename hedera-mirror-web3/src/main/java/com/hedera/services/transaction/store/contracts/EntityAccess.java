package com.hedera.services.transaction.store.contracts;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;

public interface EntityAccess {

    /* --- Account access --- */
    long getBalance(Address id);

    boolean isDeleted(Address id);

    boolean isDetached(Address id);

    boolean isExtant(Address id);

    boolean isTokenAccount(Address address);

    /* --- Storage access --- */
    void putStorage(Address id, UInt256 key, UInt256 value);

    //FUTURE WORK Will be needed for opcodes
    UInt256 getStorage(Address id, UInt256 key);

    void flushStorage();

    /* --- Bytecode access --- */
    void storeCode(Address id, Bytes code);

    /**
     * Returns the bytecode for the contract with the given account id; or null if there is no byte present for this
     * contract.
     *
     * @param id the account id of the target contract
     * @return the target contract's bytecode, or null if it is not present
     */
    //FUTURE WORK Will be needed for CodeCache
    Bytes fetchCodeIfPresent(Address id);
}
