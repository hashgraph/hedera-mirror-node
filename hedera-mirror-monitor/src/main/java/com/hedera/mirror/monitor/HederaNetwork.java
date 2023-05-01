/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.monitor;

import com.hedera.hashgraph.sdk.Client;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import lombok.Getter;

@Getter
public enum HederaNetwork {
    MAINNET("mainnet-public"),
    PREVIEWNET("previewnet"),
    TESTNET("testnet"),
    OTHER("");

    @Getter(lazy = true)
    private final Set<NodeProperties> nodes = nodes();

    private final MirrorNodeProperties mirrorNode;

    HederaNetwork(String network) {
        this.mirrorNode = mirrorNode(network);
    }

    private MirrorNodeProperties mirrorNode(String environment) {
        String host = environment + ".mirrornode.hedera.com";
        MirrorNodeProperties mirrorNodeProperties = new MirrorNodeProperties();
        mirrorNodeProperties.getGrpc().setHost(host);
        mirrorNodeProperties.getRest().setHost(host);
        return mirrorNodeProperties;
    }

    private Set<NodeProperties> nodes() {
        if (this == OTHER) {
            return Set.of();
        }

        try (var client = Client.forName(name().toLowerCase())) {
            return client.getNetwork().entrySet().stream()
                    .map(e -> new NodeProperties(e.getValue().toString(), e.getKey()))
                    .collect(Collectors.toSet());
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
    }
}
