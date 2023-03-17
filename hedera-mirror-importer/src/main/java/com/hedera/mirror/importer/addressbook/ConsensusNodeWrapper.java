package com.hedera.mirror.importer.addressbook;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import java.security.PublicKey;
import java.util.Objects;
import lombok.Value;

import com.hedera.mirror.common.domain.addressbook.AddressBookEntry;
import com.hedera.mirror.common.domain.addressbook.NodeStake;
import com.hedera.mirror.common.domain.entity.EntityId;

@Value
final class ConsensusNodeWrapper implements ConsensusNode {

    private final AddressBookEntry addressBookEntry;
    private final NodeStake nodeStake;
    private final long nodeCount;
    private final long totalStake;

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ConsensusNode node) {
            return Objects.equals(node.getNodeId(), getNodeId());
        }

        return false;
    }

    public long getNodeId() {
        return addressBookEntry.getNodeId();
    }

    public EntityId getNodeAccountId() {
        return addressBookEntry.getNodeAccountId();
    }

    @Override
    public PublicKey getPublicKey() {
        return addressBookEntry.getPublicKeyObject();
    }

    @Override
    public long getStake() {
        if (totalStake > 0) {
            return nodeStake != null ? nodeStake.getStake() : 0L;
        } else {
            return 1L;
        }
    }

    public long getTotalStake() {
        return totalStake > 0 ? totalStake : nodeCount;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(getNodeId());
    }

    @Override
    public String toString() {
        return String.valueOf(getNodeId());
    }
}
