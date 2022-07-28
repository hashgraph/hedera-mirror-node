/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.transaction.operation.context;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.AccountStorageEntry;
import java.util.NavigableMap;

//FUTURE WORK to be implemented
public class HederaStackedWorldStateUpdater {

    private final WorldLedgers trackingLedgers;

    public HederaStackedWorldStateUpdater(WorldLedgers trackingLedgers) {
        this.trackingLedgers = trackingLedgers;
    }

    public Account get(final Address addressOrAlias) {
        return new Account() {
            @Override
            public Address getAddress() {
                return null;
            }

            @Override
            public Hash getAddressHash() {
                return null;
            }

            @Override
            public long getNonce() {
                return 0;
            }

            @Override
            public Wei getBalance() {
                return null;
            }

            @Override
            public Bytes getCode() {
                return null;
            }

            @Override
            public Hash getCodeHash() {
                return null;
            }

            @Override
            public UInt256 getStorageValue(UInt256 uInt256) {
                return null;
            }

            @Override
            public UInt256 getOriginalStorageValue(UInt256 uInt256) {
                return null;
            }

            @Override
            public NavigableMap<Bytes32, AccountStorageEntry> storageEntriesFrom(Bytes32 bytes32, int i) {
                return null;
            }
        };
    }

    public byte[] permissivelyUnaliased(final byte[] evmAddress) {
        return evmAddress;
    }

    public boolean contractIsTokenTreasury(Address toBeDeleted) {
        return false;
    }

    public boolean contractHasAnyBalance(Address toBeDeleted) {
        return false;
    }

    public boolean contractOwnsNfts(Address toBeDeleted) {
        return false;
    }

    public WorldLedgers trackingLedgers() {
        return null;
    }

    public ContractAliases aliases() {
        return trackingLedgers.aliases();
    }
}
