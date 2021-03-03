package com.hedera.mirror.test.e2e.acceptance.client;

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

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import lombok.Value;
import lombok.extern.log4j.Log4j2;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;

@Log4j2
@Value
public class SDKClient {
    private final Client client;
    private final PublicKey payerPublicKey;
    private final PrivateKey operatorKey;
    private final AccountId operatorId;
    private final String mirrorNodeAddress;
    private final long messageTimeoutSeconds;
    private final AccountId nodeId;
    private final Hbar maxTransactionFee;

    public SDKClient(AcceptanceTestProperties acceptanceTestProperties) throws InterruptedException {

        // Grab configuration variables from the .env file
        operatorId = AccountId.fromString(acceptanceTestProperties.getOperatorId());
        operatorKey = PrivateKey.fromString(acceptanceTestProperties.getOperatorKey());
        payerPublicKey = operatorKey.getPublicKey();
        mirrorNodeAddress = acceptanceTestProperties.getMirrorNodeAddress();
        messageTimeoutSeconds = acceptanceTestProperties.getMessageTimeout().toSeconds();
        nodeId = AccountId.fromString(acceptanceTestProperties.getNodeId());
        maxTransactionFee = Hbar.fromTinybars(acceptanceTestProperties.getMaxTinyBarTransactionFee());

        Client client;
        var nodeAddress = acceptanceTestProperties.getNodeAddress();
        if (nodeAddress.equalsIgnoreCase("testnet")) {
            log.debug("Creating SDK client for TestNet");
            client = Client.forTestnet();
        } else if (nodeAddress.equalsIgnoreCase("mainnet")) {
            log.debug("Creating SDK client for MainNet");
            client = Client.forMainnet();
        } else {
            var nodeId = AccountId.fromString(acceptanceTestProperties.getNodeId());
            log.debug("Creating SDK client for node {} at {}", nodeId, nodeAddress);

            // Build client
            client = Client.forNetwork(Map.of(nodeAddress, nodeId));
        }

        client.setOperator(operatorId, operatorKey);
        client.setMirrorNetwork(List.of(acceptanceTestProperties.getMirrorNodeAddress()));

        this.client = client;
    }

    public ExpandedAccountId getExpandedOperatorAccountId() {
        return new ExpandedAccountId(operatorId, operatorKey, payerPublicKey);
    }

    public void close() throws TimeoutException {
        client.close();
    }
}
