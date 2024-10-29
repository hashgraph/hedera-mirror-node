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

class StateNetworkInfoTest extends Web3IntegrationTest {

    @Resource
    private MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @Resource
    private StateNetworkInfo stateNetworkInfo;

    private static final AccountID NODE_ACCOUNT_ID =
            AccountID.newBuilder().accountNum(3).build();

    private static final int NODE_ID = 2;

    @Test
    void testLedgerId() {
        Bytes expectedLedgerId = Bytes.wrap(new byte[] {0x03});
        assertThat(stateNetworkInfo.ledgerId()).isEqualTo(expectedLedgerId);
    }

    @Test
    void testSelfNodeInfo() {
        SelfNodeInfo selfNodeInfo = stateNetworkInfo.selfNodeInfo();
        assertThat(selfNodeInfo).isNotNull().satisfies(info -> {
            assertThat(info.nodeId()).isEqualTo(0);
            assertThat(info.accountId()).isEqualTo(AccountID.DEFAULT);
            assertThat(info.hapiVersion()).isEqualTo(mirrorNodeEvmProperties.getSemanticEvmVersion());
        });
    }

    @Test
    void testAddressBookIsNotEmpty() {
        assertThat(stateNetworkInfo.addressBook()).isNotEmpty();
    }

    @Test
    void testNodeInfo() {
        NodeInfo nodeInfo = stateNetworkInfo.nodeInfo(NODE_ID);
        assertThat(nodeInfo).isNotNull().satisfies(info -> {
            assertThat(info.nodeId()).isEqualTo(NODE_ID);
            assertThat(info.accountId()).isEqualTo(NODE_ACCOUNT_ID);
            assertThat(info.memo()).isEqualTo("");
            assertThat(info.externalHostName()).isEqualTo("");
            assertThat(info.externalPort()).isEqualTo(0);
            assertThat(info.hexEncodedPublicKey()).isEqualTo("");
            assertThat(info.stake()).isEqualTo(0L);
            assertThat(info.sigCertBytes()).isEqualTo(Bytes.EMPTY);
            assertThat(info.internalHostName()).isEqualTo("");
            assertThat(info.internalPort()).isEqualTo(0);
            assertThat(info.selfName()).isEqualTo("");
        });
    }

    @Test
    void testNodeInfoInvalid() {
        NodeInfo nodeInfo = stateNetworkInfo.nodeInfo(Integer.MAX_VALUE);
        assertThat(nodeInfo).isNull();
    }

    @Test
    void testContainsNode() {
        assertThat(stateNetworkInfo.containsNode(NODE_ID)).isTrue();
    }

    @Test
    void testContainsNodeInvalid() {
        assertThat(stateNetworkInfo.containsNode(Integer.MAX_VALUE)).isFalse();
    }
}
