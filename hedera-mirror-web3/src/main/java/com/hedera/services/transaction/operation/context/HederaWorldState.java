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

import com.hedera.services.transaction.operation.util.BytesComparator;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import java.util.Map;
import java.util.TreeMap;

public class HederaWorldState {
    public class Updater implements HederaWorldUpdater {
        Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> stateChanges =
                new TreeMap<>(BytesComparator.INSTANCE);
        @Override
        public void addSbhRefund(long gasCost) {

        }

        @Override
        public Address newContractAddress(Address sponsor) {
            return null;
        }

        public Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> getStateChanges() {
            return stateChanges;
        }
    }
}
