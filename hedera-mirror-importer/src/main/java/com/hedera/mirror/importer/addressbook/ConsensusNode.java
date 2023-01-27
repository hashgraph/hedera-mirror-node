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
import org.jetbrains.annotations.NotNull;

import com.hedera.mirror.common.domain.entity.EntityId;

/**
 * Represents a consensus node while abstracting away the possible different sources of node information.
 */
public interface ConsensusNode extends Comparable<ConsensusNode> {

    default int compareTo(@NotNull ConsensusNode other) {
        return Long.compare(getNodeId(), other.getNodeId());
    }

    EntityId getNodeAccountId();

    long getNodeId();

    PublicKey getPublicKey();

    /**
     * The node's current stake in tinybars. If staking is not activated, this will be set to one.
     *
     * @return The current node stake in tinybars
     */
    long getStake();

    /**
     * The network's current total aggregate stake in tinybars. If staking is not activated, this will be set to the
     * number of nodes in the address book.
     *
     * @return the current total network stake in tinybars
     */
    long getTotalStake();
}
