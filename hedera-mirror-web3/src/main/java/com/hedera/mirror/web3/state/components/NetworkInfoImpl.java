/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.state.components;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.State;
import com.swirlds.state.lifecycle.info.NetworkInfo;
import com.swirlds.state.lifecycle.info.NodeInfo;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Named;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
public class NetworkInfoImpl implements NetworkInfo {

    @Nonnull
    @Override
    public Bytes ledgerId() {
        throw new UnsupportedOperationException("Ledger ID is not supported.");
    }

    @Nonnull
    @Override
    public NodeInfo selfNodeInfo() {
        return nodeInfo();
    }

    @Nonnull
    @Override
    public List<NodeInfo> addressBook() {
        return List.of(nodeInfo());
    }

    @Nullable
    @Override
    public NodeInfo nodeInfo(long nodeId) {
        return null;
    }

    @Override
    public boolean containsNode(final long nodeId) {
        return nodeInfo(nodeId) != null;
    }

    @Override
    public void updateFrom(State state) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * Returns a {@link NodeInfo} that is a complete mock other than the software version present in the given
     * configuration.
     *
     * @return a mock self node info
     */
    private NodeInfo nodeInfo() {
        return new NodeInfo() {
            @Override
            public long nodeId() {
                return 0;
            }

            @Override
            public AccountID accountId() {
                return AccountID.DEFAULT;
            }

            @Override
            public long weight() {
                return 0;
            }

            @Override
            public Bytes sigCertBytes() {
                return Bytes.EMPTY;
            }

            @Override
            public List<ServiceEndpoint> gossipEndpoints() {
                return Collections.emptyList();
            }

            @Override
            public String hexEncodedPublicKey() {
                return "";
            }
        };
    }
}
