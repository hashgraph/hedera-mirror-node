package com.hedera.mirror.monitor;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import java.util.Collections;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum HederaNetwork {

    MAINNET(mainnet(), mirrorNode("mainnet")),
    PREVIEWNET(previewnet(), mirrorNode("previewnet")),
    TESTNET(testnet(), mirrorNode("testnet")),
    OTHER(Collections.emptySet(), null);

    private final Set<NodeProperties> nodes;
    private final MirrorNodeProperties mirrorNode;

    private static MirrorNodeProperties mirrorNode(String environment) {
        String host = environment + ".mirrornode.hedera.com";
        MirrorNodeProperties mirrorNodeProperties = new MirrorNodeProperties();
        mirrorNodeProperties.getGrpc().setHost("hcs." + host);
        mirrorNodeProperties.getRest().setHost(host);
        return mirrorNodeProperties;
    }

    private static Set<NodeProperties> mainnet() {
        return Set.of(
                new NodeProperties("0.0.3", "35.237.200.180"),
                new NodeProperties("0.0.4", "35.186.191.247"),
                new NodeProperties("0.0.5", "35.192.2.25"),
                new NodeProperties("0.0.6", "35.199.161.108"),
                new NodeProperties("0.0.7", "35.203.82.240"),
                new NodeProperties("0.0.8", "35.236.5.219"),
                new NodeProperties("0.0.9", "35.197.192.225"),
                new NodeProperties("0.0.10", "35.242.233.154"),
                new NodeProperties("0.0.11", "35.240.118.96"),
                new NodeProperties("0.0.12", "35.204.86.32")
        );
    }

    private static Set<NodeProperties> previewnet() {
        return Set.of(
                new NodeProperties("0.0.3", "0.previewnet.hedera.com"),
                new NodeProperties("0.0.4", "1.previewnet.hedera.com"),
                new NodeProperties("0.0.5", "2.previewnet.hedera.com"),
                new NodeProperties("0.0.6", "3.previewnet.hedera.com")
        );
    }

    private static Set<NodeProperties> testnet() {
        return Set.of(
                new NodeProperties("0.0.3", "0.testnet.hedera.com"),
                new NodeProperties("0.0.4", "1.testnet.hedera.com"),
                new NodeProperties("0.0.5", "2.testnet.hedera.com"),
                new NodeProperties("0.0.6", "3.testnet.hedera.com"),
                new NodeProperties("0.0.7", "4.testnet.hedera.com")
        );
    }
}
