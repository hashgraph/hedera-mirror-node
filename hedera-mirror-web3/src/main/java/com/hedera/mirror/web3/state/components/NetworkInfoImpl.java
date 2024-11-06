/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.node.app.info.SelfNodeInfoImpl;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.info.NetworkInfo;
import com.swirlds.state.spi.info.NodeInfo;
import com.swirlds.state.spi.info.SelfNodeInfo;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Named;
import java.util.List;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
public class NetworkInfoImpl implements NetworkInfo {

    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @Nonnull
    @Override
    public Bytes ledgerId() {
        throw new UnsupportedOperationException("Ledger ID is not supported.");
    }

    @Nonnull
    @Override
    public SelfNodeInfo selfNodeInfo() {
        return mockSelfNodeInfo();
    }

    @Nonnull
    @Override
    public List<NodeInfo> addressBook() {
        return List.of();
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

    /**
     * Returns a {@link SelfNodeInfo} that is a complete mock other than the software version present in the given
     * configuration.
     *
     * @return a mock self node info
     */
    private SelfNodeInfo mockSelfNodeInfo() {
        return new SelfNodeInfoImpl(
                0,
                AccountID.DEFAULT,
                0,
                "",
                0,
                "",
                0,
                "",
                "",
                Bytes.EMPTY,
                mirrorNodeEvmProperties.getSemanticEvmVersion(),
                "");
    }
}
