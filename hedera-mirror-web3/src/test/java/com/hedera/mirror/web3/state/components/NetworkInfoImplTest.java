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

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.info.NodeInfo;
import com.swirlds.state.spi.info.SelfNodeInfo;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;

class NetworkInfoImplTest extends Web3IntegrationTest {

    private static final int NODE_ID = 2;

    @Resource
    private MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @Resource
    private NetworkInfoImpl networkInfoImpl;

    @Test
    void testLedgerId() {
        assertThat(networkInfoImpl.ledgerId()).isEqualTo(Bytes.EMPTY);
    }

    @Test
    void testSelfNodeInfo() {
        SelfNodeInfo selfNodeInfo = networkInfoImpl.selfNodeInfo();
        assertThat(selfNodeInfo).isNotNull().satisfies(info -> {
            assertThat(info.nodeId()).isZero();
            assertThat(info.accountId()).isEqualTo(AccountID.DEFAULT);
            assertThat(info.hapiVersion()).isEqualTo(mirrorNodeEvmProperties.getSemanticEvmVersion());
        });
    }

    @Test
    void testAddressBookIsEmpty() {
        assertThat(networkInfoImpl.addressBook()).isEmpty();
    }

    @Test
    void testNodeInfo() {
        assertThat(networkInfoImpl.nodeInfo(NODE_ID)).isNull();
    }

    @Test
    void testNodeInfoInvalid() {
        NodeInfo nodeInfo = networkInfoImpl.nodeInfo(Integer.MAX_VALUE);
        assertThat(nodeInfo).isNull();
    }

    @Test
    void testContainsNode() {
        assertThat(networkInfoImpl.containsNode(NODE_ID)).isFalse();
    }

    @Test
    void testContainsNodeInvalid() {
        assertThat(networkInfoImpl.containsNode(Integer.MAX_VALUE)).isFalse();
    }
}
