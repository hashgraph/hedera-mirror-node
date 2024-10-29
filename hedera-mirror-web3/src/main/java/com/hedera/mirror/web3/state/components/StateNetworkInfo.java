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
public class StateNetworkInfo implements NetworkInfo {

    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    private static final Bytes DEV_LEDGER_ID = Bytes.wrap(new byte[] {0x03});
    private static final List<NodeInfo> FAKE_NODE_INFOS = List.of(
            fakeInfoWith(2L, AccountID.newBuilder().accountNum(3).build()),
            fakeInfoWith(4L, AccountID.newBuilder().accountNum(4).build()),
            fakeInfoWith(8L, AccountID.newBuilder().accountNum(5).build()));

    @Nonnull
    @Override
    public Bytes ledgerId() {
        return DEV_LEDGER_ID;
    }

    @Nonnull
    @Override
    public SelfNodeInfo selfNodeInfo() {
        return mockSelfNodeInfo();
    }

    @Nonnull
    @Override
    public List<NodeInfo> addressBook() {
        return FAKE_NODE_INFOS;
    }

    @Nullable
    @Override
    public NodeInfo nodeInfo(long nodeId) {
        return FAKE_NODE_INFOS.stream()
                .filter(node -> node.nodeId() == nodeId)
                .findFirst()
                .orElse(null);
    }

    @Override
    public boolean containsNode(final long nodeId) {
        return nodeInfo(nodeId) != null;
    }

    private static NodeInfo fakeInfoWith(final long nodeId, @Nonnull final AccountID nodeAccountId) {
        return new NodeInfo() {
            @Override
            public long nodeId() {
                return nodeId;
            }

            @Override
            public AccountID accountId() {
                return nodeAccountId;
            }

            @Override
            public String memo() {
                return "";
            }

            @Override
            public String externalHostName() {
                return "";
            }

            @Override
            public int externalPort() {
                return 0;
            }

            @Override
            public String hexEncodedPublicKey() {
                return "";
            }

            @Override
            public long stake() {
                return 0L;
            }

            @Override
            public Bytes sigCertBytes() {
                return Bytes.EMPTY;
            }

            @Override
            public String internalHostName() {
                return "";
            }

            @Override
            public int internalPort() {
                return 0;
            }

            @Override
            public String selfName() {
                return "";
            }
        };
    }

    /**
     * Returns a {@link SelfNodeInfo} that is a complete mock other than the software version present in the
     * given configuration.
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
