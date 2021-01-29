package com.hedera.mirror.grpc.jmeter.props;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import lombok.Value;

import com.hedera.hashgraph.sdk.account.AccountId;

@Value
public class NodeInfo {
    private final AccountId nodeId;
    private final String nodeHost;
    private final String nodePort;

    public NodeInfo(String nodeInfo) {
        String[] nodeParts = nodeInfo.split(":");
        nodeHost = nodeParts[0];
        nodeId = AccountId.fromString(nodeParts[1]);
        nodePort = nodeParts[2];
    }

    public String getNodeAddress() {
        return nodeHost + ":" + nodePort;
    }
}
