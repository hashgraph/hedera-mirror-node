package com.hedera.mirror.test.e2e.acceptance.client;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.Value;
import lombok.extern.log4j.Log4j2;

import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PrivateKey;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PublicKey;
import com.hedera.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;

@Log4j2
@Value
public class SDKClient {
    private final Client client;
    private final Ed25519PublicKey payerPublicKey;
    private final AccountId operatorId;

    public SDKClient(AcceptanceTestProperties acceptanceTestProperties) {

        // Grab configuration variables from the .env file
        operatorId = AccountId.fromString(acceptanceTestProperties.getOperatorId());
        var operatorKey = Ed25519PrivateKey.fromString(acceptanceTestProperties.getOperatorKey());
        payerPublicKey = operatorKey.publicKey;

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
            client = new Client(Map.of(nodeId, nodeAddress));
        }

        client.setOperator(operatorId, operatorKey);

        this.client = client;
    }

    public void close() throws TimeoutException, InterruptedException {
        log.debug("Closing SDK client, waits up to 10 s for valid close");
        client.close(10, TimeUnit.SECONDS);
    }
}
